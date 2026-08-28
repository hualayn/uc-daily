package com.study.checkin

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.study.checkin.data.AppDatabase
import com.study.checkin.ui.DEFAULT_MED_REMINDER_TIMES
import com.study.checkin.ui.PREF_MED_REMINDER_TIMES
import com.study.checkin.ui.timeToMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 服药提醒（系统通知 + 精确闹钟），独立于 ViewModel/UI：
 * 前台（ViewModel 每分钟同步）与后台（闹钟/开机广播）共用同一套判定与发通知逻辑，
 * 保证应用被系统杀掉（MIUI 等）后，到点仍由系统闹钟唤醒并提醒。
 *
 * 判定口径（与首页铃铛一致）：
 *  应服药总数 = 已到点（<= 当前时刻）的提醒时间个数
 *  实际服药总数 = 今天服药记录总条数
 *  实际 < 应服 → 发出 / 刷新通知（状态栏常驻图标 / 应用图标角标）
 *  实际 >= 应服 → 取消通知
 */
object MedReminder {
    const val CHANNEL_ID = "med_reminder"
    const val NOTIFICATION_ID = 1001
    const val TAG = "MedReminder"

    /**
     * 闹钟广播动作与 PendingIntent 请求码基址。
     * 每个提醒时间占一个请求码槽位（最多 6 个）：系统会按 PendingIntent 去重，
     * 共用同一个 PendingIntent 时后设置的闹钟会顶掉先设置的，必须逐槽区分。
     */
    private const val ALARM_ACTION = "com.study.checkin.MED_ALARM"
    private const val ALARM_REQUEST_CODE_BASE = 1000
    private const val MAX_MED_TIMES = 6

    /**
     * 通知角标样式（setBadgeIconType 取值）：0=无角标 1=圆点 2=数字。
     * 高版本 SDK 移除了 Notification.BADGE_ICON_TYPE_* 常量，但接口取值沿用原约定，故硬编码。
     */
    private const val BADGE_ICON_TYPE_NUMERICAL = 2

    /** 当前提醒状态 */
    data class Status(
        val dueTimes: List<String>,
        val taken: Int,
        val missedCount: Int
    ) {
        val missing: Boolean get() = missedCount > 0
    }

    private fun reminderTimes(ctx: Context): List<String> {
        // 与 MealLogViewModel 使用同一个 prefs 文件（app_prefs）
        val raw = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString(PREF_MED_REMINDER_TIMES, null) ?: return DEFAULT_MED_REMINDER_TIMES
        val list = raw.split(",").map { it.trim() }
            .filter { it.matches(Regex("\\d{2}:\\d{2}")) }.sorted()
        return list.ifEmpty { DEFAULT_MED_REMINDER_TIMES }
    }

    /**
     * 读取当前提醒状态（直读 SharedPreferences 与 Room，后台可安全调用）。
     * 挂起函数：Room 默认禁止主线程访问数据库，DB 读取切到 IO 线程。
     */
    suspend fun status(ctx: Context): Status {
        val now = LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        val dueTimes = reminderTimes(ctx).filter { t ->
            timeToMinutes(t)?.let { m -> nowMin >= m } == true
        }
        val taken = withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(ctx)
                .medRecordDao().countByDate(LocalDate.now().toString())
        }
        return Status(dueTimes, taken, (dueTimes.size - taken).coerceAtLeast(0))
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "服药提醒", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "今天的实际服药次数未达到已到点的应服药次数时提醒" }
            )
        }
    }

    /**
     * 同步服药提醒通知：未达应服 → 发出 / 刷新；已到点但已服完或尚未到点 → 取消。
     * 挂起函数（内部 DB 读取在 IO 线程；通知 API 任意线程可调）。
     */
    suspend fun sync(ctx: Context) {
        val app = ctx.applicationContext
        ensureChannel(app)
        val s = status(app)
        Log.i(TAG, "同步提醒：应服=[${s.dueTimes.joinToString("、")}] 已服=${s.taken} 未服=${s.missedCount}")
        val nm = NotificationManagerCompat.from(app)
        if (s.dueTimes.isEmpty() || !s.missing) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        val contentIntent = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_med_notify)
            // 图标 / 应用角标着色（与首页铃铛红点同色）
            .setColor(0xFFE53935.toInt())
            // 角标数量与样式：请求"数字型"角标（红底显示未服药次数）。
            // 是否显示数字由桌面启动器决定：支持角标的（如三星、Nova 等）显示数字红点；
            // 只支持圆点的（如 Pixel、小米）仍显示红点，无跨厂商强制数字的 API
            .setNumber(s.missedCount)
            .setBadgeIconType(BADGE_ICON_TYPE_NUMERICAL)
            .setContentTitle("服药提醒")
            .setContentText("您还有 ${s.missedCount} 次未服药，请尽快服药！")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS：无法发出，等授权后再次同步
            Log.i(TAG, "通知权限未授予，跳过发出通知")
        }
    }

    // region 精确闹钟：应用关闭时到点唤醒（后台可靠性的关键）

    private fun alarmPendingIntent(ctx: Context, slot: Int): PendingIntent {
        val intent = Intent(ctx, MedAlarmReceiver::class.java).setAction(ALARM_ACTION)
        return PendingIntent.getBroadcast(
            ctx,
            ALARM_REQUEST_CODE_BASE + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 是否可用精确闹钟（Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限，清单声明后安装时默认授予） */
    fun canScheduleExactAlarms(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * （重新）为每个提醒时间安排下一次触发：今天的该时间未过则今天触发，否则明天。
     * 先清除本应用已有闹钟再逐个设置，重复调用（启动/改时间/触发后）不会残留旧闹钟。
     * 无精确闹钟权限时退化为不精确闹钟（系统可能合并延后）。
     */
    fun scheduleNext(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        // 先清空全部槽位，避免改动提醒次数/时间后残留旧闹钟
        for (slot in 0 until MAX_MED_TIMES) {
            am.cancel(alarmPendingIntent(app, slot))
        }
        val exactAllowed = canScheduleExactAlarms(app)
        val now = LocalDateTime.now()
        reminderTimes(app).forEachIndexed { slot, t ->
            val minutes = timeToMinutes(t) ?: return@forEachIndexed
            val time = LocalTime.of(minutes / 60, minutes % 60)
            val fireAt = if (time.isAfter(now.toLocalTime())) {
                now.toLocalDate().atTime(time)
            } else {
                now.toLocalDate().plusDays(1).atTime(time)
            }
            val triggerAt = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (triggerAt <= System.currentTimeMillis()) return@forEachIndexed
            val pi = alarmPendingIntent(app, slot)
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
        Log.i(TAG, "已安排服药提醒闹钟：${reminderTimes(app).joinToString("、")}（精确=${exactAllowed}）")
    }

    /**
     * 打开“精确闹钟”权限页（Android 12+）：
     * 13+ 打开系统专项授权页；12 回到系统设置首页。只能在前台（用户点击）时调用。
     */
    fun requestExactAlarmPermission(activity: Activity) {
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${activity.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        activity.startActivity(intent)
    }

    // endregion
}

/**
 * 精确闹钟触发：到点时即便应用处于后台/已被杀死，
 * 先重排今天剩余 + 明天的闹钟，再检查应服情况并通知（发出 / 取消）。
 */
class MedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val ctx = context.applicationContext
        Log.i(MedReminder.TAG, "服药提醒闹钟触发")
        MedReminder.scheduleNext(ctx)
        // sync 是挂起函数（要读数据库）：后台广播里用全局协程 fire-and-forget 执行
        CoroutineScope(Dispatchers.IO).launch { MedReminder.sync(ctx) }
    }
}

/** 系统重启后系统会清空待触发闹钟：重新注册服药提醒闹钟 */
class MedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(MedReminder.TAG, "开机完成，重注册服药提醒闹钟")
            MedReminder.scheduleNext(context.applicationContext)
        }
    }
}

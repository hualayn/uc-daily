package com.ucdaily

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ucdaily.data.AppDatabase
import com.ucdaily.ui.DEFAULT_MED_REMINDER_TIMES
import com.ucdaily.ui.PREF_MED_REMINDER_TIMES
import com.ucdaily.ui.timeToMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 服药提醒（系统通知 + 系统闹钟），独立于 ViewModel/UI：
 * 前台（ViewModel 每分钟同步）与后台（闹钟/开机广播）共用同一套判定与发通知逻辑，
 * 保证应用被系统杀掉（MIUI 等）后，到点仍由系统闹钟唤醒并提醒。
 *
 * 判定口径（与首页铃铛一致）：
 *  应服药总数 = 已到点（<= 当前时刻）的提醒时间个数
 *  实际服药总数 = 今天服药记录总条数
 *  实际 < 应服 → 发出 / 刷新通知；实际 >= 应服 → 取消通知（角标随之清除）
 *
 * 前台行为（需求：打开首页后不再发系统通知，只保留右上角铃铛提醒）：
 *  应用在前台期间（[setAppInForeground]，由 MainActivity 生命周期维护）[sync]
 *  不发送通知，并撤掉后台残留的通知；应用打开时的提醒由首页右上角铃铛承担。
 *  退后台后闹钟 / 后台服务 / 角标刷新照常发通知（含桌面角标恢复）
 *
 * 桌面角标（对照《桌面应用角标问题》Q&A）：
 *  - 角标数值 = 通知栏内（媒体 / 进度条 / 常驻除外）各通知 messageCount 的累加，默认 1；
 *    按《桌面应用角标适配说明》反射写 mMessageCount = 未服次数
 *    （setNumber 是通知面板内小角标、不是桌面角标；不能 setOngoing——常驻通知不统计）
 *  - 用户在桌面点击图标启动应用后，桌面默认隐藏角标（常见问题 3），重新显示只有两条路：
 *    发一条 id 不重复的新通知 / 更新 messageCount。对应本类的发通知策略：
 *    ① 未服次数增加（新到点）、面板无通知（首次 / 被划掉后补发）
 *       → 换新通知 id、走带声音的 [CHANNEL_ID] 通道响铃一次；
 *    ② 每分钟例行刷新、记录服药后次数减少、以及角标重发（[refreshLauncherBadge]）
 *       → 走无声音的 [CHANNEL_ID_SILENT] 通道静默重发（角标重新显示，不响铃）
 *  - 角标重发的时机（实测：MIUI 桌面在应用处于前台期间不处理角标重新显示，
 *    必须等应用真正退到后台后"新通知"到达才会恢复角标）：
 *    仅应用退到后台时（onStop）立即换新 id 刷一次——真机验证该次重发即可恢复
 *    角标；应用打开时重发无效果（前台期间桌面不处理）且多一次通知事件，已移除；
 *    延迟补发反而造成角标闪动，同样不使用
 *  - 后台可靠性：MIUI 等系统会冻结 / 杀掉普通后台进程，应用退后台后每分钟循环
 *    停摆。对策（本项目按需求不使用精确闹钟，一律普通闹钟）：
 *    ① MedReminderService（普通后台服务，无通知、不占通知栏）降低进程被
 *       冻结 / 杀死的概率，进程存活期间其 Handler 定时器到点执行同一套 sync 判定
 *       （未服 → 新 id 响铃通知 + 角标），比普通闹钟更准时（锁屏期间普通闹钟
 *       可能被系统合并、延迟）；
 *    ② AlarmManager 普通闹钟（setAndAllowWhileIdle(RTC_WAKEUP)，无需任何权限）：
 *       到点系统唤醒设备并投递广播，进程被杀也能触发（系统拉起进程），
 *       接收器 goAsync 期间读库 + 发通知——进程被杀后到点提醒的保证
 *  - 通知 id 单调递增并持久化（永不复用），保证：退后台换新 id 后角标必然重新显示、
 *    进程重建后不会误响铃、面板中最多只有一条本应用通知（角标按条累加，两条会翻倍）
 */
object MedReminder {
    /** 带声音通道：未服次数增加 / 首次发出 / 被划掉后补发（响铃提醒） */
    const val CHANNEL_ID = "med_reminder"
    /** 静默通道：例行刷新 / 角标重发（importance 与主通道一致，状态栏图标与桌面角标照常显示统计） */
    const val CHANNEL_ID_SILENT = "med_reminder_silent"
    const val TAG = "MedReminder"

    // 旧版本前台服务通知的通道（通道跨应用更新持久化）：本版本服务已改为
    // 普通后台服务（不持有通知），该通道不再使用，由 ensureChannel 主动删除，
    // 一并清掉可能残留在通知栏的"后台运行中"常驻通知
    private const val LEGACY_SERVICE_CHANNEL_ID = "med_reminder_service"

    /** 通知 id 基址；每次换新 id 时 +1，持久化后永不复用 */
    const val NOTIFICATION_ID_BASE = 1001
    private const val PREF_NOTIFY_ID = "med_notify_id"
    private const val PREF_NOTIFY_COUNT = "med_notify_count"

    /** 闹钟广播动作与 PendingIntent 请求码基址。
     * 每个提醒时间占一个请求码槽位（最多 6 个）：系统会按 PendingIntent 去重，
     * 共用同一个 PendingIntent 时后设置的闹钟会顶掉先设置的，必须逐槽区分。 */
    private const val ALARM_ACTION = "com.ucdaily.MED_ALARM"
    private const val ALARM_REQUEST_CODE_BASE = 1000
    private const val MAX_MED_TIMES = 6

    // region 通知 id / 已发次数状态（持久化，跨进程重建保持；lastPostedMissed = -1 表示面板无本应用通知）

    private var notifyId = NOTIFICATION_ID_BASE - 1
    private var lastPostedMissed = -1
    private var notifyStateLoaded = false

    private fun loadNotifyState(ctx: Context) {
        if (notifyStateLoaded) return
        notifyStateLoaded = true
        val p = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        notifyId = p.getInt(PREF_NOTIFY_ID, NOTIFICATION_ID_BASE - 1)
        lastPostedMissed = p.getInt(PREF_NOTIFY_COUNT, -1)
    }

    private fun saveNotifyState(ctx: Context) {
        ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putInt(PREF_NOTIFY_ID, notifyId)
            .putInt(PREF_NOTIFY_COUNT, lastPostedMissed)
            .apply()
    }

    /** 分配一个从未使用过的新通知 id（"id 不重复"是角标重新显示的前提之一） */
    private fun allocateNotifyId(ctx: Context): Int {
        notifyId += 1
        saveNotifyState(ctx)
        return notifyId
    }

    // endregion

    /**
     * 应用是否在前台（由 MainActivity 生命周期维护）。
     * 前台期间（首页已打开）未服药由首页右上角铃铛提醒，[sync] 不发送系统通知，
     * 并把后台（闹钟 / 角标刷新）发出的残留通知撤掉；退后台后闹钟 / 服务 /
     * 角标刷新照常发通知。
     */
    @Volatile
    private var appInForeground = false

    fun setAppInForeground(foreground: Boolean) {
        appInForeground = foreground
    }

    /**
     * 应用主动打开的临时外部页面（相机 / 相册选择器 / 系统文件保存界面）是否在前台：
     * 此时 Activity 虽然进入停止状态，但用户仍在操作本应用流程——
     * onStop 不能把它当作"退后台"（不能恢复系统通知、不能刷新桌面角标，
     * 否则拍张照就会弹未服药通知）。
     * 置位时机：各 ActivityResult 启动器 launch 之前（MainActivity / ExportDialog）；
     * 复位时机：回到应用（MainActivity.onStart 兜底）、启动器结果回调（成功 / 取消都会回调）、
     * 启动失败时由调用方回滚。
     */
    @Volatile
    private var transientExternalOpen = false

    fun setTransientExternalOpen(open: Boolean) {
        transientExternalOpen = open
    }

    fun isTransientExternalOpen(): Boolean = transientExternalOpen

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
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.med_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = ctx.getString(R.string.med_reminder_channel_desc) }
            )
        }
        // 静默通道：例行刷新 / 角标重发用。importance 与主通道相同 → 状态栏图标、
        // 桌面角标统计行为一致，只是没有任何提示音 / 震动 / 灯
        if (nm.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_SILENT,
                    ctx.getString(R.string.med_reminder_channel_silent),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = ctx.getString(R.string.med_reminder_channel_silent_desc)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                }
            )
        }
        // 清理：旧版本前台服务通道已不再使用（本版本改为无通知的普通后台服务），
        // 删除通道同时清掉可能残留在通知栏的"后台运行中"常驻通知
        nm.deleteNotificationChannel(LEGACY_SERVICE_CHANNEL_ID)
    }

    /**
     * 同步服药提醒通知：未达应服 → 发出 / 刷新；已到点但已服完或尚未到点 → 取消。
     * 应用在前台（首页已打开）时不发送通知——未服药由首页右上角铃铛提醒，
     * 只撤掉后台发出的残留通知（见 [setAppInForeground]）。
     * 挂起函数（内部 DB 读取在 IO 线程；通知 API 任意线程可调）。
     * notifyId / lastPostedMissed 只在主线程读写：本方法会被前台每分钟循环（主线程）
     * 与系统闹钟广播（IO 线程）并发调用，统一切到主线程串行化，避免 id / 计数状态错乱。
     */
    suspend fun sync(ctx: Context) {
        withContext(Dispatchers.Main.immediate) {
            val app = ctx.applicationContext
            loadNotifyState(app)
            ensureChannel(app)
            val s = status(app)
            Log.i(TAG, "同步提醒：应服=[${s.dueTimes.joinToString("、")}] 已服=${s.taken} 未服=${s.missedCount}")
            val nm = NotificationManagerCompat.from(app)
            if (s.dueTimes.isEmpty() || !s.missing) {
                // 未服归零：取消全部本应用通知 → 桌面角标随之清除
                nm.activeNotifications.forEach { nm.cancel(it.id) }
                lastPostedMissed = -1
                saveNotifyState(app)
                return@withContext
            }
            // 应用在前台（首页已打开）：未服药由首页右上角铃铛提醒，不发送系统通知；
            // 撤掉之前后台（闹钟 / 角标刷新）发出的残留通知，状态栏保持干净。
            // 退后台时角标刷新（onStop）会照常重新发出
            if (appInForeground) {
                nm.activeNotifications.forEach { nm.cancel(it.id) }
                lastPostedMissed = -1
                saveNotifyState(app)
                return@withContext
            }
            val active = nm.activeNotifications.firstOrNull()
            // 响铃条件：未服次数增加（新到点未服）、面板中无本通知（首次发出 / 被划掉后补发）；
            // 其余（每分钟例行刷新、记录服药后次数减少）走静默通道重发即可，不打扰用户
            val needAlert = s.missedCount > lastPostedMissed || active == null
            val channelId = if (needAlert) CHANNEL_ID else CHANNEL_ID_SILENT
            val notification = buildNotification(app, s, channelId)
            applyLauncherBadgeCount(notification, s.missedCount)
            val id = if (needAlert) {
                // 换新 id；先撤旧通知，避免面板同时出现两条（角标按 messageCount 累加会翻倍）
                active?.let { nm.cancel(it.id) }
                allocateNotifyId(app)
            } else {
                // 沿用现有 id 原地更新（不产生"移除"事件，桌面角标按新 messageCount 刷新）
                active!!.id
            }
            try {
                nm.notify(id, notification)
            } catch (e: SecurityException) {
                // Android 13+ 未授予 POST_NOTIFICATIONS：无法发出，等授权后再次同步
                Log.i(TAG, "通知权限未授予，跳过发出通知")
                return@withContext
            }
            lastPostedMissed = s.missedCount
            saveNotifyState(app)
            Log.i(TAG, "同步提醒完成：通知 id=$id ${if (needAlert) "（新 id，响铃）" else "（沿用 id，静默）"}")
        }
    }

    /**
     * 重新显示桌面角标：用户在桌面点击图标启动应用时桌面默认隐藏角标
     * （《桌面应用角标问题》常见问题 3），重新显示的途径是"发一条 id 不重复的新通知"
     * 或"更新 messageCount"。这里换新通知 id、走静默通道重发：角标重新显示且不响铃。
     * 调用时机（见类注释"角标重发的时机"）：仅应用退到后台时（onStop）一次
     * （trigger 参数仅用于日志定位）。
     * 未服归零时面板无通知、无角标可刷，直接返回。
     * 挂起函数（内部 DB 读取在 IO 线程），与 [sync] 同样切到主线程串行化状态。
     */
    suspend fun refreshLauncherBadge(ctx: Context, trigger: String) {
        withContext(Dispatchers.Main.immediate) {
            val app = ctx.applicationContext
            loadNotifyState(app)
            ensureChannel(app)
            val s = status(app)
            if (s.dueTimes.isEmpty() || !s.missing) return@withContext
            val nm = NotificationManagerCompat.from(app)
            val notification = buildNotification(app, s, CHANNEL_ID_SILENT)
            applyLauncherBadgeCount(notification, s.missedCount)
            // 撤掉现有通知（无论 id 是否相同）再换新 id 发出——"id 不重复"是角标重新显示的前提
            nm.activeNotifications.forEach { nm.cancel(it.id) }
            val id = allocateNotifyId(app)
            try {
                nm.notify(id, notification)
            } catch (e: SecurityException) {
                Log.i(TAG, "通知权限未授予，跳过角标刷新")
                return@withContext
            }
            lastPostedMissed = s.missedCount
            saveNotifyState(app)
            Log.i(TAG, "角标刷新（$trigger）：新通知 id=$id 未服=${s.missedCount}")
        }
    }

    /** 构建服药提醒通知（内容相同；通道决定是否响铃） */
    private fun buildNotification(app: Context, s: Status, channelId: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_med_notify)
            // 图标 / 应用角标着色（与首页铃铛红点同色）
            .setColor(0xFFE53935.toInt())
            // 通知自身小角标的数字（通知面板内图标旁的小角标，与桌面应用角标不是同一字段）
            .setNumber(s.missedCount)
            .setContentTitle(app.getString(R.string.med_reminder_notification_title))
            .setContentText(app.getString(R.string.med_reminder_missed, s.missedCount))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // 注意不能 setOngoing(true)：桌面角标不统计常驻通知（媒体 / 进度条 / 常驻均排除）；
            // "未服期间常驻提醒"由应用存活期每分钟重发 + 到点闹钟重发保证，
            // 未服归零时取消通知，角标随之清除
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    /**
     * 写框架 Notification 的 messageCount——桌面应用角标的数值来源（《桌面应用角标适配说明》）。
     * 桌面把通知栏内所有通知（媒体 / 进度条 / 常驻除外）的 messageCount 累加作为角标值，
     * 每条通知默认 1；它不是 setNumber 对应的字段（后者只影响通知面板内的小角标）。
     * 字段名 / 机制可能随系统版本变化，失败时保留默认值仅记日志（角标降级为 1）。
     */
    private fun applyLauncherBadgeCount(notification: Notification, count: Int) {
        try {
            val field = Notification::class.java.getDeclaredField("mMessageCount")
            field.isAccessible = true
            field.set(notification, count)
        } catch (t: Throwable) {
            Log.w(TAG, "设置 messageCount 失败，桌面角标退回默认值 1：$t")
        }
    }

    // region 后台服务：到点检查（前台服务保活，实现见 MedReminderService）

    /** 今天尚未到点（含当前这一分钟）的最早提醒时间；无剩余时间返回 null */
    fun nextReminderTime(ctx: Context): LocalTime? {
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val minutes = reminderTimes(ctx).mapNotNull { timeToMinutes(it) }
            .filter { it >= nowMin }
            .minOrNull() ?: return null
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    /**
     * 今天还有未到点时间时启动 / 刷新后台提醒服务（普通后台服务，不持有通知）：
     * 应用启动、修改提醒时间、闹钟触发、开机都会经 [scheduleNext] 走到这里。
     * 保活的进程被冻结的概率更低，其 Handler 定时器保证进程存活期间
     * 到点检查执行（未服 → 发通知 + 角标）；锁屏后进程若被冻结 / 杀掉，
     * 由 AlarmManager 闹钟（系统唤醒，见 [scheduleNext]）兜底。
     * Android 12+ 从受限后台（如开机广播）启动会被系统拒绝，仅记日志——
     * 不影响到点提醒（闹钟广播 goAsync 期间完成检查），服务只是进程存活期的准时性保障。
     */
    fun startBackgroundService(ctx: Context) {
        if (nextReminderTime(ctx) == null) return
        try {
            ctx.startService(Intent(ctx, MedReminderService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "启动后台提醒服务被系统拒绝：$e")
        }
    }

    // endregion

    // region 系统闹钟：应用关闭时到点唤醒（后台可靠性的关键）

    private fun alarmPendingIntent(ctx: Context, slot: Int): PendingIntent {
        val intent = Intent(ctx, MedAlarmReceiver::class.java).setAction(ALARM_ACTION)
        return PendingIntent.getBroadcast(
            ctx,
            ALARM_REQUEST_CODE_BASE + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * （重新）为每个提醒时间安排下一次触发：今天的该时间未过则今天触发，否则明天。
     * 先清除本应用已有闹钟再逐个设置，重复调用（启动/改时间/触发后）不会残留旧闹钟。
     *
     * 按需求一律使用**普通**闹钟（setAndAllowWhileIdle(RTC_WAKEUP)），不使用精确闹钟：
     * 无需任何权限；到点系统唤醒设备并投递广播，进程被杀也能触发（系统拉起进程）。
     * 注意：锁屏 Doze 期间系统可能合并闹钟、略有延迟，到点提醒的准时性由
     * 前台服务定时器（进程存活期间）弥补，见 [startBackgroundService]。
     */
    fun scheduleNext(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        // 先清空全部槽位，避免改动提醒次数/时间后残留旧闹钟
        for (slot in 0 until MAX_MED_TIMES) {
            am.cancel(alarmPendingIntent(app, slot))
        }
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
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        Log.i(TAG, "已安排服药提醒闹钟：${reminderTimes(app).joinToString("、")}")
        // 闹钟之外再上后台服务定时器双保险（进程存活期间到点必有检查）
        startBackgroundService(app)
    }

    // endregion
}

/**
 * 闹钟触发：到点时即便应用处于后台/已被杀死，
 * 先重排今天剩余 + 明天的闹钟，再检查应服情况并通知（发出 / 取消）。
 *
 * 必须 goAsync()：onReceive 返回后系统会立即回收刚为广播拉起的进程，
 * fire-and-forget 协程（读库 + 发通知）大概率被杀在半路，导致到点没有通知。
 * goAsync() 把进程"借住"到异步工作完成（pending.finish()）为止。
 */
class MedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val ctx = context.applicationContext
        Log.i(MedReminder.TAG, "服药提醒闹钟触发")
        MedReminder.scheduleNext(ctx)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MedReminder.sync(ctx)
            } finally {
                pending.finish()
            }
        }
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

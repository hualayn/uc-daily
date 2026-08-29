package com.study.checkin

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 服药提醒后台服务（普通后台服务：不持有通知，不占用通知栏）。
 *
 * 作用：进程存活期间，Handler 定时器到点执行与前台同一套 [MedReminder.sync] 判定
 * （未服 → 新通知 id 响铃 + 桌面角标）；锁屏期间比普通闹钟更准时
 * （普通闹钟可能被系统合并、延迟）。
 * 进程被冻结 / 杀掉后本服务失效，到点提醒由 AlarmManager 普通闹钟兜底
 * （系统唤醒并投递广播，进程被杀也能触发）。
 *
 * 为什么不用前台服务：前台服务必须持有常驻通知，MIUI 会无视通道重要性在
 * 通知栏显示它，打扰用户；普通后台服务没有通知，满足"后台运行但不占通知栏"。
 *
 * 生命周期：
 * - [MedReminder.scheduleNext] 末尾统一启动（应用启动 / 改提醒时间 / 闹钟触发 / 开机），
 *   仅当今天还有未到点时间。Android 12+ 从受限后台（闹钟 / 开机广播）启动会被
 *   系统拒绝，仅记日志，不影响到点闹钟提醒。
 * - 定时器依次覆盖今天剩余的每个提醒时间，每次到点检查后安排下一个
 * - 今天时间全部过完 → stopSelf；系统杀掉服务后 START_STICKY 自动重建并重排
 */
class MedReminderService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCheck: Runnable? = null
    /** 服务存活期间协程不会被回收，用服务级 scope 跑挂起逻辑 */
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 普通后台服务：不发出任何通知
        Log.i(MedReminder.TAG, "后台提醒服务已启动")
        scheduleNextCheck()
        return START_STICKY
    }

    /** 安排今天下一个未到点提醒时间的检查；没有剩余时间则停止服务 */
    private fun scheduleNextCheck() {
        pendingCheck?.let { handler.removeCallbacks(it) }
        val next = MedReminder.nextReminderTime(this)
        if (next == null) {
            Log.i(MedReminder.TAG, "今天已无剩余提醒时间，停止后台提醒服务")
            stopSelf()
            return
        }
        val run = Runnable {
            // 到点：与前台同一套判定（未服 → 新 id 响铃通知 + 角标）；
            // 服务存活期间 fire-and-forget 协程不会被中途回收
            serviceScope.launch { MedReminder.sync(this@MedReminderService) }
            scheduleNextCheck()
        }
        pendingCheck = run
        val triggerAt = LocalDateTime.of(LocalDate.now(), next)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) {
            handler.post(run)
        } else {
            handler.postAtTime(run, triggerAt)
        }
        Log.i(MedReminder.TAG, "后台提醒服务：下次到点检查 $next")
    }

    override fun onDestroy() {
        pendingCheck?.let { handler.removeCallbacks(it) }
        pendingCheck = null
        serviceScope.cancel()
        Log.i(MedReminder.TAG, "后台提醒服务已停止")
    }
}

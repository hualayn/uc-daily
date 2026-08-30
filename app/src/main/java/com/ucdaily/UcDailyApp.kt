package com.ucdaily

import android.app.Application
import android.content.Context

/**
 * 应用入口：按"我的 → 语言"所选语言应用资源（默认跟随系统）。
 * 应用级上下文本地化后，后台（闹钟 / 开机广播 / 服务）发出的
 * 服药提醒通知与通知渠道文案也随所选语言显示。
 */
class UcDailyApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.localizedContext(base))
    }
}

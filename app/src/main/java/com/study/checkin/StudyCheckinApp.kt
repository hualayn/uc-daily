package com.study.checkin

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用入口：固定应用语言为简体中文。
 * 应用界面全部为中文，同时保证 M3 日期选择器的标题 / 星期 / 月份名等
 * 系统资源文案在设备语言为英文时也显示为中文。
 */
class StudyCheckinApp : Application() {
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.SIMPLIFIED_CHINESE)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
}

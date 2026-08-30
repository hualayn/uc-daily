package com.ucdaily

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内多语言（"我的 → 语言"）：
 * - 默认跟随系统语言；用户可选择 11 种语言之一（含简体中文 / 英文）；
 * - 选择在 SharedPreferences（app_prefs / app_language）持久化；
 * - 在 Application 与 Activity 的 attachBaseContext 应用（[localizedContext]），
 *   使界面资源、通知渠道 / 提醒通知文案随所选语言显示；
 * - 切换语言后调用 Activity.recreate() 即时生效。
 *
 * 说明：语言包随 AAB 打包，通过 Google Play 的"仅下发与用户相关的语言"能力
 * 按设备语言分发；新版本（含新语言）通过 Google Play Core 应用内更新下发。
 */
object AppLocale {
    const val PREFS_NAME = "app_prefs"
    const val PREF_KEY_LANGUAGE = "app_language"
    const val TAG_SYSTEM = "system"

    /** 一个可选语言：tag 持久化，locale 应用，endonym 为语言自称（system 项为 null，用 R.string.language_system） */
    data class Language(
        val tag: String,
        val locale: Locale?,
        val endonym: String?
    )

    /** 可选语言列表（顺序即"我的 → 语言"对话框展示顺序） */
    val LANGUAGES: List<Language> = listOf(
        Language(TAG_SYSTEM, null, null),
        Language("zh", Locale.SIMPLIFIED_CHINESE, "中文"),
        Language("en", Locale.ENGLISH, "English"),
        Language("ja", Locale.JAPANESE, "日本語"),
        Language("ko", Locale.KOREAN, "한국어"),
        Language("fr", Locale.FRENCH, "Français"),
        Language("de", Locale.GERMAN, "Deutsch"),
        Language("it", Locale.ITALIAN, "Italiano"),
        Language("es", Locale("es"), "Español"),
        Language("pt", Locale("pt"), "Português"),
        Language("ru", Locale("ru"), "Русский"),
        Language("ar", Locale("ar"), "العربية")
    )

    /** 当前选择（未设置 = 跟随系统） */
    fun currentTag(ctx: Context): String =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY_LANGUAGE, TAG_SYSTEM) ?: TAG_SYSTEM

    fun localeForTag(tag: String): Locale? =
        LANGUAGES.firstOrNull { it.tag == tag }?.locale

    /** 语言在菜单上的展示名（跟随系统项走资源，随当前 UI 语言本地化） */
    fun endonymOf(tag: String): String? =
        LANGUAGES.firstOrNull { it.tag == tag }?.endonym

    /**
     * 把所选语言应用到 base 上下文（跟随系统时原样返回）。
     * 可在 Application / Activity 的 attachBaseContext 安全调用
     * （直接读原始 SharedPreferences 文件，不依赖完整 Context 初始化）。
     */
    fun localizedContext(base: Context): Context {
        val locale = localeForTag(currentTag(base)) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

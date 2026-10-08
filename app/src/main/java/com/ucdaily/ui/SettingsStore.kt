package com.ucdaily.ui

import android.app.Application
import android.content.res.Configuration
import com.ucdaily.AppLocale
import com.ucdaily.R
import com.ucdaily.data.MedRecordDao
import org.json.JSONArray
import java.util.Locale

/** 头像选项：default（通用人物）/ boy（男生）/ girl（女生），SharedPreferences 持久化 */
const val AVATAR_DEFAULT = "default"
const val AVATAR_BOY = "boy"
const val AVATAR_GIRL = "girl"

/** 常用药物列表：SharedPreferences 键（JSON 数组字符串）与最大条数 */
const val PREF_COMMON_MED_NAMES = "common_med_names"
const val MAX_COMMON_MED_NAMES = 12

/** 主题模式 / 字体大小 / 服药提醒时间：SharedPreferences 键（逗号分隔的 HH:mm 列表） */
const val PREF_THEME_MODE = "theme_mode"
const val PREF_FONT_SIZE = "font_size"
const val PREF_MED_REMINDER_TIMES = "med_reminder_times"

/** 首页寄语横幅列表：SharedPreferences 键（JSON 数组字符串；空列表 = 首页回退轮播内置默认寄语） */
const val PREF_HOME_SLOGANS = "home_slogans"

/** 单条寄语最大长度 */
const val MAX_HOME_SLOGAN_LEN = 30

/** 内置默认寄语的多语言资源 id（顺序固定；展示/落库时按当前语言解析为字符串） */
val DEFAULT_HOME_SLOGANS_RES = listOf(
    R.string.default_slogan_1,
    R.string.default_slogan_2,
    R.string.default_slogan_3,
    R.string.default_slogan_4,
    R.string.default_slogan_5,
    R.string.default_slogan_6,
    R.string.default_slogan_7,
    R.string.default_slogan_8
)

/** 按当前应用语言解析内置默认寄语（新数据/恢复默认时使用） */
fun defaultHomeSlogans(app: Application): List<String> =
    DEFAULT_HOME_SLOGANS_RES.map { app.getString(it) }

/** 服药提醒时间的默认值与扩充池（次数增加时按序补位） */
val DEFAULT_MED_REMINDER_TIMES = listOf("08:00", "14:00", "20:00")
val MED_REMINDER_TIME_POOL = listOf("08:00", "12:00", "16:00", "20:00", "22:00", "23:00", "06:00", "10:00", "18:00")

/**
 * 设置持久化（SharedPreferences：app_prefs）：
 * 昵称 / 头像 / 主题 / 字体 / 语言 / 首页寄语 / 服药提醒时间 / 常用药物。
 * ViewModel 只持有 UI 状态，所有设置读写委托到这里。
 */
class AppSettingsStore(
    private val app: Application,
    private val medDao: MedRecordDao
) {
    private val prefs = app.getSharedPreferences("app_prefs", Application.MODE_PRIVATE)

    // region 昵称 / 头像

    fun loadNickname(): String =
        prefs.getString("nickname", null) ?: app.getString(R.string.profile_default_nickname)

    fun saveNickname(name: String) {
        prefs.edit().putString("nickname", name).apply()
    }

    fun loadAvatar(): String =
        prefs.getString("avatar", AVATAR_DEFAULT) ?: AVATAR_DEFAULT

    fun saveAvatar(avatar: String) {
        prefs.edit().putString("avatar", avatar).apply()
    }

    // endregion

    // region 主题 / 字体 / 语言

    fun loadThemeMode(): ThemeMode =
        ThemeMode.fromKey(prefs.getString(PREF_THEME_MODE, null))

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(PREF_THEME_MODE, mode.key).apply()
    }

    fun loadFontLevel(): FontSizeLevel =
        FontSizeLevel.fromKey(prefs.getString(PREF_FONT_SIZE, null))

    fun saveFontLevel(level: FontSizeLevel) {
        prefs.edit().putString(PREF_FONT_SIZE, level.key).apply()
    }

    fun loadLanguageTag(): String =
        AppLocale.currentTag(app)

    fun saveLanguageTag(tag: String) {
        prefs.edit().putString(AppLocale.PREF_KEY_LANGUAGE, tag).apply()
    }

    // endregion

    // region 首页寄语（横幅轮播列表：我的→首页寄语页管理，首页顶部欢迎卡每天按序取一条）

    /** 读取寄语列表：未设置/损坏时返回按当前语言解析的内置默认列表。
     *  旧数据迁移：若存储内容只是某次"恢复默认/编辑"时固化下来的内置默认副本
     *  （当时语言若为英文，存的就是英文文案），并非用户自定义内容 —— 按当前语言
     *  重新解析，保证内置寄语始终跟随"我的 → 语言" */
    fun loadHomeSlogans(): List<String> {
        val raw = prefs.getString(PREF_HOME_SLOGANS, null)
        if (raw == null) return defaultHomeSlogans(app)
        val list = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            return defaultHomeSlogans(app)
        }
        if (list.isEmpty()) return defaultHomeSlogans(app)
        // 与当前语言默认一致 → 原样返回
        if (list == defaultHomeSlogans(app)) return list
        // 逐语言比对：是其他语言下的内置默认副本 → 按当前语言重解析
        val isStoredDefaultCopy = AppLocale.LANGUAGES
            .mapNotNull { it.locale }
            .any { list == defaultSlogansIn(it) }
        return if (isStoredDefaultCopy) defaultHomeSlogans(app) else list
    }

    /** 按指定 locale 解析内置默认寄语（用于识别"某语言下固化的默认副本"） */
    private fun defaultSlogansIn(locale: Locale): List<String> {
        val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
        return DEFAULT_HOME_SLOGANS_RES.map { app.createConfigurationContext(config).getString(it) }
    }

    /** 持久化寄语列表（JSON 数组字符串；空列表原样保存，首页显示时回退默认） */
    fun persistHomeSlogans(list: List<String>) {
        prefs.edit().putString(PREF_HOME_SLOGANS, JSONArray(list).toString()).apply()
    }

    /** 恢复内置默认寄语列表：清空固化的副本，默认寄语按当前语言实时解析 */
    fun resetHomeSlogans() {
        prefs.edit().remove(PREF_HOME_SLOGANS).apply()
    }

    // endregion

    // region 常用药物

    /** 读取常用药物：优先用户持久化列表（空列表 = 用户已清空）；未设置时由历史服药记录初始化 */
    suspend fun loadCommonMeds(): List<String> {
        val raw = prefs.getString(PREF_COMMON_MED_NAMES, null)
        if (raw != null) {
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        val seeded = medDao.getRecentNames()
        if (seeded.isNotEmpty()) persistCommonMeds(seeded)
        return seeded
    }

    /** 持久化常用药物列表（JSON 数组字符串） */
    fun persistCommonMeds(names: List<String>) {
        prefs.edit().putString(PREF_COMMON_MED_NAMES, JSONArray(names).toString()).apply()
    }

    /** 保存服药后把药名加入常用列表（去重、追加到末尾、最多保留 MAX_COMMON_MED_NAMES 个） */
    fun addToCommonMeds(current: List<String>, name: String): List<String> {
        if (name.isBlank() || name in current) return current
        val updated = (current + name).takeLast(MAX_COMMON_MED_NAMES)
        persistCommonMeds(updated)
        return updated
    }

    // endregion

    // region 服药提醒时间

    fun loadMedReminderTimes(): List<String> {
        val raw = prefs.getString(PREF_MED_REMINDER_TIMES, null) ?: return DEFAULT_MED_REMINDER_TIMES
        val list = raw.split(",").map { it.trim() }.filter { it.matches(Regex("\\d{2}:\\d{2}")) }.sorted()
        return list.ifEmpty { DEFAULT_MED_REMINDER_TIMES }
    }

    /** 持久化提醒时间（整体升序落库） */
    fun persistMedReminderTimes(times: List<String>) {
        val sorted = times.sorted()
        prefs.edit().putString(PREF_MED_REMINDER_TIMES, sorted.joinToString(",")).apply()
    }

    // endregion
}

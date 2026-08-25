package com.study.checkin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 蓝色系浅色主题（替换 M3 默认紫色基线）。
 * primary 为深蓝，中性色（surface/outline 等）带轻微蓝调。
 */
private val LightBlueScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D33),
    inversePrimary = Color(0xFFA8CDFF),
    secondary = Color(0xFF545F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F7),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2D9FF),
    onTertiaryContainer = Color(0xFF251432),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDFE2EC),
    onSurfaceVariant = Color(0xFF43474F),
    surfaceTint = Color(0xFF0061A4),
    inverseSurface = Color(0xFF2D3136),
    inverseOnSurface = Color(0xFFF0F0F7),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceDim = Color(0xFFD9DADE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2FA),
    surfaceContainer = Color(0xFFEDEEF6),
    surfaceContainerHigh = Color(0xFFE8E9EF),
    surfaceContainerHighest = Color(0xFFE3E3E9),
)

/** 蓝色系深色主题 */
private val DarkBlueScheme = darkColorScheme(
    primary = Color(0xFFA8CDFF),
    onPrimary = Color(0xFF003352),
    primaryContainer = Color(0xFF004973),
    onPrimaryContainer = Color(0xFFD1E4FF),
    inversePrimary = Color(0xFF0061A4),
    secondary = Color(0xFFBCC8DD),
    onSecondary = Color(0xFF263140),
    secondaryContainer = Color(0xFF3C4757),
    onSecondaryContainer = Color(0xFFD8E3F7),
    tertiary = Color(0xFFD5BEE5),
    onTertiary = Color(0xFF3B2A48),
    tertiaryContainer = Color(0xFF524060),
    onTertiaryContainer = Color(0xFFF2D9FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFA8CDFF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2D3136),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF36393E),
    surfaceDim = Color(0xFF101418),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C1F24),
    surfaceContainerHigh = Color(0xFF26292E),
    surfaceContainerHighest = Color(0xFF313439),
)

/** 主题模式：跟随系统 / 强制浅色 / 强制深色（"我的"页主题菜单可切换） */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** 应用当前生效的深色模式（跟随系统 / 强制浅色 / 强制深色），供卡片等局部配色读取 */
internal val LocalDarkTheme = staticCompositionLocalOf { false }

/** 应用主题：蓝色系配色，深/浅由调用方按 ThemeMode 决定 */
@Composable
fun UcDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkBlueScheme else LightBlueScheme,
            content = content
        )
    }
}

/** 日历卡 / 首页"今日"卡背景：浅色主题淡蓝，深色主题藏蓝（跟随应用主题，而非系统深色模式） */
@Composable
fun blueCardBackground(): Color =
    if (LocalDarkTheme.current) Color(0xFF1A2A44) else Color(0xFFD6EAF8)

/** 日历卡描边：浅色主题淡蓝，深色主题钢蓝 */
@Composable
fun blueCardBorder(): Color =
    if (LocalDarkTheme.current) Color(0xFF3F5E8C) else Color(0xFF8FB8DA)

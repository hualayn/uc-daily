package com.ucdaily.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 设计稿（docs/ui-mockup.html）浅色令牌：
 * 品牌主色 #2563EB，页面背景 #F4F7FB，卡片 #FFFFFF，中性色带轻微蓝调。
 */
private val LightBlueScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EDFF),
    onPrimaryContainer = Color(0xFF1E40AF),
    inversePrimary = Color(0xFF5B9BFF),
    secondary = Color(0xFF66748C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDF1F7),
    onSecondaryContainer = Color(0xFF1B2437),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0EAFE),
    onTertiaryContainer = Color(0xFF6D28D9),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE9E9),
    onErrorContainer = Color(0xFFB91C1C),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF1B2437),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2437),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF66748C),
    surfaceTint = Color(0xFF2563EB),
    inverseSurface = Color(0xFF2E3A52),
    inverseOnSurface = Color(0xFFF0F2F8),
    outline = Color(0xFFD9E1EC),
    outlineVariant = Color(0xFFE5EAF2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE3EC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FBFD),
    surfaceContainer = Color(0xFFF4F7FB),
    surfaceContainerHigh = Color(0xFFEDF1F7),
    surfaceContainerHighest = Color(0xFFE7ECF4),
)

/** 设计稿深色令牌：背景 #0E1420，卡片 #1A2334，主色 #5B9BFF */
private val DarkBlueScheme = darkColorScheme(
    primary = Color(0xFF5B9BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1D2C49),
    onPrimaryContainer = Color(0xFF9CC2FF),
    inversePrimary = Color(0xFF2563EB),
    secondary = Color(0xFF93A2BC),
    onSecondary = Color(0xFF101826),
    secondaryContainer = Color(0xFF232E44),
    onSecondaryContainer = Color(0xFFE9EEF8),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF241A36),
    tertiaryContainer = Color(0xFF241A36),
    onTertiaryContainer = Color(0xFFC4B0FB),
    error = Color(0xFFF87171),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF301518),
    onErrorContainer = Color(0xFFFCA5A5),
    background = Color(0xFF0E1420),
    onBackground = Color(0xFFE9EEF8),
    surface = Color(0xFF1A2334),
    onSurface = Color(0xFFE9EEF8),
    surfaceVariant = Color(0xFF232E44),
    onSurfaceVariant = Color(0xFF93A2BC),
    surfaceTint = Color(0xFF5B9BFF),
    inverseSurface = Color(0xFFE9EEF8),
    inverseOnSurface = Color(0xFF2E3A52),
    outline = Color(0xFF2C3A55),
    outlineVariant = Color(0xFF2C3A55),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF31405C),
    surfaceDim = Color(0xFF0E1420),
    surfaceContainerLowest = Color(0xFF161E2C),
    surfaceContainerLow = Color(0xFF1A2334),
    surfaceContainer = Color(0xFF1E2939),
    surfaceContainerHigh = Color(0xFF232E44),
    surfaceContainerHighest = Color(0xFF2A374E),
)

/** 主题模式：跟随系统 / 强制浅色 / 强制深色（"我的"页主题菜单可切换；labelRes 为多语言文案资源） */
enum class ThemeMode(val key: String, @StringRes val labelRes: Int) {
    SYSTEM("system", com.ucdaily.R.string.theme_system),
    LIGHT("light", com.ucdaily.R.string.theme_light),
    DARK("dark", com.ucdaily.R.string.theme_dark);

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * 字体大小档位（"我的"页可切换，默认标准）：
 * 只影响首页（含当天记录）/ 耐受 / 日常管理 三个 Tab 的文字，"我的"页与全局面板保持原样。
 */
enum class FontSizeLevel(val key: String, @StringRes val labelRes: Int, val scale: Float) {
    SMALL("small", com.ucdaily.R.string.font_size_small, 0.9f),
    STANDARD("standard", com.ucdaily.R.string.font_size_standard, 1.0f),
    LARGE("large", com.ucdaily.R.string.font_size_large, 1.15f),
    EXTRA_LARGE("extra_large", com.ucdaily.R.string.font_size_extra_large, 1.3f);

    companion object {
        fun fromKey(key: String?): FontSizeLevel = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/**
 * 按所选字体大小缩放内容：sp 文本（含 Material 主题字号与显式 sp 值）乘以 scale，
 * dp 布局尺寸不受影响，卡片/行高随文字自然撑高。
 */
@Composable
fun FontScaledContent(
    scale: Float,
    content: @Composable () -> Unit
) {
    val current = LocalDensity.current
    CompositionLocalProvider(
        // 保持原 density，仅替换 fontScale（只放大/缩小 sp 文本）
        LocalDensity provides Density(density = current.density, fontScale = scale)
    ) {
        content()
    }
}

/** 应用当前生效的深色模式（跟随系统 / 强制浅色 / 强制深色），供卡片等局部配色读取 */
internal val LocalDarkTheme = staticCompositionLocalOf { false }

/** 应用主题：设计稿蓝色系配色 + 设计令牌（[LocalUcPalette]），深/浅由调用方按 ThemeMode 决定 */
@Composable
fun UcDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalUcPalette provides if (darkTheme) DarkUcPalette else LightUcPalette
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkBlueScheme else LightBlueScheme,
            content = content
        )
    }
}

/** 兼容旧引用：卡片背景跟随设计令牌（浅色 = 白色卡片，深色 = 深蓝卡片） */
@Composable
fun blueCardBackground(): Color = ucPalette().surface

/** 兼容旧引用：卡片描边 = 设计令牌 ring */
@Composable
fun blueCardBorder(): Color = ucPalette().ring

package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ucdaily.AppLocale
import com.ucdaily.BuildConfig
import com.ucdaily.R

/**
 * 设置页（"我的→设置" 或 首页齿轮进入）：
 * 首页寄语 / 主题 / 字体大小 / 语言 / 软件更新 / 关于
 */
@Composable
fun SettingsScreen(
    state: MealUiState,
    onOpenHomeSlogans: () -> Unit,
    onFontSizeChange: (FontSizeLevel) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // 版本号：直接读构建时注入的 versionName（CI 按 tag 注入，本地默认 1.1.0，避免文案写死）
    val versionName = BuildConfig.VERSION_NAME

    // 内置默认寄语（按当前语言解析）：用于判断寄语列表是否仍为默认
    val defaultSlogans = DEFAULT_HOME_SLOGANS_RES.map { stringResource(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部标题栏（统一样式）
        SecondaryTopBar(
            onBack = onBack,
            title = stringResource(R.string.profile_menu_settings)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 设置菜单卡（设计稿 .menu）：首页寄语 / 主题 / 字体大小 / 语言 / 软件更新 / 关于
            val p = ucPalette()
            UcCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MenuItemRow(
                        icon = Icons.Filled.EditNote,
                        iconBg = p.purpleSoft,
                        iconTint = p.purpleText,
                        label = stringResource(R.string.profile_menu_slogans),
                        sub = stringResource(R.string.settings_menu_slogans_sub),
                        // 已自定义时显示条数，未修改过提示"默认寄语"
                        value = if (state.homeSlogans == defaultSlogans) {
                            stringResource(R.string.profile_menu_slogans_default_trailing)
                        } else {
                            stringResource(R.string.common_items_count, state.homeSlogans.size)
                        },
                        onClick = onOpenHomeSlogans
                    )
                    SettingsDivider()
                    MenuItemRow(
                        icon = Icons.Filled.DarkMode,
                        iconBg = p.surface2,
                        iconTint = p.text2,
                        label = stringResource(R.string.profile_menu_theme),
                        sub = stringResource(R.string.settings_menu_theme_sub),
                        value = stringResource(state.themeMode.labelRes),
                        onClick = { showThemeDialog = true }
                    )
                    SettingsDivider()
                    MenuItemRow(
                        icon = Icons.Filled.TextFields,
                        iconBg = p.surface2,
                        iconTint = p.text2,
                        label = stringResource(R.string.profile_menu_font),
                        sub = stringResource(R.string.settings_menu_font_sub),
                        // 当前档位（作用于首页记录 / 耐受 / 日常管理）
                        value = stringResource(state.fontLevel.labelRes),
                        onClick = { showFontSizeDialog = true }
                    )
                    SettingsDivider()
                    // 语言：跟随系统 + 11 种语言（切换后 recreate 即时生效）
                    MenuItemRow(
                        icon = Icons.Filled.Language,
                        iconBg = p.greenSoft,
                        iconTint = p.greenText,
                        label = stringResource(R.string.profile_menu_language),
                        sub = stringResource(R.string.settings_menu_language_sub),
                        value = AppLocale.endonymOf(state.languageTag)
                            ?: stringResource(R.string.language_system),
                        onClick = { showLanguageDialog = true }
                    )
                    SettingsDivider()
                    // 软件更新（Google Play Core 应用内更新）
                    MenuItemRow(
                        icon = Icons.Filled.SystemUpdateAlt,
                        iconBg = p.primarySoft,
                        iconTint = p.primaryText,
                        label = stringResource(R.string.profile_menu_update),
                        sub = stringResource(R.string.settings_menu_update_sub),
                        value = stringResource(R.string.profile_menu_update_trailing, versionName),
                        onClick = onCheckUpdate
                    )
                    SettingsDivider()
                    MenuItemRow(
                        icon = Icons.Filled.Info,
                        iconBg = p.surface2,
                        iconTint = p.text2,
                        label = stringResource(R.string.profile_menu_about),
                        sub = stringResource(R.string.settings_menu_about_sub),
                        value = stringResource(R.string.profile_menu_about_trailing, versionName),
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.profile_menu_theme)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeOptionRow(
                            mode = mode,
                            selected = state.themeMode == mode,
                            onSelect = {
                                onThemeModeChange(mode)
                                showThemeDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showFontSizeDialog) {
        AlertDialog(
            onDismissRequest = { showFontSizeDialog = false },
            title = { Text(stringResource(R.string.profile_menu_font)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.profile_font_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FontSizeLevel.entries.forEach { level ->
                        FontSizeOptionRow(
                            level = level,
                            selected = state.fontLevel == level,
                            onSelect = {
                                onFontSizeChange(level)
                                showFontSizeDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFontSizeDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 语言选择：跟随系统 + 11 种语言（语言自称展示）
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.profile_menu_language)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    AppLocale.LANGUAGES.forEach { lang ->
                        LanguageOptionRow(
                            label = lang.endonym ?: stringResource(R.string.language_system),
                            selected = state.languageTag == lang.tag,
                            onSelect = {
                                onLanguageChange(lang.tag)
                                showLanguageDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.profile_about_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.profile_about_version, versionName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profile_about_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profile_about_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.common_got_it))
                }
            }
        )
    }
}

/** 主题选项行：单选圆点 + 模式名（跟随系统/浅色/深色） */
@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(mode.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 字体大小选项行：档位名 + 示例文字（按对应 fontScale 展示实际效果） */
@Composable
private fun FontSizeOptionRow(
    level: FontSizeLevel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(level.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        // 示例：按该档位 fontScale 渲染"文字"，直观对比大小
        FontScaledContent(scale = level.scale) {
            Text(
                text = stringResource(R.string.profile_font_sample),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/** 菜单卡内的分隔线（缩进对齐图标后） */
@Composable
private fun SettingsDivider() {
    val p = ucPalette()
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp),
        color = p.surface2
    )
}

/** 语言选项行：单选圆点 + 语言自称（跟随系统/中文/English/日本語…） */
@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

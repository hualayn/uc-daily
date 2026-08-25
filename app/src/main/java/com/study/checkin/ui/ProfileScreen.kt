package com.study.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** 我的：头像/昵称（可编辑）+ 功能菜单（统计信息/导出记录/服药设置/主题/关于） */
@Composable
fun ProfileScreen(
    state: MealUiState,
    onSetNickname: (String) -> Unit,
    onSetAvatar: (String) -> Unit,
    onOpenStats: () -> Unit,
    onExport: suspend (LocalDate, LocalDate, Set<ExportType>, ExportFormat) -> ExportResult?,
    onOpenMedSettings: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { showAvatarDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = avatarIcon(state.avatar),
                contentDescription = "点击修改头像",
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = state.nickname,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showEditDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "修改昵称",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "溃结日常记录 · 数据仅保存在本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 功能菜单卡：统计信息 / 导出记录 / 服药设置 / 主题 / 关于
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                ProfileMenuItem(
                    icon = Icons.Filled.Insights,
                    label = "统计信息",
                    trailing = "详细统计",
                    onClick = onOpenStats
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                ProfileMenuItem(
                    icon = Icons.Filled.Upload,
                    label = "导出记录",
                    trailing = "剪切板/文件",
                    onClick = { showExportDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                ProfileMenuItem(
                    icon = Icons.Filled.LocalPharmacy,
                    label = "服药设置",
                    trailing = "${state.medReminderTimes.size} 次/天",
                    onClick = onOpenMedSettings
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                ProfileMenuItem(
                    icon = Icons.Filled.DarkMode,
                    label = "主题",
                    trailing = state.themeMode.label,
                    onClick = { showThemeDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                ProfileMenuItem(
                    icon = Icons.Filled.Info,
                    label = "关于",
                    trailing = "v1.0",
                    onClick = { showAboutDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showEditDialog) {
        var name by remember { mutableStateOf(state.nickname) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetNickname(name)
                    showEditDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text("修改头像") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AvatarOption(
                        label = "男生头像",
                        icon = Icons.Filled.Man,
                        selected = state.avatar == AVATAR_BOY,
                        onSelect = {
                            onSetAvatar(AVATAR_BOY)
                            showAvatarDialog = false
                        }
                    )
                    AvatarOption(
                        label = "女生头像",
                        icon = Icons.Filled.Woman,
                        selected = state.avatar == AVATAR_GIRL,
                        onSelect = {
                            onSetAvatar(AVATAR_GIRL)
                            showAvatarDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题") },
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
                    Text("取消")
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于溃结日常记录") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "UC Daily · v1.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "为溃疡性结肠炎（UC）设计的日常记录工具：\n" +
                            "· 饮食拍照 + 食物耐受标签\n" +
                            "· 服药记录与提醒时间设置\n" +
                            "· 便便记录（布里斯托分级）与活动度自评\n" +
                            "· 每日感受与日历回溯",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "活动度为简化 UCDAI（0~8 分），仅供自我监测参考。数据与照片仅保存在本机应用目录，不会上传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    // 导出记录对话框（与日历页同一实现，复用 ExportDialog）
    if (showExportDialog) {
        ExportDialog(
            onExport = onExport,
            onDismiss = { showExportDialog = false }
        )
    }
}

/** 头像 id 对应图标：boy=男生 / girl=女生 / 默认=通用人物 */
internal fun avatarIcon(avatar: String): ImageVector = when (avatar) {
    AVATAR_BOY -> Icons.Filled.Man
    AVATAR_GIRL -> Icons.Filled.Woman
    else -> Icons.Filled.Person
}

/** 头像选项卡片：点击即选中并关闭对话框，当前头像高亮 */
@Composable
private fun AvatarOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 14.dp, horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(38.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 功能菜单行：左侧彩色图标 + 标题，右侧说明文字 + 箭头 */
@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    trailing: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 主题选项行：单选圆点 + 名称，点击即生效并关闭对话框 */
@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        Text(
            text = mode.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

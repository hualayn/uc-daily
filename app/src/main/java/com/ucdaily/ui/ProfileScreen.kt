package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R
import java.time.LocalDate

/**
 * 我的页（设计稿 .me-hero + .menu）：
 * 渐变 Hero（头像 + 昵称可编辑 + 副标题）+ 功能菜单卡（彩色图标底 + 副标题 + 右侧当前值）。
 */
@Composable
fun ProfileScreen(
    state: MealUiState,
    onSetNickname: (String) -> Unit,
    onSetAvatar: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenMedSettings: () -> Unit,
    onExport: suspend (LocalDate, LocalDate, Set<ExportType>, ExportFormat) -> ExportResult?,
    onRestore: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val p = ucPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ① 渐变 Hero：头像（点击更换）+ 昵称（点击修改）+ 副标题
        val heroShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    10.dp,
                    heroShape,
                    ambientColor = Color(0xFF2563EB).copy(alpha = if (LocalDarkTheme.current) 0.4f else 0.25f),
                    spotColor = Color(0xFF2563EB).copy(alpha = if (LocalDarkTheme.current) 0.4f else 0.25f)
                )
                .clip(heroShape)
                .background(heroBrush())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像：半透明白底 + 白描边，点击更换
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                        .clickable { showAvatarDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = avatarIcon(state.avatar),
                        contentDescription = stringResource(R.string.profile_tap_avatar),
                        modifier = Modifier.size(32.dp),
                        tint = Color.White.copy(alpha = 0.95f)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.nickname,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.profile_edit_nickname_title),
                                modifier = Modifier.size(14.dp),
                                tint = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.profile_subtitle),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ② 功能菜单卡：统计信息 / 服药设置 / 导出记录 / 恢复记录 / 设置
        UcCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                MenuItemRow(
                    icon = Icons.Filled.Insights,
                    iconBg = p.primarySoft,
                    iconTint = p.primaryText,
                    label = stringResource(R.string.profile_menu_stats),
                    sub = stringResource(R.string.profile_menu_stats_sub),
                    onClick = onOpenStats
                )
                MenuDivider()
                MenuItemRow(
                    icon = Icons.Filled.LocalPharmacy,
                    iconBg = p.primarySoft,
                    iconTint = p.primaryText,
                    label = stringResource(R.string.profile_menu_med_settings),
                    sub = stringResource(R.string.profile_menu_med_settings_sub),
                    value = stringResource(
                        R.string.profile_menu_med_settings_trailing,
                        state.medReminderTimes.size
                    ),
                    onClick = onOpenMedSettings
                )
                MenuDivider()
                MenuItemRow(
                    icon = Icons.Filled.Upload,
                    iconBg = p.greenSoft,
                    iconTint = p.greenText,
                    label = stringResource(R.string.profile_menu_export),
                    sub = stringResource(R.string.profile_menu_export_sub),
                    onClick = { showExportDialog = true }
                )
                MenuDivider()
                MenuItemRow(
                    icon = Icons.Filled.Restore,
                    iconBg = p.greenSoft,
                    iconTint = p.greenText,
                    label = stringResource(R.string.profile_menu_restore),
                    sub = stringResource(R.string.profile_menu_restore_sub),
                    onClick = onRestore
                )
                MenuDivider()
                MenuItemRow(
                    icon = Icons.Filled.Settings,
                    iconBg = p.surface2,
                    iconTint = p.text2,
                    label = stringResource(R.string.profile_menu_settings),
                    sub = stringResource(R.string.profile_menu_settings_sub),
                    onClick = onOpenSettings
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showEditDialog) {
        var name by remember { mutableStateOf(state.nickname) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = p.surface,
            title = { Text(stringResource(R.string.profile_edit_nickname_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetNickname(name)
                    showEditDialog = false
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = p.surface,
            title = { Text(stringResource(R.string.profile_edit_avatar_title)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AvatarOption(
                        label = stringResource(R.string.profile_avatar_boy),
                        icon = Icons.Filled.Man,
                        selected = state.avatar == AVATAR_BOY,
                        onSelect = {
                            onSetAvatar(AVATAR_BOY)
                            showAvatarDialog = false
                        }
                    )
                    AvatarOption(
                        label = stringResource(R.string.profile_avatar_girl),
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
                    Text(stringResource(R.string.common_cancel))
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
    val p = ucPalette()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) p.primarySoft
                else p.surface2
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
                    if (selected) p.primary
                    else p.primarySoft
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(38.dp),
                tint = if (selected) Color.White
                else p.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) p.primaryText
            else p.text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 菜单卡内的分隔线（缩进对齐图标后） */
@Composable
private fun MenuDivider() {
    val p = ucPalette()
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp),
        color = p.surface2
    )
}

package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R

/**
 * 我的→首页寄语（设计稿 .srow）：管理首页顶部欢迎卡的横幅轮播列表。
 * 每条寄语：序号圆点 + 文案 + 修改/删除小按钮；底部添加（描边按钮）+ 恢复内置默认。
 * 列表删空时首页自动回退轮播内置默认寄语。
 */
@Composable
fun HomeSloganScreen(
    state: MealUiState,
    onAdd: (String) -> Unit,
    onUpdate: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    // 编辑对话框：-2 = 添加新寄语；0..n-1 = 修改第 i 条；null = 未打开
    var dialogIndex by remember { mutableStateOf<Int?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val slogans = state.homeSlogans
    val p = ucPalette()
    // 内置默认寄语（按当前语言解析）：用于判断列表是否仍为默认
    val defaultSlogans = DEFAULT_HOME_SLOGANS_RES.map { stringResource(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部标题栏（统一样式）
        SecondaryTopBar(onBack = onBack, title = stringResource(R.string.slogan_title))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.slogan_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (slogans.isEmpty()) {
                // 列表为空：首页将轮播内置默认寄语
                UcCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.slogan_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.text2
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onReset) {
                            Text(stringResource(R.string.slogan_reset), color = p.primary)
                        }
                    }
                }
            } else {
                // 寄语列表：序号 + 文案 + 修改/删除（设计稿 .srow）
                val cardShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(elevation = 2.dp, shape = cardShape)
                        .clip(cardShape)
                        .background(p.surface)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        slogans.forEachIndexed { i, s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dialogIndex = i }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 序号：20dp 圆形 surface2 底
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(p.surface2),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${i + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = p.text2
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = s,
                                    fontSize = 12.5.sp,
                                    color = p.text,
                                    modifier = Modifier.weight(1f)
                                )
                                SloganOpButton(
                                    icon = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.common_edit),
                                    tint = p.text2,
                                    onClick = { dialogIndex = i }
                                )
                                SloganOpButton(
                                    icon = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = p.redText,
                                    onClick = { onDelete(i) }
                                )
                            }
                            if (i < slogans.size - 1) {
                                HorizontalDivider(color = p.surface2)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // 添加寄语（设计稿 .btn.out：白底 + 描边）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(elevation = 1.dp, shape = RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.surface)
                        .clickable { dialogIndex = ADD_SLOGAN_INDEX }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = p.text
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.slogan_add), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                }
            }

            // 恢复默认（仅当列表与内置默认不同时显示）
            if (slogans != defaultSlogans) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.slogan_reset), color = p.primary, textAlign = TextAlign.Center)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 编辑 / 添加对话框（内容留空时保存按钮禁用）
    dialogIndex?.let { index ->
        val isAdd = index == ADD_SLOGAN_INDEX
        var text by remember { mutableStateOf(if (isAdd) "" else slogans.getOrNull(index) ?: "") }
        AlertDialog(
            onDismissRequest = { dialogIndex = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = p.surface,
            title = { Text(stringResource(if (isAdd) R.string.slogan_add else R.string.slogan_edit)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_HOME_SLOGAN_LEN) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(stringResource(R.string.slogan_field, text.length, MAX_HOME_SLOGAN_LEN)) },
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            if (isAdd) onAdd(trimmed) else onUpdate(index, trimmed)
                        }
                        dialogIndex = null
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogIndex = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 恢复默认确认（会替换当前自定义内容）
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = p.surface,
            title = { Text(stringResource(R.string.slogan_reset)) },
            text = {
                Text(
                    stringResource(R.string.slogan_reset_message, DEFAULT_HOME_SLOGANS_RES.size)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetConfirm = false
                }) {
                    Text(stringResource(R.string.common_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 寄语行的操作小按钮（26dp 圆角方块 + 13dp 图标） */
@Composable
private fun SloganOpButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    val p = ucPalette()
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(p.surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(13.dp),
            tint = tint
        )
    }
}

/** 添加寄语的对话框下标占位值（真实条目下标从 0 开始，不会冲突） */
private const val ADD_SLOGAN_INDEX = -2

package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ucdaily.R

/**
 * 我的→首页寄语：管理首页顶部欢迎卡的横幅轮播列表。
 * 每条寄语可点行修改；右侧图标删除；底部可添加、可恢复内置默认；
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
    // 内置默认寄语（按当前语言解析）：用于判断列表是否仍为默认
    val defaultSlogans = DEFAULT_HOME_SLOGANS_RES.map { stringResource(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部标题栏（与服药设置页同款）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.common_back),
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.slogan_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

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
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.slogan_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onReset) {
                            Text(stringResource(R.string.slogan_reset))
                        }
                    }
                }
            } else {
                // 寄语列表：序号 + 文案 + 修改/删除
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        slogans.forEachIndexed { i, s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dialogIndex = i }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(26.dp)
                                )
                                Text(
                                    text = s,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { dialogIndex = i }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.common_edit),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDelete(i) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.common_delete),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (i < slogans.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { dialogIndex = ADD_SLOGAN_INDEX },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.slogan_add))
                }
            }

            // 恢复默认（仅当列表与内置默认不同时显示）
            if (slogans != defaultSlogans) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.slogan_reset))
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
            title = { Text(stringResource(if (isAdd) R.string.slogan_add else R.string.slogan_edit)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_HOME_SLOGAN_LEN) },
                    modifier = Modifier.fillMaxWidth(),
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

/** 添加寄语的对话框下标占位值（真实条目下标从 0 开始，不会冲突） */
private const val ADD_SLOGAN_INDEX = -2

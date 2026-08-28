package com.study.checkin.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.study.checkin.MedReminder

/** 服药设置页（我的→服药设置）：每天服药次数（1~6）+ 对应提醒时间 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedSettingsScreen(
    state: MealUiState,
    onTimesChange: (Int) -> Unit,
    onTimeChange: (Int, String) -> Unit,
    onBack: () -> Unit
) {
    var pickerIndex by remember { mutableStateOf(-1) }
    val times = state.medReminderTimes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "服药设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // ① 每天服药次数
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = blueCardBackground()),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "每天服药次数", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "最多 6 次，与提醒时间一一对应",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { onTimesChange(times.size - 1) },
                            enabled = times.size > 1
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "减少一次"
                            )
                        }
                        Text(
                            text = "${times.size} 次",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { onTimesChange(times.size + 1) },
                            enabled = times.size < 6
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "增加一次"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ② 提醒时间列表
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    times.forEachIndexed { i, t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickerIndex = i }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalPharmacy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "第 ${i + 1} 次",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = t,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = "修改时间",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (i < times.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ③ 精确闹钟权限（保证应用处于后台/被杀时仍能到点提醒）
            ExactAlarmPermissionCard()

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "添加或补录服药时默认取当前时间。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 提醒时间选择对话框（M3 TimePicker，24 小时制）
    if (pickerIndex in times.indices) {
        val index = pickerIndex
        val (h, m) = times[index].split(":").map { it.toInt() }
        // TimePickerState 只能创建一次：记在状态槽里，首次进入时初始化
        var timeState by remember(index) { mutableStateOf<TimePickerState?>(null) }
        if (timeState == null) {
            timeState = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        }
        AlertDialog(
            onDismissRequest = { pickerIndex = -1 },
            title = { Text("第 ${index + 1} 次提醒时间") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timeState!!)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(index, "%02d:%02d".format(timeState!!.hour, timeState!!.minute))
                    pickerIndex = -1
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerIndex = -1 }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 精确闹钟权限卡片（Android 12+ SCHEDULE_EXACT_ALARM）：
 * 未授予时精确闹钟退化为不精确闹钟，MIUI 等对后台严格的管理型系统上
 * 到点提醒可能被延后；权限状态在页面回到前台（ON_RESUME）时刷新。
 */
@Composable
private fun ExactAlarmPermissionCard() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(MedReminder.canScheduleExactAlarms(ctx)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = MedReminder.canScheduleExactAlarms(ctx)
                // 刚从系统设置回来且权限新授予：重排闹钟，把之前的不精确闹钟升级为精确闹钟
                if (nowGranted && !granted) MedReminder.scheduleNext(ctx)
                granted = nowGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "到点提醒（精确闹钟）", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (granted) {
                        "已开启：应用退到后台也能按时提醒"
                    } else {
                        "未开启：应用关闭时可能无法按时提醒"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (granted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已开启",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                TextButton(
                    onClick = {
                        (ctx as? Activity)?.let { MedReminder.requestExactAlarmPermission(it) }
                    }
                ) {
                    Text("去开启")
                }
            }
        }
    }
}

package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R

/**
 * 服药设置页（我的→服药设置，设计稿 .medcard / .time-row）：
 * 渐变 Hero 卡（每天服药次数 + 步进器）+ 提醒时间列表（白卡 + 蓝色图标底 + 主色时间）。
 */
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
    val p = ucPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部标题栏（统一样式）
        SecondaryTopBar(
            onBack = onBack,
            title = stringResource(R.string.med_settings_title)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // ① 每天服药次数（设计稿 .medcard：渐变底 + 标题/说明 + 半透明白色步进器）
            val heroShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        8.dp,
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.med_settings_daily_count),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.med_settings_times_hint),
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MedStepButton(
                            icon = Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.med_settings_decrease_one),
                            onClick = { onTimesChange(times.size - 1) },
                            enabled = times.size > 1
                        )
                        Text(
                            text = stringResource(R.string.common_times_count, times.size),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        MedStepButton(
                            icon = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.med_settings_increase_one),
                            onClick = { onTimesChange(times.size + 1) },
                            enabled = times.size < 6
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ② 提醒时间列表（设计稿 .time-row：白卡 + 蓝色图标底 + 主色加粗时间）
            val cardShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .softShadow(elevation = 2.dp, shape = cardShape)
                    .clip(cardShape)
                    .background(p.surface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    times.forEachIndexed { i, t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickerIndex = i }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.primarySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalPharmacy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = p.primaryText
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.med_settings_nth, i + 1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = p.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = t,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = p.primaryText
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.med_settings_change_time),
                                modifier = Modifier.size(18.dp),
                                tint = p.ring
                            )
                        }
                        if (i < times.size - 1) {
                            HorizontalDivider(color = p.surface2)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.med_settings_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 提醒时间选择对话框（M3 TimePicker，24 小时制）
    if (pickerIndex in times.indices) {
        val index = pickerIndex
        val timeState = rememberTimePickerState(
            initialHour = times[index].substringBefore(':').toIntOrNull() ?: 8,
            initialMinute = times[index].substringAfter(':').toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickerIndex = -1 },
            title = { Text(stringResource(R.string.med_settings_picker_title, index + 1)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(index, "%02d:%02d".format(timeState.hour, timeState.minute))
                    pickerIndex = -1
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerIndex = -1 }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 渐变 Hero 卡内的步进按钮：34dp 半透明白圆 + 白图标 */
@Composable
private fun MedStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = Color.White
        )
    }
}

package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ucdaily.R
import com.ucdaily.data.ActivityLevel
import com.ucdaily.data.BRISTOL_LABELS
import com.ucdaily.data.FoodTolerance
import com.ucdaily.data.activityLevel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

/** 详细统计页（我的→统计信息）：记录概览 + 数量统计 + 排便详情 + 耐受情况，全时段数据 */
@Composable
fun StatsScreen(
    state: MealUiState,
    onBack: () -> Unit,
    /** 点数量块：打开对应类别的全时段记录汇总列表 */
    onOpenRecords: (ExportType) -> Unit
) {
    val today = LocalDate.now()

    // 有记录的天（饮食 ∪ 排便）：算首次记录日期与连续天数
    val recordDaySet = remember(state.recordDates, state.symptomByDate) {
        state.recordDates + state.symptomByDate.keys
    }
    val firstDate: LocalDate? = remember(recordDaySet) {
        recordDaySet.minOrNull()?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
    }
    val totalDays = if (firstDate != null) {
        ChronoUnit.DAYS.between(firstDate, today).coerceAtLeast(0) + 1
    } else 0
    val streak = remember(recordDaySet) {
        var d = today
        if (d.toString() !in recordDaySet) d = d.minusDays(1)
        var n = 0
        while (d.toString() in recordDaySet) {
            n++
            d = d.minusDays(1)
        }
        n
    }

    val symptoms = state.allSymptoms
    val bristolCounts = remember(symptoms) {
        (1..7).map { t -> symptoms.count { it.bristolType == t } }
    }
    val maxBristol = bristolCounts.maxOrNull() ?: 0
    val activityCounts = remember(symptoms) {
        ActivityLevel.entries.map { level -> symptoms.count { it.activityLevel == level } }
    }
    val avgPain = remember(symptoms) {
        if (symptoms.isEmpty()) 0.0
        else symptoms.map { it.painScore.toDouble() }.average()
    }

    val context = LocalContext.current
    val monthDayFmt = remember(context.resources.configuration.locales[0]) {
        val locale = context.resources.configuration.locales[0]
        DateTimeFormatter.ofPattern(context.getString(R.string.date_pattern_md), locale)
    }
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
                    contentDescription = stringResource(R.string.common_back),
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.stats_title),
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
            // ① 记录概览（蓝底卡）
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = blueCardBackground()),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatsBigNumber(
                            stringResource(R.string.stats_days_value, totalDays),
                            stringResource(R.string.stats_days_label),
                            Modifier.weight(1f)
                        )
                        StatsBigNumber(
                            stringResource(R.string.stats_streak_value, streak),
                            stringResource(R.string.stats_streak_label),
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = firstDate?.let { stringResource(R.string.stats_first_record, it.format(monthDayFmt)) }
                            ?: stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ② 数量统计（2×2；点击打开对应类别的全时段记录汇总列表）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsTile(
                    stringResource(R.string.stats_meal_records),
                    stringResource(R.string.common_items_count, state.totalRecords),
                    Modifier.weight(1f)
                ) {
                    onOpenRecords(ExportType.MEAL)
                }
                StatsTile(
                    stringResource(R.string.stats_bowel_records),
                    stringResource(
                        R.string.stats_bowel_summary,
                        state.symptomByDate.size,
                        symptoms.sumOf { it.bowelCount }
                    ),
                    Modifier.weight(1f)
                ) { onOpenRecords(ExportType.BOWEL) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsTile(
                    stringResource(R.string.stats_med_records),
                    stringResource(R.string.common_items_count, state.totalMedRecords),
                    Modifier.weight(1f)
                ) {
                    onOpenRecords(ExportType.MED)
                }
                StatsTile(
                    stringResource(R.string.stats_note_records),
                    stringResource(R.string.common_days_count, state.totalNoteDays),
                    Modifier.weight(1f)
                ) {
                    onOpenRecords(ExportType.NOTE)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ③ 排便详情
            if (symptoms.isNotEmpty()) {
                StatsSectionTitle(stringResource(R.string.stats_bowel_detail))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // 布里斯托便级分布
                        (1..7).forEach { t ->
                            val count = bristolCounts[t - 1]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$t ${stringResource(BRISTOL_LABELS[t - 1])}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(96.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(
                                                if (maxBristol > 0) count.toFloat() / maxBristol else 0f
                                            )
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(28.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 症状出现天数
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatsChip(
                                stringResource(R.string.panel_blood),
                                stringResource(R.string.common_days_count, symptoms.count { it.blood > 0 }),
                                Modifier.weight(1f)
                            )
                            StatsChip(
                                stringResource(R.string.panel_mucus),
                                stringResource(R.string.common_days_count, symptoms.count { it.mucus }),
                                Modifier.weight(1f)
                            )
                            StatsChip(
                                stringResource(R.string.panel_urgency),
                                stringResource(R.string.common_days_count, symptoms.count { it.urgency }),
                                Modifier.weight(1f)
                            )
                            StatsChip(
                                stringResource(R.string.symptom_night_diarrhea),
                                stringResource(R.string.common_days_count, symptoms.count { it.nightDiarrhea }),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.stats_avg_pain, "%.1f".format(avgPain)),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 活动度分布
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ActivityLevel.entries.forEachIndexed { i, level ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(activityColor(level))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(level.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.common_times_count, activityCounts[i]),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ④ 耐受情况
            if (state.foodTags.isNotEmpty()) {
                StatsSectionTitle(stringResource(R.string.stats_tolerance_title))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val counts = state.foodTags.groupingBy { it.tolerance }.eachCount()
                        listOf(
                            FoodTolerance.OK to Color(0xFF43A047),
                            FoodTolerance.CAUTION to Color(0xFFF9A825),
                            FoodTolerance.BAD to Color(0xFFE53935)
                        ).forEach { (tolerance, color) ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.stats_tolerance_value,
                                        stringResource(tolerance.labelRes),
                                        counts[tolerance.ordinal] ?: 0
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stats_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** 概览卡内的大数字（标题 + 数值） */
@Composable
private fun StatsBigNumber(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 数量统计小块（可点击跳转对应记录列表） */
@Composable
private fun StatsTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 小节标题 */
@Composable
private fun StatsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** 症状天数小标签（调用方在 Row 内传 weight） */
@Composable
private fun StatsChip(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

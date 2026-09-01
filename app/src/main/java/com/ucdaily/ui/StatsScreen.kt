package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R
import com.ucdaily.data.ActivityLevel
import com.ucdaily.data.BRISTOL_LABELS
import com.ucdaily.data.FoodTolerance
import com.ucdaily.data.activityLevel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

/**
 * 详细统计页（我的→统计信息，设计稿 .ov / .t / .bar）：
 * 渐变概览卡（记录天数 / 连续记录）+ 2×2 数量卡（点卡片打开对应全时段列表）
 * + 排便详情（便级分布 / 症状天数 / 平均腹痛 / 活动度分布）+ 耐受情况，全时段数据。
 */
@Composable
fun StatsScreen(
    state: MealUiState,
    onBack: () -> Unit,
    /** 点数量块：打开对应类别的全时段记录汇总列表 */
    onOpenRecords: (ExportType) -> Unit
) {
    val today = LocalDate.now()
    val p = ucPalette()

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
        // 顶部标题栏（统一样式）
        SecondaryTopBar(onBack = onBack, title = stringResource(R.string.stats_title))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ① 记录概览（设计稿 .ov：渐变底 + 双大数字 + 中间分隔 + 首次记录行）
            val heroShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(heroShape)
                    .background(heroBrush())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = totalDays.toString(),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.stats_days_label),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = streak.toString(),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.stats_streak_label),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = Color.White.copy(alpha = 0.2f)
                    )
                    Text(
                        text = firstDate?.let { stringResource(R.string.stats_first_record, it.format(monthDayFmt)) }
                            ?: stringResource(R.string.stats_empty),
                        fontSize = 10.5.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ② 数量统计（2×2；点击打开对应类别的全时段记录汇总列表）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsTile(
                    kind = RecordKind.MEAL,
                    title = stringResource(R.string.stats_meal_records),
                    value = stringResource(R.string.common_items_count, state.totalRecords),
                    modifier = Modifier.weight(1f)
                ) {
                    onOpenRecords(ExportType.MEAL)
                }
                StatsTile(
                    kind = RecordKind.BOWEL,
                    title = stringResource(R.string.stats_bowel_records),
                    value = stringResource(
                        R.string.stats_bowel_summary,
                        state.symptomByDate.size,
                        symptoms.sumOf { it.bowelCount }
                    ),
                    modifier = Modifier.weight(1f)
                ) { onOpenRecords(ExportType.BOWEL) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsTile(
                    kind = RecordKind.MED,
                    title = stringResource(R.string.stats_med_records),
                    value = stringResource(R.string.common_items_count, state.totalMedRecords),
                    modifier = Modifier.weight(1f)
                ) {
                    onOpenRecords(ExportType.MED)
                }
                StatsTile(
                    kind = RecordKind.NOTE,
                    title = stringResource(R.string.stats_note_records),
                    value = stringResource(R.string.common_days_count, state.totalNoteDays),
                    modifier = Modifier.weight(1f)
                ) {
                    onOpenRecords(ExportType.NOTE)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ③ 排便详情
            if (symptoms.isNotEmpty()) {
                SectionHead(stringResource(R.string.stats_bowel_detail))
                UcCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // 布里斯托便级分布（设计稿 .bar：8px 圆角轨道 + 渐变填充）
                        (1..7).forEach { t ->
                            val count = bristolCounts[t - 1]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$t ${stringResource(BRISTOL_LABELS[t - 1])}",
                                    fontSize = 11.sp,
                                    color = p.text2,
                                    modifier = Modifier.width(96.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(p.surface2)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(
                                                if (maxBristol > 0) count.toFloat() / maxBristol else 0f
                                            )
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(primaryBtnBrush())
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$count",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = p.text,
                                    modifier = Modifier.width(24.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 症状出现天数（设计稿 .chip：数值在上 + 标签在下）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatsChip(
                                value = stringResource(
                                    R.string.common_days_count,
                                    symptoms.count { it.blood > 0 }
                                ),
                                label = stringResource(R.string.panel_blood),
                                modifier = Modifier.weight(1f)
                            )
                            StatsChip(
                                value = stringResource(
                                    R.string.common_days_count,
                                    symptoms.count { it.mucus }
                                ),
                                label = stringResource(R.string.panel_mucus),
                                modifier = Modifier.weight(1f)
                            )
                            StatsChip(
                                value = stringResource(
                                    R.string.common_days_count,
                                    symptoms.count { it.urgency }
                                ),
                                label = stringResource(R.string.panel_urgency),
                                modifier = Modifier.weight(1f)
                            )
                            StatsChip(
                                value = stringResource(
                                    R.string.common_days_count,
                                    symptoms.count { it.painScore > 0 }
                                ),
                                label = stringResource(R.string.panel_pain),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(
                                R.string.stats_avg_pain,
                                avgPain.toInt().toString()
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        // 活动度分布（设计稿 .act-row：色点 + 名称 + 计数）
                        ActivityLevel.entries.forEachIndexed { i, level ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(activityColor(level))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(level.labelRes),
                                    fontSize = 12.sp,
                                    color = p.text2,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.common_days_count,
                                        activityCounts[i]
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = p.text
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ④ 耐受情况（设计稿 .tol-row：色点 + 名称 + 计数）
            SectionHead(stringResource(R.string.stats_tolerance_title))
            UcCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    val toleranceCount: (FoodTolerance) -> Int =
                        { t -> state.foodTags.count { FoodTolerance.fromValue(it.tolerance) == t } }
                    TOLERANCE_ORDER.forEach { tol ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(toleranceColor(tol))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(tol.labelRes),
                                fontSize = 12.sp,
                                color = p.text2,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = toleranceCount(tol).toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = p.text
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部免责说明（居中灰字）
            Text(
                text = stringResource(R.string.stats_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

/** 数量统计小块（设计稿 .t：白卡 + emoji + 大数字 + 说明 + 右箭头，点击打开对应列表） */
@Composable
private fun StatsTile(
    kind: RecordKind,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = ucPalette()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .shadow(
                2.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.35f else 0.05f),
                spotColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.35f else 0.06f)
            )
            .clip(shape)
            .background(p.surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recordKindEmoji(kind),
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = p.ring
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = p.text,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.5.sp,
                color = p.text2,
                maxLines = 1
            )
        }
    }
}

/** 症状天数小标签（设计稿 .chip：surface2 底，数值在上 + 标签在下） */
@Composable
private fun StatsChip(value: String, label: String, modifier: Modifier = Modifier) {
    val p = ucPalette()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface2)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = p.text
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            color = p.text2
        )
    }
}

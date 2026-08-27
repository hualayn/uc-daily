package com.study.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.study.checkin.data.BLOOD_LABELS
import com.study.checkin.data.BRISTOL_LABELS
import com.study.checkin.data.DailyNote
import com.study.checkin.data.DailySymptom
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MedRecord
import com.study.checkin.data.PAIN_LOCATION_LABELS
import java.time.LocalDate

/**
 * 记录汇总页：全时段某一类记录的明细列表（我的→统计信息→点数量块进入，
 * 覆盖在统计页之上，返回回到统计页）。
 * 按日期分组（新日期在前），组内按时间升序。
 */
@Composable
fun RecordListScreen(
    state: MealUiState,
    type: ExportType,
    onBack: () -> Unit
) {
    val title = when (type) {
        ExportType.MEAL -> "饮食记录汇总"
        ExportType.BOWEL -> "排便记录汇总"
        ExportType.MED -> "服药记录汇总"
        ExportType.NOTE -> "感受记录汇总"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部标题栏（与统计信息页一致）
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        when (type) {
            ExportType.MEAL -> {
                val groups = groupByDate(
                    state.allMeals.sortedWith(
                        compareByDescending<MealRecord> { it.date }.thenBy { it.time }
                    ),
                    { it.date }
                )
                RecordListByDate(groups, "还没有饮食记录") { MealRow(it) }
            }
            ExportType.BOWEL -> {
                val groups = groupByDate(
                    state.allSymptoms.sortedWith(
                        compareByDescending<DailySymptom> { it.date }
                            .thenBy { it.time.ifEmpty { "99:99" } }
                    ),
                    { it.date }
                )
                RecordListByDate(groups, "还没有排便记录") { BowelRow(it) }
            }
            ExportType.MED -> {
                val groups = groupByDate(
                    state.allMeds.sortedWith(
                        compareByDescending<MedRecord> { it.date }.thenBy { it.time }
                    ),
                    { it.date }
                )
                RecordListByDate(groups, "还没有服药记录") { MedRow(it) }
            }
            ExportType.NOTE -> {
                RecordListByDate(
                    groupByDate(state.allNotes) { it.date },
                    "还没有感受记录"
                ) { NoteRow(it) }
            }
        }
    }
}

/** 按日期分组（输入列表须已按日期倒序，组内保持列表顺序） */
private fun <T> groupByDate(
    list: List<T>,
    dateOf: (T) -> String
): List<Pair<String, List<T>>> {
    val groups = LinkedHashMap<String, MutableList<T>>()
    list.forEach { groups.getOrPut(dateOf(it)) { mutableListOf() }.add(it) }
    return groups.map { (d, v) -> d to v }
}

/** 日期分组列表：每个日期一张卡片（标题 + 条数 + 当日明细行），日期新→旧 */
@Composable
private fun <T> RecordListByDate(
    groups: List<Pair<String, List<T>>>,
    emptyText: String,
    row: @Composable (T) -> Unit
) {
    if (groups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(groups) { (date, dayItems) ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateLabel(date),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${dayItems.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    dayItems.forEachIndexed { i, item ->
                        if (i > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        row(item)
                    }
                }
            }
        }
    }
}

/** "yyyy-MM-dd" → "M月d日 周X"（解析失败原样显示） */
private fun dateLabel(d: String): String {
    val ld = runCatching { LocalDate.parse(d) }.getOrNull() ?: return d
    return "${ld.monthValue}月${ld.dayOfMonth}日 ${WEEK_CHARS[ld.dayOfWeek.value - 1]}"
}

private val WEEK_CHARS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 明细行左侧的固定宽时间（HH:mm；空 = 未记录，显示占位） */
@Composable
private fun RowTime(time: String) {
    Text(
        text = time.ifEmpty { "—" },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(44.dp)
    )
}

/** 饮食明细行：时间 + 餐次（+ 照片数）/ 食物标签 / 备注 */
@Composable
private fun MealRow(r: MealRecord) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowTime(r.time)
            Text(
                text = r.mealType.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (r.photos.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📷 ${r.photos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (r.tags.isNotEmpty()) {
            Text(
                text = r.tags.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (r.note.isNotBlank()) {
            Text(text = r.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 排便明细行：时间 + 次数 + 便级 / 症状标记 / 其他不适 */
@Composable
private fun BowelRow(s: DailySymptom) {
    val flags = buildList {
        if (s.blood > 0) add("便血${BLOOD_LABELS[s.blood]}")
        if (s.mucus) add("黏液")
        if (s.urgency) add("急迫感")
        if (s.nightDiarrhea) add("夜间腹泻")
        if (s.painScore > 0) {
            val loc = if (s.painLocation in 1..4) PAIN_LOCATION_LABELS[s.painLocation] else ""
            add("腹痛${s.painScore}分$loc")
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowTime(s.time)
            Text(
                text = "排便 ${s.bowelCount} 次",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (s.bristolType in 1..7) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "便级${s.bristolType} ${BRISTOL_LABELS[s.bristolType - 1]}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (flags.isNotEmpty()) {
            Text(
                text = flags.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (s.note.isNotBlank()) {
            Text(text = s.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 服药明细行：时间 + 药名（+ 剂量） */
@Composable
private fun MedRow(m: MedRecord) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowTime(m.time)
        Text(
            text = m.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        if (m.dose.isNotBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = m.dose,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 感受明细行：全文（一天一条） */
@Composable
private fun NoteRow(n: DailyNote) {
    Text(
        text = n.text,
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 22.sp
    )
}

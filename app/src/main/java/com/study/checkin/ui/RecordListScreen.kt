package com.study.checkin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * 记录按 年 → 月 → 日 三级分级展示：
 * - 底部悬浮框三个按钮"年 / 月 / 日"：
 *   年 = 全部收起，只展示年份（点年份展开其月份）；
 *   月 = 展开全部月份（点月份展开该月日期记录）；
 *   日 = 完全展开到日期记录。
 * - 年 / 月行也可手动点击展开 / 收起。
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
                RecordHierarchy(groups, "还没有饮食记录") { MealRow(it) }
            }
            ExportType.BOWEL -> {
                val groups = groupByDate(
                    state.allSymptoms.sortedWith(
                        compareByDescending<DailySymptom> { it.date }
                            .thenBy { it.time.ifEmpty { "99:99" } }
                    ),
                    { it.date }
                )
                RecordHierarchy(groups, "还没有排便记录") { BowelRow(it) }
            }
            ExportType.MED -> {
                val groups = groupByDate(
                    state.allMeds.sortedWith(
                        compareByDescending<MedRecord> { it.date }.thenBy { it.time }
                    ),
                    { it.date }
                )
                RecordHierarchy(groups, "还没有服药记录") { MedRow(it) }
            }
            ExportType.NOTE -> {
                RecordHierarchy(
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

// ------------------------- 年 / 月 / 日 三级分级 -------------------------

private data class DayGroup<T>(val date: String, val items: List<T>)

private data class MonthGroup<T>(val month: Int, val days: List<DayGroup<T>>) {
    val count: Int get() = days.sumOf { it.items.size }
}

private data class YearGroup<T>(val year: Int, val months: List<MonthGroup<T>>) {
    val count: Int get() = months.sumOf { it.count }
}

/** 从日期分组（倒序输入）构建 年 → 月 → 日 层级（输出同样倒序） */
private fun <T> buildHierarchy(groups: List<Pair<String, List<T>>>): List<YearGroup<T>> {
    val byYear = LinkedHashMap<Int, LinkedHashMap<Int, MutableList<DayGroup<T>>>>()
    groups.forEach { (date, items) ->
        val y = date.substring(0, 4).toIntOrNull() ?: return@forEach
        val m = date.substring(5, 7).toIntOrNull() ?: return@forEach
        byYear.getOrPut(y) { LinkedHashMap() }
            .getOrPut(m) { mutableListOf() }
            .add(DayGroup(date, items))
    }
    return byYear.map { (y, months) ->
        YearGroup(y, months.map { (m, days) -> MonthGroup(m, days) })
    }
}

/** 展平列表的节点（LazyColumn 不支持可变深度嵌套，把树展平为节点序列） */
private sealed interface Node<T> {
    data class Year(val year: Int, val count: Int, val expanded: Boolean) : Node<Nothing>
    data class Month(val year: Int, val month: Int, val count: Int, val expanded: Boolean) : Node<Nothing>
    data class Day<T>(val date: String, val items: List<T>) : Node<T>
}

/**
 * 年 → 月 → 日 三级层级列表 + 底部悬浮层级切换框。
 * level：0 = 年（全收起）1 = 月（展开所有月份）2 = 日（完全展开），默认日（与旧版一致）。
 */
@Composable
private fun <T> RecordHierarchy(
    groups: List<Pair<String, List<T>>>,
    emptyText: String,
    row: @Composable (T) -> Unit
) {
    val years = remember(groups) { buildHierarchy(groups) }

    if (years.isEmpty()) {
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

    val allYears = years.map { it.year }
    val allMonths = years.flatMap { y -> y.months.map { m -> y.year to m.month } }

    var level by remember { mutableIntStateOf(2) }
    var expandedYears by remember { mutableStateOf(allYears.toSet()) }
    var expandedMonths by remember { mutableStateOf(allMonths.toSet()) }

    // 悬浮框按钮：展开 / 收起至指定层级（重复点击 = 恢复该层级的完全展开）
    fun selectLevel(l: Int) {
        level = l
        when (l) {
            0 -> {
                expandedYears = emptySet()
                expandedMonths = emptySet()
            }
            1 -> {
                expandedYears = allYears.toSet()
                expandedMonths = emptySet()
            }
            else -> {
                expandedYears = allYears.toSet()
                expandedMonths = allMonths.toSet()
            }
        }
    }

    val nodes = buildList {
        years.forEach { y ->
            val yOpen = y.year in expandedYears
            add(Node.Year(y.year, y.count, yOpen))
            if (yOpen) {
                y.months.forEach { m ->
                    val key = y.year to m.month
                    val mOpen = key in expandedMonths
                    add(Node.Month(y.year, m.month, m.count, mOpen))
                    if (mOpen) {
                        m.days.forEach { d -> add(Node.Day(d.date, d.items)) }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 底部多留空间，避免最后一行被悬浮框挡住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                nodes,
                key = { node ->
                    when (node) {
                        is Node.Year -> "y${node.year}"
                        is Node.Month -> "m${node.year}-${node.month}"
                        is Node.Day -> "d${node.date}"
                    }
                }
            ) { node ->
                when (node) {
                    is Node.Year -> YearHeader(
                        year = node.year,
                        count = node.count,
                        expanded = node.expanded,
                        onClick = {
                            expandedYears =
                                if (node.year in expandedYears) expandedYears - node.year
                                else expandedYears + node.year
                        }
                    )
                    is Node.Month -> MonthHeader(
                        month = node.month,
                        count = node.count,
                        expanded = node.expanded,
                        onClick = {
                            val key = node.year to node.month
                            expandedMonths =
                                if (key in expandedMonths) expandedMonths - key
                                else expandedMonths + key
                        }
                    )
                    is Node.Day -> DayCard(node.date, node.items, row)
                }
            }
        }

        // 底部悬浮层级切换框：年 / 月 / 日
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 56.dp, vertical = 14.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LevelButton("年", level == 0) { selectLevel(0) }
                LevelButton("月", level == 1) { selectLevel(1) }
                LevelButton("日", level == 2) { selectLevel(2) }
            }
        }
    }
}

/** 年份头行（点击展开 / 收起该年月份） */
@Composable
private fun YearHeader(year: Int, count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${year}年",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "共 ${count} 条",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        ExpandChevron(expanded)
    }
}

/** 月份头行（点击展开 / 收起该月日期记录） */
@Composable
private fun MonthHeader(month: Int, count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            )
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${month}月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${count} 条",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        ExpandChevron(expanded)
    }
}

/** 头行右侧箭头：展开时旋转 90° 朝下 */
@Composable
private fun ExpandChevron(expanded: Boolean) {
    val angle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200),
        label = "chevron"
    )
    Icon(
        imageVector = Icons.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer { rotationZ = angle }
    )
}

/** 悬浮框层级按钮：选中时高亮 */
@Composable
private fun LevelButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp)
        )
    }
}

/** 日期卡片：日期头 + 条数 + 当日明细行 */
@Composable
private fun <T> DayCard(date: String, dayItems: List<T>, row: @Composable (T) -> Unit) {
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

package com.study.checkin.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.study.checkin.data.ActivityLevel
import com.study.checkin.data.BLOOD_LABELS
import com.study.checkin.data.BRISTOL_LABELS
import com.study.checkin.data.DailySymptom
import com.study.checkin.data.FoodTolerance
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MedRecord
import com.study.checkin.data.PAIN_LOCATION_LABELS
import com.study.checkin.data.activityLevel
import com.study.checkin.data.activityScore
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 月份切换滑动动画时长（标题与网格共用，保证同步滑动） */
private const val MONTH_SLIDE_MS = 260

/** 日历页筛选按钮主题色（与首页统计卡一致：饮食/服药/便便；感受用青绿） */
private fun categoryColor(category: CalendarCategory): Color = when (category) {
    CalendarCategory.MEAL -> Color(0xFF43A047)   // 与首页"饮食"统计卡一致
    CalendarCategory.MED -> Color(0xFF1E88E5)    // 与首页"服药"统计卡一致
    CalendarCategory.BOWEL -> Color(0xFFF9A825)  // 与首页"便便"统计卡一致
    CalendarCategory.NOTE -> Color(0xFF00897B)   // 感受：青绿色
}

/**
 * 日历页记录类别筛选按钮（4dp 圆角矩形）：
 * 选中 = 主题色加深背景 + 描边高亮 + 加粗文字；未选中 = 淡色背景、灰色文字。
 * 默认为选中（由 state.calendarFilter 初始值保证）。无打勾符号。
 */
@Composable
private fun CalendarFilterPill(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = if (selected) 0.28f else 0.12f))
            .then(
                if (selected) {
                    Modifier.border(1.dp, accent, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 日历 Tab：月历（热力图）+ 年月选择 + 选中日全部记录 */
@Composable
fun CalendarScreen(
    state: MealUiState,
    onDateSelected: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToMonth: (YearMonth) -> Unit,
    onPhotoClick: (String, List<String>) -> Unit,
    onEditRecord: (MealRecord) -> Unit,
    onDeleteRecord: (MealRecord) -> Unit,
    onEditSymptom: (DailySymptom) -> Unit,
    onDeleteSymptom: (Int) -> Unit,
    onEditMed: (MedRecord) -> Unit,
    onDeleteMed: (MedRecord) -> Unit,
    onOpenNotePanel: () -> Unit,
    onDeleteNote: () -> Unit,
    /** 点按日历页记录类别筛选按钮（多选） */
    onToggleCalendarCategory: (CalendarCategory) -> Unit
) {
    // 是否显示年月快速选择对话框
    var showYearMonthPicker by remember { mutableStateOf(false) }

    if (state.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 日历模块（日期头 + 星期头 + 月历）：统一卡片容器（16dp 圆角、4dp 阴影），
        // 背景/描边随浅深主题切换（blueCardBackground / blueCardBorder）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, blueCardBorder(), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = blueCardBackground()
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 日期头：左右箭头换月；中间大字为展示月的年月（跟随滑动翻页），小字为选中日期，点击中间可快速选择年月
                CalendarHeader(
                    displayMonth = state.currentMonth,
                    selectedDate = state.selectedDate,
                    onPrevMonth = { onPrevMonth() },
                    onNextMonth = { onNextMonth() },
                    onTextClick = { showYearMonthPicker = true }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 星期头（与日历同一 7 列网格布局，保证列位置完全对齐；选中日对应的周几高亮为蓝色）
                val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                val selectedWeekIndex = state.selectedDate.dayOfWeek.value - 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(weekDays) { index, day ->
                        val highlighted = index == selectedWeekIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                                color = if (highlighted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 日历翻页区
                state.monthView?.let { monthView ->
                    CalendarPager(
                        monthView = monthView,
                        selectedDate = state.selectedDate,
                        today = state.today,
                        onDateClick = onDateSelected,
                        // 往左滑动 → 下一个月；往右滑动 → 上一个月
                        onSwipeLeft = { onNextMonth() },
                        onSwipeRight = { onPrevMonth() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 记录类别多选筛选：饮食 / 服药 / 便便 / 感受（与首页统计筛选互不影响；默认全选，选中高亮）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalendarCategory.values().forEach { category ->
                CalendarFilterPill(
                    label = category.label,
                    accent = categoryColor(category),
                    selected = category in state.calendarFilter,
                    onClick = { onToggleCalendarCategory(category) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 当日全部记录（今日感受置顶，其余按时间排序；
        // 与首页一致：点卡片选中后出现编辑/删除按钮；不受首页统计筛选影响）
        DayRecordList(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onPhotoClick = onPhotoClick,
            onEditRecord = onEditRecord,
            onDeleteRecord = onDeleteRecord,
            onOpenSymptom = onEditSymptom,
            onDeleteSymptom = onDeleteSymptom,
            onEditMed = onEditMed,
            onDeleteMed = onDeleteMed,
            onOpenNote = onOpenNotePanel,
            onDeleteNote = onDeleteNote,
            selectable = true,
            applyFilter = false,
            calendarFilter = state.calendarFilter
        )

        // 累计统计
        Text(
            text = "累计 ${state.totalRecordDays} 天有记录 · 共 ${state.totalRecords} 条",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }

    // 年月快速选择对话框
    if (showYearMonthPicker) {
        YearMonthPickerDialog(
            initialYear = state.currentMonth.year,
            initialMonth = state.currentMonth.monthValue,
            onConfirm = { year, month ->
                showYearMonthPicker = false
                onJumpToMonth(YearMonth.of(year, month))
            },
            onDismiss = { showYearMonthPicker = false }
        )
    }
}

/**
 * 导出记录对话框：
 * - 开始/结束日期，默认当前一周（周一至周日），点日期打开日期选择器
 * - 记录类型：饮食 / 服药 / 便便 / 感受（默认全选）
 * - 输出方式：剪切板 或 文件；文件支持 txt / csv
 * （"我的"页导出记录菜单也复用本对话框，故为 internal）
 */
@Composable
internal fun ExportDialog(
    onExport: suspend (LocalDate, LocalDate, Set<ExportType>, ExportFormat) -> ExportResult?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // 默认当前一周（周一 ~ 周日）
    var start by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var end by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY).plusDays(6)) }
    var types by remember { mutableStateOf(ExportType.entries.toSet()) }
    var toClipboard by remember { mutableStateOf(true) }
    var format by remember { mutableStateOf(ExportFormat.TXT) }
    var exporting by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // 文件输出：先暂存导出文本，用户通过系统"创建文档"选定位置后写入
    var pendingExport by remember { mutableStateOf<ExportResult?>(null) }
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (pending == null || uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(pending.text.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "已保存 ${pending.fileName}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        title = { },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 第一行：开始日期 - 结束日期（点击日期可修改）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExportClickableDate(date = start) { showStartPicker = true }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "—", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    ExportClickableDate(date = end) { showEndPicker = true }
                }

                // 第二行：记录类型（多选按钮，默认全选，可多选）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExportType.entries.forEach { type ->
                        ExportSelectButton(
                            text = type.label,
                            selected = type in types,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                types = if (type in types) types - type else types + type
                            }
                        )
                    }
                }

                // 第三行：输出方式（单选按钮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExportSelectButton(
                        text = "剪切板",
                        selected = toClipboard,
                        modifier = Modifier.weight(1f),
                        onClick = { toClipboard = true }
                    )
                    ExportSelectButton(
                        text = "文件",
                        selected = !toClipboard,
                        modifier = Modifier.weight(1f),
                        onClick = { toClipboard = false }
                    )
                }

                // 第四行：文件格式（单选按钮，仅输出为文件时显示）
                if (!toClipboard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExportFormat.entries.forEach { f ->
                            ExportSelectButton(
                                text = f.ext,
                                selected = format == f,
                                modifier = Modifier.weight(1f),
                                onClick = { format = f }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (exporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(
                    enabled = types.isNotEmpty(),
                    onClick = {
                        exporting = true
                        scope.launch {
                            val result = onExport(start, end, types, format)
                            exporting = false
                            if (result == null) {
                                Toast.makeText(context, "所选范围内没有对应类型的记录", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            if (toClipboard) {
                                clipboard.setText(AnnotatedString(result.text))
                                Toast.makeText(context, "已复制到剪切板", Toast.LENGTH_SHORT).show()
                            } else {
                                // 文件输出：走系统"创建文档"流程，用户选定位置后写入
                                pendingExport = result
                                createFile.launch(result.fileName)
                            }
                        }
                    }
                ) {
                    Text(if (types.isEmpty()) "请选择记录类型" else "导出")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!exporting) onDismiss() }) { Text("取消") }
        }
    )

    // 日期选择（标题分别显示"开始日期"/"结束日期"）
    if (showStartPicker) {
        ExportDatePickerDialog(
            title = "开始日期",
            initial = start,
            onConfirm = {
                start = it
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        ExportDatePickerDialog(
            title = "结束日期",
            initial = end,
            onConfirm = {
                end = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

/** 导出对话框中的日期文字：可点击打开日期选择器 */
@Composable
private fun ExportClickableDate(
    date: LocalDate,
    onClick: () -> Unit
) {
    Text(
        text = date.toString(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * 导出对话框中的选择按钮（单选/多选通用）：
 * 未选中 = 透明底 + 主色描边 + 主色文字；选中 = 主色填充 + 白色文字。
 */
@Composable
private fun ExportSelectButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .wrapContentSize(Alignment.Center)
        )
    }
}

/** 日期选择对话框（M3 DatePicker，标题/选中日期/按钮均为中文） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDatePickerDialog(
    title: String,
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                // 标题：居中显示"开始日期"/"结束日期"，大号字体
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            },
            headline = {
                // 顶部显示当前选中的日期（中文格式：yyyy年M月d日），左侧留 24dp 距离
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val d = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
                    Text(
                        text = "${d.year}年${d.monthValue}月${d.dayOfMonth}日",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                    )
                } else {
                    Text(
                        text = "请选择日期",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                    )
                }
            }
        )
    }
}

/**
 * 日历日期头：
 * 左侧箭头 = 上一个月，右侧箭头 = 下一个月；
 * 中间第一行（居中、大字）：X年X月 —— 当前日历展示的年月，
 *   随日历左右滑动、箭头翻页同步更新；
 * 中间第二行（居中、小字灰色）：X月X日 周X —— 当前选中的日期（周几蓝色高亮）；
 * 点击中间文字弹出年月快速选择
 */
@Composable
private fun CalendarHeader(
    displayMonth: YearMonth,
    selectedDate: LocalDate,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTextClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevMonth) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "上一个月"
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onTextClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 当前日历展示的年月（跟随左右滑动/箭头翻页）
            Text(
                text = "${displayMonth.year}年${displayMonth.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 当前选中的日期（下方记录列表按此日期展示）
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = weekLabel(selectedDate),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = "下一个月"
            )
        }
    }
}

/** 周几文案（周一~周日），首页与日历页共用 */
fun weekLabel(date: LocalDate): String =
    "周" + "一二三四五六日"[date.dayOfWeek.value - 1]

/** ISO 周序号（1~53），首页与日历页共用 */
fun isoWeek(date: LocalDate): Int =
    date.get(WeekFields.ISO.weekOfWeekBasedYear())

/** 年月选择器每列可见条目数 */
private const val WHEEL_VISIBLE_ITEMS = 5
/** 年月选择器单个条目高度（dp） */
private const val WHEEL_ITEM_HEIGHT_DP = 44
/** 年月选择器最小可选年份 */
private const val WHEEL_MIN_YEAR = 2000
/** 滚动内容上下留白（dp）：(可见高度 - 条目高度) / 2，保证首尾条目都能滚到中心线 */
private val WHEEL_END_PADDING_DP = ((WHEEL_VISIBLE_ITEMS - 1) / 2 * WHEEL_ITEM_HEIGHT_DP).dp

/** 年月快速选择对话框：年、月两列滚动选择，中间高亮项为当前选中，点确定跳转 */
@Composable
private fun YearMonthPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    onConfirm: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val maxYear = LocalDate.now().year + 1
    val years = (WHEEL_MIN_YEAR..maxYear).map { it.toString() }
    val months = (1..12).map { "${it}月" }
    var yearIndex by remember {
        mutableIntStateOf((initialYear - WHEEL_MIN_YEAR).coerceIn(0, years.size - 1))
    }
    var monthIndex by remember { mutableIntStateOf((initialMonth - 1).coerceIn(0, 11)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年月") },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((WHEEL_VISIBLE_ITEMS * WHEEL_ITEM_HEIGHT_DP).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WheelColumn(
                    items = years,
                    selectedIndex = yearIndex,
                    onSelected = { yearIndex = it },
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                WheelColumn(
                    items = months,
                    selectedIndex = monthIndex,
                    onSelected = { monthIndex = it },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(WHEEL_MIN_YEAR + yearIndex, monthIndex + 1) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 单列轮式选择：中心线处的条目为选中项，支持滚动与直接点击。
 *
 * 滚动内容上下各留 (可见高度 - 条目高度)/2 的空白，这样滚动偏移 i*条目高 时
 * 第 i 个条目正好停在中心线，首尾条目也都能被选中（否则首尾各差 2 个选不到）。
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val itemHeightPx = with(LocalDensity.current) { WHEEL_ITEM_HEIGHT_DP.dp.toPx() }
    // 条目 i 停到中心线时的滚动偏移 = i * 条目高
    val scrollState = rememberScrollState(initial = (selectedIndex * itemHeightPx).toInt())
    val onSelectedRef = rememberUpdatedState(onSelected)

    // 滚动时实时更新选中：以距离中心线最近的条目为准
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { value ->
                val index = (value / itemHeightPx).roundToInt().coerceIn(0, items.size - 1)
                onSelectedRef.value(index)
            }
    }

    Box(modifier = modifier.height((WHEEL_VISIBLE_ITEMS * WHEEL_ITEM_HEIGHT_DP).dp)) {
        // 中心高亮条（在滚动内容之下）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT_DP.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(WHEEL_END_PADDING_DP))
            items.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == selectedIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT_DP.dp)
                        .clickable {
                            onSelected(index)
                            scope.launch {
                                scrollState.animateScrollTo((index * itemHeightPx).toInt())
                            }
                        }
                )
            }
            Spacer(modifier = Modifier.height(WHEEL_END_PADDING_DP))
        }
    }
}

/**
 * 日历翻页区：单页渲染 + 方向感知滑动动画。
 * 水平拖动超过阈值后切换月份：
 * - 往左滑（手指向左）→ 下一个月
 * - 往右滑（手指向右）→ 上一个月
 * 新月份从对应方向滑入，旧月份滑出，与滑动方向一致。
 */
@Composable
private fun CalendarPager(
    monthView: MonthView,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val density = LocalDensity.current
    val onSwipeLeftRef = rememberUpdatedState(onSwipeLeft)
    val onSwipeRightRef = rememberUpdatedState(onSwipeRight)
    // 本次手势累计位移（px）。往左滑为负，往右滑为正
    var dragAccum by remember { mutableFloatStateOf(0f) }
    // 滑动触发阈值
    val thresholdPx = with(density) { 60.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val accum = dragAccum
                        dragAccum = 0f
                        when {
                            accum < -thresholdPx -> onSwipeLeftRef.value()
                            accum > thresholdPx -> onSwipeRightRef.value()
                        }
                    },
                    onDragCancel = { dragAccum = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccum += dragAmount
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = monthView,
            transitionSpec = {
                // targetState.isAfter = 往后的月份（往左滑触发）：新页从右滑入
                if (targetState.yearMonth.isAfter(initialState.yearMonth)) {
                    slideInHorizontally(tween(MONTH_SLIDE_MS)) { it } togetherWith
                        slideOutHorizontally(tween(MONTH_SLIDE_MS)) { -it }
                } else {
                    slideInHorizontally(tween(MONTH_SLIDE_MS)) { -it } togetherWith
                        slideOutHorizontally(tween(MONTH_SLIDE_MS)) { it }
                }
            },
            label = "calendarMonth"
        ) { view ->
            MonthGrid(
                days = view.days,
                selectedDate = selectedDate,
                today = today,
                onDateClick = onDateClick
            )
        }
    }
}

/** 单页日历网格 */
@Composable
private fun MonthGrid(
    days: List<CalendarDay>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(days) { day ->
            CalendarDayCell(
                day = day,
                isSelected = day.date == selectedDate,
                isToday = day.date == today,
                onClick = { onDateClick(day.date) }
            )
        }
    }
}

/** 日历单元格 */
@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val textColor = when {
        !day.inCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                // 今天且未被选中：蓝色描边突出
                if (isToday && !isSelected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            // 底部状态点：有排便记录时按活动度着热力色（绿=缓解 黄=轻 橙=中 红=重），
            // 仅有饮食记录时保持中性小圆点
            val dotColor = when {
                day.activity != null -> activityColor(day.activity)
                day.hasRecord -> if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
                else -> null
            }
            if (dotColor != null && !isSelected) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(if (day.activity != null) 6.dp else 4.dp)
                        .background(dotColor, CircleShape)
                )
            }
        }
    }
}

/** 活动度对应的热力色（日历圆点与徽章共用） */
fun activityColor(level: ActivityLevel): Color = when (level) {
    ActivityLevel.REMISSION -> Color(0xFF4CAF50) // 绿：缓解
    ActivityLevel.MILD -> Color(0xFFF9A825)      // 黄：轻度
    ActivityLevel.MODERATE -> Color(0xFFEF6C00)  // 橙：中度
    ActivityLevel.SEVERE -> Color(0xFFE53935)    // 红：重度
}

/** 记录创建时间（HH:mm）：排便卡片展示 + 当日列表排序共用（RecordPanels 也引用） */
fun recordTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

/** 排便记录摘要文案 */
private fun symptomSummary(s: DailySymptom): String {
    val parts = mutableListOf("排便 ${s.bowelCount} 次")
    if (s.nightDiarrhea) parts.add("夜间腹泻")
    // 布里斯托分级直接显示描述（不显示数字编号）
    if (s.bristolType in 1..7) parts.add(BRISTOL_LABELS[s.bristolType - 1])
    if (s.blood in 1..3) parts.add("便血·${BLOOD_LABELS[s.blood]}")
    if (s.mucus) parts.add("黏液")
    if (s.painScore > 0) {
        val loc = if (s.painLocation in 1..4) "·${PAIN_LOCATION_LABELS[s.painLocation]}" else ""
        parts.add("腹痛 ${s.painScore} 分$loc")
    }
    if (s.urgency) parts.add("急迫感")
    if (s.note.isNotBlank()) parts.add(s.note)
    return parts.joinToString(" · ")
}

/** 记录类型小徽章（饮食/排便/服药/感受卡片第一行左侧） */
@Composable
internal fun TypeBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/** 小标签（食物/药品名等）；tint 非空时用对应颜色（如耐受状态色），否则中性色 */
@Composable
internal fun TagChip(
    text: String,
    tint: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (tint != null) {
                    tint.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                }
            )
            .border(
                1.dp,
                if (tint != null) tint.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (tint != null) tint else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 单条饮食记录卡片：类型徽章 + 时间 + 备注 + 食物标签（按耐受状态着色）+ 照片（供 DayRecordList 复用） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordCard(
    record: MealRecord,
    onPhotoClick: (String, List<String>) -> Unit,
    onEdit: (MealRecord) -> Unit,
    onDelete: (MealRecord) -> Unit,
    selectable: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    /** 食物名 -> 耐受状态（用于标签着色，绿=可耐受 红=不耐受 黄=谨慎） */
    tagTolerances: Map<String, FoodTolerance> = emptyMap()
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (selectable && selected) 2.dp else 1.dp,
                if (selectable && selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                RoundedCornerShape(12.dp)
            )
            .then(if (selectable) Modifier.clickable(onClick = onSelect) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类型徽章 + 时间；选中后（或日历页）右侧出现编辑/删除图标
                TypeBadge(record.mealType.label)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = record.time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!selectable || selected) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { onEdit(record) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "编辑记录",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除记录",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (record.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.note,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // 食物标签：按耐受列表中的状态着色（绿=可耐受 红=不耐受 黄=谨慎，无记录则中性色）
            if (record.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    record.tags.forEach { tag ->
                        TagChip(
                            text = tag,
                            tint = tagTolerances[tag]?.let { toleranceColor(it) }
                        )
                    }
                }
            }

            // 照片：横向滑动查看（一张照片一行，再多也不会撑高卡片）
            val photos = record.photos.filter { File(it).exists() }
            if (photos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    items(photos, key = { it }) { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "饮食照片",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPhotoClick(path, photos) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除记录") },
            text = { Text("确定删除这条${record.mealType.label}记录吗？照片不会从设备中删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(record)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 单条排便记录卡片：摘要 + 活动度徽章 + 删除（同一天可多条，点卡片进入编辑） */
@Composable
fun SymptomCard(
    symptom: DailySymptom,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    selectable: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (selectable && selected) 2.dp else 1.dp,
                if (selectable && selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                RoundedCornerShape(12.dp)
            )
            .then(
                if (selectable) {
                    Modifier.clickable(onClick = onSelect)
                } else {
                    Modifier.clickable(onClick = onOpen)
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 第一行：类型徽章 + 时间 + 活动度；选中后（或日历页）右侧出现编辑/删除图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge("排便")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    // 优先用记录时间（补录可调），旧数据无时间时回退到保存时间
                    text = symptom.time.ifEmpty { recordTime(symptom.createdAt) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(activityColor(symptom.activityLevel).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${symptom.activityLevel.label} ${activityScore(symptom)}分",
                        style = MaterialTheme.typography.labelSmall,
                        color = activityColor(symptom.activityLevel)
                    )
                }
                if (!selectable || selected) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "编辑排便记录",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除排便记录",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = symptomSummary(symptom),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除排便记录") },
            text = { Text("确定删除这条排便记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

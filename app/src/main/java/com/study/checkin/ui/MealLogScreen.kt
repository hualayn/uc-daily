package com.study.checkin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.study.checkin.data.ActivityLevel
import com.study.checkin.data.BLOOD_LABELS
import com.study.checkin.data.BRISTOL_LABELS
import com.study.checkin.data.DailySymptom
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MealType
import com.study.checkin.data.PAIN_LOCATION_LABELS
import com.study.checkin.data.activityLevel
import com.study.checkin.data.activityScore
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 月份切换滑动动画时长（标题与网格共用，保证同步滑动） */
private const val MONTH_SLIDE_MS = 260

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealLogScreen(
    state: MealUiState,
    onDateSelected: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToMonth: (YearMonth) -> Unit,
    onStartAdd: () -> Unit,
    onAddPhotoByCamera: () -> Unit,
    onAddPhotoByGallery: () -> Unit,
    onRemoveDraftPhoto: (Int) -> Unit,
    onDraftMealTypeChange: (MealType) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onSaveRecord: () -> Unit,
    onCancelAdd: () -> Unit,
    onEditRecord: (MealRecord) -> Unit,
    onDeleteRecord: (MealRecord) -> Unit,
    onOpenSymptomPanel: () -> Unit,
    onCloseSymptomPanel: () -> Unit,
    onSymptomDraftChange: (SymptomDraft) -> Unit,
    onSaveSymptom: () -> Unit,
    onDeleteSymptom: () -> Unit
) {
    // 全屏查看的照片路径
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }
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

    // 添加记录面板
    if (state.isAdding) {
        AddRecordPanel(
            state = state,
            onAddPhotoByCamera = onAddPhotoByCamera,
            onAddPhotoByGallery = onAddPhotoByGallery,
            onRemoveDraftPhoto = onRemoveDraftPhoto,
            onMealTypeChange = onDraftMealTypeChange,
            onNoteChange = onDraftNoteChange,
            onSave = onSaveRecord,
            onCancel = onCancelAdd
        )
        fullscreenPhoto?.let { path ->
            FullscreenPhoto(path = path, onDismiss = { fullscreenPhoto = null })
        }
        return
    }

    // 排便记录面板
    if (state.isSymptomPanelOpen) {
        BowelSymptomPanel(
            state = state,
            onDraftChange = onSymptomDraftChange,
            onSave = onSaveSymptom,
            onCancel = onCloseSymptomPanel
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "饮食记录",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 月份标题（随日历一起方向感知滑动，点击可快速选择年月）
        MonthHeader(
            month = state.currentMonth,
            onClick = { showYearMonthPicker = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 星期头（与日历同一 7 列网格布局，保证列位置完全对齐）
        val weekDays = listOf("一", "二", "三", "四", "五", "六", "日")
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(weekDays) { day ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

        Spacer(modifier = Modifier.height(16.dp))

        // 选中日期信息
        val isToday = state.selectedDate == state.today
        val dateText = if (isToday) "今天" else state.selectedDate.format(
            DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
        )
        val recordCount = state.selectedDateRecords.size
        val selectedSymptom = state.symptomByDate[state.selectedDate.toString()]
        Text(
            text = buildString {
                append(dateText)
                append(" · ")
                append(if (recordCount > 0) "$recordCount 条饮食" else "无饮食记录")
                if (selectedSymptom != null) {
                    append(" · ")
                    append("${selectedSymptom.activityLevel.label}（${activityScore(selectedSymptom)}分）")
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 当日排便记录卡片
        SymptomCard(
            symptom = selectedSymptom,
            onOpen = onOpenSymptomPanel,
            onDelete = onDeleteSymptom
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 当日饮食记录列表
        if (recordCount > 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.selectedDateRecords, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onPhotoClick = { fullscreenPhoto = it },
                        onEdit = { onEditRecord(record) },
                        onDelete = { onDeleteRecord(record) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "这一天还没有记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 添加记录按钮
        Button(
            onClick = onStartAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("＋ 添加记录", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 累计统计
        Text(
            text = "累计 ${state.totalRecordDays} 天有记录 · 共 ${state.totalRecords} 条",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 全屏查看照片
    fullscreenPhoto?.let { path ->
        FullscreenPhoto(path = path, onDismiss = { fullscreenPhoto = null })
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

/** 月份标题：随日历一起方向感知滑动（与 CalendarPager 同一过渡规格，不重叠），点击弹出年月快速选择 */
@Composable
private fun MonthHeader(month: YearMonth, onClick: () -> Unit) {
    AnimatedContent(
        targetState = month,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            if (targetState.isAfter(initialState)) {
                slideInHorizontally(tween(MONTH_SLIDE_MS)) { it } togetherWith
                    slideOutHorizontally(tween(MONTH_SLIDE_MS)) { -it }
            } else {
                slideInHorizontally(tween(MONTH_SLIDE_MS)) { -it } togetherWith
                    slideOutHorizontally(tween(MONTH_SLIDE_MS)) { it }
            }
        },
        label = "monthTitle"
    ) { m ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${m.year}年${m.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "选择年月",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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

/** 单条饮食记录卡片：餐次 + 时间 + 备注 + 照片 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordCard(
    record: MealRecord,
    onPhotoClick: (String) -> Unit,
    onEdit: (MealRecord) -> Unit,
    onDelete: (MealRecord) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 餐次标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = record.mealType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = record.time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

            if (record.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.note,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            val photos = record.photos.filter { File(it).exists() }
            if (photos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.forEach { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "饮食照片",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPhotoClick(path) },
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

/** 添加记录面板：餐次 + 照片（拍照/相册）+ 备注 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRecordPanel(
    state: MealUiState,
    onAddPhotoByCamera: () -> Unit,
    onAddPhotoByGallery: () -> Unit,
    onRemoveDraftPhoto: (Int) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val isEditing = state.editingRecordId != null
    val isToday = state.selectedDate == state.today
    val dateText = if (isToday) "今天" else state.selectedDate.format(
        DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "编辑记录" else "添加记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "取消")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isEditing) {
                "正在编辑 $dateText 的记录"
            } else {
                buildString {
                    append("记录到：").append(dateText)
                    if (!isToday) append("（补录）")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "餐次",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MealType.entries.forEach { type ->
                FilterChip(
                    selected = state.draft.mealType == type,
                    onClick = { onMealTypeChange(type) },
                    label = { Text(type.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "照片（可添加多张）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (state.draft.photos.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.draft.photos.forEachIndexed { index, path ->
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "待保存的照片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { onRemoveDraftPhoto(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "移除照片",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAddPhotoByCamera) {
                Text("📷 拍照")
            }
            OutlinedButton(onClick = onAddPhotoByGallery) {
                Text("🖼️ 从相册")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "备注",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.draft.note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            placeholder = { Text("吃了什么？吃完感觉如何？") },
            minLines = 3
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (isEditing) "保存修改" else "保存记录", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 全屏查看照片 */
@Composable
private fun FullscreenPhoto(
    path: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = "饮食照片",
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentScale = ContentScale.Fit
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White
            )
        }
    }
}

/** 活动度对应的热力色（日历圆点与徽章共用） */
private fun activityColor(level: ActivityLevel): Color = when (level) {
    ActivityLevel.REMISSION -> Color(0xFF4CAF50) // 绿：缓解
    ActivityLevel.MILD -> Color(0xFFF9A825)      // 黄：轻度
    ActivityLevel.MODERATE -> Color(0xFFEF6C00)  // 橙：中度
    ActivityLevel.SEVERE -> Color(0xFFE53935)    // 红：重度
}

/** 活动度徽章：浅色底 + 同色文字（缓解 2分） */
@Composable
private fun ActivityBadge(level: ActivityLevel, score: Int) {
    val color = activityColor(level)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${level.label} ${score}分",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/** 排便记录摘要文案 */
private fun symptomSummary(s: DailySymptom): String {
    val parts = mutableListOf("排便 ${s.bowelCount} 次")
    if (s.nightDiarrhea) parts.add("夜间腹泻")
    if (s.bristolType in 1..7) parts.add("便型 ${s.bristolType}")
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

/** 当日排便记录卡片：无记录时引导录入，有记录时展示摘要 + 活动度徽章 + 删除 */
@Composable
private fun SymptomCard(
    symptom: DailySymptom?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "排便记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (symptom == null) "今天还没有记录，点我录入" else symptomSummary(symptom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (symptom == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (symptom != null) {
                ActivityBadge(level = symptom.activityLevel, score = activityScore(symptom))
                Spacer(modifier = Modifier.width(8.dp))
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
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除排便记录") },
            text = { Text("确定删除这一天的排便记录吗？") },
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

/** 面板内分组小标题 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 排便/症状记录面板：排便次数、便型（布里斯托）、便血、腹痛等，保存时实时预览活动度 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BowelSymptomPanel(
    state: MealUiState,
    onDraftChange: (SymptomDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val draft = state.symptomDraft
    val isToday = state.selectedDate == state.today
    val dateText = if (isToday) "今天" else state.selectedDate.format(
        DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "排便记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "取消")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                append("记录到：").append(dateText)
                if (!isToday) append("（补录）")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 可滚动内容区
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 排便次数
            SectionLabel("排便次数（白天）")
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onDraftChange(draft.copy(bowelCount = (draft.bowelCount - 1).coerceAtLeast(0))) },
                    enabled = draft.bowelCount > 0
                ) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "${draft.bowelCount} 次",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = { onDraftChange(draft.copy(bowelCount = (draft.bowelCount + 1).coerceAtMost(30))) }
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilterChip(
                selected = draft.nightDiarrhea,
                onClick = { onDraftChange(draft.copy(nightDiarrhea = !draft.nightDiarrhea)) },
                label = { Text("夜间腹泻") }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("大便性状（布里斯托分级 1~7）")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BRISTOL_LABELS.forEachIndexed { i, label ->
                    val type = i + 1
                    FilterChip(
                        selected = draft.bristolType == type,
                        onClick = {
                            onDraftChange(draft.copy(bristolType = if (draft.bristolType == type) 0 else type))
                        },
                        label = { Text("$type $label") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("便血")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BLOOD_LABELS.forEachIndexed { i, label ->
                    FilterChip(
                        selected = draft.blood == i,
                        onClick = { onDraftChange(draft.copy(blood = i)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("其他症状")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = draft.mucus,
                    onClick = { onDraftChange(draft.copy(mucus = !draft.mucus)) },
                    label = { Text("黏液") }
                )
                FilterChip(
                    selected = draft.urgency,
                    onClick = { onDraftChange(draft.copy(urgency = !draft.urgency)) },
                    label = { Text("急迫感") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("腹痛（0~10 分）")
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = draft.painScore.toFloat(),
                    onValueChange = { onDraftChange(draft.copy(painScore = it.roundToInt())) },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${draft.painScore}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PAIN_LOCATION_LABELS.forEachIndexed { i, label ->
                    FilterChip(
                        selected = draft.painLocation == i,
                        onClick = { onDraftChange(draft.copy(painLocation = i)) },
                        label = { Text(if (i == 0) "无腹痛" else label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("其他不适")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { onDraftChange(draft.copy(note = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                placeholder = { Text("腹胀、乏力、发热等") },
                minLines = 2
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // 实时活动度预览
        val score = symptomDraftScore(draft)
        val level = ActivityLevel.fromScore(score)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityBadge(level = level, score = score)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "参考活动度（简化评分，仅供自我监测）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("保存记录", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

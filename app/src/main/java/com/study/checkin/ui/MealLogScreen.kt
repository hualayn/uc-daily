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
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MealType
import java.io.File
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
    onStartAdd: () -> Unit,
    onAddPhotoByCamera: () -> Unit,
    onAddPhotoByGallery: () -> Unit,
    onRemoveDraftPhoto: (Int) -> Unit,
    onDraftMealTypeChange: (MealType) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onSaveRecord: () -> Unit,
    onCancelAdd: () -> Unit,
    onDeleteRecord: (MealRecord) -> Unit
) {
    // 全屏查看的照片路径
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }

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

        // 月份标题（随日历一起方向感知滑动）
        MonthHeader(month = state.currentMonth)

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
        Text(
            text = buildString {
                append(dateText)
                append(" · ")
                append(if (recordCount > 0) "$recordCount 条记录" else "无记录")
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 当日记录列表
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
}

/** 月份标题：随日历一起方向感知滑动（与 CalendarPager 同一过渡规格，不重叠） */
@Composable
private fun MonthHeader(month: YearMonth) {
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
        Text(
            text = "${m.year}年${m.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
            // 有记录的小圆点（非选中且非本月不显示）
            if (day.hasRecord && !isSelected) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
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
                text = "添加记录",
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
            text = "照片",
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
            Text("保存记录", style = MaterialTheme.typography.titleMedium)
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

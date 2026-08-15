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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.request.ImageRequest
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** 月份切换滑动动画时长（标题与网格共用，保证同步滑动） */
private const val MONTH_SLIDE_MS = 260

@Composable
fun CheckinScreen(
    state: CheckinUiState,
    onCheckin: () -> Unit,
    onCameraClick: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val today = state.today
    val selectedDate = state.selectedDate
    val isToday = selectedDate == today

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
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "学习打卡",
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
                selectedDate = selectedDate,
                today = today,
                onDateClick = onDateSelected,
                // 往左滑动 → 下一个月；往右滑动 → 上一个月
                onSwipeLeft = { onNextMonth() },
                onSwipeRight = { onPrevMonth() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 选中日期信息
        val displayText = if (isToday) "今天" else selectedDate.format(
            java.time.format.DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 选中日期照片预览
        if (state.selectedDatePhoto.isNotEmpty()) {
            val file = File(state.selectedDatePhoto)
            if (file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = "打卡照片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 打卡按钮
        Button(
            onClick = onCheckin,
            enabled = isToday && !state.selectedDateChecked,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                text = when {
                    !isToday -> if (state.selectedDateChecked) "已打卡 ✓" else "未打卡"
                    state.selectedDateChecked -> "今日已打卡 ✓"
                    else -> "打卡"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (isToday) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCameraClick,
                enabled = !state.selectedDateChecked,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("📸 拍照")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 累计统计
        Text(
            text = "累计打卡 ${state.totalDays} 天",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    selectedDate: LocalDate,
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
            // 已打卡的小圆点（非选中且非本月不显示）
            if (day.checked && !isSelected) {
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

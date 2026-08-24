package com.study.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.study.checkin.data.ActivityLevel
import com.study.checkin.data.DailySymptom
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MedRecord
import com.study.checkin.data.activityLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 首页顶部轮播横幅（按日期取一条） */
private val BANNERS = listOf(
    "一定要好好吃饭！",
    "记录每一天，安心多一点",
    "少食多餐，温柔对待肠胃",
    "症状会波动，你比它更强大",
    "今天也好好照顾自己",
    "清淡一点，肠道轻松一点",
    "按时吃饭，按时记录",
    "记住今天，身体都记得"
)

/** 星期文案（周一开头） */
private val WEEK_CHARS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 统计卡主题色：饮食 / 便便 / 服药 */
private val MEAL_ACCENT = Color(0xFF43A047)
private val BOWEL_ACCENT = Color(0xFFF9A825)
private val MED_ACCENT = Color(0xFF1E88E5)

/** 欢迎卡：渐变底 + 头像 + 按时段问候 + 今日寄语 */
@Composable
private fun WelcomeCard(state: MealUiState) {
    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..21 -> "晚上好"
        else -> "夜深了"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = avatarIcon(state.avatar),
                contentDescription = "头像",
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$greeting，${state.nickname}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = BANNERS[state.today.dayOfYear % BANNERS.size],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 首页：
 * 顶部（头像 + 横幅）→ 日期 + 周历（左右滑动换周）→ 当日统计 → 添加入口（饮食/便便/服药/笔记）→ 当天记录
 */
@Composable
fun HomeScreen(
    state: MealUiState,
    onDateSelected: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onAddSymptom: () -> Unit,
    onEditSymptom: (DailySymptom) -> Unit,
    onAddNote: () -> Unit,
    onFilterToggle: (DayFilter) -> Unit,
    onPhotoClick: (String, List<String>) -> Unit,
    onEditRecord: (MealRecord) -> Unit,
    onDeleteRecord: (MealRecord) -> Unit,
    onDeleteSymptom: (Int) -> Unit,
    onEditMed: (MedRecord) -> Unit,
    onDeleteMed: (MedRecord) -> Unit,
    onDeleteNote: () -> Unit
) {
    if (state.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val symptoms = state.selectedDateSymptoms

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
    ) {
        // ① 欢迎卡：渐变底 + 头像 + 按时段问候 + 今日寄语
        WelcomeCard(state = state)

        Spacer(modifier = Modifier.height(12.dp))

        // ② 今日卡片：日期头 + 周历 + 当日统计（统一容器）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                // 天蓝色填充
                containerColor = Color(0xFFD6EAF8)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                // 日期头：左右箭头换周；第一行居中 "X月X日 周X"（周几蓝色高亮），第二行灰色小字 "X年，第X周"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevWeek) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowLeft,
                            contentDescription = "上一周",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.selectedDate.monthValue}月${state.selectedDate.dayOfMonth}日",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = weekLabel(state.selectedDate),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${state.selectedDate.year}年，第${isoWeek(state.selectedDate)}周",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNextWeek) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = "下一周",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 周历（左右滑动换周，点击选日）
                WeekStrip(
                    state = state,
                    onDateSelected = onDateSelected,
                    onPrevWeek = onPrevWeek,
                    onNextWeek = onNextWeek
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 当日统计（按类型着色，点击筛选当天记录）：饮食 / 服药 / 便便
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        emoji = "🍚",
                        value = "${state.selectedDateRecords.size}",
                        label = "饮食",
                        accent = MEAL_ACCENT,
                        active = state.dayRecordFilter == DayFilter.MEAL,
                        onClick = { onFilterToggle(DayFilter.MEAL) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        emoji = "💊",
                        value = "${state.selectedDateMeds.size}",
                        label = "服药",
                        accent = MED_ACCENT,
                        active = state.dayRecordFilter == DayFilter.MED,
                        onClick = { onFilterToggle(DayFilter.MED) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        emoji = "💩",
                        value = if (symptoms.isEmpty()) "—" else "${symptoms.sumOf { it.bowelCount }}",
                        label = "便便",
                        accent = BOWEL_ACCENT,
                        active = state.dayRecordFilter == DayFilter.BOWEL,
                        onClick = { onFilterToggle(DayFilter.BOWEL) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ③ 当天记录（标题 + 条数徽章；取消筛选 = 再点一次已选中的统计卡）
        DayRecordHeader(state = state)
        Spacer(modifier = Modifier.height(8.dp))

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
            onOpenNote = onAddNote,
            onDeleteNote = onDeleteNote,
            // 首页：点卡片选中，选中后出现编辑/删除按钮
            selectable = true
        )
    }
}

/** 周历条：显示选中日期所在周，水平滑动切换周 */
@Composable
private fun WeekStrip(
    state: MealUiState,
    onDateSelected: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val density = LocalDensity.current
    val onPrevRef = rememberUpdatedState(onPrevWeek)
    val onNextRef = rememberUpdatedState(onNextWeek)
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { 60.dp.toPx() }

    // 展示周由 homeWeekAnchor 决定：滑动/箭头换周只移动 anchor，不改变选中日期
    val weekStart = state.homeWeekAnchor.with(DayOfWeek.MONDAY)
    val days = (0 until 7).map { weekStart.plusDays(it.toLong()) }

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
                            accum < -thresholdPx -> onNextRef.value()
                            accum > thresholdPx -> onPrevRef.value()
                        }
                    },
                    onDragCancel = { dragAccum = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccum += dragAmount
                    }
                )
            }
    ) {
        // 选中日期对应的"周X"高亮
        val selectedWeekIndex = state.selectedDate.dayOfWeek.value - 1
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEachIndexed { index, day ->
                WeekDayCell(
                    day = day,
                    weekChar = WEEK_CHARS[index],
                    weekHighlighted = index == selectedWeekIndex,
                    selected = day == state.selectedDate,
                    isToday = day == state.today,
                    activity = state.symptomByDate[day.toString()]?.activityLevel,
                    onClick = { onDateSelected(day) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 周历中的单个日期 */
@Composable
private fun WeekDayCell(
    day: LocalDate,
    weekChar: String,
    weekHighlighted: Boolean,
    selected: Boolean,
    isToday: Boolean,
    activity: ActivityLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = weekChar,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (weekHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (weekHighlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else if (isToday) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 活动度热力点（无记录时占位保持对齐）
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .then(
                    if (activity != null && !selected) {
                        Modifier.background(activityColor(activity))
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/** 当日统计卡片（柔和主题色底 + 同色大数字；点击筛选当天记录，选中态加深并描边） */
@Composable
private fun StatCard(
    emoji: String,
    value: String,
    label: String,
    accent: Color,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (active) 0.28f else 0.12f))
            .then(
                if (active) {
                    Modifier.border(1.dp, accent, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


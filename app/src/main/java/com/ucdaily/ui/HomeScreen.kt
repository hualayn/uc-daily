package com.ucdaily.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ucdaily.R
import com.ucdaily.data.ActivityLevel
import com.ucdaily.data.DailySymptom
import com.ucdaily.data.MealRecord
import com.ucdaily.data.MedRecord
import com.ucdaily.data.activityLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 星期文案资源 id（周一开头） */
private val WEEK_RES = listOf(
    R.string.week_mon, R.string.week_tue, R.string.week_wed, R.string.week_thu,
    R.string.week_fri, R.string.week_sat, R.string.week_sun
)

/** 统计卡主题色：饮食 / 便便 / 服药 */
private val MEAL_ACCENT = Color(0xFF43A047)
private val BOWEL_ACCENT = Color(0xFFF9A825)
private val MED_ACCENT = Color(0xFF1E88E5)

/** 欢迎卡：渐变底 + 头像（点击进入"我的"）+ 按时段问候 + 今日寄语 + 右上角服药提醒铃铛（未到剂量时亮红点）+ 齿轮（进入设置） */
@Composable
private fun WelcomeCard(
    state: MealUiState,
    medMissing: Boolean,
    onBellClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> stringResource(R.string.greeting_morning)
        in 11..13 -> stringResource(R.string.greeting_noon)
        in 14..17 -> stringResource(R.string.greeting_afternoon)
        in 18..21 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_night)
    }
    // 内置默认寄语（按当前语言解析，列表为空时回退轮播）
    val defaultSlogans = DEFAULT_HOME_SLOGANS_RES.map { stringResource(it) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = avatarIcon(state.avatar),
                contentDescription = stringResource(R.string.home_avatar_content_desc),
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
                // 横幅轮播：每天按日期取一条（"我的→首页寄语"可修改/增删；列表为空时回退内置默认）
                text = state.homeSlogans
                    .ifEmpty { defaultSlogans }
                    .let { it[state.today.dayOfYear % it.size] },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 右上角服药提醒铃铛：今天实际服药数 < 已到点的应服药数时亮红点
        Box {
            IconButton(onClick = onBellClick) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.med_reminder_notification_title),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (medMissing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -8.dp, y = 8.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
            }
        }
        // 铃铛右侧齿轮：直接进入设置页
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.profile_menu_settings),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 首页：
 * 顶部（点击头像进入"我的" + 横幅）→ 日期 + 日历（默认周视图：左右滑动/箭头换周；下滑展开整月、整月上滑收起；
 * 整月视图下左右滑动/箭头换月）→ 当日统计 → 添加入口（饮食/便便/服药/笔记）→ 当天记录
 */
@Composable
fun HomeScreen(
    state: MealUiState,
    onAvatarClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
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
    onDeleteNote: () -> Unit,
    onAddMed: () -> Unit
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

    // 日历是否展开为整月：周视图下滑展开，整月视图上滑收起；
    // 展开时左右滑动/箭头换月，收起时换周
    var expanded by remember { mutableStateOf(false) }

    // 服药提醒铃铛：每分钟检查一次"今天的提醒时间是否已到点但未记录服药"
    var nowMinute by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMinute = LocalTime.now()
            delay((60 - nowMinute.second % 60) * 1000L)
        }
    }
    val medStatus = computeMedReminderStatus(state.medReminderTimes, state.todayMedTimes.size, nowMinute)
    var showMedReminder by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
    ) {
        // ① 欢迎卡：渐变底 + 头像（点击进入"我的"）+ 按时段问候 + 今日寄语（右上角 = 服药提醒铃铛）
        WelcomeCard(
            state = state,
            medMissing = medStatus.missing,
            onBellClick = { showMedReminder = true },
            onSettingsClick = onOpenSettings,
            onAvatarClick = onAvatarClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ② 今日卡片：日期头 + 周历 + 当日统计（统一容器）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                // 天蓝色填充（深色主题自动切藏蓝）
                containerColor = blueCardBackground()
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                // 日期头：左右箭头——周视图换周，整月视图换月；
                // 第一行居中 "X月X日 周X"（周几蓝色高亮），第二行灰色小字 "X年，第X周"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (expanded) onPrevMonth() else onPrevWeek() }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(
                                if (expanded) R.string.home_prev_month else R.string.home_prev_week
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (expanded) {
                            // 整月视图：大字显示日历当前展示的月份
                            Text(
                                text = stringResource(
                                    R.string.home_month_header,
                                    state.homeWeekAnchor.year,
                                    state.homeWeekAnchor.monthValue
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.home_date_header,
                                        state.selectedDate.monthValue,
                                        state.selectedDate.dayOfMonth
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(weekLabelRes(state.selectedDate)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.home_year_week_header,
                                    state.selectedDate.year,
                                    isoWeek(state.selectedDate)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { if (expanded) onNextMonth() else onNextWeek() }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(
                                if (expanded) R.string.home_next_month else R.string.home_next_week
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 日历（默认周视图：左右滑动/箭头换周，点击选日；下滑展开整月，整月视图上滑收起回周视图；
                // 整月视图下左右滑动/箭头换月）
                HomeCalendar(
                    state = state,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onDateSelected = onDateSelected,
                    onPrevWeek = onPrevWeek,
                    onNextWeek = onNextWeek,
                    onPrevMonth = onPrevMonth,
                    onNextMonth = onNextMonth
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
                        label = stringResource(R.string.type_meal),
                        accent = MEAL_ACCENT,
                        active = state.dayRecordFilter == DayFilter.MEAL,
                        onClick = { onFilterToggle(DayFilter.MEAL) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        emoji = "💊",
                        value = "${state.selectedDateMeds.size}/${state.medReminderTimes.size}",
                        label = stringResource(R.string.type_med),
                        accent = MED_ACCENT,
                        active = state.dayRecordFilter == DayFilter.MED,
                        onClick = { onFilterToggle(DayFilter.MED) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        emoji = "💩",
                        value = if (symptoms.isEmpty()) "—" else "${symptoms.sumOf { it.bowelCount }}",
                        label = stringResource(R.string.type_bowel),
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

    // 点铃铛：未达剂量时显示未服次数 + "去服药"入口
    if (showMedReminder) {
        AlertDialog(
            onDismissRequest = { showMedReminder = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MED_ACCENT
                )
            },
            title = { Text(stringResource(R.string.med_reminder_notification_title)) },
            text = {
                Text(
                    if (medStatus.missing) {
                        // 与系统通知同一文案
                        stringResource(
                            R.string.med_reminder_missed,
                            medStatus.dueTimes.size - state.todayMedTimes.size
                        )
                    } else if (medStatus.dueTimes.isEmpty()) {
                        stringResource(R.string.home_med_no_due_time)
                    } else {
                        stringResource(
                            R.string.home_med_all_done,
                            state.todayMedTimes.size,
                            medStatus.dueTimes.size
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMedReminder = false
                    if (medStatus.missing) onAddMed()
                }) {
                    Text(
                        if (medStatus.missing) stringResource(R.string.home_med_go)
                        else stringResource(R.string.common_got_it)
                    )
                }
            },
            dismissButton = {
                if (medStatus.missing) {
                    TextButton(onClick = { showMedReminder = false }) {
                        Text(stringResource(R.string.common_later))
                    }
                }
            }
        )
    }
}

/** "HH:mm" 转当天分钟数（格式不合法返回 null）；服药提醒通知（ViewModel）也复用 */
internal fun timeToMinutes(t: String): Int? {
    val parts = t.split(':')
    if (parts.size != 2) return null
    return parts[0].toIntOrNull()?.let { h -> parts[1].toIntOrNull()?.let { m -> h * 60 + m } }
}

/** 服药提醒状态：已到点的提醒时间列表 + 是否未达剂量 */
private data class MedReminderStatus(
    /** 今天已到点（<= 当前时刻）的提醒时间 */
    val dueTimes: List<String>,
    /** 未达剂量：今天实际服药总条数 < 已到点的应服药总次数 */
    val missing: Boolean
)

/**
 * 今天是否未服药（按总数判定）：
 * - 当前应服药总数 = 已到点（<= 当前时刻）的提醒时间个数；
 * - 实际服药总数 = 今天的服药记录总条数；
 * - 实际 < 应服 → 未服药（亮红点）；实际 >= 应服 → 已服药。
 */
private fun computeMedReminderStatus(
    reminders: List<String>,
    todayMedCount: Int,
    now: LocalTime
): MedReminderStatus {
    val nowMin = now.hour * 60 + now.minute
    val dueTimes = reminders.filter { timeToMinutes(it)?.let { m -> nowMin >= m } == true }
    return MedReminderStatus(dueTimes, todayMedCount < dueTimes.size)
}

/**
 * 首页日历：默认周视图（星期头 + anchor 周）；
 * 周视图上向下滑动 → 展开为 homeWeekAnchor 所在月的整月视图；
 * 整月视图上向上滑动 → 收起回周视图。
 * 横向滑动：周视图换周，整月视图换月；只移动 homeWeekAnchor，不改变选中日期。
 * 展开状态（expanded）由父级持有，以便日期头箭头同步按周/月切换。
 */
@Composable
private fun HomeCalendar(
    state: MealUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val density = LocalDensity.current
    val onPrevRef = rememberUpdatedState(onPrevWeek)
    val onNextRef = rememberUpdatedState(onNextWeek)
    val onPrevMonthRef = rememberUpdatedState(onPrevMonth)
    val onNextMonthRef = rememberUpdatedState(onNextMonth)
    val expandedRef = rememberUpdatedState(expanded)
    val onExpandedChangeRef = rememberUpdatedState(onExpandedChange)
    val thresholdPx = with(density) { 50.dp.toPx() }

    // 展示周由 homeWeekAnchor 决定：滑动/箭头换周只移动 anchor，不改变选中日期
    val anchor = state.homeWeekAnchor
    val anchorWeekStart = anchor.with(DayOfWeek.MONDAY)
    val anchorMonth = YearMonth.from(anchor)
    val firstWeekStart = anchorMonth.atDay(1).with(DayOfWeek.MONDAY)
    val lastWeekStart = anchorMonth.atEndOfMonth().with(DayOfWeek.MONDAY)
    // 覆盖整月的全部周（周一开头）
    val allWeekStarts = buildList {
        var week = firstWeekStart
        while (week <= lastWeekStart) {
            add(week)
            week = week.plusWeeks(1)
        }
    }
    val anchorIndex = allWeekStarts.indexOfFirst { it == anchorWeekStart }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        val absDx = abs(dx)
                        val absDy = abs(dy)
                        when {
                            // 横向为主：周视图换周，整月视图换月
                            absDx >= absDy && absDx > thresholdPx -> when {
                                dx < 0 ->
                                    if (expandedRef.value) onNextMonthRef.value() else onNextRef.value()
                                else ->
                                    if (expandedRef.value) onPrevMonthRef.value() else onPrevRef.value()
                            }
                            // 纵向为主：周视图下滑 → 整月；整月上滑 → 周视图
                            absDy > thresholdPx -> when {
                                dy > 0 && !expandedRef.value -> onExpandedChangeRef.value(true)
                                dy < 0 && expandedRef.value -> onExpandedChangeRef.value(false)
                            }
                        }
                        dx = 0f
                        dy = 0f
                    },
                    onDragCancel = { dx = 0f; dy = 0f },
                    onDrag = { _, amount ->
                        dx += amount.x
                        dy += amount.y
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 星期头（选中日期对应的周几高亮）
            val selectedWeekIndex = state.selectedDate.dayOfWeek.value - 1
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEK_RES.forEachIndexed { index, res ->
                    Text(
                        text = stringResource(res),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == selectedWeekIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == selectedWeekIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // anchor 之前的周：仅整月视图显示（从 anchor 侧向上展开、向 anchor 侧收起）
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    allWeekStarts.take(anchorIndex).forEach { weekStart ->
                        HomeCalendarWeekRow(
                            weekStart = weekStart,
                            anchorMonth = anchorMonth,
                            state = state,
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }

            // anchor 周：周视图 = 星期头 + 这一行
            HomeCalendarWeekRow(
                weekStart = anchorWeekStart,
                anchorMonth = anchorMonth,
                state = state,
                onDateSelected = onDateSelected
            )

            // anchor 之后的周：仅整月视图显示（从 anchor 侧向下展开、向 anchor 侧收起）
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    allWeekStarts.drop(anchorIndex + 1).forEach { weekStart ->
                        HomeCalendarWeekRow(
                            weekStart = weekStart,
                            anchorMonth = anchorMonth,
                            state = state,
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }
        }
    }
}

/** 日历中的一行 7 天（其他月份的日期淡显，仍可点选） */
@Composable
private fun HomeCalendarWeekRow(
    weekStart: LocalDate,
    anchorMonth: YearMonth,
    state: MealUiState,
    onDateSelected: (LocalDate) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        (0 until 7).forEach { offset ->
            val day = weekStart.plusDays(offset.toLong())
            HomeCalendarDayCell(
                day = day,
                dimmed = YearMonth.from(day) != anchorMonth,
                selected = day == state.selectedDate,
                isToday = day == state.today,
                activity = state.symptomByDate[day.toString()]?.activityLevel,
                onClick = { onDateSelected(day) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 日历行中的单个日期：选中/今天高亮，跨月日期淡显，底部活动度热力点 */
@Composable
private fun HomeCalendarDayCell(
    day: LocalDate,
    dimmed: Boolean,
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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    when {
                        selected -> Modifier.background(MaterialTheme.colorScheme.primary)
                        isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else -> Modifier
                    }
                )
                .clickable(onClick = onClick)
                .alpha(if (dimmed) 0.35f else 1f),
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
                .alpha(if (dimmed) 0.35f else 1f)
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


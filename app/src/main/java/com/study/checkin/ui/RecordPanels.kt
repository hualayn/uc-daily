package com.study.checkin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.os.SystemClock
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.study.checkin.data.ActivityLevel
import com.study.checkin.data.BLOOD_LABELS
import com.study.checkin.data.BRISTOL_LABELS
import com.study.checkin.data.DailyNote
import com.study.checkin.data.DailySymptom
import com.study.checkin.data.FoodTag
import com.study.checkin.data.FoodTolerance
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MealType
import com.study.checkin.data.MedRecord
import com.study.checkin.data.PAIN_LOCATION_LABELS
import com.study.checkin.data.activityLevel
import com.study.checkin.data.activityScore
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 全局记录面板层：渲染在 Tab 内容之上，任何 Tab 都可以打开面板。
 * 面板互斥（状态机保证同一时间至多打开一个）。
 */
@Composable
fun RecordOverlays(
    state: MealUiState,
    onAddPhotoByCamera: () -> Unit,
    onAddPhotoByGallery: () -> Unit,
    onRemoveDraftPhoto: (Int) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddFood: (String, FoodTolerance) -> Unit,
    onDraftMealTypeChange: (MealType) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onDraftTimeChange: (String) -> Unit,
    onSaveRecord: () -> Unit,
    onCancelAdd: () -> Unit,
    onCloseSymptom: () -> Unit,
    onSymptomDraftChange: (SymptomDraft) -> Unit,
    onSaveSymptom: () -> Unit,
    onMedDraftChange: (MedDraft) -> Unit,
    onSaveMed: () -> Unit,
    onCancelMed: () -> Unit,
    onRemoveCommonMed: (String) -> Unit,
    onNoteDraftChange: (NoteDraft) -> Unit,
    onSaveNote: () -> Unit,
    onCancelNote: () -> Unit,
    onDismissPhoto: () -> Unit
) {
    when {
        state.isAdding -> AddRecordPanel(
            state = state,
            onAddPhotoByCamera = onAddPhotoByCamera,
            onAddPhotoByGallery = onAddPhotoByGallery,
            onRemoveDraftPhoto = onRemoveDraftPhoto,
            onToggleTag = onToggleTag,
            onAddFood = onAddFood,
            onMealTypeChange = onDraftMealTypeChange,
            onNoteChange = onDraftNoteChange,
            onTimeChange = onDraftTimeChange,
            onSave = onSaveRecord,
            onCancel = onCancelAdd
        )

        state.isSymptomPanelOpen -> BowelSymptomPanel(
            state = state,
            onDraftChange = onSymptomDraftChange,
            onSave = onSaveSymptom,
            onCancel = onCloseSymptom
        )

        state.isMedPanelOpen -> MedRecordPanel(
            state = state,
            onDraftChange = onMedDraftChange,
            onSave = onSaveMed,
            onCancel = onCancelMed,
            onRemoveCommonMed = onRemoveCommonMed
        )

        state.isNotePanelOpen -> NotePanel(
            state = state,
            onDraftChange = onNoteDraftChange,
            onSave = onSaveNote,
            onCancel = onCancelNote
        )

        else -> state.fullscreenPhotos.takeIf { it.isNotEmpty() }?.let { photos ->
            FullscreenPhoto(
                photos = photos,
                initialIndex = state.fullscreenPhotoIndex,
                onDismiss = onDismissPhoto
            )
        }
    }
}

/** 当日全部混排条目（不筛选，按时间升序） */
private fun MealUiState.allEntries(): List<DayEntry> =
    (
        selectedDateSymptoms.map { SymptomEntry(it) } +
            selectedDateRecords.map { MealEntry(it) } +
            selectedDateMeds.map { MedEntry(it) }
        ).sortedWith(compareBy<DayEntry> { it.sortTime }.thenBy { it.key })

/** 按当前筛选取当日可见的混排条目（null = 全部，按时间升序） */
private fun MealUiState.visibleEntries(): List<DayEntry> {
    val all = allEntries()
    return when (dayRecordFilter) {
        null -> all
        DayFilter.MEAL -> all.filterIsInstance<MealEntry>()
        DayFilter.BOWEL -> all.filterIsInstance<SymptomEntry>()
        DayFilter.MED -> all.filterIsInstance<MedEntry>()
    }
}

/** 筛选状态下不展示"今日感受"（它不属于任何统计类别） */
private fun MealUiState.visibleNote(): DailyNote? =
    if (dayRecordFilter == null) selectedDateNote else null

/** 当前实际展示的记录条数（含置顶的感受） */
private fun MealUiState.visibleCount(): Int =
    (if (visibleNote() != null) 1 else 0) + visibleEntries().size

/** "当天记录"标题行：标题 + 条数徽章；提供 onClearFilter 时，筛选状态下显示"恢复"按钮 */
@Composable
fun DayRecordHeader(
    state: MealUiState,
    onClearFilter: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "当天记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        val count = state.visibleCount()
        if (count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        if (state.dayRecordFilter != null && onClearFilter != null) {
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onClearFilter) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("恢复")
            }
        }
    }
}

/** 当日记录列表（首页与日历页共用）：今日感受置顶；排便/饮食/服药按时间顺序混排；支持按统计类别筛选 */
@Composable
fun DayRecordList(
    state: MealUiState,
    modifier: Modifier,
    onPhotoClick: (String, List<String>) -> Unit,
    onEditRecord: (MealRecord) -> Unit,
    onDeleteRecord: (MealRecord) -> Unit,
    onOpenSymptom: (DailySymptom) -> Unit,
    onDeleteSymptom: (Int) -> Unit,
    onEditMed: (MedRecord) -> Unit,
    onDeleteMed: (MedRecord) -> Unit,
    onOpenNote: () -> Unit,
    onDeleteNote: () -> Unit,
    /** 首页模式：点卡片选中（单选），选中后才出现编辑/删除按钮；日历页保持原交互 */
    selectable: Boolean = false,
    /** 是否应用首页统计筛选（饮食/服药/便便）；日历页传 false，始终显示全部记录 */
    applyFilter: Boolean = true,
    /** 日历页类别多选筛选（非 null 时按所选类别过滤；null = 不应用） */
    calendarFilter: Set<CalendarCategory>? = null
) {
    var entries = if (applyFilter) state.visibleEntries() else state.allEntries()
    var note = if (applyFilter) state.visibleNote() else state.selectedDateNote

    // 日历页：按所选类别过滤（感受仅当 NOTE 选中时显示）
    if (calendarFilter != null) {
        entries = entries.filter { entry ->
            when (entry) {
                is MealEntry -> CalendarCategory.MEAL in calendarFilter
                is SymptomEntry -> CalendarCategory.BOWEL in calendarFilter
                is MedEntry -> CalendarCategory.MED in calendarFilter
            }
        }
        if (CalendarCategory.NOTE !in calendarFilter) note = null
    }
    val hasAny = entries.isNotEmpty() || note != null
    var selectedKey by remember { mutableStateOf<String?>(null) }
    /** 食物名 -> 耐受状态，用于饮食卡片标签着色 */
    val tagTolerances: Map<String, FoodTolerance> =
        state.foodTags.associate { it.name to FoodTolerance.fromValue(it.tolerance) }

    if (hasAny) {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 今日感受：置顶
            if (note != null) {
                item(key = "note") {
                    NoteCard(
                        note = note,
                        onOpen = onOpenNote,
                        onDelete = onDeleteNote,
                        selectable = selectable,
                        selected = selectedKey == "note",
                        onSelect = {
                            selectedKey = if (selectedKey == "note") null else "note"
                        }
                    )
                }
            }
            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    is SymptomEntry -> SymptomCard(
                        symptom = entry.value,
                        onOpen = { onOpenSymptom(entry.value) },
                        onDelete = { onDeleteSymptom(entry.value.id) },
                        selectable = selectable,
                        selected = selectedKey == entry.key,
                        onSelect = {
                            selectedKey = if (selectedKey == entry.key) null else entry.key
                        }
                    )
                    is MealEntry -> RecordCard(
                        record = entry.value,
                        onPhotoClick = onPhotoClick,
                        onEdit = { onEditRecord(entry.value) },
                        onDelete = { onDeleteRecord(entry.value) },
                        selectable = selectable,
                        selected = selectedKey == entry.key,
                        onSelect = {
                            selectedKey = if (selectedKey == entry.key) null else entry.key
                        },
                        tagTolerances = tagTolerances
                    )
                    is MedEntry -> MedRecordCard(
                        med = entry.value,
                        onEdit = { onEditMed(entry.value) },
                        onDelete = { onDeleteMed(entry.value) },
                        selectable = selectable,
                        selected = selectedKey == entry.key,
                        onSelect = {
                            selectedKey = if (selectedKey == entry.key) null else entry.key
                        }
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🌿", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        applyFilter && state.dayRecordFilter != null ->
                            "这一天没有${state.dayRecordFilter!!.label}记录"
                        // 日历页多选筛选后为空：区分"当天本就没有记录"与"当天有记录但被类别筛掉"
                        calendarFilter != null ->
                            if (state.allEntries().isNotEmpty() || state.selectedDateNote != null) {
                                "这一天没有所选类别的记录"
                            } else {
                                "这一天还没有记录"
                            }
                        else -> "这一天还没有记录"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "好好吃饭，按时吃药，记一笔给自己",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 当日列表混合条目：统一按时间排序的抽象（value 由各子类的类型化属性提供） */
private sealed interface DayEntry {
    val key: String
    /** 排序时间（HH:mm，格式统一可直接字符串比较） */
    val sortTime: String
}

private data class SymptomEntry(val value: DailySymptom) : DayEntry {
    override val key: String get() = "symptom_${value.id}"
    /** 优先用记录时间（补录可调），旧数据无时间时回退到保存时间 */
    override val sortTime: String get() = value.time.ifEmpty { recordTime(value.createdAt) }
}

private data class MealEntry(val value: MealRecord) : DayEntry {
    override val key: String get() = "meal_${value.id}"
    override val sortTime: String get() = value.time
}

private data class MedEntry(val value: MedRecord) : DayEntry {
    override val key: String get() = "med_${value.id}"
    override val sortTime: String get() = value.time
}

/** 添加/编辑饮食记录面板：餐次 + 照片 + 添加耐受食物 + 食物标签 + 备注 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRecordPanel(
    state: MealUiState,
    onAddPhotoByCamera: () -> Unit,
    onAddPhotoByGallery: () -> Unit,
    onRemoveDraftPhoto: (Int) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddFood: (String, FoodTolerance) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onNoteChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
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
                text = if (isEditing) "编辑饮食" else "添加饮食",
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

        // 可滚动内容区
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 记录时间（补录时可调整）
            SectionLabel("时间")
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(time = state.draft.time, onSelect = onTimeChange)

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("餐次")
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
            SectionLabel("照片（可添加多张）")
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

            // 添加食物标签（从耐受页移来）
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("添加食物标签")
            Spacer(modifier = Modifier.height(8.dp))
            AddFoodSection(onAddFood = onAddFood)

            if (state.foodTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("食物标签（可多选）")
                Spacer(modifier = Modifier.height(8.dp))
                // 按类别摆放：可耐受 → 谨慎 → 不耐受（稳定排序，组内顺序不变）
                val orderedTags = state.foodTags.sortedBy {
                    when (FoodTolerance.fromValue(it.tolerance)) {
                        FoodTolerance.OK -> 0
                        FoodTolerance.CAUTION -> 1
                        FoodTolerance.BAD -> 2
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    orderedTags.forEach { tag ->
                        ToleranceTagChip(
                            tag = tag,
                            selected = state.draft.tags.contains(tag.name),
                            onClick = { onToggleTag(tag.name) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("备注")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.draft.note,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                placeholder = { Text("烹饪方式？吃完感觉如何？") },
                minLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

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

/** 服药记录面板：药名（含常用药快捷选择）+ 剂量 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedRecordPanel(
    state: MealUiState,
    onDraftChange: (MedDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRemoveCommonMed: (String) -> Unit
) {
    val draft = state.medDraft
    val isEditing = state.editingMedId != null
    val isToday = state.selectedDate == state.today
    val dateText = if (isToday) "今天" else state.selectedDate.format(
        DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
    )
    /** 长按后显示删除角标的标签名（null = 无） */
    var editingMedName by remember { mutableStateOf<String?>(null) }

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
                text = if (isEditing) "编辑服药" else "添加服药",
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

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 记录时间（补录时可调整）
            SectionLabel("时间")
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(time = draft.time) {
                onDraftChange(draft.copy(time = it))
            }

            Spacer(modifier = Modifier.height(20.dp))
            if (state.commonMedNames.isNotEmpty()) {
                SectionLabel("常用药物")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.commonMedNames.forEach { name ->
                        CommonMedChip(
                            name = name,
                            selected = draft.name == name,
                            showDelete = editingMedName == name,
                            onClick = { onDraftChange(draft.copy(name = name)) },
                            onLongClick = { editingMedName = name },
                            onDelete = {
                                editingMedName = null
                                onRemoveCommonMed(name)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            SectionLabel("药物名称")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("如：美沙拉嗪") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("剂量")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.dose,
                onValueChange = { onDraftChange(draft.copy(dose = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("如：1 片 / 500mg") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = onSave,
            enabled = draft.name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (isEditing) "保存修改" else "保存记录", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 常用药物快捷标签：点击选中该药名；长按在右上角显示删除角标，
 * 点击角标移除该标签（只移除快捷标签，不影响已保存的服药记录）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommonMedChip(
    name: String,
    selected: Boolean,
    showDelete: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
            shape = RoundedCornerShape(8.dp),
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            border = if (selected) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
        if (showDelete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = -5.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除",
                    modifier = Modifier.size(10.dp),
                    tint = Color.White
                )
            }
        }
    }
}

/** 每日感受面板 */
@Composable
private fun NotePanel(
    state: MealUiState,
    onDraftChange: (NoteDraft) -> Unit,
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
                text = "今日感受",
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
        OutlinedTextField(
            value = state.noteDraft.text,
            onValueChange = { onDraftChange(NoteDraft(text = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = { Text("今天感觉怎么样？\n排便、睡眠、心情、不适…") },
            minLines = 8
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSave,
            enabled = state.noteDraft.text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("保存感受", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 服药记录卡片 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedRecordCard(
    med: MedRecord,
    onEdit: (MedRecord) -> Unit,
    onDelete: (MedRecord) -> Unit,
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
            .then(if (selectable) Modifier.clickable(onClick = onSelect) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 第一行：类型徽章 + 时间；选中后（或日历页）右侧出现编辑/删除图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge("服药")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = med.time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!selectable || selected) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { onEdit(med) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "编辑服药记录",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除服药记录",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // 第二行：服用药品标签（药名 + 剂量）
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagChip(text = med.name)
                if (med.dose.isNotBlank()) {
                    TagChip(text = med.dose)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除服药记录") },
            text = { Text("确定删除“${med.name}”这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(med)
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

/** 每日感受卡片 */
@Composable
private fun NoteCard(
    note: DailyNote,
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
            // 第一行：类型徽章；选中后（或日历页）右侧出现编辑/删除图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge("感受")
                if (!selectable || selected) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "编辑感受",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除感受",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = note.text, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除感受") },
            text = { Text("确定删除这一天的感受记录吗？") },
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
        val isEditing = state.editingSymptomId != null
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "编辑排便" else "排便记录",
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
                append(if (isEditing) "正在编辑：" else "记录到：").append(dateText)
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
            // 记录时间（补录时可调整）
            SectionLabel("时间")
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(time = draft.time) {
                onDraftChange(draft.copy(time = it))
            }

            Spacer(modifier = Modifier.height(20.dp))
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
            SectionLabel("便便性状（布里斯托分级 1~7）")
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
            Text(if (isEditing) "保存修改" else "保存记录", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
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

/**
 * 食物标签（添加饮食页多选用）：框色即耐受状态（绿=可耐受 红=不耐受 黄=谨慎），
 * 点击切换选中
 */
@Composable
private fun ToleranceTagChip(
    tag: FoodTag,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = toleranceColor(FoodTolerance.fromValue(tag.tolerance))
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .border(1.5.dp, color, RoundedCornerShape(50.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 记录时间选择行（三个面板共用）：显示当前 HH:mm，点击弹窗调整。
 * 补录忘记的记录时，用来把时间改回实际发生时间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(time: String, onSelect: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🕐", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "点击调整",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showDialog) {
        TimePickerDialog(
            initialTime = time,
            onConfirm = { t ->
                showDialog = false
                onSelect(t)
            },
            onDismiss = { showDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.substringBefore(':').toIntOrNull() ?: 12,
        initialMinute = initialTime.substringAfter(':').toIntOrNull() ?: 0,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm("%02d:%02d".format(state.hour, state.minute))
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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

/**
 * 全屏查看照片：
 * - 未放大：左右滑动切换（左上角 序号/总数），双击放大 2 倍
 * - 双指捏合缩放（1x–4x），双指移动微调位置；缩回 1x 自动归位
 * - 放大后：单指拖动平移（不切换图片），双击恢复原大小
 * - 点照片不再关闭，仅右上角 X 关闭
 */
@Composable
private fun FullscreenPhoto(
    photos: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    ) { photos.size }

    // 缩放状态各页共享：放大时单指平移且禁止翻页，未放大时正常左右滑动
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // 手势在 pager 外层统一处理：放大时消费事件（pager 无法翻页），
                // 未放大时不消费（pager 处理左右滑动切换）
                .pointerInput(Unit) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val slop = viewConfiguration.touchSlop
                    var lastTapMs = 0L
                    awaitEachGesture {
                        val first = awaitFirstDown()
                        val downMs = SystemClock.uptimeMillis()
                        var secondId: PointerId? = null
                        var prevDist = 0f
                        var prevMid: Offset? = null
                        var lastPos: Offset? = null
                        var maxDrag = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            // 第二根手指按下 → 进入双指缩放模式
                            if (secondId == null && pressed.size >= 2) {
                                secondId = pressed.first { it.id != first.id }.id
                                val a = pressed.first { it.id == first.id }.position
                                val b = pressed.first { it.id != first.id }.position
                                prevDist = dist(a, b).coerceAtLeast(1f)
                                prevMid = midpoint(a, b)
                            }

                            when {
                                // 双指：缩放 + 移动（消费事件，pager 不响应）
                                secondId != null -> {
                                    val a = pressed.firstOrNull { it.id == first.id }?.position
                                    val b = pressed.firstOrNull { it.id == secondId }?.position
                                    if (a != null && b != null) {
                                        val distNow = dist(a, b).coerceAtLeast(1f)
                                        val mid = midpoint(a, b)
                                        val newZoom = (zoom * (distNow / prevDist)).coerceIn(1f, 4f)
                                        val actual = newZoom / zoom
                                        val pm = prevMid ?: mid
                                        if (newZoom <= 1f) {
                                            // 捏合回最小：归位
                                            zoom = 1f
                                            pan = Offset.Zero
                                        } else {
                                            // 锚定上一帧捏合中心（手指下的点保持不动）
                                            pan = Offset(
                                                pan.x + (mid.x - pm.x) + (1f - actual) * (pm.x - center.x),
                                                pan.y + (mid.y - pm.y) + (1f - actual) * (pm.y - center.y)
                                            )
                                            zoom = newZoom
                                        }
                                        prevDist = distNow
                                        prevMid = mid
                                        event.changes.forEach { it.consume() }
                                    } else {
                                        // 有一指抬起：下一帧按当前缩放状态决定（平移或放行）
                                        secondId = null
                                        prevMid = null
                                        lastPos = null
                                    }
                                }

                                // 放大 + 单指：拖动平移图片（消费事件，禁止翻页）
                                zoom > 1f -> {
                                    val pos = pressed.first().position
                                    maxDrag = maxOf(maxDrag, dist(pos, first.position))
                                    val last = lastPos ?: pos
                                    lastPos = pos
                                    pan = Offset(pan.x + (pos.x - last.x), pan.y + (pos.y - last.y))
                                    event.changes.forEach { it.consume() }
                                }

                                // 未放大：不消费，左右滑动由 pager 处理
                                else -> {
                                    val pos = pressed.first().position
                                    maxDrag = maxOf(maxDrag, dist(pos, first.position))
                                    lastPos = pos
                                }
                            }
                        }

                        // 点击判定：纯单指、位移小于 slop、时间短
                        if (secondId == null &&
                            maxDrag < slop &&
                            SystemClock.uptimeMillis() - downMs < 500L
                        ) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastTapMs < 300L) {
                                // 双击：未放大 → 放大 2 倍；已放大 → 恢复原大小
                                if (zoom > 1f) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                } else {
                                    zoom = 2f
                                }
                                lastTapMs = 0L
                            } else {
                                lastTapMs = now
                            }
                        }
                    }
                }
                .scale(zoom)
                .offset { IntOffset(pan.x.roundToInt(), pan.y.roundToInt()) }
        ) { index ->
            AsyncImage(
                model = File(photos[index]),
                contentDescription = "饮食照片",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }
        // 左上角：序号 / 总数（多张时才显示）
        if (photos.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
        // 右上角：关闭按钮
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

/** 两点距离 */
private fun dist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

/** 两点中点 */
private fun midpoint(a: Offset, b: Offset): Offset = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

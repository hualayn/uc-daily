package com.ucdaily.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.os.SystemClock
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ucdaily.R
import com.ucdaily.data.ActivityLevel
import com.ucdaily.data.BLOOD_LABELS
import com.ucdaily.data.BRISTOL_LABELS
import com.ucdaily.data.DailyNote
import com.ucdaily.data.DailySymptom
import com.ucdaily.data.FoodTag
import com.ucdaily.data.FoodTolerance
import com.ucdaily.data.MealRecord
import com.ucdaily.data.MealType
import com.ucdaily.data.MedRecord
import com.ucdaily.data.PAIN_LOCATION_LABELS
import com.ucdaily.data.activityLevel
import com.ucdaily.data.activityScore
import java.io.File
import java.time.format.DateTimeFormatter
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

/** 面板日期文案：今天 = "今天"，否则按当前语言格式化 "M月d日 E" */
@Composable
private fun panelDateText(date: java.time.LocalDate): String {
    if (date == java.time.LocalDate.now()) return stringResource(R.string.panel_today)
    val context = LocalContext.current
    val fmt = remember(date) {
        DateTimeFormatter.ofPattern(
            context.getString(R.string.date_pattern_md_week),
            context.resources.configuration.locales[0]
        )
    }
    return date.format(fmt)
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
            text = stringResource(R.string.day_records_title),
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
                    text = stringResource(R.string.common_items_count, count),
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
                Text(stringResource(R.string.common_restore))
            }
        }
    }
}

/** 当日记录列表（首页）：今日感受置顶；排便/饮食/服药按时间顺序混排；支持按统计类别筛选 */
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
    /** 点卡片选中（单选），选中后才出现编辑/删除按钮 */
    selectable: Boolean = true
) {
    val entries = state.visibleEntries()
    val note = state.visibleNote()
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
                    text = if (state.dayRecordFilter != null) {
                        stringResource(
                            R.string.day_records_empty_filtered,
                            stringResource(state.dayRecordFilter!!.labelRes)
                        )
                    } else {
                        stringResource(R.string.day_records_empty)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.day_records_empty_hint),
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
    val dateText = panelDateText(state.selectedDate)

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
                text = if (isEditing) stringResource(R.string.panel_edit_meal)
                else stringResource(R.string.panel_add_meal),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isEditing) {
                stringResource(R.string.panel_editing_on, dateText)
            } else {
                buildString {
                    append(stringResource(R.string.panel_record_to)).append(dateText)
                    if (!isToday) append(stringResource(R.string.panel_backfill))
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
            SectionLabel(stringResource(R.string.panel_time))
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(time = state.draft.time, onSelect = onTimeChange)

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_meal_type))
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = state.draft.mealType == type,
                        onClick = { onMealTypeChange(type) },
                        label = { Text(stringResource(type.labelRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_photos))
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
                                contentDescription = stringResource(R.string.panel_pending_photo),
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
                                    contentDescription = stringResource(R.string.panel_remove_photo),
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
                    Text(stringResource(R.string.panel_take_photo))
                }
                OutlinedButton(onClick = onAddPhotoByGallery) {
                    Text(stringResource(R.string.panel_from_album))
                }
            }

            // 添加食物标签（从耐受页移来）
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_add_food_tag))
            Spacer(modifier = Modifier.height(8.dp))
            AddFoodSection(onAddFood = onAddFood)

            if (state.foodTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.panel_food_tags))
                Spacer(modifier = Modifier.height(8.dp))
                // 按类别摆放：可耐受 → 尝试 → 不耐受（稳定排序，组内顺序不变）
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
            SectionLabel(stringResource(R.string.panel_note))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.draft.note,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                placeholder = { Text(stringResource(R.string.panel_meal_note_hint)) },
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
            Text(
                if (isEditing) stringResource(R.string.panel_save_changes)
                else stringResource(R.string.panel_save_record),
                style = MaterialTheme.typography.titleMedium
            )
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
    val dateText = panelDateText(state.selectedDate)
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
                text = if (isEditing) stringResource(R.string.panel_edit_med)
                else stringResource(R.string.panel_add_med),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                append(stringResource(R.string.panel_record_to)).append(dateText)
                if (!isToday) append(stringResource(R.string.panel_backfill))
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
            SectionLabel(stringResource(R.string.panel_time))
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(time = draft.time) {
                onDraftChange(draft.copy(time = it))
            }

            Spacer(modifier = Modifier.height(20.dp))
            if (state.commonMedNames.isNotEmpty()) {
                SectionLabel(stringResource(R.string.panel_common_meds))
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

            SectionLabel(stringResource(R.string.panel_med_name))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.panel_med_name_hint)) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.panel_med_dose))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.dose,
                onValueChange = { onDraftChange(draft.copy(dose = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.panel_med_dose_hint)) },
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
            Text(
                if (isEditing) stringResource(R.string.panel_save_changes)
                else stringResource(R.string.panel_save_record),
                style = MaterialTheme.typography.titleMedium
            )
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
                    contentDescription = stringResource(R.string.common_delete),
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
    val dateText = panelDateText(state.selectedDate)

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
                text = stringResource(R.string.panel_note_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                append(stringResource(R.string.panel_record_to)).append(dateText)
                if (!isToday) append(stringResource(R.string.panel_backfill))
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
            placeholder = { Text(stringResource(R.string.panel_note_hint)) },
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
            Text(stringResource(R.string.panel_save_note), style = MaterialTheme.typography.titleMedium)
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
                TypeBadge(stringResource(R.string.type_med))
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
                            contentDescription = stringResource(R.string.med_edit),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.med_delete),
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
            title = { Text(stringResource(R.string.med_delete)) },
            text = { Text(stringResource(R.string.med_delete_message, med.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(med)
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                TypeBadge(stringResource(R.string.type_note))
                if (!selectable || selected) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.note_edit),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.note_delete),
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
            title = { Text(stringResource(R.string.note_delete)) },
            text = { Text(stringResource(R.string.note_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
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
    val dateText = panelDateText(state.selectedDate)

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
                text = if (isEditing) stringResource(R.string.panel_edit_bowel)
                else stringResource(R.string.panel_bowel_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                append(
                    if (isEditing) stringResource(R.string.panel_editing_label)
                    else stringResource(R.string.panel_record_to)
                ).append(dateText)
                if (!isToday) append(stringResource(R.string.panel_backfill))
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
            SectionLabel(stringResource(R.string.panel_time))
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(time = draft.time) {
                onDraftChange(draft.copy(time = it))
            }

            Spacer(modifier = Modifier.height(20.dp))
            // 排便次数
            SectionLabel(stringResource(R.string.panel_bowel_count))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onDraftChange(draft.copy(bowelCount = (draft.bowelCount - 1).coerceAtLeast(0))) },
                    enabled = draft.bowelCount > 0
                ) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = stringResource(R.string.common_times_count, draft.bowelCount),
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
                label = { Text(stringResource(R.string.symptom_night_diarrhea)) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_bristol_title))
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BRISTOL_LABELS.forEachIndexed { i, res ->
                    val type = i + 1
                    FilterChip(
                        selected = draft.bristolType == type,
                        onClick = {
                            onDraftChange(draft.copy(bristolType = if (draft.bristolType == type) 0 else type))
                        },
                        label = { Text("$type ${stringResource(res)}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_blood))
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BLOOD_LABELS.forEachIndexed { i, res ->
                    FilterChip(
                        selected = draft.blood == i,
                        onClick = { onDraftChange(draft.copy(blood = i)) },
                        label = { Text(stringResource(res)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_other_symptoms))
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = draft.mucus,
                    onClick = { onDraftChange(draft.copy(mucus = !draft.mucus)) },
                    label = { Text(stringResource(R.string.panel_mucus)) }
                )
                FilterChip(
                    selected = draft.urgency,
                    onClick = { onDraftChange(draft.copy(urgency = !draft.urgency)) },
                    label = { Text(stringResource(R.string.panel_urgency)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_pain))
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
                PAIN_LOCATION_LABELS.forEachIndexed { i, res ->
                    FilterChip(
                        selected = draft.painLocation == i,
                        onClick = { onDraftChange(draft.copy(painLocation = i)) },
                        label = {
                            Text(
                                if (i == 0) stringResource(R.string.panel_no_pain)
                                else stringResource(res)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.panel_other_discomfort))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { onDraftChange(draft.copy(note = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                placeholder = { Text(stringResource(R.string.panel_other_discomfort_hint)) },
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
                text = stringResource(R.string.panel_activity_hint),
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
            Text(
                if (isEditing) stringResource(R.string.panel_save_changes)
                else stringResource(R.string.panel_save_record),
                style = MaterialTheme.typography.titleMedium
            )
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
 * 食物标签（添加饮食页多选用）：框色即耐受状态（绿=可耐受 红=不耐受 黄=尝试），
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
            text = stringResource(R.string.panel_tap_to_adjust),
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
        title = { Text(stringResource(R.string.panel_select_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm("%02d:%02d".format(state.hour, state.minute))
            }) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
            text = stringResource(R.string.activity_badge, stringResource(level.labelRes), score),
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
                contentDescription = stringResource(R.string.record_meal_photo),
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
                contentDescription = stringResource(R.string.common_close),
                tint = Color.White
            )
        }
    }
}

/** 两点距离 */
private fun dist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

/** 两点中点 */
private fun midpoint(a: Offset, b: Offset): Offset = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

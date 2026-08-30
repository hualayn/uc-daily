package com.ucdaily.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ucdaily.MedReminder
import com.ucdaily.R
import com.ucdaily.data.ActivityLevel
import com.ucdaily.data.BLOOD_LABELS
import com.ucdaily.data.BRISTOL_LABELS
import com.ucdaily.data.DailySymptom
import com.ucdaily.data.FoodTolerance
import com.ucdaily.data.MealRecord
import com.ucdaily.data.PAIN_LOCATION_LABELS
import com.ucdaily.data.activityLevel
import com.ucdaily.data.activityScore
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlinx.coroutines.launch

/**
 * 导出记录对话框：
 * - 开始/结束日期，默认当前一周（周一至周日），点日期打开日期选择器
 * - 记录类型：饮食 / 服药 / 便便 / 感受（默认全选）
 * - 输出方式：剪切板 或 文件；文件支持 txt / csv（选中 csv 时下方提示可用该文件恢复记录）
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
    // 系统"创建文档"选择界面 = 临时外部页面：结果返回（选定 / 取消都会回调）时复位标记，
    // 否则 Activity 停止期间会被当成退后台而触发未服药通知（与相机 / 相册同理）
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        MedReminder.setTransientExternalOpen(false)
        val pending = pendingExport
        pendingExport = null
        if (pending == null || uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(pending.text.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, context.getString(R.string.export_saved, pending.fileName), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.export_save_failed, e.message), Toast.LENGTH_SHORT).show()
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
                            text = stringResource(type.labelRes),
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
                        text = stringResource(R.string.export_clipboard),
                        selected = toClipboard,
                        modifier = Modifier.weight(1f),
                        onClick = { toClipboard = true }
                    )
                    ExportSelectButton(
                        text = stringResource(R.string.export_file),
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
                    // 选中 csv 时：提示可用该文件恢复记录（说明文字自"我的"页移来）
                    if (format == ExportFormat.CSV) {
                        Text(
                            text = stringResource(R.string.export_csv_restore_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                Toast.makeText(context, R.string.export_no_records, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            if (toClipboard) {
                                clipboard.setText(AnnotatedString(result.text))
                                Toast.makeText(context, R.string.export_copied, Toast.LENGTH_SHORT).show()
                            } else {
                                // 文件输出：走系统"创建文档"流程，用户选定位置后写入
                                // 系统保存界面 = 临时外部页面：先标记，onStop 才不会误判为退后台
                                MedReminder.setTransientExternalOpen(true)
                                pendingExport = result
                                createFile.launch(result.fileName)
                            }
                        }
                    }
                ) {
                    Text(
                        if (types.isEmpty()) stringResource(R.string.export_select_type)
                        else stringResource(R.string.export_button)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!exporting) onDismiss() }) { Text(stringResource(R.string.common_cancel)) }
        }
    )

    // 日期选择（标题分别显示"开始日期"/"结束日期"）
    if (showStartPicker) {
        ExportDatePickerDialog(
            title = stringResource(R.string.export_start_date),
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
            title = stringResource(R.string.export_end_date),
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
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
                // 顶部显示当前选中的日期（本地化格式），左侧留 24dp 距离
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val d = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
                    Text(
                        text = stringResource(R.string.export_date_headline, d.year, d.monthValue, d.dayOfMonth),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.export_select_date),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                    )
                }
            }
        )
    }
}

/** 周几文案资源 id（周一~周日），首页与日历页共用 */
fun weekLabelRes(date: LocalDate): Int = when (date.dayOfWeek.value) {
    1 -> R.string.week_mon
    2 -> R.string.week_tue
    3 -> R.string.week_wed
    4 -> R.string.week_thu
    5 -> R.string.week_fri
    6 -> R.string.week_sat
    else -> R.string.week_sun
}

/** ISO 周序号（1~53），首页与日历页共用 */
fun isoWeek(date: LocalDate): Int =
    date.get(WeekFields.ISO.weekOfWeekBasedYear())

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

/** 排便记录摘要文案（按当前语言本地化） */
@Composable
private fun symptomSummary(s: DailySymptom): String {
    val parts = mutableListOf(stringResource(R.string.summary_bowel, s.bowelCount))
    if (s.nightDiarrhea) parts.add(stringResource(R.string.symptom_night_diarrhea))
    // 布里斯托分级直接显示描述（不显示数字编号）
    if (s.bristolType in 1..7) parts.add(stringResource(BRISTOL_LABELS[s.bristolType - 1]))
    if (s.blood in 1..3) parts.add(stringResource(R.string.summary_blood, stringResource(BLOOD_LABELS[s.blood])))
    if (s.mucus) parts.add(stringResource(R.string.symptom_mucus))
    if (s.painScore > 0) {
        val loc = if (s.painLocation in 1..4) "·${stringResource(PAIN_LOCATION_LABELS[s.painLocation])}" else ""
        parts.add(stringResource(R.string.summary_pain, s.painScore, loc))
    }
    if (s.urgency) parts.add(stringResource(R.string.symptom_urgency))
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
    /** 食物名 -> 耐受状态（用于标签着色，绿=可耐受 红=不耐受 黄=尝试） */
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
                TypeBadge(stringResource(record.mealType.labelRes))
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
                            contentDescription = stringResource(R.string.record_edit),
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
                            contentDescription = stringResource(R.string.record_delete),
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

            // 食物标签：按耐受列表中的状态着色（绿=可耐受 红=不耐受 黄=尝试，无记录则中性色）
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
                            contentDescription = stringResource(R.string.record_meal_photo),
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
            title = { Text(stringResource(R.string.record_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.record_delete_meal_message,
                        stringResource(record.mealType.labelRes)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(record)
                    }
                ) {
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
                TypeBadge(stringResource(R.string.type_bowel))
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
                        text = stringResource(
                            R.string.activity_badge,
                            stringResource(symptom.activityLevel.labelRes),
                            activityScore(symptom)
                        ),
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
                            contentDescription = stringResource(R.string.symptom_edit),
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
                            contentDescription = stringResource(R.string.symptom_delete),
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
            title = { Text(stringResource(R.string.symptom_delete)) },
            text = { Text(stringResource(R.string.symptom_delete_message)) },
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

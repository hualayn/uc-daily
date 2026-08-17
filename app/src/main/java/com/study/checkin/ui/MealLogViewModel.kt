package com.study.checkin.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.study.checkin.data.ActivityLevel
import com.study.checkin.data.AppDatabase
import com.study.checkin.data.DailySymptom
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MealType
import com.study.checkin.data.activityLevel
import com.study.checkin.data.activityScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 日历中每一天 */
data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val hasRecord: Boolean,
    /** 当日排便记录的活动度（null = 无排便记录），驱动日历热力圆点 */
    val activity: ActivityLevel? = null
)

/** 一个月的日历视图 */
data class MonthView(
    val yearMonth: YearMonth,
    val days: List<CalendarDay>
)

/** 添加记录时的草稿 */
data class DraftRecord(
    val mealType: MealType,
    val note: String = "",
    val photos: List<String> = emptyList()
)

/** 排便/症状记录草稿（字段与 DailySymptom 对应，不含 id/date/createdAt） */
data class SymptomDraft(
    val bowelCount: Int = 1,
    val nightDiarrhea: Boolean = false,
    val bristolType: Int = 0,
    val blood: Int = 0,
    val mucus: Boolean = false,
    val painScore: Int = 0,
    val painLocation: Int = 0,
    val urgency: Boolean = false,
    val note: String = ""
) {
    companion object {
        fun from(record: DailySymptom?): SymptomDraft = record?.let {
            SymptomDraft(
                bowelCount = it.bowelCount,
                nightDiarrhea = it.nightDiarrhea,
                bristolType = it.bristolType,
                blood = it.blood,
                mucus = it.mucus,
                painScore = it.painScore,
                painLocation = it.painLocation,
                urgency = it.urgency,
                note = it.note
            )
        } ?: SymptomDraft()
    }
}

/** 依据草稿实时计算参考活动度评分（面板内即时预览） */
fun symptomDraftScore(d: SymptomDraft): Int =
    activityScore(DailySymptom(date = "", bowelCount = d.bowelCount, blood = d.blood))

data class MealUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val currentMonth: YearMonth = YearMonth.now(),
    val monthView: MonthView? = null,
    /** 有记录的日期集合（yyyy-MM-dd），驱动日历小圆点 */
    val recordDates: Set<String> = emptySet(),
    val selectedDateRecords: List<MealRecord> = emptyList(),
    val totalRecordDays: Int = 0,
    val totalRecords: Int = 0,
    /** 每日排便/症状记录（date -> 记录），驱动日历热力圆点与当日症状卡片 */
    val symptomByDate: Map<String, DailySymptom> = emptyMap(),
    /** 排便记录面板是否打开 */
    val isSymptomPanelOpen: Boolean = false,
    val symptomDraft: SymptomDraft = SymptomDraft(),
    /** 面板是否打开（添加或编辑共用同一个面板） */
    val isAdding: Boolean = false,
    /** 非 null 表示正在编辑该记录（复用添加面板） */
    val editingRecordId: Int? = null,
    val draft: DraftRecord = DraftRecord(MealType.fromTime(LocalTime.now()))
)

class MealLogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val dao = AppDatabase.getDatabase(app).mealRecordDao()
    private val symptomDao = AppDatabase.getDatabase(app).dailySymptomDao()

    private val _uiState = MutableStateFlow(MealUiState())
    val uiState: StateFlow<MealUiState> = _uiState

    /** 相机拍照的待写入文件路径（拍摄成功后并入草稿） */
    private var pendingCameraPath: String? = null

    init {
        loadState()
    }

    // region 数据加载

    private fun loadState() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = false,
                recordDates = dao.getRecordDates().toSet(),
                totalRecordDays = dao.getRecordDays(),
                totalRecords = dao.getTotalCount(),
                symptomByDate = symptomDao.getAll().associateBy { it.date },
                selectedDateRecords = dao.getRecordsByDate(_uiState.value.selectedDate.toString())
            )
            refreshMonth(_uiState.value.currentMonth)
        }
    }

    /** 重新加载排便记录并重建月份视图（保存/删除症状后调用） */
    private fun refreshSymptoms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                symptomByDate = symptomDao.getAll().associateBy { it.date }
            )
            refreshMonth(_uiState.value.currentMonth)
        }
    }

    /** 同步重建月份视图（数据已全部在内存中） */
    private fun refreshMonth(yearMonth: YearMonth) {
        val state = _uiState.value
        _uiState.value = state.copy(
            monthView = buildMonthView(yearMonth, state.recordDates, state.symptomByDate)
        )
    }

    private fun buildMonthView(
        yearMonth: YearMonth,
        recordDates: Set<String>,
        symptomByDate: Map<String, DailySymptom>
    ): MonthView {
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()

        // 从本月第一个周一开始补齐到完整的 6 行（42 天）
        val startOfWeek = firstDay.with(DayOfWeek.MONDAY)
        val days = (0 until 42).map { offset ->
            val date = startOfWeek.plusDays(offset.toLong())
            CalendarDay(
                date = date,
                inCurrentMonth = date >= firstDay && date <= lastDay,
                hasRecord = recordDates.contains(date.toString()),
                activity = symptomByDate[date.toString()]?.activityLevel
            )
        }
        return MonthView(yearMonth, days)
    }

    // endregion

    // region 日历导航

    fun selectDate(date: LocalDate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedDate = date,
                selectedDateRecords = dao.getRecordsByDate(date.toString())
            )
        }
    }

    /** 上一个月 */
    fun prevMonth() {
        val prev = _uiState.value.currentMonth.minusMonths(1)
        _uiState.value = _uiState.value.copy(currentMonth = prev)
        refreshMonth(prev)
    }

    /** 下一个月 */
    fun nextMonth() {
        val next = _uiState.value.currentMonth.plusMonths(1)
        _uiState.value = _uiState.value.copy(currentMonth = next)
        refreshMonth(next)
    }

    /** 快速跳转到指定年月（点击月份标题选择），行为与滑动翻页一致，选中日期保持不变 */
    fun jumpToMonth(yearMonth: YearMonth) {
        _uiState.value = _uiState.value.copy(currentMonth = yearMonth)
        refreshMonth(yearMonth)
    }

    // endregion

    // region 添加记录（草稿）

    fun startAdd() {
        _uiState.value = _uiState.value.copy(
            isAdding = true,
            isSymptomPanelOpen = false,
            editingRecordId = null,
            draft = DraftRecord(mealType = MealType.fromTime(LocalTime.now()))
        )
    }

    /** 进入编辑模式：把记录载入草稿，复用添加面板 */
    fun startEdit(record: MealRecord) {
        _uiState.value = _uiState.value.copy(
            isAdding = true,
            editingRecordId = record.id,
            draft = DraftRecord(
                mealType = record.mealType,
                note = record.note,
                photos = record.photos
            )
        )
    }

    fun cancelAdd() {
        pendingCameraPath = null
        _uiState.value = _uiState.value.copy(
            isAdding = false,
            editingRecordId = null,
            draft = DraftRecord(mealType = MealType.fromTime(LocalTime.now()))
        )
    }

    fun setDraftMealType(type: MealType) {
        val s = _uiState.value
        _uiState.value = s.copy(draft = s.draft.copy(mealType = type))
    }

    fun setDraftNote(note: String) {
        val s = _uiState.value
        _uiState.value = s.copy(draft = s.draft.copy(note = note))
    }

    fun removeDraftPhoto(index: Int) {
        // 只从草稿移除，不动磁盘文件：若之后取消编辑，原记录仍引用该照片，
        // 不能提前删除（与删除记录"照片不会从设备中删除"的约定保持一致）
        val s = _uiState.value
        _uiState.value = s.copy(
            draft = s.draft.copy(photos = s.draft.photos.filterIndexed { i, _ -> i != index })
        )
    }

    /** 创建相机写入文件并返回 FileProvider Uri，供 Activity 启动相机 */
    fun prepareCameraFile(): Uri? {
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val ts = System.currentTimeMillis()
        val file = File(dir, "meal_$ts.jpg")
        return try {
            pendingCameraPath = file.absolutePath
            FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
        } catch (e: IllegalArgumentException) {
            pendingCameraPath = null
            null
        }
    }

    /** 相机拍摄成功，将照片并入草稿 */
    fun onCameraPhotoTaken() {
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path != null && File(path).exists()) {
            appendDraftPhoto(path)
        }
    }

    /** 相机取消或失败 */
    fun onCameraCancelled() {
        pendingCameraPath = null
    }

    /** 从相册选取的多张照片：逐张复制到应用私有目录后并入草稿 */
    fun addGalleryPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val savedPaths = withContext(Dispatchers.IO) {
                val dir = app.getExternalFilesDir(null) ?: app.filesDir
                // 同一毫秒内多张照片用下标错开文件名，避免互相覆盖
                uris.mapIndexed { index, uri ->
                    try {
                        val ts = System.currentTimeMillis() + index
                        val ext = queryExtension(uri)
                        val file = File(dir, "meal_$ts.$ext")
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (file.length() > 0) file.absolutePath else null
                    } catch (e: Exception) {
                        null
                    }
                }.filterNotNull()
            }
            if (savedPaths.isNotEmpty()) {
                val s = _uiState.value
                _uiState.value = s.copy(
                    draft = s.draft.copy(photos = s.draft.photos + savedPaths)
                )
            }
        }
    }

    private fun queryExtension(uri: Uri): String {
        val name = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
        return ext ?: "jpg"
    }

    private fun appendDraftPhoto(path: String) {
        val s = _uiState.value
        _uiState.value = s.copy(draft = s.draft.copy(photos = s.draft.photos + path))
    }

    /** 保存当前草稿：编辑模式下更新原记录（日期/时间保持不变），否则按选中日期新建 */
    fun saveRecord() {
        viewModelScope.launch {
            val s = _uiState.value
            val editingId = s.editingRecordId
            if (editingId != null) {
                // 编辑：只更新餐次/照片/备注，日期与时间沿用原记录
                dao.getRecordById(editingId)?.let { existing ->
                    dao.update(
                        existing.copy(
                            mealType = s.draft.mealType,
                            photos = s.draft.photos,
                            note = s.draft.note.trim()
                        )
                    )
                }
            } else {
                val date = s.selectedDate
                val time = if (date == LocalDate.now()) {
                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                } else {
                    "12:00" // 补录历史日期时，时间取中午
                }
                dao.insert(
                    MealRecord(
                        date = date.toString(),
                        time = time,
                        mealType = s.draft.mealType,
                        photos = s.draft.photos,
                        note = s.draft.note.trim()
                    )
                )
            }
            pendingCameraPath = null
            _uiState.value = s.copy(
                isAdding = false,
                editingRecordId = null,
                draft = DraftRecord(mealType = MealType.fromTime(LocalTime.now())),
                recordDates = dao.getRecordDates().toSet(),
                totalRecordDays = dao.getRecordDays(),
                totalRecords = dao.getTotalCount(),
                selectedDateRecords = dao.getRecordsByDate(s.selectedDate.toString())
            )
            refreshMonth(s.currentMonth)
        }
    }

    // endregion

    // region 删除记录

    fun deleteRecord(id: Int) {
        viewModelScope.launch {
            dao.deleteById(id)
            val s = _uiState.value
            _uiState.value = s.copy(
                recordDates = dao.getRecordDates().toSet(),
                totalRecordDays = dao.getRecordDays(),
                totalRecords = dao.getTotalCount(),
                selectedDateRecords = dao.getRecordsByDate(s.selectedDate.toString())
            )
            refreshMonth(s.currentMonth)
        }
    }

    // endregion

    // region 排便/症状记录

    /** 打开排便记录面板（针对当前选中日期，已有记录则载入草稿），并关闭其他面板 */
    fun startSymptomPanel() {
        val s = _uiState.value
        _uiState.value = s.copy(
            isSymptomPanelOpen = true,
            isAdding = false,
            editingRecordId = null,
            symptomDraft = SymptomDraft.from(s.symptomByDate[s.selectedDate.toString()])
        )
    }

    fun cancelSymptomPanel() {
        _uiState.value = _uiState.value.copy(
            isSymptomPanelOpen = false,
            symptomDraft = SymptomDraft()
        )
    }

    fun setSymptomDraft(draft: SymptomDraft) {
        _uiState.value = _uiState.value.copy(symptomDraft = draft)
    }

    /** 保存（当天重复保存则覆盖）当前选中日期的排便记录 */
    fun saveSymptom() {
        viewModelScope.launch {
            val s = _uiState.value
            val date = s.selectedDate
            val d = s.symptomDraft
            val existing = s.symptomByDate[date.toString()]
            val toSave = DailySymptom(
                id = existing?.id ?: 0,
                date = date.toString(),
                bowelCount = d.bowelCount,
                nightDiarrhea = d.nightDiarrhea,
                bristolType = d.bristolType,
                blood = d.blood,
                mucus = d.mucus,
                painScore = d.painScore,
                painLocation = d.painLocation,
                urgency = d.urgency,
                note = d.note.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            symptomDao.upsert(toSave)
            _uiState.value = _uiState.value.copy(
                isSymptomPanelOpen = false,
                symptomDraft = SymptomDraft()
            )
            refreshSymptoms()
        }
    }

    /** 删除当前选中日期的排便记录 */
    fun deleteSymptom() {
        viewModelScope.launch {
            val s = _uiState.value
            s.symptomByDate[s.selectedDate.toString()]?.let { symptomDao.deleteById(it.id) }
            refreshSymptoms()
        }
    }

    // endregion
}

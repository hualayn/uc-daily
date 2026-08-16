package com.study.checkin.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.study.checkin.data.AppDatabase
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MealType
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
    val hasRecord: Boolean
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
    val isAdding: Boolean = false,
    val draft: DraftRecord = DraftRecord(MealType.fromTime(LocalTime.now()))
)

class MealLogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val dao = AppDatabase.getDatabase(app).mealRecordDao()

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
                selectedDateRecords = dao.getRecordsByDate(_uiState.value.selectedDate.toString())
            )
            refreshMonth(_uiState.value.currentMonth)
        }
    }

    /** 同步重建月份视图（数据已全部在内存中） */
    private fun refreshMonth(yearMonth: YearMonth) {
        val state = _uiState.value
        _uiState.value = state.copy(
            monthView = buildMonthView(yearMonth, state.recordDates)
        )
    }

    private fun buildMonthView(yearMonth: YearMonth, recordDates: Set<String>): MonthView {
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()

        // 从本月第一个周一开始补齐到完整的 6 行（42 天）
        val startOfWeek = firstDay.with(DayOfWeek.MONDAY)
        val days = (0 until 42).map { offset ->
            val date = startOfWeek.plusDays(offset.toLong())
            CalendarDay(
                date = date,
                inCurrentMonth = date >= firstDay && date <= lastDay,
                hasRecord = recordDates.contains(date.toString())
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

    // endregion

    // region 添加记录（草稿）

    fun startAdd() {
        _uiState.value = _uiState.value.copy(
            isAdding = true,
            draft = DraftRecord(mealType = MealType.fromTime(LocalTime.now()))
        )
    }

    fun cancelAdd() {
        pendingCameraPath = null
        _uiState.value = _uiState.value.copy(
            isAdding = false,
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

    /** 从相册选取的照片：复制到应用私有目录后并入草稿 */
    fun addGalleryPhoto(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                try {
                    val dir = app.getExternalFilesDir(null) ?: app.filesDir
                    val ts = System.currentTimeMillis()
                    val ext = queryExtension(uri)
                    val file = File(dir, "meal_$ts.$ext")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (file.length() > 0) file.absolutePath else null
                } catch (e: Exception) {
                    null
                }
            }
            if (savedPath != null) appendDraftPhoto(savedPath)
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

    /** 保存当前草稿为一条记录（日期取当前选中日期） */
    fun saveRecord() {
        viewModelScope.launch {
            val s = _uiState.value
            val date = s.selectedDate
            val time = if (date == LocalDate.now()) {
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            } else {
                "12:00" // 补录历史日期时，时间取中午
            }
            val record = MealRecord(
                date = date.toString(),
                time = time,
                mealType = s.draft.mealType,
                photos = s.draft.photos,
                note = s.draft.note.trim()
            )
            dao.insert(record)
            pendingCameraPath = null
            _uiState.value = s.copy(
                isAdding = false,
                draft = DraftRecord(mealType = MealType.fromTime(LocalTime.now())),
                recordDates = dao.getRecordDates().toSet(),
                totalRecordDays = dao.getRecordDays(),
                totalRecords = dao.getTotalCount(),
                selectedDateRecords = dao.getRecordsByDate(date.toString())
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
}

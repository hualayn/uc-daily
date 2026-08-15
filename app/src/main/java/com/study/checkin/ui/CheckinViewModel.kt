package com.study.checkin.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.study.checkin.data.AppDatabase
import com.study.checkin.data.CheckinEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar
import java.util.Locale

/** 日历中每一天 */
data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val checked: Boolean,
    val photoPath: String
)

/** 一个月的日历视图 */
data class MonthView(
    val yearMonth: YearMonth,
    val days: List<CalendarDay>
)

data class CheckinUiState(
    val totalDays: Int = 0,
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateChecked: Boolean = false,
    val selectedDatePhoto: String = "",
    val currentMonth: YearMonth = YearMonth.now(),
    val monthView: MonthView? = null,
    val checkedDates: Set<String> = emptySet()
)

class CheckinViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val dao = AppDatabase.getDatabase(app).checkinDao()

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState

    private var _photoFilePath: String? = null
    var currentPhotoUri: Uri? = null

    /** date -> photoPath 内存缓存，打卡后更新，切换月份不再查库 */
    private val checkedPhotos = mutableMapOf<String, String>()

    init {
        loadState()
    }

    fun getPhotoUri(): Uri {
        val storageDir = app.getExternalFilesDir(null) ?: app.filesDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Calendar.getInstance().time)
        val imageFile = File(storageDir, "checkin_${timestamp}.jpg")
        _photoFilePath = imageFile.absolutePath
        currentPhotoUri = FileProvider.getUriForFile(
            app,
            app.packageName + ".fileprovider",
            imageFile
        )
        return currentPhotoUri!!
    }

    private fun loadState() {
        viewModelScope.launch {
            // 一次性拉取全部打卡记录（id + 日期 + 照片路径）
            val records = dao.getRecords(100000)
            val checkedDates = records.map { it.date }.toSet()
            checkedPhotos.clear()
            records.forEach { checkedPhotos[it.date] = it.photoPath }
            _uiState.value = _uiState.value.copy(
                totalDays = dao.getTotalCheckinCount(),
                loading = false,
                checkedDates = checkedDates
            )
            refreshMonth(_uiState.value.currentMonth)
        }
    }

    /** 同步重建月份视图（数据已全部在内存中） */
    private fun refreshMonth(yearMonth: YearMonth) {
        val state = _uiState.value
        _uiState.value = state.copy(
            monthView = buildMonthView(yearMonth, state.checkedDates, checkedPhotos),
            selectedDateChecked = state.checkedDates.contains(state.selectedDate.toString()),
            selectedDatePhoto = checkedPhotos[state.selectedDate.toString()] ?: ""
        )
    }

    private fun buildMonthView(
        yearMonth: YearMonth,
        checkedDates: Set<String>,
        photoByDate: Map<String, String>
    ): MonthView {
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()

        // 从本月第一个周一开始补齐到完整的 6 行（42 天）
        val startOfWeek = firstDay.with(DayOfWeek.MONDAY)
        val days = (0 until 42).map { offset ->
            val date = startOfWeek.plusDays(offset.toLong())
            val dateStr = date.toString()
            CalendarDay(
                date = date,
                inCurrentMonth = date >= firstDay && date <= lastDay,
                checked = checkedDates.contains(dateStr),
                photoPath = photoByDate[dateStr] ?: ""
            )
        }
        return MonthView(yearMonth, days)
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

    /** 回到今天所在月份 */
    fun goToToday() {
        val todayMonth = YearMonth.now()
        _uiState.value = _uiState.value.copy(currentMonth = todayMonth)
        refreshMonth(todayMonth)
    }

    fun doTodayCheckin(photoUri: Uri?) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val photoPath = _photoFilePath ?: ""
            val record = CheckinEntity(date = today, photoPath = photoPath)
            dao.insert(record)
            _photoFilePath = null
            currentPhotoUri = null
            checkedPhotos[today] = photoPath

            val checkedDates = dao.getRecords(100000).map { it.date }.toSet()
            _uiState.value = _uiState.value.copy(
                totalDays = dao.getTotalCheckinCount(),
                checkedDates = checkedDates,
                selectedDateChecked = true,
                selectedDatePhoto = photoPath
            )
            refreshMonth(_uiState.value.currentMonth)
        }
    }

    fun selectDate(date: LocalDate) {
        val dateStr = date.toString()
        viewModelScope.launch {
            val checked = _uiState.value.checkedDates.contains(dateStr)
            _uiState.value = _uiState.value.copy(
                selectedDate = date,
                selectedDateChecked = checked,
                selectedDatePhoto = checkedPhotos[dateStr] ?: ""
            )
        }
    }
}

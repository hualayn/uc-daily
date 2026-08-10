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
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

data class CheckinUiState(
    val todayChecked: Boolean = false,
    val totalDays: Int = 0,
    val recentRecords: List<String> = emptyList(),
    val recentPhotos: List<String> = emptyList(),
    val todayPhoto: String = "",
    val loading: Boolean = true,
    val today: String = LocalDate.now().toString(),
    val selectedDate: String = LocalDate.now().toString(),
    val selectedDateChecked: Boolean = false,
    val selectedDatePhoto: String = "",
    val allDates: List<String> = (0..30).map { LocalDate.now().minusDays(it.toLong()).toString() }
)

class CheckinViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val dao = AppDatabase.getDatabase(app).checkinDao()

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState

    private var _photoFilePath: String? = null
    var currentPhotoUri: Uri? = null

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
            val today = LocalDate.now().toString()
            val todayChecked = dao.isCheckinToday(today)
            val total = dao.getTotalCheckinCount()
            val records = dao.getRecords(10)
            val todayRecord = dao.getRecordByDate(today)

            _uiState.value = CheckinUiState(
                todayChecked = todayChecked,
                totalDays = total,
                recentRecords = records.map { it.date },
                recentPhotos = records.map { it.photoPath },
                todayPhoto = todayRecord?.photoPath ?: "",
                loading = false,
                today = today
            )
        }
    }

    fun doTodayCheckin(photoUri: Uri?) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            // 使用实际文件路径而不是 Uri 字符串
            val photoPath = _photoFilePath ?: ""
            val record = CheckinEntity(date = today, photoPath = photoPath)
            dao.insert(record)
            _photoFilePath = null
            currentPhotoUri = null
            loadState()
            // 如果选中日期是今天，同步更新选中状态
            if (_uiState.value.selectedDate == today) {
                _uiState.value = _uiState.value.copy(
                    selectedDateChecked = true,
                    selectedDatePhoto = photoPath
                )
            }
        }
    }

    fun selectDate(date: String) {
        viewModelScope.launch {
            val checked = dao.isCheckinToday(date)
            val record = dao.getRecordByDate(date)
            _uiState.value = _uiState.value.copy(
                selectedDate = date,
                selectedDateChecked = checked,
                selectedDatePhoto = record?.photoPath ?: ""
            )
        }
    }
}

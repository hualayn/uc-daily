package com.study.checkin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.study.checkin.data.AppDatabase
import com.study.checkin.data.CheckinEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CheckinUiState(
    val todayChecked: Boolean = false,
    val totalDays: Int = 0,
    val recentRecords: List<String> = emptyList(),
    val loading: Boolean = true,
    val today: String = LocalDate.now().toString()
)

class CheckinViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).checkinDao()

    private val _uiState = MutableStateFlow(CheckinUiState())
    val uiState: StateFlow<CheckinUiState> = _uiState

    init {
        loadState()
    }

    private fun loadState() {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val todayChecked = dao.isCheckinToday(today)
            val total = dao.getTotalCheckinCount()
            val records = dao.getRecords(10).map { it.date }

            _uiState.value = CheckinUiState(
                todayChecked = todayChecked,
                totalDays = total,
                recentRecords = records,
                loading = false,
                today = today
            )
        }
    }

    fun doTodayCheckin() {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val record = CheckinEntity(date = today)
            dao.insert(record)
            loadState()
        }
    }
}

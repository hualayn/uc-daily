package com.study.checkin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.study.checkin.ui.CheckinScreen
import com.study.checkin.ui.CheckinViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CheckinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()
                    CheckinScreen(state, onCheckin = { viewModel.doTodayCheckin() })
                }
            }
        }
    }
}

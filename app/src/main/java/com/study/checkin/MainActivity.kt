package com.study.checkin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.study.checkin.ui.CheckinUiState
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

@Composable
fun CheckinScreen(
    state: CheckinUiState,
    onCheckin: () -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "学习打卡",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "今天：${state.today}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCheckin,
            enabled = !state.todayChecked,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                text = if (state.todayChecked) "今日已打卡 ✓" else "打卡",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "累计打卡 ${state.totalDays} 天",
            style = MaterialTheme.typography.titleMedium
        )

        if (state.recentRecords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "最近记录",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            state.recentRecords.forEach { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

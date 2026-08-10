package com.study.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CheckinScreen(
    state: CheckinUiState,
    onCheckin: () -> Unit,
    onCameraClick: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now().toString()
    val selectedDate = state.selectedDate
    val isToday = selectedDate == today

    // 自动滚动到选中日期
    val listState = rememberLazyListState()
    val selectedIndex = state.allDates.indexOf(selectedDate)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.scrollToItem(maxOf(0, selectedIndex - 2))
        }
    }

    if (state.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "学习打卡",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 日历日期选择器
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(state.allDates) { date ->
                val localDate = LocalDate.parse(date, formatter)
                val dayOfWeek = weekDays[localDate.dayOfWeek.value % 7]
                val day = localDate.dayOfMonth
                val isSelected = date == selectedDate
                val isPast = localDate.isBefore(LocalDate.now())

                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .clickable { onDateSelected(date) }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else if (state.selectedDate == date) MaterialTheme.colorScheme.primaryContainer
                            else if (state.allDates.indexOf(date) < state.allDates.size - 1) Color.Transparent
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = day.toString(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else if (isToday) MaterialTheme.colorScheme.primary
                            else if (isPast) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 选中日期显示
        Text(
            text = if (isToday) "今天" else "${selectedDate}",
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 选中日期的照片预览
        if (state.selectedDatePhoto.isNotEmpty()) {
            val file = File(state.selectedDatePhoto)
            if (file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = "打卡照片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 打卡按钮（仅今天可用）
        Button(
            onClick = onCheckin,
            enabled = isToday && !state.selectedDateChecked,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                text = when {
                    !isToday -> "${if (state.selectedDateChecked) "已打卡 ✓" else "未打卡"}"
                    state.selectedDateChecked -> "今日已打卡 ✓"
                    else -> "打卡"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (isToday) {
            Spacer(modifier = Modifier.height(12.dp))
            // 拍照按钮（仅今天可用）
            OutlinedButton(
                onClick = onCameraClick,
                enabled = !state.selectedDateChecked,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("📸 拍照")
            }
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
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(state.recentRecords) { i, date ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (i < state.recentPhotos.size && state.recentPhotos[i].isNotEmpty()) {
                            val photoFile = File(state.recentPhotos[i])
                            if (photoFile.exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(photoFile)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = "打卡照片",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

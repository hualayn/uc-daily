package com.study.checkin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.imageLoader
import coil.decode.BitmapDecoder
import coil.request.ImageRequest
import java.io.File

@Composable
fun CheckinScreen(
    state: CheckinUiState,
    onCheckin: () -> Unit,
    onCameraClick: () -> Unit
) {
    val context = LocalContext.current

    // Setup Coil for file:// URI support
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(BitmapDecoder.Factory())
            }
            .build()
    }
    CoilImageLoader(imageLoader)

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

        // 今日照片预览
        if (state.todayPhoto.isNotEmpty()) {
            val file = File(state.todayPhoto)
            if (file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = "今日打卡照片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    imageLoader = imageLoader
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

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

        Spacer(modifier = Modifier.height(12.dp))

        // 拍照按钮
        OutlinedButton(
            onClick = onCameraClick,
            enabled = !state.todayChecked,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("📸 拍照")
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
                                    contentScale = ContentScale.Crop,
                                    imageLoader = imageLoader
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

@Composable
private fun CoilImageLoader(loader: ImageLoader) {
    val context = LocalContext.current
    LaunchedEffect(loader) {
        context.imageLoader = loader
    }
}

package com.study.checkin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.study.checkin.ui.CheckinScreen
import com.study.checkin.ui.CheckinViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CheckinViewModel by viewModels()

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val photoUri = viewModel.currentPhotoUri
            if (photoUri != null) {
                viewModel.doTodayCheckin(photoUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()
                    CheckinScreen(
                        state = state,
                        onCheckin = { viewModel.doTodayCheckin(null) },
                        onCameraClick = {
                            val uri = viewModel.getPhotoUri()
                            try {
                                cameraLauncher.launch(uri)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(
                                    this,
                                    "没有找到相机应用，请检查模拟器相机设置",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: IllegalArgumentException) {
                                Toast.makeText(
                                    this,
                                    "无法创建相机文件: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

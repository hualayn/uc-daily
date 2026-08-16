package com.study.checkin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.study.checkin.ui.MealLogScreen
import com.study.checkin.ui.MealLogViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MealLogViewModel by viewModels()

    /** 相机拍照 */
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.onCameraPhotoTaken()
        } else {
            viewModel.onCameraCancelled()
        }
    }

    /** 相册选图（系统 PhotoPicker，无需额外权限） */
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addGalleryPhoto(uri)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        val uri = viewModel.prepareCameraFile()
        if (uri == null) {
            Toast.makeText(this, "无法创建相机文件", Toast.LENGTH_LONG).show()
            return
        }
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

    private fun onCameraClick() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                    MealLogScreen(
                        state = state,
                        onDateSelected = { date -> viewModel.selectDate(date) },
                        onPrevMonth = { viewModel.prevMonth() },
                        onNextMonth = { viewModel.nextMonth() },
                        onStartAdd = { viewModel.startAdd() },
                        onAddPhotoByCamera = { onCameraClick() },
                        onAddPhotoByGallery = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onRemoveDraftPhoto = { index -> viewModel.removeDraftPhoto(index) },
                        onDraftMealTypeChange = { type -> viewModel.setDraftMealType(type) },
                        onDraftNoteChange = { note -> viewModel.setDraftNote(note) },
                        onSaveRecord = { viewModel.saveRecord() },
                        onCancelAdd = { viewModel.cancelAdd() },
                        onDeleteRecord = { record -> viewModel.deleteRecord(record.id) }
                    )
                }
            }
        }
    }
}

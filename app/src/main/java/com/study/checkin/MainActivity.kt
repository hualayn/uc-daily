package com.study.checkin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.study.checkin.ui.CalendarScreen
import com.study.checkin.ui.HomeScreen
import com.study.checkin.ui.MedSettingsScreen
import com.study.checkin.ui.MealLogViewModel
import com.study.checkin.ui.StatsScreen
import com.study.checkin.ui.ThemeMode
import com.study.checkin.ui.UcDailyTheme
import com.study.checkin.ui.ProfileScreen
import com.study.checkin.ui.RecordOverlays
import com.study.checkin.ui.ToleranceScreen

/** 底部 Tab 定义 */
private data class TabItem(val label: String, val icon: ImageVector)

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

    /** 相册选图（系统 PhotoPicker 多选，无需额外权限；单次最多 9 张） */
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addGalleryPhotos(uris)
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
            // 主题：按"我的→主题"所选模式决定深/浅（默认跟随系统）
            val state by viewModel.uiState.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (state.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
            }
            UcDailyTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showQuickAdd by remember { mutableStateOf(false) }
                    var showExitDialog by remember { mutableStateOf(false) }
                    var showStats by remember { mutableStateOf(false) }
                    var showMedSettings by remember { mutableStateOf(false) }

                    // 系统返回键：逐层返回（退出确认 → 快捷菜单 → 全屏照片 → 各记录面板 → 非首页 Tab → 首页则弹退出确认）
                    BackHandler {
                        when {
                            showExitDialog -> showExitDialog = false
                            showQuickAdd -> showQuickAdd = false
                            state.fullscreenPhotos.isNotEmpty() -> viewModel.hidePhoto()
                            state.isAdding -> viewModel.cancelAdd()
                            state.isSymptomPanelOpen -> viewModel.cancelSymptomPanel()
                            state.isMedPanelOpen -> viewModel.cancelMedPanel()
                            state.isNotePanelOpen -> viewModel.cancelNotePanel()
                            showStats -> showStats = false
                            showMedSettings -> showMedSettings = false
                            state.selectedTab != 0 -> viewModel.selectTab(0)
                            else -> showExitDialog = true
                        }
                    }

                    if (showExitDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitDialog = false },
                            title = { Text("退出程序") },
                            text = { Text("确定要关闭程序吗？") },
                            confirmButton = {
                                TextButton(onClick = { finish() }) {
                                    Text("退出", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExitDialog = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                        // Tab 内容
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            when (state.selectedTab) {
                                0 -> HomeScreen(
                                    state = state,
                                    onDateSelected = { viewModel.selectDate(it) },
                                    onPrevWeek = { viewModel.prevHomeWeek() },
                                    onNextWeek = { viewModel.nextHomeWeek() },
                                    onPrevMonth = { viewModel.prevHomeMonth() },
                                    onNextMonth = { viewModel.nextHomeMonth() },
                                    onAddSymptom = { viewModel.startSymptomPanel() },
                                    onEditSymptom = { viewModel.startEditSymptom(it) },
                                    onAddNote = { viewModel.openNotePanel() },
                                    onFilterToggle = { viewModel.toggleDayRecordFilter(it) },
                                    onPhotoClick = { path, photos -> viewModel.showPhoto(path, photos) },
                                    onEditRecord = { viewModel.startEdit(it) },
                                    onDeleteRecord = { viewModel.deleteRecord(it.id) },
                                    onDeleteSymptom = { viewModel.deleteSymptom(it) },
                                    onEditMed = { viewModel.startEditMed(it) },
                                    onDeleteMed = { viewModel.deleteMed(it.id) },
                                    onDeleteNote = { viewModel.deleteNote() },
                                    onAddMed = { viewModel.startAddMedForToday() }
                                )

                                1 -> ToleranceScreen(
                                    state = state,
                                    onCycleTolerance = { viewModel.cycleFoodTag(it) },
                                    onDeleteFood = { viewModel.deleteFoodTag(it) },
                                    onMoveFood = { name, tolerance, before ->
                                        viewModel.moveFoodTag(name, tolerance, before)
                                    }
                                )

                                2 -> CalendarScreen(
                                    state = state,
                                    onDateSelected = { viewModel.selectDate(it) },
                                    onPrevMonth = { viewModel.prevMonth() },
                                    onNextMonth = { viewModel.nextMonth() },
                                    onJumpToMonth = { viewModel.jumpToMonth(it) },
                                    onPhotoClick = { path, photos -> viewModel.showPhoto(path, photos) },
                                    onEditRecord = { viewModel.startEdit(it) },
                                    onDeleteRecord = { viewModel.deleteRecord(it.id) },
                                    onEditSymptom = { viewModel.startEditSymptom(it) },
                                    onDeleteSymptom = { viewModel.deleteSymptom(it) },
                                    onEditMed = { viewModel.startEditMed(it) },
                                    onDeleteMed = { viewModel.deleteMed(it.id) },
                                    onOpenNotePanel = { viewModel.openNotePanel() },
                                    onDeleteNote = { viewModel.deleteNote() },
                                    onToggleCalendarCategory = { viewModel.toggleCalendarCategory(it) }
                                )

                                else -> ProfileScreen(
                                    state = state,
                                    onSetNickname = { viewModel.setNickname(it) },
                                    onSetAvatar = { viewModel.setAvatar(it) },
                                    onOpenStats = { showStats = true },
                                    onExport = { start, end, types, format ->
                                        viewModel.exportRecords(start, end, types, format)
                                    },
                                    onOpenMedSettings = { showMedSettings = true },
                                    onThemeModeChange = { viewModel.setThemeMode(it) }
                                )
                            }
                        }

                        // 全局面板层：任何 Tab 都能打开记录面板 / 全屏照片
                        RecordOverlays(
                            state = state,
                            onAddPhotoByCamera = { onCameraClick() },
                            onAddPhotoByGallery = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            onRemoveDraftPhoto = { viewModel.removeDraftPhoto(it) },
                            onToggleTag = { viewModel.toggleDraftTag(it) },
                            onAddFood = { name, tol -> viewModel.addFoodTag(name, tol) },
                            onDraftMealTypeChange = { viewModel.setDraftMealType(it) },
                            onDraftNoteChange = { viewModel.setDraftNote(it) },
                            onDraftTimeChange = { viewModel.setDraftTime(it) },
                            onSaveRecord = { viewModel.saveRecord() },
                            onCancelAdd = { viewModel.cancelAdd() },
                            onCloseSymptom = { viewModel.cancelSymptomPanel() },
                            onSymptomDraftChange = { viewModel.setSymptomDraft(it) },
                            onSaveSymptom = { viewModel.saveSymptom() },
                            onMedDraftChange = { viewModel.setMedDraft(it) },
                            onSaveMed = { viewModel.saveMed() },
                            onCancelMed = { viewModel.cancelMedPanel() },
                            onRemoveCommonMed = { viewModel.removeCommonMed(it) },
                            onNoteDraftChange = { viewModel.setNoteDraft(it) },
                            onSaveNote = { viewModel.saveNote() },
                            onCancelNote = { viewModel.cancelNotePanel() },
                            onDismissPhoto = { viewModel.hidePhoto() }
                        )

                        // 底部导航
                        BottomTabs(
                            selectedTab = state.selectedTab,
                            onSelect = { viewModel.selectTab(it) },
                            onCenterAdd = { showQuickAdd = true }
                        )
                        }
                    if (showQuickAdd) {
                        QuickAddPopup(
                            onMeal = { showQuickAdd = false; viewModel.startAdd() },
                            onSymptom = { showQuickAdd = false; viewModel.startSymptomPanel() },
                            onMed = { showQuickAdd = false; viewModel.startAddMed() },
                            onNote = { showQuickAdd = false; viewModel.openNotePanel() },
                            onDismiss = { showQuickAdd = false }
                        )
                    }

                    // 我的页二级全屏页：统计信息 / 服药设置（覆盖底部导航，返回键关闭）
                    if (showStats) {
                        StatsScreen(state = state, onBack = { showStats = false })
                    }
                    if (showMedSettings) {
                        MedSettingsScreen(
                            state = state,
                            onTimesChange = { viewModel.setMedTimesPerDay(it) },
                            onTimeChange = { i, t -> viewModel.setMedReminderTime(i, t) },
                            onBack = { showMedSettings = false }
                        )
                    }
                    }
                }
            }
        }
    }
}

/** 底部功能菜单：首页 / 耐受 / ＋ / 日历 / 我的，中间为凸起的快捷添加按钮 */
@Composable
private fun BottomTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    onCenterAdd: () -> Unit
) {
    val tabs = listOf(
        TabItem("首页", Icons.Filled.Home),
        TabItem("耐受", Icons.Filled.CheckCircle),
        TabItem("日历", Icons.Filled.DateRange),
        TabItem("我的", Icons.Filled.Person)
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        // 主栏：左 2 项 + 中间留白(放 ＋ 按钮) + 右 2 项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(top = 16.dp)
                .padding(horizontal = 8.dp)
        ) {
            NavSlot(tabs[0], selected = selectedTab == 0, onClick = { onSelect(0) }, Modifier.weight(1f))
            NavSlot(tabs[1], selected = selectedTab == 1, onClick = { onSelect(1) }, Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
            NavSlot(tabs[2], selected = selectedTab == 2, onClick = { onSelect(2) }, Modifier.weight(1f))
            NavSlot(tabs[3], selected = selectedTab == 3, onClick = { onSelect(3) }, Modifier.weight(1f))
        }
        // 中间凸起的 ＋ 快捷添加按钮
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(60.dp)
                .offset(y = (-18).dp)
                .clip(CircleShape)
                .shadow(6.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onCenterAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "添加记录",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** 底部栏单个 Tab（自绘，配合中间凸起按钮使用） */
@Composable
private fun NavSlot(
    tab: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** ＋ 按钮弹出的快捷添加菜单：饮食 / 便便 / 服药 / 笔记 */
@Composable
private fun QuickAddPopup(
    onMeal: () -> Unit,
    onSymptom: () -> Unit,
    onMed: () -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 半透明遮罩，点击关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss)
        )
        // 居中的选项卡片
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(
                text = "添加记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAddItem("🍚", "饮食", onMeal, Modifier.weight(1f))
                QuickAddItem("💩", "便便", onSymptom, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAddItem("💊", "服药", onMed, Modifier.weight(1f))
                QuickAddItem("📝", "笔记", onNote, Modifier.weight(1f))
            }
        }
    }
}

/** 快捷添加菜单中的单个选项 */
@Composable
private fun QuickAddItem(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

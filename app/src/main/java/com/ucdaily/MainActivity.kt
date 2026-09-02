package com.ucdaily

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ucdaily.data.RestoreImporter
import com.ucdaily.play.AppUpdateController
import com.ucdaily.ui.DailyManagementScreen
import com.ucdaily.ui.FontScaledContent
import com.ucdaily.ui.HomeScreen
import com.ucdaily.ui.LocalDarkTheme
import com.ucdaily.ui.RecordKind
import com.ucdaily.ui.UcDialog
import com.ucdaily.ui.primaryBtnBrush
import com.ucdaily.ui.recordKindEmoji
import com.ucdaily.ui.recordTypeColors
import com.ucdaily.ui.softShadow
import com.ucdaily.ui.ucPalette
import com.ucdaily.ui.HomeSloganScreen
import com.ucdaily.ui.MedSettingsScreen
import com.ucdaily.ui.MealLogViewModel
import com.ucdaily.ui.StatsScreen
import com.ucdaily.ui.ThemeMode
import com.ucdaily.ui.UcDailyTheme
import com.ucdaily.ui.ProfileScreen
import com.ucdaily.ui.SettingsScreen
import com.ucdaily.ui.ExportType
import com.ucdaily.ui.RecordListScreen
import com.ucdaily.ui.RecordOverlays
import com.ucdaily.ui.ToleranceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 底部 Tab 定义 */
private data class TabItem(val label: String, val icon: ImageVector)

/** 恢复记录的结果：Done = 解析完成（含统计）；Invalid = 文件无法识别/读取失败 */
private sealed interface RestoreUi {
    data class Done(val result: RestoreImporter.Result) : RestoreUi
    data object Invalid : RestoreUi
}

class MainActivity : ComponentActivity() {
    private val viewModel: MealLogViewModel by viewModels()

    /** Google Play Core 应用内更新控制器（多国语言版本随 AAB 通过 Play 分发） */
    private val appUpdate = AppUpdateController(this)

    /** 按"我的 → 语言"所选语言应用资源（默认跟随系统） */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.localizedContext(newBase))
    }

    /** 相机拍照（临时外部页面：launch 前置位 MedReminder 标记，结果返回时复位，
     *  避免 onStop 把"去拍照"当成退后台而触发未服药通知） */
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        MedReminder.setTransientExternalOpen(false)
        if (success) {
            viewModel.onCameraPhotoTaken()
        } else {
            viewModel.onCameraCancelled()
        }
    }

    /** 相册选图（系统 PhotoPicker 多选，无需额外权限；单次最多 9 张；
     *  同相机：临时外部页面，结果返回时复位标记） */
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        MedReminder.setTransientExternalOpen(false)
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
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    /** Android 13+ 通知权限（服药提醒系统通知用；授予后下一次同步自动发出通知） */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.syncMedReminderNotification()
    }

    /** 恢复记录：选择导出的 CSV 文件（"我的 → 恢复记录"） */
    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) readRestoreFile(uri)
    }

    /** 恢复记录的结果状态（非 null 时弹出结果对话框） */
    private var restoreOutcome by mutableStateOf<RestoreUi?>(null)

    /** 读取所选 CSV 并交给 ViewModel 解析入库，完成后弹出结果 */
    private fun readRestoreFile(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
            }
            restoreOutcome = if (text == null) {
                RestoreUi.Invalid
            } else {
                viewModel.restoreRecords(text)?.let { RestoreUi.Done(it) } ?: RestoreUi.Invalid
            }
        }
    }

    private fun launchCamera() {
        val uri = viewModel.prepareCameraFile()
        if (uri == null) {
            Toast.makeText(this, R.string.camera_file_create_failed, Toast.LENGTH_LONG).show()
            return
        }
        try {
            // 相机 = 临时外部页面：先标记，onStop 才不会误判为退后台（启动失败时回滚）
            MedReminder.setTransientExternalOpen(true)
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            MedReminder.setTransientExternalOpen(false)
            Toast.makeText(
                this,
                R.string.camera_app_not_found,
                Toast.LENGTH_LONG
            ).show()
        } catch (e: IllegalArgumentException) {
            MedReminder.setTransientExternalOpen(false)
            Toast.makeText(
                this,
                getString(R.string.camera_file_create_failed_detail, e.message),
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
        // 进入前台：首页打开期间未服药由右上角铃铛提醒，不发系统通知。
        // 必须在 ViewModel 首次同步（loadState → MedReminder.sync）之前设置
        MedReminder.setAppInForeground(true)
        // Android 13+：首次启动请求通知权限（服药提醒需要系统通知）
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 应用启动时静默检查 Google Play Core 更新（每次进程生命周期一次；
        // 侧载版本检查失败会静默降级，不打扰用户）
        lifecycleScope.launch {
            delay(1500)
            appUpdate.checkForUpdates()
        }
        // 每次应用回到前台（变为可见）时检查是否已跨零点：
        // 后台期间进程可能被系统冻结/Doze/杀掉，ViewModel 里的零点定时器不一定触发；
        // 回到前台主动补查一次"今天"，保证日历选中日跟着新的一天走
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // 回到前台：未服药由首页右上角铃铛提醒，不发系统通知；
                // 立即同步一次撤掉后台残留的通知（onCreate 首次进入时同样走这里）
                // 复位"临时外部页面"标记兜底（如启动器结果回调因 Activity 销毁而丢失）
                MedReminder.setTransientExternalOpen(false)
                MedReminder.setAppInForeground(true)
                viewModel.checkDayChange()
                viewModel.syncMedReminderNotification()
            }

            override fun onStop(owner: LifecycleOwner) {
                // 临时外部页面（相机 / 相册 / 系统文件保存界面）在前台：用户仍在
                // 操作本应用，不当作退后台（否则拍张照就会发出未服药通知）
                if (MedReminder.isTransientExternalOpen()) return
                // 退到后台：恢复系统通知能力（先标记后台，角标刷新才会真正发出通知）
                MedReminder.setAppInForeground(false)
                // 从桌面 / 最近任务打开应用会触发"桌面隐藏角标"的系统默认行为；
                // 桌面在应用前台期间不处理角标更新，只有应用真正退后台后到达的
                // 新通知（换新 id 静默重发，不响铃）才会恢复角标
                viewModel.refreshMedReminderBadgeToBackground()
            }
        })
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
                    var showHomeSlogans by remember { mutableStateOf(false) }
                    var showSettings by remember { mutableStateOf(false) }
                    /** 记录汇总列表（统计页点数量块打开，覆盖在统计页之上） */
                    var recordListType by remember { mutableStateOf<ExportType?>(null) }

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
                            recordListType != null -> recordListType = null
                            showStats -> showStats = false
                            showMedSettings -> showMedSettings = false
                            showHomeSlogans -> showHomeSlogans = false
                            showSettings -> showSettings = false
                            state.selectedTab != 0 -> viewModel.selectTab(0)
                            else -> showExitDialog = true
                        }
                    }

                    if (showExitDialog) {
                        val p = ucPalette()
                        UcDialog(
                            icon = Icons.Filled.Home,
                            iconBg = p.surface2,
                            iconTint = p.text2,
                            title = stringResource(R.string.exit_dialog_title),
                            message = stringResource(R.string.exit_dialog_message),
                            confirmLabel = stringResource(R.string.exit_dialog_confirm),
                            confirmIsDanger = true,
                            onConfirm = { finish() },
                            dismissLabel = stringResource(R.string.common_cancel),
                            onDismiss = { showExitDialog = false }
                        )
                    }

                    // Google Play Core 应用内更新：检查失败 / 已是最新（手动检查时提示）
                    val updateUi by appUpdate.ui.collectAsState()
                    var manualUpdate by remember { mutableStateOf(false) }
                    LaunchedEffect(updateUi) {
                        when (val u = updateUi) {
                            is AppUpdateController.Ui.Failed -> {
                                if (u.manual) {
                                    Toast.makeText(this@MainActivity, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                                }
                                manualUpdate = false
                                appUpdate.dismiss()
                            }
                            is AppUpdateController.Ui.UpToDate -> {
                                if (manualUpdate) {
                                    Toast.makeText(this@MainActivity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show()
                                }
                                manualUpdate = false
                                appUpdate.dismiss()
                            }
                            else -> Unit
                        }
                    }
                    when (updateUi) {
                        is AppUpdateController.Ui.Available -> AlertDialog(
                            onDismissRequest = { appUpdate.dismiss() },
                            title = { Text(stringResource(R.string.update_dialog_title)) },
                            text = { Text(stringResource(R.string.update_dialog_message)) },
                            confirmButton = {
                                TextButton(onClick = { appUpdate.startUpdate() }) {
                                    Text(stringResource(R.string.update_now))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { appUpdate.dismiss() }) {
                                    Text(stringResource(R.string.common_later))
                                }
                            }
                        )
                        is AppUpdateController.Ui.Downloading -> AlertDialog(
                            onDismissRequest = {},
                            title = { Text(stringResource(R.string.update_download_title)) },
                            text = { Text(stringResource(R.string.update_download_message)) },
                            confirmButton = {}
                        )
                        is AppUpdateController.Ui.Ready -> AlertDialog(
                            onDismissRequest = { appUpdate.dismiss() },
                            title = { Text(stringResource(R.string.update_ready_title)) },
                            text = { Text(stringResource(R.string.update_ready_message)) },
                            confirmButton = {
                                TextButton(onClick = { appUpdate.completeUpdate() }) {
                                    Text(stringResource(R.string.update_restart))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { appUpdate.dismiss() }) {
                                    Text(stringResource(R.string.common_later))
                                }
                            }
                        )
                        else -> Unit
                    }

                    // 恢复记录结果对话框
                    restoreOutcome?.let { outcome ->
                        AlertDialog(
                            onDismissRequest = { restoreOutcome = null },
                            title = { Text(stringResource(R.string.restore_result_title)) },
                            text = {
                                when (outcome) {
                                    is RestoreUi.Invalid -> Text(
                                        stringResource(R.string.restore_error_invalid)
                                    )
                                    is RestoreUi.Done -> Column {
                                        val r = outcome.result
                                        Text(
                                            stringResource(
                                                R.string.restore_result_summary,
                                                r.meals, r.meds, r.symptoms, r.notes, r.tags
                                            )
                                        )
                                        if (r.skipped > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                stringResource(R.string.restore_result_skipped, r.skipped),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (r.failed > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                stringResource(R.string.restore_result_failed, r.failed),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { restoreOutcome = null }) {
                                    Text(stringResource(R.string.common_got_it))
                                }
                            }
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                        // Tab 内容（面板层已移出其外层 Box，本 Box 高度不再被压缩）
                        // clipToBounds：裁剪内容不越出本区域，防止固定高度元素绘制溢出
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clipToBounds()
                        ) {
                            // 首页 / 耐受 / 日常管理三个 Tab 按"我的→字体大小"档位缩放文字
                            val fontScale = state.fontLevel.scale
                            when (state.selectedTab) {
                                0 -> FontScaledContent(scale = fontScale) {
                                    HomeScreen(
                                        state = state,
                                        onAvatarClick = { viewModel.selectTab(3) },
                                        onOpenSettings = { showSettings = true },
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
                                }

                                1 -> FontScaledContent(scale = fontScale) {
                                    ToleranceScreen(
                                        state = state,
                                        onCycleTolerance = { viewModel.cycleFoodTag(it) },
                                        onDeleteFood = { viewModel.deleteFoodTag(it) },
                                        onMoveFood = { name, tolerance, before ->
                                            viewModel.moveFoodTag(name, tolerance, before)
                                        }
                                    )
                                }

                                2 -> FontScaledContent(scale = fontScale) {
                                    DailyManagementScreen()
                                }

                                else -> ProfileScreen(
                                    state = state,
                                    onSetNickname = { viewModel.setNickname(it) },
                                    onSetAvatar = { viewModel.setAvatar(it) },
                                    onOpenStats = { showStats = true },
                                    onOpenMedSettings = { showMedSettings = true },
                                    onExport = { start, end, types, format ->
                                        viewModel.exportRecords(start, end, types, format)
                                    },
                                    onRestore = {
                                        restoreLauncher.launch(
                                            arrayOf(
                                                "text/*",
                                                "text/comma-separated-values",
                                                "application/csv"
                                            )
                                        )
                                    },
                                    onOpenSettings = { showSettings = true }
                                )
                            }
                        }

                        // 底部导航
                        BottomTabs(
                            selectedTab = state.selectedTab,
                            onSelect = { viewModel.selectTab(it) },
                            onCenterAdd = { showQuickAdd = true }
                        )
                        }
                    // 全局面板层：浮在原页面之上（不参与 Column 布局，不再把 Tab 压缩成 0 高）——
                    // 记录面板 / 全屏照片打开时，原页面 + 底部导航在遮罩下仍可见
                    RecordOverlays(
                        state = state,
                        onAddPhotoByCamera = { onCameraClick() },
                        onAddPhotoByGallery = {
                            // 相册选择器 = 临时外部页面：先标记，onStop 才不会误判为退后台
                            MedReminder.setTransientExternalOpen(true)
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
                        StatsScreen(
                            state = state,
                            onBack = { showStats = false },
                            onOpenRecords = { recordListType = it }
                        )
                    }
                    // 记录汇总列表（统计页数量块 → 全时段明细，返回回统计页）
                    recordListType?.let { type ->
                        RecordListScreen(
                            state = state,
                            type = type,
                            onBack = { recordListType = null }
                        )
                    }
                    if (showMedSettings) {
                        MedSettingsScreen(
                            state = state,
                            onTimesChange = { viewModel.setMedTimesPerDay(it) },
                            onTimeChange = { i, t -> viewModel.setMedReminderTime(i, t) },
                            onBack = { showMedSettings = false }
                        )
                    }
                    // 我的页二级全屏页：设置（首页寄语/主题/字体大小/语言/软件更新/关于，返回键关闭）
                    if (showSettings) {
                        SettingsScreen(
                            state = state,
                            onOpenHomeSlogans = { showHomeSlogans = true },
                            onFontSizeChange = { viewModel.setFontSize(it) },
                            onThemeModeChange = { viewModel.setThemeMode(it) },
                            // 语言切换：持久化后 recreate 立即按新语言重建界面
                            onLanguageChange = {
                                viewModel.setLanguage(it)
                                recreate()
                            },
                            // 手动检查更新（Google Play Core）
                            onCheckUpdate = {
                                manualUpdate = true
                                appUpdate.checkForUpdates(manual = true)
                            },
                            onBack = { showSettings = false }
                        )
                    }
                    // 我的页二级全屏页：首页寄语（横幅轮播列表管理，返回键关闭）
                    if (showHomeSlogans) {
                        HomeSloganScreen(
                            state = state,
                            onAdd = { viewModel.addHomeSlogan(it) },
                            onUpdate = { i, t -> viewModel.updateHomeSlogan(i, t) },
                            onDelete = { viewModel.deleteHomeSlogan(it) },
                            onReset = { viewModel.resetHomeSlogans() },
                            onBack = { showHomeSlogans = false }
                        )
                    }
                    }
                }
            }
        }
    }
}

/** 底部导航（设计稿 .nav）：顶部圆角 + 上投影；选中项文字套 primary-soft 胶囊；中间凸起渐变 ＋ 按钮 */
@Composable
private fun BottomTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    onCenterAdd: () -> Unit
) {
    val p = ucPalette()
    val tabs = listOf(
        TabItem(stringResource(R.string.tab_home), Icons.Filled.Home),
        TabItem(stringResource(R.string.tab_tolerance), Icons.Filled.CheckCircle),
        TabItem(stringResource(R.string.tab_daily_management), Icons.Filled.MenuBook),
        TabItem(stringResource(R.string.tab_profile), Icons.Filled.Person)
    )
    val navShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    // 外层不裁剪：＋ 按钮需凸出导航栏顶部 22dp，不能被导航栏的圆角裁剪吃掉
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 导航栏主体（圆角裁剪只作用于主体本身）：上投影突出栏体（10dp，α0.3/0.4）——
        // α0.8 太深，会在栏体上方形成一条暗带挡住页面内容，故降档
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    10.dp,
                    navShape,
                    ambientColor = (if (LocalDarkTheme.current) Color.White else Color.Black)
                        .copy(alpha = if (LocalDarkTheme.current) 0.4f else 0.3f),
                    spotColor = (if (LocalDarkTheme.current) Color.White else Color.Black)
                        .copy(alpha = if (LocalDarkTheme.current) 0.4f else 0.3f)
                )
                .clip(navShape)
                .background(p.surface)
        ) {
            // 主栏：4 个 tab 与中间留白各占 1/5 宽（与设计稿 .ni{flex:1} 一致）——
            // tab 整体向中间靠拢：耐受/日常管理离 ＋ 按钮更近，首页/我的离屏幕边缘更远
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavSlot(tabs[0], selected = selectedTab == 0, onClick = { onSelect(0) }, modifier = Modifier.weight(1f))
                NavSlot(tabs[1], selected = selectedTab == 1, onClick = { onSelect(1) }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
                NavSlot(tabs[2], selected = selectedTab == 2, onClick = { onSelect(2) }, modifier = Modifier.weight(1f))
                NavSlot(tabs[3], selected = selectedTab == 3, onClick = { onSelect(3) }, modifier = Modifier.weight(1f))
            }
        }
        // 中间凸起的 ＋ 快捷添加按钮（54dp 渐变圆 + 页面底色描边）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .size(54.dp)
                .shadow(
                    10.dp,
                    CircleShape,
                    ambientColor = Color(0xFF2563EB).copy(alpha = 0.8f),
                    spotColor = Color(0xFF2563EB).copy(alpha = 0.8f)
                )
                .clip(CircleShape)
                .background(primaryBtnBrush())
                .border(4.dp, p.bg, CircleShape)
                .clickable(onClick = onCenterAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.quick_add_title),
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }
    }
}

/** 底部栏单个 Tab：图标 + 文字（选中时文字套 primary-soft 胶囊）。
 *  modifier 传入 weight(1f) 后占 1/5 栏宽，图标/胶囊在格内居中，整格可点 */
@Composable
private fun NavSlot(
    tab: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = ucPalette()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            modifier = Modifier.size(16.dp),
            tint = if (selected) p.primary else p.text2
        )
        Spacer(modifier = Modifier.height(1.5.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .then(if (selected) Modifier.background(p.primarySoft) else Modifier)
                .padding(horizontal = 8.dp, vertical = 1.dp)
        ) {
            Text(
                text = tab.label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) p.primaryText else p.text2
            )
        }
    }
}

/** ＋ 按钮弹出的快捷添加菜单（设计稿 .qadd）：居中 2×2 彩色磁贴（饮食/便便/服药/感受） */
@Composable
private fun QuickAddPopup(
    onMeal: () -> Unit,
    onSymptom: () -> Unit,
    onMed: () -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = ucPalette()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(268.dp)
                .softShadow(elevation = 14.dp, shape = RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(p.surface)
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_add_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = p.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAddTile(RecordKind.MEAL, stringResource(R.string.type_meal), onMeal, Modifier.weight(1f))
                QuickAddTile(RecordKind.BOWEL, stringResource(R.string.type_bowel), onSymptom, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAddTile(RecordKind.MED, stringResource(R.string.type_med), onMed, Modifier.weight(1f))
                QuickAddTile(RecordKind.NOTE, stringResource(R.string.type_note), onNote, Modifier.weight(1f))
            }
        }
    }
}

/** 快捷添加菜单中的单个磁贴：柔和色底 + 彩色 emoji + 同色文字（设计稿 .qt） */
@Composable
private fun QuickAddTile(
    kind: RecordKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tc = recordTypeColors(kind)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tc.soft)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = recordKindEmoji(kind), fontSize = 22.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tc.text
        )
    }
}

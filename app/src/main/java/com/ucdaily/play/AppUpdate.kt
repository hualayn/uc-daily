package com.ucdaily.play

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play Core（应用内更新）封装：Flexible 灵活更新（play-core app-update 2.x）。
 *
 * 用途：本应用为多国语言版本——语言资源随 AAB 通过 Google Play 下发，
 * 新增语言 / 功能的新版本由 Play 分发，用户可在应用内直接更新：
 * - 应用启动时静默检查（[checkForUpdates]，每次进程生命周期一次）；
 * - "我的 → 软件更新"手动检查（manual = true，失败时提示）；
 * - 发现新版本 → 弹框"立即更新" → 后台下载（不打断使用）→
 *   下载完成弹框"立即重启"→ [completeUpdate] 重启装上新版本。
 *
 * 注意：Play Core 仅对从 Google Play 安装的版本生效；侧载 / 开发机安装时
 * getAppUpdateInfo 返回失败（ERROR_API_NOT_AVAILABLE 等），
 * 自动检查静默降级、手动检查由 UI 给出 Toast 提示。
 */
class AppUpdateController(private val activity: Activity) {

    sealed interface Ui {
        data object Idle : Ui
        data object Fetching : Ui
        data object Available : Ui
        data object UpToDate : Ui
        data object Downloading : Ui
        data object Ready : Ui
        data class Failed(val manual: Boolean) : Ui
    }

    private val _ui = MutableStateFlow<Ui>(Ui.Idle)
    val ui: StateFlow<Ui> = _ui

    private var manual = false
    private var autoChecked = false
    private var flowStarted = false

    private val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()

    private val manager by lazy {
        AppUpdateManagerFactory.create(activity).also { m ->
            m.registerListener { state ->
                when (state.installStatus()) {
                    InstallStatus.DOWNLOADING -> _ui.value = Ui.Downloading
                    InstallStatus.DOWNLOADED,
                    InstallStatus.INSTALLED -> _ui.value = Ui.Ready
                    InstallStatus.FAILED,
                    InstallStatus.CANCELED -> {
                        flowStarted = false
                        _ui.value = if (manual) Ui.Failed(true) else Ui.Idle
                    }
                    else -> Unit
                }
            }
        }
    }

    /** 检查更新。manual = 用户手动触发（"我的 → 软件更新"），失败时提示；自动检查失败时静默降级 */
    fun checkForUpdates(manual: Boolean = false) {
        if (_ui.value is Ui.Downloading || _ui.value is Ui.Ready) return
        this.manual = manual
        if (autoChecked && !manual) return
        autoChecked = true
        _ui.value = Ui.Fetching
        try {
            manager.getAppUpdateInfo().addOnSuccessListener { info ->
                when (info.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE ->
                        if (info.isUpdateTypeAllowed(options)) _ui.value = Ui.Available
                        else _ui.value = if (manual) Ui.Failed(true) else Ui.Idle
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                        // 上次流程尚未完成：恢复"正在下载"提示
                        _ui.value = Ui.Downloading
                    else -> _ui.value = Ui.UpToDate
                }
            }.addOnFailureListener { e ->
                Log.i(TAG, "checkForUpdate failed: ${e.message}")
                _ui.value = if (manual) Ui.Failed(true) else Ui.Idle
            }
        } catch (e: Exception) {
            Log.i(TAG, "Play Core unavailable (not a Play install?): ${e.message}")
            _ui.value = if (manual) Ui.Failed(true) else Ui.Idle
        }
    }

    /** 启动 Flexible 更新流程：后台下载，完成后 UI 进入 Ready（等待用户重启） */
    fun startUpdate() {
        if (flowStarted) return
        try {
            manager.getAppUpdateInfo().addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    flowStarted = true
                    manager.startUpdateFlow(info, activity, options).addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.i(TAG, "startUpdateFlow failed: ${task.exception?.message}")
                            flowStarted = false
                            if (manual) _ui.value = Ui.Failed(true)
                        }
                    }
                } else if (manual) {
                    _ui.value = Ui.Failed(true)
                }
            }.addOnFailureListener { e ->
                Log.i(TAG, "startUpdateFlow failed: ${e.message}")
                if (manual) _ui.value = Ui.Failed(true)
            }
        } catch (e: Exception) {
            Log.i(TAG, "startUpdateFlow failed: ${e.message}")
            if (manual) _ui.value = Ui.Failed(true)
        }
    }

    /** 重启应用以安装已下载的更新（应用终止并重启） */
    fun completeUpdate() {
        try {
            manager.completeUpdate()
        } catch (e: Exception) {
            Log.i(TAG, "completeUpdate failed: ${e.message}")
        }
    }

    /** 关闭更新提示（"稍后" / 自动提示超时后回到 Idle） */
    fun dismiss() {
        if (_ui.value is Ui.Downloading) return
        flowStarted = false
        _ui.value = Ui.Idle
    }
}

private const val TAG = "AppUpdate"

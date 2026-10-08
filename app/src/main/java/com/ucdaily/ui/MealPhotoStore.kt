package com.ucdaily.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.ucdaily.BuildConfig
import com.ucdaily.util.PhotoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** FileProvider authority（与 AndroidManifest 一致；由 applicationId 派生，包名变化时不会漂移） */
const val FILE_PROVIDER_AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider"

/**
 * 饮食照片管线：相机拍照 / 相册多选 → 复制到应用私有目录 → 压缩到 300KB 以下
 * → 返回待并入草稿的路径（状态更新由调用方 ViewModel 完成）。
 *
 * 相机写入文件（pendingCameraPath）在本类内部管理：
 * 取消 / 重开相机会清理上次遗留的孤儿文件，不再残留磁盘垃圾。
 */
class MealPhotoStore(private val app: Application) {

    /** 相机拍照的待写入文件路径（拍摄成功后并入草稿） */
    @Volatile
    private var pendingCameraPath: String? = null

    /** 创建相机写入文件并返回 FileProvider Uri，供 Activity 启动相机 */
    fun prepareCameraFile(): Uri? {
        // 上次启动相机后未拍摄（取消/失败前又重开）：清理遗留文件，避免孤儿
        pendingCameraPath?.let { File(it).delete() }
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val ts = System.currentTimeMillis()
        val file = File(dir, "meal_$ts.jpg")
        return try {
            pendingCameraPath = file.absolutePath
            FileProvider.getUriForFile(app, FILE_PROVIDER_AUTHORITY, file)
        } catch (e: IllegalArgumentException) {
            pendingCameraPath = null
            null
        }
    }

    /**
     * 相机拍摄成功：压缩到 300KB 以下后返回最终照片路径（IO 线程压缩，不阻塞 UI）。
     * @return 照片绝对路径；拍摄失败（文件不存在）返回 null
     */
    suspend fun onCameraPhotoTaken(): String? {
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path == null) return null
        // 存在性检查与压缩都在 IO 线程，避免主线程文件 IO
        return withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@withContext null
            compressPhotoInPlace(file).absolutePath
        }
    }

    /** 相机取消或失败：清理可能已被相机 app 创建的孤儿文件 */
    fun onCameraCancelled() {
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path != null) File(path).delete()
    }

    /** 从相册选取的多张照片：逐张复制到应用私有目录（含压缩）后返回路径列表 */
    suspend fun addGalleryPhotos(uris: List<Uri>): List<String> {
        if (uris.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val dir = app.getExternalFilesDir(null) ?: app.filesDir
            // 同一毫秒内多张照片用下标错开文件名，避免互相覆盖
            uris.mapIndexed { index, uri ->
                try {
                    val ts = System.currentTimeMillis() + index
                    val ext = queryExtension(uri)
                    val file = File(dir, "meal_$ts.$ext")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    // 与拍照一致：压缩到 300KB 以下再入草稿（压缩失败则保留原图）
                    if (file.length() > 0) compressPhotoInPlace(file).absolutePath
                    else {
                        file.delete()
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }.filterNotNull()
        }
    }

    /**
     * 原地压缩照片文件至 300KB 以下（重编码为 JPEG）。
     * 压缩成功且比原图小时替换原文件（原为 PNG/WebP 时文件名改为 .jpg）；
     * 压缩失败（如 HEIC 无法解码）原样返回原文件。
     */
    private fun compressPhotoInPlace(file: File): File {
        val tmp = File(file.parentFile, "${file.nameWithoutExtension}~compress.jpg")
        val ok = PhotoCompressor.compress(file, tmp) != null &&
            tmp.length() > 0 &&
            tmp.length() < file.length()
        if (ok) {
            val target = File(file.parentFile, "${file.nameWithoutExtension}.jpg")
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            if (target.absolutePath != file.absolutePath) file.delete()
            return target
        }
        tmp.delete()
        return file
    }

    private fun queryExtension(uri: Uri): String {
        val name = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
        return ext ?: "jpg"
    }
}

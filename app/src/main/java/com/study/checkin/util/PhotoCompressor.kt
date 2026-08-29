package com.study.checkin.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 照片压缩工具：把照片压缩为小于目标大小（默认 300KB）的 JPEG。
 *
 * 策略（三级递进，保证绝大多数照片都能达标）：
 * 1. 按最长边降采样解码（不整图解码进内存，控制内存峰值）；
 * 2. 读取 EXIF 方向并把位图旋正后保存（重编码不带方向标签，任何查看器显示方向都正确）；
 * 3. 从较高画质起逐档降低 JPEG 质量直到文件 ≤ 目标大小；仍超则再缩小最长边重试。
 */
object PhotoCompressor {

    /** 压缩目标：文件字节数上限（300KB） */
    const val TARGET_BYTES = 300L * 1024

    /** 最长边起点（像素）：超过则等比缩小 */
    private const val START_EDGE = 1600
    /** 最长边下限（像素）：到此再压不达标基本是极端图像 */
    private const val MIN_EDGE = 800
    /** JPEG 质量：起始 / 下限 / 步进 */
    private const val START_QUALITY = 85
    private const val MIN_QUALITY = 30
    private const val QUALITY_STEP = 10

    /**
     * 将 [input] 压缩为 JPEG 写入 [output]。
     * 返回压缩后的文件；解码失败或无法达标时返回 null（由调用方决定回退策略）。
     */
    fun compress(input: File, output: File): File? {
        return try {
            // 1) 只读尺寸（不解码像素），计算采样率
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var edge = START_EDGE
            while (edge >= MIN_EDGE) {
                val bitmap = decodeScaled(input, bounds.outWidth, bounds.outHeight, edge)
                    ?: return null
                val upright = rotateForExif(input, bitmap)
                if (upright !== bitmap) bitmap.recycle()

                // 3) 逐档降质直到达标（位图要跨档复用，达标或本轮结束才回收）
                var quality = START_QUALITY
                while (quality >= MIN_QUALITY) {
                    val baos = ByteArrayOutputStream()
                    upright.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    val bytes = baos.toByteArray()
                    if (bytes.size <= TARGET_BYTES) {
                        upright.recycle()
                        output.writeBytes(bytes)
                        return output
                    }
                    quality -= QUALITY_STEP
                }
                upright.recycle()
                // 画质已到下限仍超标 → 再缩小最长边重试
                edge = (edge * 0.75f).toInt()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 降采样解码，使最长边 ≤ [edge]（inSampleSize 取 2 的幂） */
    private fun decodeScaled(file: File, srcW: Int, srcH: Int, edge: Int): Bitmap? {
        var sample = 1
        while (srcW / sample > edge || srcH / sample > edge) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    /** 按 EXIF 方向把位图旋正（无 EXIF / 方向正常时原样返回）。
     *  相机照片只会出现 ROTATE_90/180/270；TRANSPOSE/TRANSVERSE 罕见，按纯旋转近似处理。 */
    private fun rotateForExif(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            return bitmap
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> return flipBitmap(bitmap, -1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> return flipBitmap(bitmap, 1f, -1f)
            else -> return bitmap
        }
        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun flipBitmap(bitmap: Bitmap, sx: Float, sy: Float): Bitmap = try {
        val matrix = Matrix().apply { postScale(sx, sy) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        bitmap
    }
}

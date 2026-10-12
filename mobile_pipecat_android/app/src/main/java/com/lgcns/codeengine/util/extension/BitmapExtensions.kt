package com.lgcns.codeengine.util.extension

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.annotation.Px
import com.lgcns.codeengine.activity.ImageEditActivity.Companion.DEFAULT_MAX_SIZE
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.min

fun Bitmap.compressPng(): ByteArray? =
    try {
        ByteArrayOutputStream().use { output ->
            output to compress(Bitmap.CompressFormat.PNG, 100, output)
        }.let { (output, isSuccessful) ->
            if (isSuccessful) output.toByteArray() else null
        }
    } catch (e: Exception) {
        Timber.d("Failed to bitmap compress to png. ${e.message}")
        null
    }

fun Bitmap.compressJpeg(quality: Int = 100, recycle: Boolean = false): ByteArray? =
    try {
        ByteArrayOutputStream().use { output ->
            output to compress(Bitmap.CompressFormat.JPEG, quality, output)
        }.let { (output, isSuccessful) ->
            if (recycle) this.recycle()
            if (isSuccessful) {
                val result = output.toByteArray()
                output.close()
                result
            } else null
        }
    } catch (e: Exception) {
        Timber.d("Failed to bitmap compress to jpeg. ${e.message}")
        null
    }

fun Bitmap.reduceCompressJpeg(maxSize: Int = DEFAULT_MAX_SIZE):Bitmap {
    var reducedBitmap: Bitmap = this
    val compressedOrigin = this?.compressJpeg()

    Timber.d("compressed original size:${compressedOrigin?.size}")

    if (compressedOrigin?.size!! > maxSize) {
        var reduceRatio = kotlin.math.sqrt(maxSize / (compressedOrigin?.size ?: maxSize).toDouble())
        reduceRatio *= 0.75
        Timber.d("reduceRatio=$reduceRatio")
        Timber.d("origin width:${this!!.width}, reduced width:${this!!.width * reduceRatio}")
        reducedBitmap = this.resizeMaxWidth((this.width * reduceRatio).toInt())
    }

    return reducedBitmap
}

fun Bitmap.resizeMaxWidth(@Px maxWidth: Int, recycle: Boolean = false): Bitmap {
    if (width <= maxWidth) {
        return this
    }
    val scale: Float = maxWidth.toFloat() / width
    val scaleHeight: Int = (height * scale).toInt()
    val result = Bitmap.createScaledBitmap(this, maxWidth, scaleHeight, true)
    if (recycle) {
        this.recycle()
    }
    return result
}

fun Bitmap.makeThumbnail(recycle: Boolean = false): Bitmap {
    val thumbWidth = 100

    val scale: Float = thumbWidth.toFloat() / width
    val scaleHeight: Int = (height * scale).toInt()
    val result = Bitmap.createScaledBitmap(this, thumbWidth, scaleHeight, true)
    if (recycle) {
        this.recycle()
    }
    return result
}

fun Bitmap.resizeMaxSize(
    @Px maxWidth: Int,
    @Px maxHeight: Int,
    recycle: Boolean = false,
): Bitmap {
    if (width <= maxWidth && height <= maxHeight) {
        return this
    }
    val scale: Float = min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    val scaledWidth: Int = (width * scale).toInt()
    val scaledHeight: Int = (height * scale).toInt()
    val result = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    if (recycle) {
        this.recycle()
    }
    return result
}


fun Bitmap.rotate(degrees: Int, recycle: Boolean = false): Bitmap {
    if (degrees == 0) {
        return this
    }
    val matrix = Matrix().apply {
        postRotate(degrees.toFloat())
    }
    val result = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (recycle) {
        this.recycle()
    }
    return result
}

enum class CropType {
    KAKAO_PREVIEW,
}

fun Bitmap.crop(type: CropType) =
    when (type) {
        CropType.KAKAO_PREVIEW -> cropKaKaoPreview()
    }

private fun Bitmap.cropKaKaoPreview(): Bitmap {
    try {
        val resultWidth = width
        val resultHeight = (resultWidth.toFloat() * 1.3f).toInt()

        if (resultHeight >= height) {
            // 이미지 비율이 3:4 이하일 경우 그냥 원본 이미지를 사용한다
            Timber.d("No need to crop, use the original ${width}x${height}.")
            return this
        }
        // 이미지 비율이 3:4 이상일 때 3:4 로 자른다
        val resultBitmap = Bitmap.createBitmap(resultWidth, resultHeight, config)
        Canvas(resultBitmap).drawBitmap(this, 0f, 0f, null)

        Timber.d("crop ${resultBitmap.width}x${resultBitmap.height} <-- original ${width}x${height}")

        return resultBitmap
    } catch (e: Exception) {
        Timber.w("Failed to crop for kakao preview. ${e.message}")
        return this
    }
}
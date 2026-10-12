package com.lgcns.codeengine.util.extension

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.View.MeasureSpec.makeMeasureSpec
import android.webkit.WebView
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "ScreenshotExtensions"

fun View.captureBitmap(): Bitmap? {
    return captureBitmapImplDraw()
}

suspend fun WebView.captureFullBitmap(): Bitmap? {
    return captureFullBitmapImplDraw()
}

fun File.writeJpeg(bitmap: Bitmap, quality: Int = 90): Boolean =
    try {
        parentFile?.mkdirs()
        outputStream().buffered().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
        }
    } catch (e: Exception) {
        Timber.d("Failed to write jpeg to file. ${e.message}")
        false
    }


private fun View.captureBitmapImplDraw(): Bitmap? {
    if (measuredWidth == 0 || measuredHeight == 0) {
        return null
    }
    val result = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
    draw(Canvas(result))
    return result
}

private suspend fun WebView.captureFullBitmapImplDraw(): Bitmap? {
    measure(makeMeasureSpec(0, UNSPECIFIED), makeMeasureSpec(0, UNSPECIFIED))
    layout(0, 0, measuredWidth, measuredHeight)

    awaitDomUpdateComplete()

    delay(1000)

    if (measuredWidth == 0 || measuredHeight == 0) {
        return null
    }

    val result = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
    draw(Canvas(result))

    requestLayout() // restore layout

    return result
}

private suspend inline fun WebView.awaitDomUpdateComplete() = suspendCoroutine<Unit> {
    postVisualStateCallback(0, object : WebView.VisualStateCallback() {
        override fun onComplete(requestId: Long) {
            it.resume(Unit)
        }
    })
}
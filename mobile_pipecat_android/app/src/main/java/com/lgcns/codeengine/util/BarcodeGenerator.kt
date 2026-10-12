package com.lgcns.codeengine.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.lgcns.codeengine.ui.BarcodeCreateData
import com.lgcns.codeengine.ui.BarcodeType
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

typealias PngByteArray = ByteArray

class BarcodeGenerator {

    private val BarcodeType.format: BarcodeFormat
        get() = when (this) {
            BarcodeType.CODE_128 -> BarcodeFormat.CODE_128
            BarcodeType.QR_CODE -> BarcodeFormat.QR_CODE
        }

    private val foregroundColor = Color.BLACK
    private val backgroundColor = Color.WHITE

    fun createPng(request: BarcodeCreateData, quality: Int = 100): PngByteArray {
        val bitmap = createBitmap(request)
        val result = bitmap.toPngByteArray(quality)
        bitmap.recycle()
        return result
    }

    private fun createBitmap(request: BarcodeCreateData): Bitmap {
        val result = Bitmap.createBitmap(
            request.width, request.height, Bitmap.Config.RGB_565
        )
        val canvas = Canvas(result)
        canvas.drawColor(backgroundColor)

        val textPadding = when(request.drawCode) {
            true -> {
                val textPadding = (request.textSize * 0.3f).roundToInt()
                val textBound = canvas.drawBarcodeText(
                    text = request.data,
                    theTextSize = request.textSize.toFloat(),
                    textPadding = textPadding
                )
                textBound.height() - (textPadding * 2)
            }
            false -> 0
        }

        result.drawBarcodeImage(
            data = request.data,
            format = request.type.format,
            width = result.width,
            height = request.height - textPadding // text height + padding
        )
        return result
    }

    private fun Canvas.drawBarcodeText(text: String, theTextSize: Float, textPadding: Int): Rect {
        val textPaint = TextPaint().apply {
            textSize = theTextSize
            color = foregroundColor
        }
        val textBounds = Rect().apply {
            textPaint.getTextBounds(text, 0, text.length, this)
        }
        drawText(
            text,
            (width - textBounds.width()) * 0.5f, // center horizontal
            height.toFloat() - textPadding, // bottom
            textPaint
        )
        return textBounds
    }

    private fun Bitmap.drawBarcodeImage(
        data: String, format: BarcodeFormat, width: Int, height: Int,
    ) {
        val hints = mutableMapOf<EncodeHintType, Any?>()
        if (format == BarcodeFormat.QR_CODE) {
            hints[EncodeHintType.CHARACTER_SET] = "ISO-8859-1"
            hints[EncodeHintType.MARGIN] = 0 /* default = 4 */
        }
        val barcodeMatrix = MultiFormatWriter().encode(data, format, width, height, hints)
        setPixels(
            barcodeMatrix.toPixels(), 0, width, 0, 0, barcodeMatrix.width, barcodeMatrix.height
        )
    }

    private fun BitMatrix.toPixels(): IntArray {
        val result = IntArray(width * height)
        for (y in 0 until height) {
            val offset: Int = y * width
            for (x in 0 until width) {
                result[offset + x] = if (get(x, y)) foregroundColor else backgroundColor
            }
        }
        return result
    }

    private fun Bitmap.toPngByteArray(quality: Int = 100) =
        ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.PNG, quality, output)
            output.toByteArray()
        }

}
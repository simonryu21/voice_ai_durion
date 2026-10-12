package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * VoiceVizBars — 32 vertical bars with a sin-based envelope (taller in the
 * middle, shorter at the edges). When [inputLevel] is provided (0..1), the
 * envelope is scaled by it for a real-audio response.
 *
 * Mirrors `app.jsx#VizBars` 1:1 in shape; rendered via Compose Canvas.
 */
@Composable
fun VoiceVizBars(
    accent: Color = Pipecat.Vermilion,
    active: Boolean = true,
    size: Dp = 220.dp,
    inputLevel: Float = 0f,
    bars: Int = 32,
) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val start = System.nanoTime()
        while (isActive) {
            t = (System.nanoTime() - start) / 1_000_000_000f
            delay(16.milliseconds)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(size),
    ) {
        val width = this.size.width
        val height = this.size.height
        val barWidthPx = 5.dp.toPx()
        val gapPx = 4.dp.toPx()
        val totalWidth = bars * barWidthPx + (bars - 1) * gapPx
        val startX = (width - totalWidth) / 2

        val gain = 0.5f + inputLevel.coerceIn(0f, 1f) * 1.5f

        for (i in 0 until bars) {
            val phase = i.toFloat() / bars
            val base = 0.18f + 0.5f * abs(sin(phase * 5f + t * 3.5f))
            val env = sin(phase * PI.toFloat()).pow(1.4f)
            val h = if (active) base * env * gain else 0.05f
            val barH = (h * height).coerceAtLeast(4f).coerceAtMost(height)
            val x = startX + i * (barWidthPx + gapPx)
            val y = (height - barH) / 2
            drawRoundRect(
                color = accent.copy(alpha = 0.6f + h * 0.4f),
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barH),
                cornerRadius = CornerRadius(barWidthPx / 2, barWidthPx / 2),
            )
        }
    }
}

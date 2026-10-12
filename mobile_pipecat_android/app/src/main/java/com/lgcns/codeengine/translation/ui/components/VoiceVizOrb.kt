package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * VoiceVizOrb — three concentric ring-only circles breathing at 90° phase
 * offsets, plus a solid 38% accent core with a pulsing glow. No gradient.
 * Mirrors `app.jsx#VizOrb`.
 */
@Composable
fun VoiceVizOrb(
    accent: Color = Pipecat.Vermilion,
    active: Boolean = true,
    size: Dp = 220.dp,
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

    val pulse1 = 0.5f + 0.5f * sin(t * 2.2f)
    val pulse2 = 0.5f + 0.5f * sin(t * 2.2f + (PI / 2).toFloat())
    val pulse3 = 0.5f + 0.5f * sin(t * 2.2f + PI.toFloat())
    val pulses = listOf(pulse3, pulse2, pulse1)

    Canvas(modifier = Modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val maxRadius = this.size.width / 2

        // 3 concentric rings (border-only)
        pulses.forEachIndexed { i, p ->
            val radius = maxRadius * (0.25f + p * 0.25f) // 25%→50% scale
            val strokePx = (2f - i * 0.5f).dp.toPx().coerceAtLeast(0.5f)
            drawCircle(
                color = accent.copy(alpha = (1f - p) * 0.7f),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx),
            )
        }

        // Core solid disk (38% of size) with pulse-driven glow
        val coreRadius = this.size.width * 0.19f * (0.9f + pulse1 * 0.15f)
        val glow = 20f + pulse1 * 60f
        // Simulated glow: soft outer ring drawn first
        drawCircle(
            color = accent.copy(alpha = 0.25f),
            radius = coreRadius + glow.dp.toPx() * 0.25f,
            center = center,
        )
        drawCircle(
            color = accent,
            radius = coreRadius,
            center = center,
        )
    }
}

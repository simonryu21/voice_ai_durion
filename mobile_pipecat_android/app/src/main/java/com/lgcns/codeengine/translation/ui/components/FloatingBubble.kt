package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Floating Pipecat bubble (FAB) — a vermilion circle with the Pipecat
 * wordmark glyph, a breathing halo, a pulsing outer ring, and a small
 * white "active mic" badge in the top-right.
 */
@Composable
fun FloatingBubble(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    showBadge: Boolean = true,
    contentDescription: String = "Open Pipecat",
    onClick: () -> Unit = {},
) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (isActive) {
            t = (System.nanoTime() - start) / 1_000_000_000f
            delay(16.milliseconds)
        }
    }
    val pulse = 0.5f + 0.5f * sin(t * 2.2f)

    Box(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
    ) {
        // Halo: blurred vermilion disc, scaled with pulse
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(1f + pulse * 0.35f)
                .blur(1.dp)
                .background(
                    Pipecat.Vermilion.copy(alpha = 0.20f + pulse * 0.15f),
                    CircleShape,
                ),
        )

        // Soft pulse ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringWidth = (2f + pulse * 6f).dp.toPx()
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val radius = this.size.minDimension / 2
            drawCircle(
                color = Pipecat.Vermilion.copy(alpha = 0.20f),
                radius = radius + ringWidth / 2,
                center = center,
                style = Stroke(width = ringWidth),
            )
        }

        // Core vermilion disc with wordmark glyph
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(Pipecat.Vermilion, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val glyphSize = (size.value * 0.46f).dp
            Canvas(modifier = Modifier.size(glyphSize)) {
                val s = this.size.width
                drawRect(
                    color = Color.White,
                    topLeft = Offset(s * 5f / 22f, s * 5f / 22f),
                    size = Size(s * 4f / 22f, s * 12f / 22f),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(s * 13f / 22f, s * 5f / 22f),
                    size = Size(s * 4f / 22f, s * 6f / 22f),
                )
            }
        }

        // Active mic badge — top-right white disc with a pulsing vermilion dot
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(0.85f + pulse * 0.3f)
                        .background(
                            Pipecat.Vermilion.copy(alpha = 0.7f + pulse * 0.3f),
                            CircleShape,
                        ),
                )
            }
        }
    }
}

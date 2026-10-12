package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * System-overlay-style floating bar: Wordmark + mini animated bars +
 * "listening" label + Stop. Soft accent halo + 26dp pill radius matches
 * the handoff `OverlayBar`.
 */
@Composable
fun OverlayBar(
    palette: PipecatPalette,
    onStopTap: () -> Unit = {},
    label: String = "listening",
    stopContentDescription: String = "Stop",
    isDark: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haloColor = Pipecat.Vermilion.copy(alpha = 0.13f)
    val innerBg = if (isDark) Color(0xEB14100F) else Color(0xEBFFFFFF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(haloColor, RoundedCornerShape(26.dp))
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(innerBg, RoundedCornerShape(22.dp))
                .padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Wordmark(textColor = palette.text, iconOnly = true)
            MiniBars(modifier = Modifier.weight(1f))
            Text(
                text = label,
                color = palette.textDim,
                fontSize = 10.5.sp,
                letterSpacing = 0.42.sp,
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Pipecat.Vermilion, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onStopTap, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = stopContentDescription,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBars(modifier: Modifier = Modifier, bars: Int = 22) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (isActive) {
            t = (System.nanoTime() - start) / 1_000_000_000f
            delay(16.milliseconds)
        }
    }
    Canvas(modifier = modifier.height(28.dp)) {
        val w = size.width
        val h = size.height
        val barWidthPx = 3.dp.toPx()
        val gapPx = 2.5.dp.toPx()
        val totalWidth = bars * barWidthPx + (bars - 1) * gapPx
        val startX = (w - totalWidth) / 2
        for (i in 0 until bars) {
            val phase = i.toFloat() / bars
            val base = 0.2f + 0.55f * abs(sin(phase * 5.5f + t * 4f))
            val env = sin(phase * PI.toFloat()).pow(1.2f)
            val frac = base * env
            val barH = (frac * h).coerceAtLeast(4f)
            val x = startX + i * (barWidthPx + gapPx)
            val y = (h - barH) / 2
            drawRoundRect(
                color = Pipecat.Vermilion.copy(alpha = 0.55f + frac * 0.45f),
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barH),
                cornerRadius = CornerRadius(barWidthPx / 2, barWidthPx / 2),
            )
        }
    }
}

package com.lgcns.codeengine.translation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

enum class PipecatState { Idle, Listening, Thinking, Speaking }

/**
 * English defaults for the four state labels. Translate by passing a
 * [PipecatStateLabels] to [StatusPill].
 */
private val DEFAULT_LABELS: Map<PipecatState, String> = mapOf(
    PipecatState.Idle to "ready",
    PipecatState.Listening to "listening",
    PipecatState.Thinking to "thinking",
    PipecatState.Speaking to "speaking",
)

/**
 * Translated labels for each StatusPill state. Any state you omit falls
 * back to the English default.
 */
data class PipecatStateLabels(
    val idle: String? = null,
    val listening: String? = null,
    val thinking: String? = null,
    val speaking: String? = null,
) {
    fun labelFor(state: PipecatState): String = when (state) {
        PipecatState.Idle -> idle ?: DEFAULT_LABELS.getValue(state)
        PipecatState.Listening -> listening ?: DEFAULT_LABELS.getValue(state)
        PipecatState.Thinking -> thinking ?: DEFAULT_LABELS.getValue(state)
        PipecatState.Speaking -> speaking ?: DEFAULT_LABELS.getValue(state)
    }
}

/**
 * StatusPill — colored dot (green for idle, vermilion + pulse for the
 * active states), label, and optional latency stat in mono font.
 */
@Composable
fun StatusPill(
    state: PipecatState,
    palette: PipecatPalette,
    latencyMs: Int? = null,
    labels: PipecatStateLabels = PipecatStateLabels(),
    modifier: Modifier = Modifier,
) {
    val isActive = state != PipecatState.Idle
    val dotColor = if (isActive) Pipecat.Vermilion else Pipecat.ReadyGreen
    val labelText = labels.labelFor(state)

    val infinite = rememberInfiniteTransition(label = "pillDot")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        modifier = modifier
            .background(palette.chipBg, RoundedCornerShape(999.dp))
            .border(1.dp, palette.chipBorder, RoundedCornerShape(999.dp))
            .padding(PaddingValues(start = 8.dp, top = 4.dp, end = 10.dp, bottom = 4.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .let { if (isActive) it.scale(pulse).alpha(pulseAlpha) else it }
                .background(dotColor, CircleShape),
        )
        Text(
            text = " $labelText",
            color = palette.textDim,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.21.sp,
        )
        if (latencyMs != null) {
            Text(
                text = " · ${latencyMs}ms",
                color = palette.textFaint,
                fontSize = 10.5.sp,
                letterSpacing = 0.21.sp,
            )
        }
    }
}

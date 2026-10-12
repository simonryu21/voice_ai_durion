package com.lgcns.codeengine.translation.ui.components

import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.theme.Pipecat

/**
 * Three-dot typing indicator. Each 4dp dot bounces -3px on a 1.1s cycle,
 * staggered by 0 / 150 / 300 ms. Mirrors `app.jsx#TypingDots`.
 */
@Composable
fun TypingDots(
    color: Color = Pipecat.Vermilion,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "typingDots")
    Row(modifier = modifier.padding(start = 4.dp)) {
        listOf(0, 150, 300).forEachIndexed { i, delayMs ->
            val translateY by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1100
                        0f at 0
                        -3f at 330
                        0f at 660
                        0f at 1100
                    },
                    initialStartOffset = StartOffset(delayMs),
                ),
                label = "translate$i",
            )
            val alpha by infinite.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1100
                        0.4f at 0
                        1f at 330
                        0.4f at 660
                    },
                    initialStartOffset = StartOffset(delayMs),
                ),
                label = "alpha$i",
            )
            Box(
                modifier = Modifier
                    .padding(start = if (i == 0) 0.dp else 3.dp)
                    .size(4.dp)
                    .graphicsLayer {
                        translationY = translateY
                        this.alpha = alpha
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

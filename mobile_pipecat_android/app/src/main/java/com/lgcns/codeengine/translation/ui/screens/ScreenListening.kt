package com.lgcns.codeengine.translation.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.components.AskBar
import com.lgcns.codeengine.translation.ui.components.AskBarState
import com.lgcns.codeengine.translation.ui.components.PipecatAppBar
import com.lgcns.codeengine.translation.ui.components.PipecatState
import com.lgcns.codeengine.translation.ui.components.VoiceVizBars
import com.lgcns.codeengine.translation.ui.components.VoiceVizOrb
import com.lgcns.codeengine.translation.ui.components.rememberPipecatStrings
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

enum class VoiceVizStyle { Bars, Orb }

/**
 * Listening screen — radial accent glow behind a centered VoiceViz, a
 * quoted user utterance, a "microphone active · <model>" chip, and an
 * AskBar in `Listening` mode below.
 */
@Composable
fun ScreenListening(
    palette: PipecatPalette,
    onStopTap: () -> Unit = {},
    quote: String = "How do I deploy this\nto a Raspberry Pi?",
    systemLabel: String? = null,
    vizStyle: VoiceVizStyle = VoiceVizStyle.Bars,
    inputLevel: Float = 0f,
    latencyMs: Int? = 218,
) {
    val strings = rememberPipecatStrings()
    val resolvedSystemLabel = systemLabel ?: strings.listeningSystemLabel
    Column(modifier = Modifier.fillMaxSize()) {
        PipecatAppBar(
            state = PipecatState.Listening,
            palette = palette,
            latencyMs = latencyMs,
            stateLabels = strings.stateLabels,
            historyContentDescription = strings.historyLabel,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Pipecat.Vermilion.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height * 0.55f),
                            radius = size.width,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp),
            ) {
                when (vizStyle) {
                    VoiceVizStyle.Bars ->
                        VoiceVizBars(
                            accent = Pipecat.Vermilion,
                            active = true,
                            size = 240.dp,
                            inputLevel = inputLevel,
                        )
                    VoiceVizStyle.Orb ->
                        VoiceVizOrb(
                            accent = Pipecat.Vermilion,
                            active = true,
                            size = 240.dp,
                        )
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    text = "\"$quote\"",
                    color = palette.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 28.6.sp,
                    letterSpacing = (-0.22).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 280.dp),
                )

                Spacer(Modifier.height(16.dp))

                MicActiveChip(palette = palette, label = resolvedSystemLabel)
            }
        }

        AskBar(
            state = AskBarState.Listening,
            palette = palette,
            placeholder = strings.askPlaceholder,
            listeningLabel = strings.askListeningLabel,
            attachContentDescription = strings.askAttachLabel,
            micContentDescription = strings.askMicLabel,
            stopContentDescription = strings.askStopLabel,
            waveContentDescription = strings.askWaveLabel,
            onMicTap = onStopTap,
        )
    }
}

@Composable
private fun MicActiveChip(palette: PipecatPalette, label: String) {
    val infinite = rememberInfiniteTransition(label = "micChip")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotScale",
    )
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(palette.chipBg, RoundedCornerShape(999.dp))
            .border(1.dp, palette.chipBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(scale)
                .alpha(alpha)
                .background(Pipecat.Vermilion, CircleShape),
        )
        Text(
            text = label,
            color = palette.textDim,
            fontSize = 10.5.sp,
            letterSpacing = 0.42.sp,
        )
    }
}

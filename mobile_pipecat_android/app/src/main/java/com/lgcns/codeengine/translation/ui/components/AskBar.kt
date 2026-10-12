package com.lgcns.codeengine.translation.ui.components

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

enum class AskBarState { Idle, Listening, Disabled }

/**
 * Bottom AskBar — input pill with attach (+), placeholder/listening label,
 * a Mic/Stop toggle, and a trailing Wave (voice mode) button. Mirrors
 * `app.jsx#AskBar`.
 */
@Composable
fun AskBar(
    state: AskBarState,
    palette: PipecatPalette,
    onMicTap: () -> Unit,
    onWaveTap: (() -> Unit)? = null,
    onAttachTap: (() -> Unit)? = null,
    placeholder: String = "Ask anything",
    listeningLabel: String = "listening",
    attachContentDescription: String = "Attach",
    micContentDescription: String = "Start listening",
    stopContentDescription: String = "Stop listening",
    waveContentDescription: String = "Voice mode",
    /**
     * Controlled text value. When non-null and not listening, the
     * placeholder slot becomes an editable [BasicTextField]. Leave null for
     * visual-only AskBar (current behavior).
     */
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    /** Fired when the user presses the IME done/send key. */
    onSubmit: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listening = state == AskBarState.Listening
    val disabled = state == AskBarState.Disabled
    val outerOpacity = if (disabled) 0.5f else 1f

    Row(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .alpha(outerOpacity)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.inputBg, RoundedCornerShape(28.dp))
            .border(
                width = 1.5.dp,
                color = if (listening) Pipecat.Vermilion else palette.border,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(PaddingValues(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp))
            .height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = { onAttachTap?.invoke() },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = attachContentDescription,
                tint = palette.textDim,
                modifier = Modifier.size(20.dp),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                listening -> {
                    Text(
                        text = listeningLabel,
                        color = palette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    MicCaret()
                }
                value != null -> {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = palette.textFaint,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { onValueChange?.invoke(it) },
                            enabled = !disabled,
                            singleLine = true,
                            cursorBrush = SolidColor(Pipecat.Vermilion),
                            textStyle = TextStyle(
                                color = palette.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { onSubmit?.invoke(value) },
                                onDone = { onSubmit?.invoke(value) },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> {
                    Text(
                        text = placeholder,
                        color = palette.textFaint,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }

        // Mic / Stop toggle (38dp, accent-filled when listening)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (listening) Pipecat.Vermilion else palette.chipBg),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onMicTap,
                enabled = !disabled,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (listening) stopContentDescription else micContentDescription,
                    tint = if (listening) Color.White else palette.text,
                    modifier = Modifier.size(if (listening) 16.dp else 18.dp),
                )
            }
        }

        // Wave / voice-mode toggle
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(palette.chipBg),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = { onWaveTap?.invoke() },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = waveContentDescription,
                    tint = palette.text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MicCaret() {
    val infinite = rememberInfiniteTransition(label = "caret")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )
    Box(
        modifier = Modifier
            .padding(start = 2.dp)
            .width(2.dp)
            .height(14.dp)
            .alpha(alpha)
            .background(Pipecat.Vermilion),
    )
}

package com.lgcns.codeengine.translation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.domain.model.HeadsetState
import com.lgcns.codeengine.translation.domain.model.InterpretationState
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

/**
 * Single-line quote caption — mirrors the handoff Listening screen quote
 * (e.g. "How do I deploy this to a Raspberry Pi?"). Resolves the appropriate
 * fallback text from state when no assistant transcript is available.
 */
@Composable
fun CaptionLine(
    state: InterpretationState,
    headsetState: HeadsetState,
    hasPermissions: Boolean,
    palette: PipecatPalette,
    assistantText: String? = null,
    modifier: Modifier = Modifier,
) {
    val text = when {
        !hasPermissions -> "마이크 권한이 필요합니다"
        headsetState !is HeadsetState.WiredConnected &&
            headsetState !is HeadsetState.BluetoothConnected -> "이어폰을 연결해 주세요"
        state is InterpretationState.Error -> "오류가 발생했어요. 다시 시도해 주세요."
        state is InterpretationState.Connecting -> "연결 중…"
        state is InterpretationState.Active -> assistantText?.takeIf { it.isNotBlank() }
            ?: "듣고 있어요"
        state is InterpretationState.Idle -> "탭하여 시작"
        else -> ""
    }

    AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn(tween(Pipecat.MOTION_BASE_MS)) togetherWith
                fadeOut(tween(Pipecat.MOTION_BASE_MS))
        },
        label = "caption",
        modifier = modifier,
    ) { current ->
        Text(
            text = current,
            color = palette.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.22).sp, // ~ -0.01em at 22sp
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

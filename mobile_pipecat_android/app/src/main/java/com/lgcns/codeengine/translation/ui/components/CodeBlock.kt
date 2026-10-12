package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.Pipecat

enum class CodeTone { Default, Accent, Muted }

data class CodeSegment(val text: String, val tone: CodeTone = CodeTone.Default)
data class CodeLine(val segments: List<CodeSegment>) {
    companion object {
        fun plain(text: String): CodeLine = CodeLine(listOf(CodeSegment(text)))
    }
}

private fun colorFor(tone: CodeTone): Color = when (tone) {
    CodeTone.Accent -> Pipecat.Vermilion
    CodeTone.Muted -> Color(0xFF99AAAA)
    CodeTone.Default -> Color(0xFFE5DDD8)
}

/**
 * Inline code block matching the handoff Conversation screen — black bg,
 * Geist Mono 11.5sp, 12dp radius, simple tone-based segment tinting.
 */
@Composable
fun CodeBlock(
    lines: List<CodeLine>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E0B0A))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        lines.forEach { line ->
            Text(
                text = buildAnnotatedString {
                    line.segments.forEach { seg ->
                        withStyle(SpanStyle(color = colorFor(seg.tone))) {
                            append(seg.text)
                        }
                    }
                },
                fontSize = 11.5.sp,
                lineHeight = 17.825.sp,
            )
        }
    }
}

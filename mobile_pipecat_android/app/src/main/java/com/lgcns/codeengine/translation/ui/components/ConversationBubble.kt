package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

enum class BubbleRole { User, Assistant }

/**
 * Conversation bubble matching the handoff `app.jsx#Conversation`.
 * - User: right-aligned, vermilion fill, radius (18, 18, 4, 18).
 * - Assistant: left-aligned, surface fill + border, radius (18, 18, 18, 4),
 *   with a sparkle + mono meta line above.
 */
@Composable
fun ConversationBubble(
    role: BubbleRole,
    palette: PipecatPalette,
    modifier: Modifier = Modifier,
    meta: String? = null,
    content: @Composable () -> Unit,
) {
    val isUser = role == BubbleRole.User
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser && meta != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                SparkleIcon(color = Pipecat.Vermilion)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = meta,
                    fontSize = 10.sp,
                    color = palette.textDim,
                    letterSpacing = 0.4.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .widthFraction(if (isUser) 0.82f else 0.88f)
                .clip(shape)
                .background(if (isUser) Pipecat.Vermilion else palette.surface, shape)
                .then(
                    if (isUser) Modifier else Modifier.border(1.dp, palette.border, shape),
                )
                .padding(horizontal = 14.dp, vertical = if (isUser) 10.dp else 12.dp),
        ) {
            CompositionLocalBubbleColor(
                color = if (isUser) Color.White else palette.text,
                content = content,
            )
        }

        if (isUser && meta != null) {
            Spacer(Modifier.size(0.dp))
            Text(
                text = meta,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 10.sp,
                color = palette.textFaint,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

@Composable
private fun CompositionLocalBubbleColor(color: Color, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides color,
    ) {
        content()
    }
}

/** Constrains a child to a fraction of the parent's max width. */
private fun Modifier.widthFraction(fraction: Float): Modifier =
    this.then(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)))

@Composable
private fun SparkleIcon(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
        drawSparkle(color = color)
    }
}

private fun DrawScope.drawSparkle(color: Color) {
    // 4-pointed sparkle (matches the handoff path).
    val path = Path().apply {
        val w = size.width
        val h = size.height
        moveTo(w * 0.5f, h * 0.1f)
        lineTo(w * 0.58f, h * 0.36f)
        lineTo(w * 0.84f, h * 0.44f)
        lineTo(w * 0.58f, h * 0.52f)
        lineTo(w * 0.5f, h * 0.78f)
        lineTo(w * 0.42f, h * 0.52f)
        lineTo(w * 0.16f, h * 0.44f)
        lineTo(w * 0.42f, h * 0.36f)
        close()
    }
    drawPath(path = path, color = color)
}

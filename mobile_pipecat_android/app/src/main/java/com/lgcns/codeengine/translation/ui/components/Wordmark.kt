package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.Pipecat

/**
 * Pipecat wordmark — vermilion rounded tile (rx=5) + two white bars (long
 * + short) + "pipecat" text (Geist 600). Mirrors `app.jsx#Wordmark`.
 */
@Composable
fun Wordmark(
    textColor: Color,
    accent: Color = Pipecat.Vermilion,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val px = size.width
            drawRoundRect(
                color = accent,
                topLeft = Offset.Zero,
                size = Size(px, px),
                cornerRadius = CornerRadius(px * 5f / 22f),
            )
            // long bar (5..17 → 12 tall, 4 wide)
            drawRect(
                color = Color.White,
                topLeft = Offset(px * 5f / 22f, px * 5f / 22f),
                size = Size(px * 4f / 22f, px * 12f / 22f),
            )
            // short bar (5..11 → 6 tall, 4 wide)
            drawRect(
                color = Color.White,
                topLeft = Offset(px * 13f / 22f, px * 5f / 22f),
                size = Size(px * 4f / 22f, px * 6f / 22f),
            )
        }
        if (!iconOnly) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "pipecat",
                color = textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.17).sp, // ~ -0.01em at 17sp
            )
        }
    }
}

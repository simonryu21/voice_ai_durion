package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

/**
 * Top AppBar — Pipecat wordmark on the left, StatusPill + trailing history
 * IconButton on the right, with a 1dp bottom border.
 */
@Composable
fun PipecatAppBar(
    state: PipecatState,
    palette: PipecatPalette,
    latencyMs: Int? = null,
    stateLabels: PipecatStateLabels = PipecatStateLabels(),
    historyContentDescription: String = "History",
    onHistoryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = palette.border,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 18.dp, end = 4.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Wordmark(textColor = palette.text)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                state = state,
                palette = palette,
                latencyMs = latencyMs,
                labels = stateLabels,
            )
            IconButton(
                onClick = { onHistoryClick?.invoke() },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .background(palette.chipBg, RoundedCornerShape(10.dp)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = historyContentDescription,
                    tint = palette.text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

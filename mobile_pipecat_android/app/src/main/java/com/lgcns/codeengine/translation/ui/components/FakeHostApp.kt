package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

private data class Todo(val n: String, val text: String, val done: Boolean)

private val FAKE_NOTES = listOf(
    Todo("1.", "Flash latest 64-bit Pi OS to SD card", true),
    Todo("2.", "Enable SSH + I²S audio in raspi-config", true),
    Todo("3.", "Install pipecat-ai + uvloop", true),
    Todo("4.", "Mount mic array, run device probe", false),
    Todo("5.", "Set systemd unit, point at WebSocket URL", false),
    Todo("6.", "Test wake-word in noisy room", false),
)

/**
 * Generic "Notes"-style host app placeholder used behind Overlay/Bubble
 * screens. Not a recreation of any branded app.
 */
@Composable
fun FakeHostApp(palette: PipecatPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgBase),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = palette.border,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color(0xFF2E7D6B), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "N",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Notes",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
            }
            Text("+", fontSize = 22.sp, color = palette.textDim)
        }

        Column(modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp)) {
            Text(
                text = "FRI · 09:31",
                fontSize = 10.sp,
                color = palette.textFaint,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pi deployment checklist",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                letterSpacing = (-0.22).sp,
                lineHeight = 26.4.sp,
            )

            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FAKE_NOTES.forEach { todo ->
                    Row {
                        Text(
                            text = todo.n,
                            color = palette.textFaint,
                            fontSize = 14.sp,
                            modifier = Modifier.width(18.dp),
                        )
                        Text(
                            text = todo.text,
                            modifier = Modifier.weight(1f),
                            color = if (todo.done) palette.textFaint else palette.text,
                            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                            fontSize = 14.sp,
                            lineHeight = 21.7.sp,
                        )
                    }
                }
            }
        }
    }
}

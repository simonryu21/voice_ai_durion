package com.lgcns.codeengine.translation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.components.FakeHostApp
import com.lgcns.codeengine.translation.ui.components.FloatingBubble
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

/**
 * Bubble screen — FakeHostApp filling the area with a FloatingBubble
 * pinned bottom-left. Visually demonstrates the minimized floating entry
 * point on top of another app.
 */
@Composable
fun ScreenBubble(
    palette: PipecatPalette,
    onBubbleTap: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        FakeHostApp(palette = palette)
        FloatingBubble(
            onClick = onBubbleTap,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp),
        )
    }
}

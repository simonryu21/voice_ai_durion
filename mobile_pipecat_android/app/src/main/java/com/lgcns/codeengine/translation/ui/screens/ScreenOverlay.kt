package com.lgcns.codeengine.translation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.components.FakeHostApp
import com.lgcns.codeengine.translation.ui.components.OverlayBar
import com.lgcns.codeengine.translation.ui.components.rememberPipecatStrings
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

/**
 * Overlay screen — FakeHostApp filling the area with a system-overlay-style
 * OverlayBar pinned to the bottom. Visually demonstrates what the live
 * overlay looks like sitting on top of another app.
 */
@Composable
fun ScreenOverlay(
    palette: PipecatPalette,
    onStopTap: () -> Unit = {},
    isDark: Boolean = true,
) {
    val strings = rememberPipecatStrings()
    Box(modifier = Modifier.fillMaxSize()) {
        FakeHostApp(palette = palette)
        OverlayBar(
            palette = palette,
            onStopTap = onStopTap,
            label = strings.overlayListeningLabel,
            stopContentDescription = strings.overlayStopLabel,
            isDark = isDark,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

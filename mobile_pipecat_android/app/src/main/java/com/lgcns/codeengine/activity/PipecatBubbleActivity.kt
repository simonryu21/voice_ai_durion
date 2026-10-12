package com.lgcns.codeengine.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lgcns.codeengine.translation.ui.components.OverlayBar
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

/**
 * Expanded content of the Pipecat system Bubble.
 *
 * Declared `allowEmbedded` / `resizeableActivity` / `documentLaunchMode="always"`
 * in the manifest so the system can host it inside a bubble window. Renders
 * the [OverlayBar] listening surface; the Stop button collapses the bubble.
 */
class PipecatBubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = isSystemInDarkTheme()
            val palette = remember(isDark) { PipecatPalette.forTheme(isDark) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgBase)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                OverlayBar(
                    palette = palette,
                    isDark = isDark,
                    onStopTap = { finish() },
                )
            }
        }
    }
}

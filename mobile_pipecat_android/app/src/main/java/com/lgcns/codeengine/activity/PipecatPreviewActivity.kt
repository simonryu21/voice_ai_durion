package com.lgcns.codeengine.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.components.rememberPipecatStrings
import com.lgcns.codeengine.translation.ui.screens.ScreenBubble
import com.lgcns.codeengine.translation.ui.screens.ScreenConversation
import com.lgcns.codeengine.translation.ui.screens.ScreenIdle
import com.lgcns.codeengine.translation.ui.screens.ScreenListening
import com.lgcns.codeengine.translation.ui.screens.ScreenOverlay
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

private enum class PreviewScreen(val label: String) {
    Idle("01 · Idle"),
    Listening("02 · Listening"),
    Conversation("03 · Conversation"),
    Overlay("04 · Overlay"),
    Bubble("05 · Bubble"),
}

class PipecatPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PreviewRoot(onClose = { finish() })
        }
    }

    companion object {
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, PipecatPreviewActivity::class.java))
        }
    }
}

@Composable
private fun PreviewRoot(onClose: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val palette = remember(isDark) { PipecatPalette.forTheme(isDark) }
    val strings = rememberPipecatStrings()
    var screen by remember { mutableStateOf(PreviewScreen.Idle) }

    val canvas = if (isDark) Pipecat.CanvasDark else Pipecat.CanvasLight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Tabs(
                active = screen,
                palette = palette,
                closeLabel = strings.previewClose,
                onPick = { screen = it },
                onClose = onClose,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(palette.bgBase),
                ) {
                    when (screen) {
                        PreviewScreen.Idle -> ScreenIdle(palette = palette)
                        PreviewScreen.Listening -> ScreenListening(palette = palette)
                        PreviewScreen.Conversation -> ScreenConversation(palette = palette)
                        PreviewScreen.Overlay -> ScreenOverlay(palette = palette, isDark = isDark)
                        PreviewScreen.Bubble -> ScreenBubble(palette = palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tabs(
    active: PreviewScreen,
    palette: PipecatPalette,
    closeLabel: String,
    onPick: (PreviewScreen) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(palette.chipBg, RoundedCornerShape(999.dp))
            .padding(PaddingValues(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PreviewScreen.entries.forEach { p ->
            TabChip(
                label = p.label,
                isActive = p == active,
                palette = palette,
                onClick = { onPick(p) },
            )
        }
        Box(modifier = Modifier.weight(1f))
        TabChip(
            label = closeLabel,
            isActive = false,
            palette = palette,
            onClick = onClose,
        )
    }
}

@Composable
private fun TabChip(
    label: String,
    isActive: Boolean,
    palette: PipecatPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (isActive) Pipecat.Vermilion else Color.Transparent,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            letterSpacing = 0.44.sp,
            color = if (isActive) Color.White else palette.textDim,
        )
    }
}

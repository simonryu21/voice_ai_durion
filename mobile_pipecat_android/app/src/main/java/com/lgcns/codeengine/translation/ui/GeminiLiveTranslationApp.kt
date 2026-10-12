package com.lgcns.codeengine.translation.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.domain.model.InterpretationState
import com.lgcns.codeengine.translation.domain.model.Language
import com.lgcns.codeengine.translation.presentation.GeminiLiveTranslationUiState
import com.lgcns.codeengine.translation.ui.components.AskBar
import com.lgcns.codeengine.translation.ui.components.AskBarState
import com.lgcns.codeengine.translation.ui.components.CaptionLine
import com.lgcns.codeengine.translation.ui.components.LanguagePickerContent
import com.lgcns.codeengine.translation.ui.components.PipecatAppBar
import com.lgcns.codeengine.translation.ui.components.PipecatState
import com.lgcns.codeengine.translation.ui.components.VoiceVizBars
import com.lgcns.codeengine.translation.ui.components.rememberPipecatStrings
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette
import kotlinx.coroutines.launch

/**
 * Pipecat Voice UI · option B — Listening 화면 단일.
 *
 * AppBar(상단) → 라디얼 글로우 + VoiceVizBars + 인용 CaptionLine + 시스템 칩
 * → AskBar(하단) 의 3단 레이아웃.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiLiveTranslationApp(
    uiState: GeminiLiveTranslationUiState,
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onTargetLanguageChange: (Language) -> Unit,
    onCloseClick: () -> Unit,
    onSendText: (String) -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val palette = remember(isDarkTheme) { PipecatPalette.forTheme(isDarkTheme) }
    val strings = rememberPipecatStrings()

    var showLanguagePicker by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) onPermissionsGranted() else onPermissionsDenied()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val pipecatState = when (uiState.interpretationState) {
        is InterpretationState.Idle -> PipecatState.Idle
        is InterpretationState.Connecting -> PipecatState.Thinking
        is InterpretationState.Active -> PipecatState.Listening
        is InterpretationState.HeadsetRequired,
        is InterpretationState.Error -> PipecatState.Idle
    }

    val askBarState = when {
        !uiState.hasPermissions -> AskBarState.Disabled
        uiState.interpretationState is InterpretationState.Connecting -> AskBarState.Disabled
        uiState.interpretationState is InterpretationState.Active -> AskBarState.Listening
        else -> AskBarState.Idle
    }

    val isActiveSession = uiState.interpretationState is InterpretationState.Active

    // 세션 토글 — 중앙 영역 탭("탭하여 시작")과 AskBar Mic 버튼이 공유한다.
    val toggleSession = {
        when (uiState.interpretationState) {
            is InterpretationState.Active,
            is InterpretationState.Connecting -> onStopClick()
            else -> onStartClick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgBase),
    ) {
        // ── Top AppBar ───────────────────────────────────────
        Box(
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        ) {
            PipecatAppBar(
                state = pipecatState,
                palette = palette,
                latencyMs = uiState.latencyMs,
                stateLabels = strings.stateLabels,
                historyContentDescription = strings.historyLabel,
                onHistoryClick = onCloseClick,
            )
        }

        // ── Center — radial glow + viz + quote + system chip ─
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = toggleSession,
                )
                .drawBehind {
                    if (isActiveSession) {
                        val centerOffset = Offset(size.width / 2f, size.height * 0.55f)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Pipecat.Vermilion.copy(alpha = 0.15f),
                                    Color.Transparent,
                                ),
                                center = centerOffset,
                                radius = size.width,
                            ),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VoiceVizBars(
                    accent = Pipecat.Vermilion,
                    active = isActiveSession ||
                        uiState.interpretationState is InterpretationState.Connecting,
                    inputLevel = uiState.inputLevel,
                )
                Spacer(Modifier.height(36.dp))
                CaptionLine(
                    state = uiState.interpretationState,
                    headsetState = uiState.headsetState,
                    hasPermissions = uiState.hasPermissions,
                    palette = palette,
                )
                Spacer(Modifier.height(16.dp))
                SystemChip(
                    palette = palette,
                    active = isActiveSession,
                    text = "translate to · ${uiState.targetLanguage.name}",
                )
            }
        }

        // ── Bottom AskBar ────────────────────────────────────
        AskBar(
            state = askBarState,
            palette = palette,
            placeholder = strings.askPlaceholder,
            listeningLabel = strings.askListeningLabel,
            attachContentDescription = strings.askAttachLabel,
            micContentDescription = strings.askMicLabel,
            stopContentDescription = strings.askStopLabel,
            waveContentDescription = strings.askWaveLabel,
            value = textInput,
            onValueChange = { textInput = it },
            onSubmit = { submitted ->
                onSendText(submitted)
                textInput = ""
            },
            onMicTap = toggleSession,
            onWaveTap = { showLanguagePicker = true },
        )
    }

    if (showLanguagePicker) {
        ModalBottomSheet(
            onDismissRequest = { showLanguagePicker = false },
            sheetState = sheetState,
            containerColor = palette.surfaceElev,
        ) {
            LanguagePickerContent(
                selectedLanguage = uiState.targetLanguage,
                onLanguageSelected = { language ->
                    onTargetLanguageChange(language)
                    scope.launch {
                        sheetState.hide()
                        showLanguagePicker = false
                    }
                },
                onSurfaceColor = palette.text,
                subtitleColor = palette.textDim,
                isDarkTheme = isDarkTheme,
            )
        }
    }
}

@Composable
private fun SystemChip(
    palette: PipecatPalette,
    active: Boolean,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(palette.chipBg, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (active) Pipecat.Vermilion else palette.textFaint,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = palette.textDim,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.21.sp,
        )
    }
}

package com.lgcns.codeengine.translation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.components.AskBar
import com.lgcns.codeengine.translation.ui.components.AskBarState
import com.lgcns.codeengine.translation.ui.components.PipecatAppBar
import com.lgcns.codeengine.translation.ui.components.PipecatState
import com.lgcns.codeengine.translation.ui.components.SuggestionCard
import com.lgcns.codeengine.translation.ui.components.SuggestionGrid
import com.lgcns.codeengine.translation.ui.components.greetingForHour
import com.lgcns.codeengine.translation.ui.components.rememberPipecatStrings
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val MORNING_SUGGESTIONS = listOf(
    SuggestionCard(tag = "plan", title = "Draft today's\npriorities"),
    SuggestionCard(tag = "voice", title = "Practice my\nstandup intro"),
    SuggestionCard(tag = "code", title = "Review last night's\nPR comments"),
    SuggestionCard(tag = "data", title = "Summarize my\nunread inbox"),
)

private val AFTERNOON_SUGGESTIONS = listOf(
    SuggestionCard(tag = "code", title = "Explain this Python\ntraceback"),
    SuggestionCard(tag = "plan", title = "Draft a 5-step\nlaunch checklist"),
    SuggestionCard(tag = "data", title = "Summarize the\nattached CSV"),
    SuggestionCard(tag = "idea", title = "Brainstorm names\nfor this feature"),
)

private val EVENING_SUGGESTIONS = listOf(
    SuggestionCard(tag = "recap", title = "Summarize what\nI shipped today"),
    SuggestionCard(tag = "plan", title = "Draft tomorrow's\nstandup notes"),
    SuggestionCard(tag = "voice", title = "Rehearse my\ndemo script"),
    SuggestionCard(tag = "idea", title = "Suggest a short\nevening read"),
)

private val WEEKEND_SUGGESTIONS = listOf(
    SuggestionCard(tag = "idea", title = "Plan a weekend\nside project"),
    SuggestionCard(tag = "voice", title = "Practice a\nlanguage lesson"),
    SuggestionCard(tag = "plan", title = "Outline next\nweek's goals"),
    SuggestionCard(tag = "data", title = "Summarize a\nlong article"),
)

/** Suggestion set chosen by weekday/weekend and time of day. */
private fun suggestionsForContext(
    date: LocalDate = LocalDate.now(),
    time: LocalTime = LocalTime.now(),
): List<SuggestionCard> {
    if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
        return WEEKEND_SUGGESTIONS
    }
    return when {
        time.hour < 12 -> MORNING_SUGGESTIONS
        time.hour < 18 -> AFTERNOON_SUGGESTIONS
        else -> EVENING_SUGGESTIONS
    }
}

private fun sessionLabel(date: LocalDate, prefix: String): String =
    "$prefix · ${date.toString().replace("-", "·")}"

/**
 * Idle screen — AppBar, greeting with accent underline on "today", SESSION
 * label, "Suggestions · see all →" header, 2×2 SuggestionGrid, and AskBar.
 * Mirrors the handoff `app.jsx#ScreenIdle` layout 1:1.
 */
@Composable
fun ScreenIdle(
    palette: PipecatPalette,
    onMicTap: () -> Unit = {},
    onSuggestionPick: (SuggestionCard) -> Unit = {},
    suggestions: List<SuggestionCard> = suggestionsForContext(),
    askBarState: AskBarState = AskBarState.Idle,
    latencyMs: Int? = 142,
) {
    val strings = rememberPipecatStrings()
    val hour = remember { LocalTime.now().hour }
    val today = remember { LocalDate.now() }
    val greeting = strings.greetingForHour(hour)

    Column(modifier = Modifier.fillMaxSize()) {
        PipecatAppBar(
            state = PipecatState.Idle,
            palette = palette,
            latencyMs = latencyMs,
            stateLabels = strings.stateLabels,
            historyContentDescription = strings.historyLabel,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 18.dp),
        ) {
            // Text content with 18dp horizontal padding
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                val headlineSuffix = strings.idleHeadlineSuffix
                val highlight = strings.idleHighlight
                val highlightIndex = headlineSuffix.indexOf(highlight)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.textDim)) {
                            append(greeting)
                        }
                        append("\n")
                        if (highlightIndex < 0) {
                            append(headlineSuffix)
                        } else {
                            append(headlineSuffix.substring(0, highlightIndex))
                            withStyle(
                                SpanStyle(
                                    background = Pipecat.Vermilion.copy(alpha = 0.20f),
                                ),
                            ) {
                                append(highlight)
                            }
                            append(headlineSuffix.substring(highlightIndex + highlight.length))
                        }
                    },
                    fontSize = 28.sp,
                    lineHeight = 32.2.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.text,
                    letterSpacing = (-0.56).sp,
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = sessionLabel(today, strings.idleSessionPrefix),
                    fontSize = 10.5.sp,
                    color = palette.textFaint,
                    letterSpacing = 0.42.sp,
                )

                Spacer(Modifier.height(22.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(
                        text = strings.idleSuggestionsLabel.uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textFaint,
                        letterSpacing = 0.84.sp,
                    )
                    Text(
                        text = strings.idleSeeAll,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Pipecat.Vermilion,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            // SuggestionGrid uses its own 18dp internal horizontal padding
            SuggestionGrid(
                cards = suggestions,
                palette = palette,
                onSelect = onSuggestionPick,
            )
        }

        AskBar(
            state = askBarState,
            palette = palette,
            placeholder = strings.askPlaceholder,
            listeningLabel = strings.askListeningLabel,
            attachContentDescription = strings.askAttachLabel,
            micContentDescription = strings.askMicLabel,
            stopContentDescription = strings.askStopLabel,
            waveContentDescription = strings.askWaveLabel,
            onMicTap = onMicTap,
        )
    }
}

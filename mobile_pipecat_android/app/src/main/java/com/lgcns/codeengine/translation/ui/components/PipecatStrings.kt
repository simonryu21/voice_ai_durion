package com.lgcns.codeengine.translation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lgcns.codeengine.R

/**
 * Pipecat Voice UI string set, resolved from `res/values{,-en}/strings.xml`.
 * Use this when wiring screens/components in real activities so the labels
 * follow the system locale.
 */
data class PipecatStrings(
    val stateLabels: PipecatStateLabels,
    val historyLabel: String,
    val askPlaceholder: String,
    val askListeningLabel: String,
    val askAttachLabel: String,
    val askMicLabel: String,
    val askStopLabel: String,
    val askWaveLabel: String,
    val overlayListeningLabel: String,
    val overlayStopLabel: String,
    val previewClose: String,
    val idleGreetingMorning: String,
    val idleGreetingAfternoon: String,
    val idleGreetingEvening: String,
    val idleHeadlineSuffix: String,
    val idleHighlight: String,
    val idleSuggestionsLabel: String,
    val idleSeeAll: String,
    val idleSessionPrefix: String,
    val conversationReplyPlaceholder: String,
    val conversationTypingLabel: String,
    val listeningSystemLabel: String,
)

/**
 * Pick the appropriate greeting line for the current local hour.
 */
fun PipecatStrings.greetingForHour(hour: Int): String = when {
    hour < 12 -> idleGreetingMorning
    hour < 18 -> idleGreetingAfternoon
    else -> idleGreetingEvening
}

/** Resolve the Pipecat label set from the current locale. */
@Composable
fun rememberPipecatStrings(): PipecatStrings = PipecatStrings(
    stateLabels = PipecatStateLabels(
        idle = stringResource(R.string.pipecat_state_ready),
        listening = stringResource(R.string.pipecat_state_listening),
        thinking = stringResource(R.string.pipecat_state_thinking),
        speaking = stringResource(R.string.pipecat_state_speaking),
    ),
    historyLabel = stringResource(R.string.pipecat_history_label),
    askPlaceholder = stringResource(R.string.pipecat_ask_placeholder),
    askListeningLabel = stringResource(R.string.pipecat_ask_listening_label),
    askAttachLabel = stringResource(R.string.pipecat_ask_attach_label),
    askMicLabel = stringResource(R.string.pipecat_ask_mic_label),
    askStopLabel = stringResource(R.string.pipecat_ask_stop_label),
    askWaveLabel = stringResource(R.string.pipecat_ask_wave_label),
    overlayListeningLabel = stringResource(R.string.pipecat_overlay_listening_label),
    overlayStopLabel = stringResource(R.string.pipecat_overlay_stop_label),
    previewClose = stringResource(R.string.pipecat_preview_close),
    idleGreetingMorning = stringResource(R.string.pipecat_idle_greeting_morning),
    idleGreetingAfternoon = stringResource(R.string.pipecat_idle_greeting_afternoon),
    idleGreetingEvening = stringResource(R.string.pipecat_idle_greeting_evening),
    idleHeadlineSuffix = stringResource(R.string.pipecat_idle_headline_suffix),
    idleHighlight = stringResource(R.string.pipecat_idle_highlight),
    idleSuggestionsLabel = stringResource(R.string.pipecat_idle_suggestions_label),
    idleSeeAll = stringResource(R.string.pipecat_idle_see_all),
    idleSessionPrefix = stringResource(R.string.pipecat_idle_session_prefix),
    conversationReplyPlaceholder = stringResource(R.string.pipecat_conversation_reply_placeholder),
    conversationTypingLabel = stringResource(R.string.pipecat_conversation_typing_label),
    listeningSystemLabel = stringResource(R.string.pipecat_listening_system_label),
)

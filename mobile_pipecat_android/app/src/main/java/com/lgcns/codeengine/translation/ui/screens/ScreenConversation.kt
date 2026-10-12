package com.lgcns.codeengine.translation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.components.AskBar
import com.lgcns.codeengine.translation.ui.components.AskBarState
import com.lgcns.codeengine.translation.ui.components.BubbleRole
import com.lgcns.codeengine.translation.ui.components.CodeBlock
import com.lgcns.codeengine.translation.ui.components.CodeLine
import com.lgcns.codeengine.translation.ui.components.CodeSegment
import com.lgcns.codeengine.translation.ui.components.CodeTone
import com.lgcns.codeengine.translation.ui.components.ConversationBubble
import com.lgcns.codeengine.translation.ui.components.PipecatAppBar
import com.lgcns.codeengine.translation.ui.components.PipecatState
import com.lgcns.codeengine.translation.ui.components.TypingDots
import com.lgcns.codeengine.translation.ui.components.rememberPipecatStrings
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

sealed interface ConversationTurn {
    data class User(val text: String, val meta: String? = null) : ConversationTurn
    data class Assistant(
        val text: String,
        val meta: String? = null,
        val code: List<CodeLine>? = null,
        val followUps: List<String> = emptyList(),
    ) : ConversationTurn
    data object Streaming : ConversationTurn
}

private val DEFAULT_TURNS = listOf(
    ConversationTurn.User(
        text = "What's a good way to debounce a websocket reconnect loop in Python?",
        meta = "you · 09:32",
    ),
    ConversationTurn.Assistant(
        text = "Use exponential backoff with jitter. Start at 1s, double on each failure, cap at ~30s, and add random jitter so reconnecting clients don't thunder the server in lockstep.",
        meta = "pipecat · gemma-3-27b",
        code = listOf(
            CodeLine(
                listOf(
                    CodeSegment("delay", CodeTone.Muted),
                    CodeSegment(" = "),
                    CodeSegment("min", CodeTone.Accent),
                    CodeSegment("(cap, base * 2**n)"),
                ),
            ),
            CodeLine(
                listOf(
                    CodeSegment("delay", CodeTone.Muted),
                    CodeSegment(" += "),
                    CodeSegment("random", CodeTone.Accent),
                    CodeSegment(".uniform(0, delay)"),
                ),
            ),
        ),
        followUps = listOf("Show in editor", "Explain jitter", "Try aiohttp variant"),
    ),
    ConversationTurn.Streaming,
)

/**
 * Conversation screen — AppBar(thinking), scrollable bubble list with
 * optional inline CodeBlock + follow-up chips, and an `idle` AskBar with
 * "Reply or ask follow-up" placeholder.
 */
@Composable
fun ScreenConversation(
    palette: PipecatPalette,
    onMicTap: () -> Unit = {},
    turns: List<ConversationTurn> = DEFAULT_TURNS,
    askBarState: AskBarState = AskBarState.Idle,
    latencyMs: Int? = 218,
) {
    val strings = rememberPipecatStrings()
    Column(modifier = Modifier.fillMaxSize()) {
        PipecatAppBar(
            state = PipecatState.Thinking,
            palette = palette,
            latencyMs = latencyMs,
            stateLabels = strings.stateLabels,
            historyContentDescription = strings.historyLabel,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(turns) { turn ->
                when (turn) {
                    is ConversationTurn.User ->
                        ConversationBubble(
                            role = BubbleRole.User,
                            palette = palette,
                            meta = turn.meta,
                        ) {
                            Text(
                                text = turn.text,
                                fontSize = 14.5.sp,
                                lineHeight = 21.025.sp,
                            )
                        }

                    is ConversationTurn.Assistant -> AssistantTurn(
                        turn = turn,
                        palette = palette,
                    )

                    ConversationTurn.Streaming -> StreamingPlaceholder(
                        palette = palette,
                        label = strings.conversationTypingLabel,
                    )
                }
            }
        }

        AskBar(
            state = askBarState,
            palette = palette,
            placeholder = strings.conversationReplyPlaceholder,
            listeningLabel = strings.askListeningLabel,
            attachContentDescription = strings.askAttachLabel,
            micContentDescription = strings.askMicLabel,
            stopContentDescription = strings.askStopLabel,
            waveContentDescription = strings.askWaveLabel,
            onMicTap = onMicTap,
        )
    }
}

@Composable
private fun AssistantTurn(turn: ConversationTurn.Assistant, palette: PipecatPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConversationBubble(
            role = BubbleRole.Assistant,
            palette = palette,
            meta = turn.meta,
        ) {
            Text(
                text = turn.text,
                fontSize = 14.5.sp,
                lineHeight = 21.75.sp,
            )
        }

        turn.code?.let { lines ->
            Box(modifier = Modifier.fillMaxWidth(0.88f)) {
                CodeBlock(lines = lines)
            }
        }

        if (turn.followUps.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                turn.followUps.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        color = palette.textDim,
                        modifier = Modifier
                            .background(palette.chipBg, RoundedCornerShape(999.dp))
                            .border(1.dp, palette.chipBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingPlaceholder(palette: PipecatPalette, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = palette.textDim,
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.width(2.dp))
        TypingDots(color = com.lgcns.codeengine.translation.ui.theme.Pipecat.Vermilion)
    }
}

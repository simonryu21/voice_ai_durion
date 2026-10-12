package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.ui.theme.Pipecat
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette

data class SuggestionCard(
    val tag: String,
    val title: String,
    /** When `null`, the first card is rendered with accent tag. */
    val accent: Boolean? = null,
)

/**
 * 2×2 suggestion grid for Idle screen. Each card: uppercase mono tag chip
 * + 14sp title with `pre-line` whitespace. First card (or any card with
 * `accent=true`) renders its tag chip with vermilion fill.
 */
@Composable
fun SuggestionGrid(
    cards: List<SuggestionCard>,
    palette: PipecatPalette,
    onSelect: (SuggestionCard) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { card ->
                    val globalIndex = cards.indexOf(card)
                    val isAccent = card.accent ?: (globalIndex == 0)
                    SuggestionCardItem(
                        card = card,
                        isAccent = isAccent,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(card) },
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuggestionCardItem(
    card: SuggestionCard,
    isAccent: Boolean,
    palette: PipecatPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface, RoundedCornerShape(16.dp))
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 16.dp)
            .heightIn(min = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isAccent) Pipecat.Vermilion else palette.chipBg,
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                text = card.tag.uppercase(),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isAccent) Color.White else palette.textDim,
                letterSpacing = 0.76.sp, // ~0.08em at 9.5sp
            )
        }
        Text(
            text = card.title,
            fontSize = 14.sp,
            lineHeight = 18.9.sp,
            color = palette.text,
            fontWeight = FontWeight.Medium,
        )
    }
}

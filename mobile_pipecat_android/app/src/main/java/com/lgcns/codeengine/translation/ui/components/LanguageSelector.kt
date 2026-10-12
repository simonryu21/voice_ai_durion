package com.lgcns.codeengine.translation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lgcns.codeengine.translation.domain.model.Language
import com.lgcns.codeengine.translation.ui.theme.Primary
import com.lgcns.codeengine.translation.ui.theme.SlateLight200
import com.lgcns.codeengine.translation.ui.theme.SlateLight500

@Composable
fun LanguageSelector(
    targetLanguage: Language,
    surfaceColor: Color,
    borderColor: Color,
    onSurfaceColor: Color,
    subtitleColor: Color,
    isDarkTheme: Boolean,
    onTargetLanguageClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Small input indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "🌐",
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Any language input",
                fontSize = 13.sp,
                color = subtitleColor
            )
        }

        // Arrow down
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = subtitleColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Output Language Selector (main focus)
        Text(
            text = "TRANSLATE TO",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = subtitleColor,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Large output button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor)
                .border(2.dp, Primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .clickable(onClick = onTargetLanguageClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Country code badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = targetLanguage.countryCode,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = targetLanguage.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceColor
                )
                Text(
                    text = targetLanguage.nativeName,
                    fontSize = 13.sp,
                    color = subtitleColor
                )
            }

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Select language",
                tint = Primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun LanguagePickerContent(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onSurfaceColor: Color,
    subtitleColor: Color,
    isDarkTheme: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp)
    ) {
        // Header
        Text(
            text = "Select Output Language",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        // Language List
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(Language.SUPPORTED_OUTPUT_LANGUAGES) { language ->
                LanguagePickerItem(
                    language = language,
                    isSelected = language.code == selectedLanguage.code,
                    onSelect = { onLanguageSelected(language) },
                    onSurfaceColor = onSurfaceColor,
                    subtitleColor = subtitleColor,
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

@Composable
fun LanguagePickerItem(
    language: Language,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSurfaceColor: Color,
    subtitleColor: Color,
    isDarkTheme: Boolean
) {
    val backgroundColor = if (isSelected) Primary.copy(alpha = 0.1f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(backgroundColor)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Country code badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SlateLight200),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = language.countryCode,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Primary else subtitleColor
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.name,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) Primary else onSurfaceColor
            )
            Text(
                text = language.nativeName,
                fontSize = 13.sp,
                color = subtitleColor
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


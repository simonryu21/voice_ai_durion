package com.lgcns.codeengine.translation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lgcns.codeengine.translation.ui.components.AskBar
import com.lgcns.codeengine.translation.ui.components.AskBarState
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI test for the Pipecat [AskBar] component — covers the
 * accessibility content descriptions, the mic/stop toggle, and the
 * controlled text-input mode (typing + IME submit).
 */
@RunWith(AndroidJUnit4::class)
class AskBarComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val palette = PipecatPalette.forTheme(isDark = false)

    @Test
    fun idleState_exposesDefaultContentDescriptions() {
        composeTestRule.setContent {
            AskBar(state = AskBarState.Idle, palette = palette, onMicTap = {})
        }

        composeTestRule.onNodeWithContentDescription("Attach").assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Start listening")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Voice mode")
            .assertIsDisplayed()
    }

    @Test
    fun listeningState_swapsMicButtonToStop() {
        // MicCaret hosts an infinite animation — freeze the clock.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AskBar(state = AskBarState.Listening, palette = palette, onMicTap = {})
        }

        composeTestRule
            .onNodeWithContentDescription("Stop listening")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Start listening")
            .assertDoesNotExist()
    }

    @Test
    fun micButtonTap_firesCallback() {
        var micTaps = 0
        composeTestRule.setContent {
            AskBar(
                state = AskBarState.Idle,
                palette = palette,
                onMicTap = { micTaps++ },
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Start listening")
            .performClick()

        assertEquals(1, micTaps)
    }

    @Test
    fun disabledState_micButtonIsNotEnabled() {
        composeTestRule.setContent {
            AskBar(state = AskBarState.Disabled, palette = palette, onMicTap = {})
        }

        composeTestRule
            .onNodeWithContentDescription("Start listening")
            .assertIsNotEnabled()
    }

    @Test
    fun visualOnlyMode_hasNoEditableTextField() {
        composeTestRule.setContent {
            AskBar(
                state = AskBarState.Idle,
                palette = palette,
                onMicTap = {},
                placeholder = "Ask anything",
            )
        }

        composeTestRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeTestRule.onNodeWithText("Ask anything").assertIsDisplayed()
    }

    @Test
    fun controlledMode_acceptsTextAndFiresSubmit() {
        var submitted: String? = null
        composeTestRule.setContent {
            var text by remember { mutableStateOf("") }
            AskBar(
                state = AskBarState.Idle,
                palette = palette,
                onMicTap = {},
                value = text,
                onValueChange = { text = it },
                onSubmit = { submitted = it },
            )
        }

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextInput("raspberry pi")
        composeTestRule.onNodeWithText("raspberry pi").assertIsDisplayed()

        field.performImeAction()
        assertEquals("raspberry pi", submitted)
    }
}

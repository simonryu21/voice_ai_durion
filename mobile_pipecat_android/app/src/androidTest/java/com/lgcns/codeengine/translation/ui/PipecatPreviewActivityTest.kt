package com.lgcns.codeengine.translation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lgcns.codeengine.activity.PipecatPreviewActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI test for [PipecatPreviewActivity] — verifies the five
 * Pipecat Voice UI preview screens are reachable from the tab row and that
 * tapping a tab actually swaps the screen content.
 *
 * The animated screens (Listening/Conversation/Bubble) host infinite
 * animations, so `mainClock.autoAdvance` is disabled to keep `waitForIdle`
 * deterministic.
 */
@RunWith(AndroidJUnit4::class)
class PipecatPreviewActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<PipecatPreviewActivity>()

    private val idleTab = "01 · Idle"
    private val listeningTab = "02 · Listening"
    private val conversationTab = "03 · Conversation"
    private val overlayTab = "04 · Overlay"
    private val bubbleTab = "05 · Bubble"

    // Stable, hard-coded anchors from ScreenListening / ScreenConversation.
    private val listeningAnchor = "Raspberry Pi"
    private val conversationAnchor = "debounce a websocket"

    @Test
    fun allFiveScreenTabsAreRendered() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText(idleTab).assertIsDisplayed()
        composeTestRule.onNodeWithText(listeningTab).assertIsDisplayed()
        composeTestRule.onNodeWithText(conversationTab).assertIsDisplayed()
        composeTestRule.onNodeWithText(overlayTab).assertIsDisplayed()
        composeTestRule.onNodeWithText(bubbleTab).assertIsDisplayed()
    }

    @Test
    fun idleIsTheDefaultScreen() {
        composeTestRule.mainClock.autoAdvance = false

        // Neither the Listening nor the Conversation anchor is present at start.
        composeTestRule
            .onNodeWithText(listeningAnchor, substring = true)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(conversationAnchor, substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun tappingListeningTabSwitchesToTheListeningScreen() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText(listeningTab).performClick()

        composeTestRule
            .onNodeWithText(listeningAnchor, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun tappingConversationTabSwitchesToTheConversationScreen() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText(conversationTab).performClick()

        composeTestRule
            .onNodeWithText(conversationAnchor, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun switchingBackToIdleClearsThePreviousScreenContent() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText(conversationTab).performClick()
        composeTestRule
            .onNodeWithText(conversationAnchor, substring = true)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(idleTab).performClick()
        composeTestRule
            .onNodeWithText(conversationAnchor, substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun overlayAndBubbleTabsRenderWithoutCrashing() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText(overlayTab).performClick()
        composeTestRule.onNodeWithText(overlayTab).assertIsDisplayed()

        composeTestRule.onNodeWithText(bubbleTab).performClick()
        composeTestRule.onNodeWithText(bubbleTab).assertIsDisplayed()
    }
}

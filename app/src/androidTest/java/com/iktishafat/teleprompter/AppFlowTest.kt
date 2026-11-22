package com.iktishafat.teleprompter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end: create a script, type Arabic text, open the prompter, start and pause. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun createScriptAndOpenPrompter() {
        rule.onNodeWithTag("new_script").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTagExists("body_field") }
        rule.onNodeWithTag("title_field").performTextInput(" اختبار")
        rule.onNodeWithTag("body_field").performTextInput(
            "في قلب بلاد الرافدين، قامت مدينة غيّرت وجه التاريخ.\n\nبابل لم تكن مجرد عاصمة، بل كانت فكرة."
        )
        rule.onNodeWithTag("start_prompter").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTagExists("prompter_surface") }
        rule.onNodeWithTag("prompter_surface").assertIsDisplayed()
        rule.onNodeWithTag("play_pause").performClick() // start (countdown)
        rule.onNodeWithTag("play_pause").performClick() // pause
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

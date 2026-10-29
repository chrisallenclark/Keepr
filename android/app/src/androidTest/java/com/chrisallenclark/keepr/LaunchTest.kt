package com.chrisallenclark.keepr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a real emulator in CI: the app launches and draws its first screen. */
@RunWith(AndroidJUnit4::class)
class LaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesToFirstScreen() {
        composeRule.onNodeWithText("Keepr").assertIsDisplayed()
        composeRule.onNodeWithText("Android preview").assertIsDisplayed()
    }
}

package com.chrisallenclark.keepr

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First launch: the three onboarding pages, the Business/Personal choice
 * sticking, and never seeing it again once finished.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clean() {
        resetApp()
        showOnboardingOnNextLaunch()
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch() {
        scenario = launchApp()
    }

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("keepr", Context.MODE_PRIVATE)

    private fun pressBack() {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    /** The contacts page offers "Not Now" or, if access is already granted, "Skip for now". */
    private fun skipContacts() {
        rule.waitForText("Bring in your people")
        val skip = listOf("Not Now", "Skip for now", "Get Started").first {
            rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(skip).onLast().tap()
    }

    @Test
    fun walksThroughAndStartsInTheChosenMode() {
        launch()
        rule.waitForText("Your business relationships and your personal ones")
        rule.tapTab("Continue")
        rule.waitForText("One switch")
        // Today's own switch sits underneath the cover; the onboarding one is drawn last.
        rule.onAllNodesWithText("Personal").onLast().tap()
        rule.waitForText("You can switch any time.")
        rule.onAllNodesWithText("Continue").onLast().tap()
        skipContacts()

        rule.waitForGone("Bring in your people")
        assertTrue(prefs().getBoolean(PreferenceKey.HAS_ONBOARDED, false))
        assertEquals("personal", prefs().getString(PreferenceKey.CONTEXT_MODE, null))
    }

    @Test
    fun backStepsToThePreviousPage() {
        launch()
        rule.tapTab("Continue")
        rule.waitForText("One switch")
        pressBack()
        rule.waitForText("Your business relationships and your personal ones")
        assertTrue(rule.onAllNodes(hasText("One switch")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun isNotShownAgainOnceFinished() {
        launch()
        rule.tapTab("Continue")
        rule.waitForText("One switch")
        rule.onAllNodesWithText("Continue").onLast().tap()
        skipContacts()
        rule.waitForGone("Bring in your people")
        scenario?.close()
        AppContainer.resetForTesting()

        launch()
        rule.waitForText("You're caught up.")
        assertTrue(rule.onAllNodes(hasText("Your business relationships", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun testsAndScreenshotsSkipIt() {
        // resetApp() alone marks onboarding done, which every other test relies on.
        resetApp()
        launch()
        rule.waitForText("You're caught up.")
        assertTrue(rule.onAllNodes(hasText("Your business relationships", substring = true)).fetchSemanticsNodes().isEmpty())
    }
}

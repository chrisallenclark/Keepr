package com.chrisallenclark.keepr

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings: its pages, the wording choice, and erasing everything. */
@RunWith(AndroidJUnit4::class)
class SettingsTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clean() = resetApp()

    @After
    fun close() {
        scenario?.close()
    }

    private fun openSettings() {
        scenario = launchApp()
        rule.waitForText("You're caught up.")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Settings").tap()
        rule.waitForText("Follow-up reminders")
    }

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("keepr", Context.MODE_PRIVATE)

    @Test
    fun showsEverySection() {
        openSettings()
        listOf("Reminders", "Organizing", "Relationship Types", "Wording", "Call them", "Permissions", "Contacts", "Data", "Storage", "Development", "Sample data", "Delete All Data", "About", "Version", "What Keepr can't do")
            .forEach { rule.waitForText(it) }
    }

    @Test
    fun limitationsPagePushesAndComesBack() {
        openSettings()
        rule.onNodeWithText("What Keepr can't do").performScrollTo().tap()
        rule.waitForText("Google Play only allows that")
        rule.waitForGone("Follow-up reminders")

        rule.onNodeWithContentDescription("Back").tap()
        rule.waitForText("Follow-up reminders")

        // The system back gesture pops the page too, without closing Settings.
        rule.onNodeWithText("What Keepr can't do").performScrollTo().tap()
        rule.waitForText("Google Play only allows that")
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForText("Follow-up reminders")
    }

    @Test
    fun wordingChoiceRenamesGroups() {
        openSettings()
        rule.onNodeWithText("Call them").performScrollTo().tap()
        rule.waitForText("Places")
        rule.onNodeWithText("Places").tap()
        rule.waitForText("Life Time and your training company are places")
        assertEquals("Place", prefs().getString(PreferenceKey.GROUP_LABEL, null))
    }

    @Test
    fun deleteAllDataErasesPeople() {
        val container = AppContainer.get(InstrumentationRegistry.getInstrumentation().targetContext, isDemo = false)
        runBlocking {
            withContext(Dispatchers.Main) {
                container.load()
                container.repository.context.insert(Person("Priya", "Raman", context = RelationshipContext.BUSINESS))
                container.repository.context.save()
            }
            container.repository.flush()
        }
        openSettings()
        rule.onNodeWithText("Delete All Data").performScrollTo().tap()
        rule.waitForText("Delete all Keepr data?")
        rule.onNodeWithText("Delete Everything").tap()
        rule.waitForGone("Delete all Keepr data?")
        rule.waitForIdle()
        assertTrue(container.repository.context.fetch<Person>().isEmpty())
        assertTrue(rule.onAllNodes(hasText("Priya Raman")).fetchSemanticsNodes().isEmpty())
    }
}

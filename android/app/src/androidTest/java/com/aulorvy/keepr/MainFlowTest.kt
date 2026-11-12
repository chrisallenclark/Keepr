package com.aulorvy.keepr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The main loop on a real emulator, through the UI: add someone, log an
 * interaction, plan and complete a follow-up, find them in search, switch
 * Business/Personal, and see it all again after the app restarts.
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: androidx.test.core.app.ActivityScenario<MainActivity>? = null

    @Before
    fun clean() = resetApp()

    @org.junit.After
    fun close() {
        scenario?.close()
    }

    private fun launchApp() = com.aulorvy.keepr.launchApp().also { scenario = it }

    private fun addPerson(first: String, last: String, company: String? = null) {
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Add Person").tap()
        rule.onNodeWithText("Add Manually").tap()
        rule.waitForText("Add Person")
        rule.onNodeWithContentDescription("First name").performTextInput(first)
        rule.onNodeWithContentDescription("Last name").performTextInput(last)
        if (company != null) rule.onNodeWithContentDescription("Company").performTextInput(company)
        rule.onNodeWithText("Save").tap()
        rule.waitForText("$first $last")
    }

    @Test
    fun startsEmptyAndCaughtUp() {
        launchApp()
        rule.waitForText("You're caught up.")
        listOf("Today", "People", "Follow Up", "Search").forEach { rule.first(it).assertIsDisplayed() }
    }

    @Test
    fun addedPersonAppearsInPeopleAndSurvivesRestart() {
        val scenario = launchApp()
        rule.waitForText("You're caught up.")
        addPerson("Ada", "Lovelace", company = "Analytical Engines")

        // Back on People, the new person is listed.
        rule.onNodeWithContentDescription("Back").tap()
        rule.waitForText("Ada Lovelace")

        // Close the app completely and come back: the data is read from disk.
        flushSaves()
        scenario.close()
        AppContainer.resetForTesting()
        launchApp()
        rule.tapTab("People")
        rule.waitForText("Ada Lovelace")
        rule.onNodeWithText("Analytical Engines", substring = true).assertIsDisplayed()
    }

    @Test
    fun followUpFlow() {
        launchApp()
        rule.waitForText("You're caught up.")
        addPerson("Sarah", "Miller")

        // Plan a follow-up from the profile.
        rule.onNodeWithText("Add a follow-up").tap()
        rule.onNodeWithContentDescription("What do you need to do?").performTextInput("Send the training block")
        rule.onNodeWithText("Tomorrow").tap()
        rule.onNodeWithText("Save").tap()
        rule.waitForText("Send the training block")

        // It's on the Follow Up tab, and completing it moves it out of the open list.
        rule.tapTab("Follow Up")
        rule.waitForText("Send the training block")
        rule.onNodeWithContentDescription("Mark done").tap()
        rule.waitForGone("Send the training block")
    }

    @Test
    fun logInteractionAndSearch() {
        launchApp()
        rule.waitForText("You're caught up.")
        addPerson("Jake", "Martinez", company = "Martinez Roofing")

        rule.onNodeWithText("Log").tap()
        rule.waitForText("Log Interaction")
        rule.onNodeWithContentDescription("Talked about…").performTextInput("Wants to start training before the wedding")
        rule.onNodeWithContentDescription("Something to remember").performTextInput("Daughter's wedding in June")
        rule.onNodeWithText("Add").tap()
        rule.first("Save").tap()
        rule.waitForText("Wants to start training before the wedding")
        rule.waitForText("Daughter's wedding in June")

        // Search finds him by something only the note says.
        rule.tapTab("Search")
        rule.onNode(hasContentDescription("Names, companies, anything you noted")).performTextInput("wedding")
        rule.waitForText("Jake Martinez")
    }

    @Test
    fun businessPersonalSwitchFiltersPeople() {
        launchApp()
        rule.waitForText("You're caught up.")
        addPerson("Bea", "Business")
        rule.onNodeWithContentDescription("Back").tap()
        rule.waitForText("Bea Business")

        // Personal hides a business-only person; switching back shows them again.
        rule.onNodeWithText("Personal").tap()
        rule.waitForGone("Bea Business")
        rule.onNodeWithText("Business").tap()
        rule.waitForText("Bea Business")
    }

    @Test
    fun deletingAPersonRemovesThem() {
        launchApp()
        rule.waitForText("You're caught up.")
        addPerson("Dana", "Whitfield")
        rule.onNodeWithContentDescription("More").tap()
        rule.onNodeWithText("Delete Person").tap()
        rule.waitForText("Delete Dana Whitfield?")
        rule.first("Delete").tap()
        rule.waitForGone("Dana Whitfield")
    }
}

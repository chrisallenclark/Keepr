package com.chrisallenclark.keepr

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quick Capture and Brain Dump through the UI: a note about someone becomes a
 * follow-up and memories on their record, a dump becomes separate facts, and
 * backing out of either writes nothing.
 */
@RunWith(AndroidJUnit4::class)
class CaptureFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clean() = resetApp()

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch() {
        scenario = launchApp()
        rule.waitForText("You're caught up.")
    }

    /** Adds someone from People and leaves their profile open. */
    private fun addPerson(first: String, last: String) {
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Add Person").tap()
        rule.onNodeWithText("Add Manually").tap()
        rule.waitForText("Add Person")
        rule.onNodeWithContentDescription("First name").performTextInput(first)
        rule.onNodeWithContentDescription("Last name").performTextInput(last)
        rule.onNodeWithText("Save").tap()
        rule.waitForText("$first $last")
    }

    private fun openQuickCapture() {
        rule.tapTab("Today")
        rule.waitForText("Good ")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Quick Capture").tap()
        rule.waitForText("Stays on your device.")
    }

    private fun assertAbsent(text: String) {
        rule.waitForIdle()
        val found = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes()
        if (found.isNotEmpty()) throw AssertionError("\"$text\" should not exist. On screen:\n${rule.screenText()}")
    }

    @Test
    fun quickCaptureForAnExistingPersonFilesAFollowUpAndMemories() {
        launch()
        addPerson("Jake", "Martinez")
        rule.onNodeWithContentDescription("Back").tap()

        openQuickCapture()
        rule.onNodeWithContentDescription("What happened?").performTextInput(
            "Talked to Jake at Life Time. He owns a roofing company, wants to lose 20 pounds " +
                "and said to text him next week about training.",
        )
        rule.onNodeWithText("Next").tap()

        // The review preselects Jake and shows what was found.
        rule.waitForText("Review")
        rule.waitForText("Jake Martinez")
        rule.waitForText("Owns a roofing company")
        rule.waitForText("Text him next week about training")
        assertAbsent("Choose someone")
        rule.onNodeWithText("Save").tap()
        rule.waitForGone("Turn off anything that isn't worth keeping.")

        // The follow-up is on the Follow Up tab…
        rule.tapTab("Follow Up")
        rule.waitForText("Text him next week about training")

        // …and the facts are on his profile.
        rule.tapTab("People")
        rule.waitForText("Jake Martinez")
        rule.first("Jake Martinez").tap()
        rule.waitForText("Owns a roofing company")
        rule.waitForText("Wants to lose 20 pounds")
    }

    @Test
    fun brainDumpOnAProfileAddsMemories() {
        launch()
        addPerson("Priya", "Shah")

        rule.onNodeWithText("Brain dump what you know").performScrollTo().tap()
        rule.waitForText("Sort It Out")
        rule.onNodeWithContentDescription("Everything you know about them.").performTextInput(
            "Two kids, Mia and Leo\nTraining for a marathon in April",
        )
        rule.onNodeWithText("Sort It Out").tap()

        rule.waitForText("Keep What Matters")
        rule.waitForText("2 found")
        rule.onNodeWithText("Save 2").tap()
        rule.waitForGone("Keep What Matters")

        rule.waitForText("Two kids, Mia and Leo")
        rule.waitForText("Training for a marathon in April")
    }

    @Test
    fun cancellingSavesNothing() {
        launch()
        addPerson("Sam", "Rivera")

        // Brain dump, then Cancel.
        rule.onNodeWithText("Brain dump what you know").performScrollTo().tap()
        rule.waitForText("Sort It Out")
        rule.onNodeWithContentDescription("Everything you know about them.").performTextInput("Allergic to peanuts")
        rule.onNodeWithText("Cancel").tap()
        rule.waitForGone("Sort It Out")
        assertAbsent("Allergic to peanuts")
        rule.onNodeWithContentDescription("Back").tap()

        // Quick capture as far as the review, back, then Cancel.
        openQuickCapture()
        rule.onNodeWithContentDescription("What happened?").performTextInput(
            "Talked to Sam about the contract. He has a dog named Rex and said to call him tomorrow.",
        )
        rule.onNodeWithText("Next").tap()
        rule.waitForText("Review")
        rule.onNodeWithText("Back").tap()
        rule.waitForText("Stays on your device.")
        rule.onNodeWithText("Cancel").tap()
        rule.waitForGone("Stays on your device.")

        // Nothing landed anywhere.
        rule.tapTab("Follow Up")
        rule.waitForText("Nothing to follow up on")
        rule.tapTab("People")
        rule.waitForText("Sam Rivera")
        rule.first("Sam Rivera").tap()
        rule.waitForText("Brain dump what you know")
        assertAbsent("Rex")
        assertAbsent("Talked to Sam")
    }
}

package com.aulorvy.keepr

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Organizing people, through the UI: groups from People's Manage menu,
 * custom relationship types from Edit Person, and links between two people
 * that read correctly from both profiles.
 */
@RunWith(AndroidJUnit4::class)
class OrganizeTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: androidx.test.core.app.ActivityScenario<MainActivity>? = null

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

    /** Adds someone by hand; ends on their profile. */
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

    private fun openManageMenu(item: String) {
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Sort and Filter").tap()
        rule.waitForText(item)
        rule.onNodeWithText(item).tap()
    }

    /** Runs a row's swipe action the way TalkBack does. */
    private fun swipeAction(rowText: String, action: String) {
        val hasAction = SemanticsMatcher("has a \"$action\" action") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == action } == true
        }
        val node = rule.onAllNodes(hasAnyDescendant(hasText(rowText)) and hasAction).onFirst().fetchSemanticsNode()
        val custom = node.config[SemanticsActions.CustomActions].first { it.label == action }
        rule.runOnUiThread { custom.action() }
        rule.waitForIdle()
    }

    @Test
    fun createGroupAddSomeoneAndDeleteIt() {
        launch()
        addPerson("Ada", "Lovelace")

        openManageMenu("Manage Groups")
        rule.waitForText("No groups yet")
        rule.onNodeWithText("Create a Group").tap()
        rule.waitForText("New Group")
        rule.onNodeWithContentDescription("Name").performTextInput("Climbing Gym")
        rule.onNodeWithText("Save").tap()
        rule.waitForText("Climbing Gym")
        rule.waitForText("No one yet")
        rule.onNodeWithText("Done").tap()
        rule.waitForGone("No one yet")

        // Put Ada there from her profile.
        rule.first("Ada Lovelace").tap()
        rule.waitForText("Add a group")
        rule.onNodeWithText("Add a group").performScrollTo().tap()
        rule.waitForText("New Group…")
        rule.onAllNodesWithText("Climbing Gym").onLast().tap()
        rule.waitForGone("Add a group")
        rule.first("Climbing Gym").assertExists()

        // The group now lists her; opening it shows her, and tapping her opens her profile.
        openManageMenu("Manage Groups")
        rule.waitForText("1 person")
        // The sheet's window comes after the screen's, so its rows are last.
        rule.onAllNodesWithText("Climbing Gym").onLast().tap()
        rule.waitForText("Ada Lovelace")
        rule.onAllNodesWithText("Ada Lovelace").onLast().tap()
        rule.waitForGone("1 person")
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("More")).fetchSemanticsNodes().isNotEmpty() }

        // Delete the group: Ada stays.
        openManageMenu("Manage Groups")
        rule.waitForText("1 person")
        swipeAction("Climbing Gym", "Delete")
        rule.waitForText("Delete Climbing Gym?")
        rule.onNodeWithText("Delete Group").tap()
        rule.waitForText("No groups yet")
        rule.onNodeWithText("Done").tap()
        rule.waitForText("Ada Lovelace")
    }

    @Test
    fun customTypeIsCreatedFromEditPersonAssignedAndDeleted() {
        launch()
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Add Person").tap()
        rule.onNodeWithText("Add Manually").tap()
        rule.waitForText("Add Person")
        rule.onNodeWithContentDescription("First name").performTextInput("Nora")
        rule.onNodeWithContentDescription("Last name").performTextInput("Chef")

        // "New Type…" opens the type editor straight into a new type.
        rule.onNodeWithText("New Type…").performScrollTo().tap()
        rule.waitForText("Relationship Types")
        rule.onNodeWithContentDescription("Name").performTextInput("Nutrition Client")
        rule.onNodeWithText("Add").tap()
        rule.waitForText("Business Types")
        rule.first("Nutrition Client").assertExists()
        rule.onNodeWithText("Done").tap()
        rule.waitForGone("Relationship Types")

        // It's offered in Edit Person; choosing it puts it on the profile.
        rule.onNodeWithText("Nutrition Client").performScrollTo().tap()
        rule.onNodeWithText("Save").tap()
        rule.waitForText("Nora Chef")
        rule.first("Nutrition Client").assertExists()

        // Deleting the type unmarks Nora but keeps her.
        rule.onNodeWithContentDescription("Back").tap()
        openManageMenu("Manage Types")
        rule.waitForText("Business Types")
        swipeAction("Nutrition Client", "Delete")
        rule.waitForText("Delete Nutrition Client?")
        rule.onNodeWithText("Delete Type").tap()
        rule.waitForGone("Nutrition Client")
        rule.onNodeWithText("Done").tap()
        rule.waitForText("Nora Chef")
    }

    @Test
    fun linkedPeopleSeeEachOtherFromBothSides() {
        launch()
        addPerson("Alex", "Nguyen")
        addPerson("Linda", "Clark")

        // From Linda's profile: Alex is her parent.
        rule.onNodeWithText("Link to someone else").performScrollTo().tap()
        rule.waitForText("Link People")
        rule.onNodeWithText("Choose").tap()
        rule.waitForText("Link To")
        rule.first("Alex Nguyen").tap()
        rule.waitForText("Link People")
        rule.onNodeWithText("They are their…").tap()
        rule.waitForText("Grandparent")
        rule.onNodeWithText("Parent").tap()
        rule.waitForText("Alex Nguyen shows up as Parent on Linda Clark's profile")
        rule.onNodeWithText("Save").tap()
        rule.waitForGone("Link People")

        rule.first("Alex Nguyen").performScrollTo()
        rule.waitForText("Parent")

        // From Alex's side, Linda is his child.
        rule.first("Alex Nguyen").tap()
        rule.waitForText("Linda Clark")
        rule.waitForText("Child")
    }

    @Test
    fun switchingBusinessPersonalInsideTheSheetsSwitchesTheApp() {
        launch()
        addPerson("Bea", "Business")
        rule.onNodeWithContentDescription("Back").tap()
        rule.waitForText("Bea Business")

        // iOS binds Groups to the app's mode: Personal there is Personal everywhere.
        openManageMenu("Manage Groups")
        rule.waitForText("No groups yet")
        // The sheet's window comes after the screen's, so its switch is last.
        rule.onAllNodesWithText("Personal").onLast().tap()
        rule.onNodeWithText("Done").tap()
        rule.waitForGone("No groups yet")
        rule.waitForText("No personal relationships")
        rule.waitForGone("Bea Business")
        val prefs = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("keepr", android.content.Context.MODE_PRIVATE)
        org.junit.Assert.assertEquals(
            "personal",
            prefs.getString(com.aulorvy.keepr.core.persistence.PreferenceKey.CONTEXT_MODE, null),
        )

        // Relationship Types opens on the app's mode and switches it back.
        openManageMenu("Manage Types")
        rule.waitForText("Personal Types")
        rule.onAllNodesWithText("Business").onLast().tap()
        rule.waitForText("Business Types")
        rule.onNodeWithText("Done").tap()
        rule.waitForGone("Business Types")
        rule.waitForText("Bea Business")
        org.junit.Assert.assertEquals(
            "business",
            prefs.getString(com.aulorvy.keepr.core.persistence.PreferenceKey.CONTEXT_MODE, null),
        )
    }
}

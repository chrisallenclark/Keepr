package com.aulorvy.keepr

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.aulorvy.keepr.contacts.AndroidContactStore
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.persistence.PreferenceKey
import com.aulorvy.keepr.data.MemoryPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One realistic session on a fresh install, then a cold start: everything
 * done in the first half has to be there in the second.
 *
 * Someone is added with a company and a phone number; a memory, an
 * interaction, a follow-up for tomorrow and a note go on their profile; a
 * second person comes in from Contacts; the app is switched to Personal. Then
 * the app is closed completely (saves flushed, activity gone, in-memory
 * container dropped) and launched again.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    // WRITE_CONTACTS is declared only in the debug manifest, for the insert.
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val inserted = mutableListOf<Long>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clean() = resetApp()

    @After
    fun tearDown() {
        scenario?.close()
        val uri = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()
        inserted.forEach { id ->
            app.contentResolver.delete(uri, "${RawContacts._ID} = ?", arrayOf(id.toString()))
        }
        inserted.clear()
    }

    /** One local (account-less) contact, removed again in [tearDown]. */
    private fun insertContact(given: String, family: String, phone: String) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build()
        ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.GIVEN_NAME, given)
            .withValue(StructuredName.FAMILY_NAME, family)
            .build()
        ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, phone)
            .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
            .build()
        val results = app.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        inserted += ContentUris.parseId(requireNotNull(results[0].uri))

        // The provider aggregates a moment after the insert; the importer reads
        // once when it opens, so wait until the contact is readable.
        val store = AndroidContactStore(app, MemoryPreferences())
        val name = "$given $family"
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (runBlocking { store.fetchContacts() }.any { it.fullName == name }) break
            Thread.sleep(200)
        }
    }

    private fun people(): List<Person> {
        var result: List<Person> = emptyList()
        rule.runOnIdle {
            result = AppContainer.get(app, isDemo = false).repository.context.fetch<Person>().filter { !it.isDeleted }
        }
        return result
    }

    @Test
    fun aWholeSessionSurvivesClosingTheApp() {
        insertContact("Theo", "Brightwater", "(561) 555-0177")
        scenario = launchApp()
        rule.waitForText("You're caught up.")

        // Someone new, with where they work and a number.
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Add Person").tap()
        rule.onNodeWithText("Add Manually").tap()
        rule.waitForText("Add Person")
        rule.onNodeWithContentDescription("First name").performTextInput("Mara")
        rule.onNodeWithContentDescription("Last name").performTextInput("Okafor")
        rule.onNodeWithContentDescription("Company").performScrollTo().performTextInput("Okafor Design")
        rule.onNodeWithContentDescription("Phone").performScrollTo().performTextInput("(561) 555-0142")
        rule.onNodeWithText("Save").tap()
        rule.waitForText("Mara Okafor")
        rule.waitForText("Okafor Design")

        // A memory, straight on the profile.
        rule.onNodeWithText("Add one thing").performScrollTo().tap()
        rule.onNodeWithContentDescription("Something worth remembering").performTextInput("Allergic to shellfish")
        rule.first("Save").tap()
        rule.waitForText("Allergic to shellfish")

        // An interaction.
        rule.first("Log").tap()
        rule.waitForText("Log Interaction")
        rule.onNodeWithContentDescription("Talked about…").performTextInput("Walked through the brand refresh")
        rule.first("Save").tap()
        rule.waitForGone("Log Interaction")
        rule.waitForText("Walked through the brand refresh")

        // A follow-up for tomorrow.
        rule.onNodeWithText("Add a follow-up").performScrollTo().tap()
        rule.onNodeWithContentDescription("What do you need to do?").performTextInput("Send the proposal")
        rule.onNodeWithText("Tomorrow").tap()
        rule.onNodeWithText("Save").tap()
        rule.waitForText("Send the proposal")

        // A note.
        rule.onNodeWithText("Paste a summary or meeting notes").performScrollTo().tap()
        rule.waitForText("Add Note")
        rule.onNodeWithContentDescription("Paste the summary, or type what was said.")
            .performTextInput("Budget is set for Q3. They want two logo directions.")
        rule.onNodeWithContentDescription("Title").performScrollTo().performTextReplacement("Kickoff call")
        rule.onNodeWithText("Save").tap()
        rule.waitForGone("Add Note")
        rule.waitForText("Kickoff call")

        // Someone from Contacts.
        rule.onNodeWithContentDescription("Back").tap()
        rule.waitForText("Mara Okafor")
        rule.onNodeWithContentDescription("Add Person").tap()
        rule.onNodeWithText("Import from Contacts").tap()
        rule.waitForText("Add from Contacts")
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Search contacts")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Search contacts").performTextInput("Brightwater")
        val theoInSheet = hasText("Theo Brightwater") and hasAnyAncestor(hasAnyDescendant(hasContentDescription("Search contacts")))
        rule.waitUntil(10_000) { rule.onAllNodes(theoInSheet).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(theoInSheet).tap()
        rule.onNodeWithText("Add 1").tap()
        rule.waitForGone("Add from Contacts")
        rule.waitForText("Theo Brightwater")

        // Business to Personal: neither of them is personal.
        rule.onNodeWithText("Personal").tap()
        rule.waitForText("No personal relationships")
        rule.waitForGone("Mara Okafor")

        // Close the app completely and start it again.
        flushSaves()
        scenario?.close()
        AppContainer.resetForTesting()
        scenario = launchApp()

        // Personal was remembered.
        rule.tapTab("People")
        rule.waitForText("No personal relationships")
        assertEquals(
            "personal",
            app.getSharedPreferences("keepr", Context.MODE_PRIVATE).getString(PreferenceKey.CONTEXT_MODE, null),
        )

        // Back to Business: both people are there.
        rule.onNodeWithText("Business").tap()
        rule.waitForText("Mara Okafor")
        rule.waitForText("Theo Brightwater")

        // The follow-up is on the Follow Up tab.
        rule.tapTab("Follow Up")
        rule.waitForText("Send the proposal")

        // And the profile has everything.
        rule.tapTab("People")
        rule.waitForText("Mara Okafor")
        rule.first("Mara Okafor").tap()
        rule.waitForText("Okafor Design")
        rule.waitForText("Allergic to shellfish")
        rule.waitForText("Walked through the brand refresh")
        rule.waitForText("Send the proposal")
        rule.waitForText("Kickoff call")

        // The model agrees, field by field.
        val everyone = people()
        assertEquals(2, everyone.size)
        val mara = everyone.first { it.fullName == "Mara Okafor" }
        assertEquals("Okafor Design", mara.company)
        assertEquals(listOf("(561) 555-0142"), mara.phoneNumbers)
        assertEquals(RelationshipContext.BUSINESS, mara.context)
        assertTrue(mara.memoryList.any { it.content == "Allergic to shellfish" })
        assertTrue(mara.interactionList.any { it.rawNote == "Walked through the brand refresh" })
        assertEquals(listOf("Send the proposal"), mara.followUpList.filter { !it.isDeleted }.map { it.title })
        assertEquals(listOf("Kickoff call"), mara.noteList.map { it.title })
        val theo = everyone.first { it.fullName == "Theo Brightwater" }
        assertNotNull(theo.contactIdentifier)
        assertEquals(listOf("(561) 555-0177"), theo.phoneNumbers)
    }
}

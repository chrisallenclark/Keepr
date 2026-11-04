package com.chrisallenclark.keepr

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.chrisallenclark.keepr.contacts.AndroidContactStore
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.services.ContactAccess
import com.chrisallenclark.keepr.core.services.ContactRelation
import com.chrisallenclark.keepr.core.services.ContactSummary
import com.chrisallenclark.keepr.data.MemoryPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Contacts on a real emulator: the ContactsContract reader, importing through
 * the sheet (and not duplicating on a second import), and Catch Up noticing a
 * contact added after Keepr last looked. Every contact inserted here is
 * removed again afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ContactsTest {

    // WRITE_CONTACTS is declared only in the debug manifest, for these inserts.
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

    private fun launch() = launchApp().also { scenario = it }

    // MARK: - Fixtures

    /** Inserts one local (account-less) contact; returns its raw contact id. */
    private fun insertContact(
        given: String,
        family: String,
        company: String? = null,
        title: String? = null,
        phone: String? = null,
        email: String? = null,
        birthday: String? = null,
        mother: String? = null,
    ): Long {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build()
        fun data(mimeType: String, vararg values: Pair<String, Any?>) {
            val op = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, mimeType)
            values.forEach { (key, value) -> op.withValue(key, value) }
            ops += op.build()
        }
        data(StructuredName.CONTENT_ITEM_TYPE, StructuredName.GIVEN_NAME to given, StructuredName.FAMILY_NAME to family)
        if (company != null || title != null) {
            data(Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to company, Organization.TITLE to title)
        }
        phone?.let { data(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to it, Phone.TYPE to Phone.TYPE_MOBILE) }
        email?.let { data(Email.CONTENT_ITEM_TYPE, Email.ADDRESS to it, Email.TYPE to Email.TYPE_WORK) }
        birthday?.let { data(Event.CONTENT_ITEM_TYPE, Event.START_DATE to it, Event.TYPE to Event.TYPE_BIRTHDAY) }
        mother?.let { data(Relation.CONTENT_ITEM_TYPE, Relation.NAME to it, Relation.TYPE to Relation.TYPE_MOTHER) }

        val results = app.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val id = ContentUris.parseId(requireNotNull(results[0].uri))
        inserted += id
        return id
    }

    private fun insertFamily() {
        insertContact(
            "Ines", "Quillfeather", company = "Quill Studio", title = "Founder",
            phone = "(561) 555-0101", email = "ines@quillstudio.com", birthday = "1988-04-12",
        )
        insertContact("Otto", "Quillfeather", phone = "(561) 555-0102", mother = "Greta Quillfeather")
        insertContact("Pax", "Quillfeather", phone = "(561) 555-0103")
    }

    /** Reads contacts until [name] shows up (aggregation can trail the insert slightly). */
    private fun fetchUntil(store: AndroidContactStore, name: String): List<ContactSummary> {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val contacts = runBlocking { store.fetchContacts() }
            if (contacts.any { it.fullName == name } || System.currentTimeMillis() > deadline) return contacts
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

    private fun memories(): List<Memory> {
        var result: List<Memory> = emptyList()
        rule.runOnIdle { result = AppContainer.get(app, isDemo = false).repository.context.fetch<Memory>() }
        return result
    }

    /** A node inside the import sheet, not the People list behind it. */
    private fun inImportSheet(text: String): SemanticsMatcher =
        hasText(text) and hasAnyAncestor(hasAnyDescendant(hasContentDescription("Search contacts")))

    /**
     * The import list is lazy, so on a small screen a contact further down
     * isn't built until it's scrolled to. The sheet's list is the last
     * scrollable list on screen (the sheet's window comes after the screen's).
     */
    private fun scrollImporterTo(name: String) {
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onLast()
            .performScrollToNode(hasText(name))
    }

    private fun pickInImporter(name: String) {
        scrollImporterTo(name)
        rule.onNode(inImportSheet(name)).tap()
    }

    private fun openImporter() {
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Add Person").tap()
        rule.onNodeWithText("Import from Contacts").tap()
        searchImporter()
    }

    /** Waits for the contacts to load (the search field appears with them), then narrows to the fixtures. */
    private fun searchImporter() {
        rule.waitForText("Add from Contacts")
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Search contacts")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Search contacts").performTextInput("Quillfeather")
    }

    /** Runs a row's swipe action the way TalkBack does — the same handler a swipe calls. */
    private fun ComposeTestRule.swipeAction(rowText: String, label: String) {
        val nodes = onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasAnyDescendant(hasText(rowText)),
        ).fetchSemanticsNodes()
        val action = nodes.firstNotNullOfOrNull { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label }
        }
        assertNotNull("No \"$label\" action on the row for $rowText.\n${screenText()}", action)
        runOnUiThread { action!!.action() }
    }

    // MARK: - Tests

    @Test
    fun storeReadsContactsWithTheirFields() {
        insertFamily()
        val store = AndroidContactStore(app, MemoryPreferences())
        assertEquals(ContactAccess.AUTHORIZED, store.authorizationStatus())

        val contacts = fetchUntil(store, "Ines Quillfeather")
        val ines = contacts.first { it.fullName == "Ines Quillfeather" }
        assertEquals("Ines", ines.givenName)
        assertEquals("Quillfeather", ines.familyName)
        assertEquals("Quill Studio", ines.organizationName)
        assertEquals("Founder", ines.jobTitle)
        assertEquals(listOf("(561) 555-0101"), ines.phoneNumbers)
        assertEquals(listOf("ines@quillstudio.com"), ines.emailAddresses)
        assertEquals(LocalDate.of(1988, 4, 12).atStartOfDay(ZoneId.systemDefault()).toInstant(), ines.birthday)
        assertTrue("identifier is the lookup key", ines.identifier.isNotEmpty())

        val otto = contacts.first { it.fullName == "Otto Quillfeather" }
        assertEquals(listOf(ContactRelation("mother", "Greta Quillfeather")), otto.relations)
        assertEquals(null, otto.organizationName)

        // Looking one contact up by its identifier gives the same card.
        val again = runBlocking { store.contact(ines.identifier) }
        assertEquals(ines, again)
    }

    @Test
    fun importingThroughTheSheetCreatesPeopleAndNeverDuplicates() {
        insertFamily()
        launch()
        rule.waitForText("You're caught up.")

        // People is empty, so its empty state offers the importer directly.
        rule.tapTab("People")
        rule.first("Import from Contacts").tap()
        searchImporter()
        rule.waitForText("Ines Quillfeather")
        // Suggestions are read off the cards: Ines runs a business, Otto has family on his.
        rule.waitForText("Founder at Quill Studio")
        scrollImporterTo("Otto Quillfeather")
        rule.waitForText("Listed as mother on the contact card")

        pickInImporter("Ines Quillfeather")
        pickInImporter("Pax Quillfeather")
        rule.onNodeWithText("Add 2").tap()
        rule.waitForGone("Add from Contacts")
        rule.waitForText("Ines Quillfeather")
        rule.waitForText("Pax Quillfeather")

        val imported = people()
        assertEquals(2, imported.size)
        val ines = imported.first { it.fullName == "Ines Quillfeather" }
        assertNotNull(ines.contactIdentifier)
        assertEquals("Quill Studio", ines.company)
        assertEquals(RelationshipContext.BUSINESS, ines.context)
        assertEquals(listOf("Potential Client"), ines.tagList.map { it.name })
        assertTrue(memories().any { it.person === ines && it.content == "Birthday: April 12" })
        val pax = imported.first { it.fullName == "Pax Quillfeather" }
        assertTrue("nothing on Pax's card to go on", pax.tagList.isEmpty())

        // Again: the two already in Keepr show as added and can't be picked.
        openImporter()
        rule.waitForText("Otto Quillfeather")
        // Each of the two is marked; on a small screen they aren't on screen at once.
        for (name in listOf("Ines Quillfeather", "Pax Quillfeather")) {
            scrollImporterTo(name)
            rule.waitUntil(10_000) { rule.onAllNodes(hasText("Added")).fetchSemanticsNodes().isNotEmpty() }
        }
        pickInImporter("Ines Quillfeather")
        rule.onNodeWithText("Add 0").tap() // disabled: nothing happens
        pickInImporter("Otto Quillfeather")
        rule.onNodeWithText("Add 1").tap()
        rule.waitForGone("Add from Contacts")

        val after = people()
        assertEquals(3, after.size)
        assertEquals(1, after.count { it.fullName == "Ines Quillfeather" })
        val otto = after.first { it.fullName == "Otto Quillfeather" }
        assertEquals(RelationshipContext.PERSONAL, otto.context)
        assertEquals(listOf("Family"), otto.tagList.map { it.name })
    }

    @Test
    fun someoneAddedByHandIsNotMistakenForTheContact() {
        // iOS links people to contacts by identifier only: the same name and
        // number on a hand-made profile doesn't make them the same person.
        insertFamily()
        launch()
        rule.waitForText("You're caught up.")
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Add Person").tap()
        rule.onNodeWithText("Add Manually").tap()
        rule.waitForText("Add Person")
        rule.onNodeWithContentDescription("First name").performTextInput("Pax")
        rule.onNodeWithContentDescription("Last name").performTextInput("Quillfeather")
        rule.onNodeWithContentDescription("Phone").performTextInput("(561) 555-0103")
        rule.onNodeWithText("Save").tap()
        rule.waitForText("Pax Quillfeather")
        rule.onNodeWithContentDescription("Back").tap()

        openImporter()
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty() }
        scrollImporterTo("Pax Quillfeather")
        assertTrue(rule.onAllNodes(hasText("Added")).fetchSemanticsNodes().isEmpty())
        pickInImporter("Pax Quillfeather")
        rule.onNodeWithText("Add 1").tap()
        rule.waitForGone("Add from Contacts")

        val paxes = people().filter { it.fullName == "Pax Quillfeather" }
        assertEquals(2, paxes.size)
        assertEquals(1, paxes.count { it.contactIdentifier != null })
    }

    @Test
    fun catchUpListsNewContactsAndCategorizesThem() {
        launch()
        rule.waitForText("You're caught up.")
        // The first look only records what's already there.
        val prefs = app.getSharedPreferences("keepr", Context.MODE_PRIVATE)
        rule.waitUntil(10_000) { prefs.getBoolean("keepr.hasContactBaseline", false) }

        insertContact("Nadia", "Quillfeather", company = "Quill Studio", title = "Designer")
        insertContact("Remy", "Quillfeather", phone = "(561) 555-0104")

        // Coming back to Today looks again, and the Catch Up row appears.
        rule.tapTab("People")
        rule.tapTab("Today")
        rule.waitForText("2 new contacts since you last looked")
        rule.onNodeWithText("Catch Up").tap()
        rule.waitForText("New in Contacts")
        rule.waitForText("Nadia Quillfeather")
        rule.waitForText("Remy Quillfeather")

        // Accept Nadia's suggestion straight from the list.
        rule.swipeAction("Nadia Quillfeather", "Accept")
        // Gone from the queue (Today behind the sheet may now list her as recent).
        val nadiaInQueue = hasText("Nadia Quillfeather") and hasAnyAncestor(hasAnyDescendant(hasText("New in Contacts")))
        rule.waitUntil(10_000) { rule.onAllNodes(nadiaInQueue).fetchSemanticsNodes().isEmpty() }
        val nadia = people().first { it.fullName == "Nadia Quillfeather" }
        assertEquals(listOf("Professional Contact"), nadia.tagList.map { it.name })

        // Place Remy by hand, with how they met.
        rule.onNodeWithText("Remy Quillfeather").tap()
        rule.waitForText("New Contact")
        rule.onNodeWithText("Current Client").performScrollTo().tap()
        rule.onNodeWithContentDescription("Met at the gym, introduced by Michael…").performScrollTo()
            .performTextInput("Met at the open house")
        rule.onNodeWithText("Save").tap()
        rule.waitForText("Everyone's placed")

        val remy = people().first { it.fullName == "Remy Quillfeather" }
        assertNotNull(remy.contactIdentifier)
        assertEquals(listOf("Current Client"), remy.tagList.map { it.name })
        assertEquals("Met at the open house", remy.howWeMet)
        assertNotNull(remy.reviewedAt)

        // Closing Catch Up re-counts, and Today has nothing left to raise.
        rule.onNodeWithText("Done").tap()
        rule.waitForGone("Everyone's placed")
        rule.waitForGone("2 new contacts since you last looked")
        rule.waitForGone("Catch Up")
    }
}

package com.chrisallenclark.keepr

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.PersonLink
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.SampleData
import com.chrisallenclark.keepr.data.KeeprDatabase
import com.chrisallenclark.keepr.data.KeeprRepository
import com.chrisallenclark.keepr.data.MemoryPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Room layer on a real device: what's saved is what comes back after the
 * database is closed and reopened, with every relationship and delete rule
 * the iOS store has.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val fileName = "keepr-test.db"

    private val opened = mutableListOf<KeeprDatabase>()
    private val repositories = mutableListOf<KeeprRepository>()

    private fun open() = androidx.room.Room.databaseBuilder(context, KeeprDatabase::class.java, fileName).build()
        .also { opened += it }

    @Before fun clean() { context.deleteDatabase(fileName) }

    /** Close every connection before deleting the file, or the next test's open can fail on older Android. */
    @After fun tidy() {
        // Loading seeds (and saves) in the background, so let those writes land first.
        runBlocking { repositories.forEach { it.flush() } }
        opened.forEach { it.close() }
        context.deleteDatabase(fileName)
    }

    /** Saves with one repository, then reads everything back through a brand-new one. */
    private fun roundTrip(build: (KeeprRepository) -> Unit): KeeprRepository = runBlocking {
        val first = withContext(Dispatchers.Main) { KeeprRepository(open(), MemoryPreferences()).also { repositories += it } }
        withContext(Dispatchers.Main) {
            first.load()
            build(first)
            first.context.save()
        }
        first.flush()
        val second = withContext(Dispatchers.Main) { KeeprRepository(open(), MemoryPreferences()).also { repositories += it } }
        withContext(Dispatchers.Main) { second.load() }
        second
    }

    @Test
    fun graphSurvivesReopening() {
        var personId = ""
        val reopened = roundTrip { repo ->
            val ctx = repo.context
            val person = Person("Ada", "Lovelace", context = RelationshipContext.BUSINESS, company = "Analytical Engines", phoneNumbers = listOf("555-0100", "555-0199"))
            ctx.insert(person)
            val tag = KeeprStore.tag("Investor", com.chrisallenclark.keepr.core.model.TagKind.BUSINESS, ctx)
            person.tags = listOf(tag)
            val group = PersonGroup("Life Time", "figure.run").also { ctx.insert(it) }
            person.groups = listOf(group)
            val interaction = Interaction(InteractionKind.MEETING, Instant.parse("2025-06-13T12:00:00Z"), summary = "Coffee", person = person)
            ctx.insert(interaction)
            ctx.insert(Memory("Prefers early meetings", MemoryCategory.PREFERENCES, person = person, sourceInteraction = interaction))
            ctx.insert(FollowUp("Send the deck", dueDate = Instant.parse("2025-06-18T00:00:00Z"), person = person, sourceInteraction = interaction))
            personId = person.id.toString()
        }
        val people = reopened.context.fetch<Person>()
        assertEquals(1, people.size)
        val ada = people.single()
        assertEquals(personId, ada.id.toString())
        assertEquals("Ada Lovelace", ada.fullName)
        assertEquals(listOf("555-0100", "555-0199"), ada.phoneNumbers)
        assertEquals(RelationshipContext.BUSINESS, ada.context)
        assertEquals(listOf("Investor"), ada.tagList.map { it.name })
        assertEquals(listOf("Life Time"), ada.groupList.map { it.name })
        assertEquals("Coffee", ada.interactionList.single().summary)
        assertEquals(ada.interactionList.single().id, ada.memoryList.single().sourceInteraction?.id)
        assertEquals(ada.interactionList.single().id, ada.followUpList.single().sourceInteraction?.id)
        assertEquals(Instant.parse("2025-06-18T00:00:00Z"), ada.followUpList.single().dueDate)
        assertTrue("built-in types are seeded alongside", reopened.context.fetch<RelationshipTag>().size >= RelationshipTag.builtInCatalog.size)
    }

    @Test
    fun deletingAPersonCascadesOnDisk() {
        val reopened = roundTrip { repo ->
            val ctx = repo.context
            val person = Person("Ada", "Lovelace").also { ctx.insert(it) }
            val interaction = Interaction(summary = "Coffee", person = person).also { ctx.insert(it) }
            ctx.insert(Memory("Likes coffee", person = person, sourceInteraction = interaction))
            ctx.insert(FollowUp("Call", dueDate = Instant.now(), person = person))
            ctx.save()
            ctx.delete(person)
        }
        assertTrue(reopened.context.fetch<Person>().isEmpty())
        assertTrue(reopened.context.fetch<Interaction>().isEmpty())
        assertTrue(reopened.context.fetch<Memory>().isEmpty())
        assertTrue(reopened.context.fetch<FollowUp>().isEmpty())
        val tombstones = runBlocking { open().dao().deletions() }
        assertTrue("deletions are logged for sync", tombstones.any { it.recordType == "people" })
    }

    @Test
    fun deletingAnInteractionKeepsItsMemory() {
        val reopened = roundTrip { repo ->
            val ctx = repo.context
            val person = Person("Ada", "Lovelace").also { ctx.insert(it) }
            val interaction = Interaction(summary = "Coffee", person = person).also { ctx.insert(it) }
            ctx.insert(Memory("Likes coffee", person = person, sourceInteraction = interaction))
            ctx.save()
            ctx.delete(interaction)
        }
        val memory = reopened.context.fetch<Memory>().single()
        assertNull(memory.sourceInteraction)
        assertEquals("Ada", memory.person?.givenName)
    }

    @Test
    fun linksAndEditsSurvive() {
        val reopened = roundTrip { repo ->
            val ctx = repo.context
            val sarah = Person("Sarah", "Miller").also { ctx.insert(it) }
            val michael = Person("Michael", "Reed").also { ctx.insert(it) }
            ctx.insert(PersonLink(sarah, michael, "Referred By", "Referred"))
            ctx.save()
            sarah.isFavorite = true
            sarah.cadenceDays = 14
        }
        val sarah = reopened.context.fetch<Person>().single { it.givenName == "Sarah" }
        assertTrue(sarah.isFavorite)
        assertEquals(14, sarah.cadenceDays)
        assertEquals("Referred By", sarah.connections.single().label)
        assertEquals("Referred", reopened.context.fetch<Person>().single { it.givenName == "Michael" }.connections.single().label)
    }

    @Test
    fun sampleDataRoundTripsAndRemovesCleanly() {
        val reopened = roundTrip { repo -> SampleData.load(repo.context, repo.preferences) }
        assertTrue(SampleData.isLoaded(reopened.context))
        val count = reopened.context.fetch<Person>().size
        assertEquals(8, count)
        runBlocking(Dispatchers.Main) { SampleData.remove(reopened.context) }
        assertTrue(reopened.context.fetch<Person>().isEmpty())
    }
}

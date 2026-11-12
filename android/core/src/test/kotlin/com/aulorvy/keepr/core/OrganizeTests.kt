package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.GroupRules
import com.aulorvy.keepr.core.domain.LinkRules
import com.aulorvy.keepr.core.domain.RelationshipTypeRules
import com.aulorvy.keepr.core.model.LinkRole
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.model.TagKind
import com.aulorvy.keepr.core.persistence.KeeprStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules behind the groups, relationship-type and link editors. */
class OrganizeTests {

    // MARK: Groups

    @Test
    fun memberCountsReadNaturally() {
        assertEquals("No one yet", GroupRules.memberCountLabel(0))
        assertEquals("1 person", GroupRules.memberCountLabel(1))
        assertEquals("4 people", GroupRules.memberCountLabel(4))
    }

    @Test
    fun deleteMessagesSayPeopleStay() {
        assertEquals("Nothing else changes — no one is here.", GroupRules.listDeleteMessage(0))
        assertEquals("Nobody is here, so nothing else changes.", GroupRules.editorDeleteMessage(0))
        assertTrue(GroupRules.listDeleteMessage(3).startsWith("The 3 people there stay in Keepr"))
    }

    @Test
    fun usedSuggestionsAreDropped() {
        val store = TestStore()
        store.context.insert(PersonGroup("gym"))
        val open = GroupRules.openSuggestions(store.fetch())
        assertTrue(open.none { it.name == "Gym" })
        assertEquals(PersonGroup.suggestions.size - 1, open.size)
    }

    @Test
    fun newGroupGoesLastAndTakesThePersonItWasOpenedFor() {
        val store = TestStore()
        store.context.insert(PersonGroup("Existing", sortOrder = 40))
        val jake = Make.person(store.context, given = "Jake")
        val created = GroupRules.save(
            store.context, null, "  Life Time ", " Delray ", " ", "figure.run",
            RelationshipContext.BOTH, addingTo = jake,
        )
        store.save()
        assertEquals("Life Time", created.name)
        assertEquals("Delray", created.detail)
        assertNull(created.aliases)
        assertEquals(50, created.sortOrder)
        assertTrue(jake.isMember(created))
        assertEquals(1, created.memberCount)
    }

    @Test
    fun editingAGroupDoesNotAddAnyone() {
        val store = TestStore()
        val group = PersonGroup("Gym").also { store.context.insert(it) }
        val jake = Make.person(store.context, given = "Jake")
        GroupRules.save(store.context, group, "Gym 2", "", "", "house", RelationshipContext.BUSINESS, addingTo = jake)
        assertEquals("Gym 2", group.name)
        assertEquals(RelationshipContext.BUSINESS, group.context)
        assertFalse(jake.isMember(group))
    }

    @Test
    fun deletingAGroupKeepsItsMembers() {
        val store = TestStore()
        val jake = Make.person(store.context, given = "Jake")
        val group = GroupRules.save(store.context, null, "Gym", "", "", "figure.run", RelationshipContext.BOTH, addingTo = jake)
        store.save()
        store.context.delete(group)
        store.save()
        assertTrue(store.fetch<com.aulorvy.keepr.core.model.Person>().contains(jake))
        assertTrue(jake.groupList.isEmpty())
    }

    // MARK: Relationship types

    @Test
    fun namesAreUniquePerKindIgnoringCaseAndSpaces() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val tags = store.fetch<RelationshipTag>()
        assertTrue(RelationshipTypeRules.nameIsTaken("  lead ", TagKind.BUSINESS, tags))
        assertFalse(RelationshipTypeRules.nameIsTaken("Lead", TagKind.PERSONAL, tags))
        val lead = tags.first { it.name == "Lead" }
        assertFalse(RelationshipTypeRules.nameIsTaken("LEAD", TagKind.BUSINESS, tags, excluding = lead))
    }

    @Test
    fun creatingATypeRefusesDuplicatesAndGoesLast() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val maxOrder = store.fetch<RelationshipTag>().maxOf { it.sortOrder }
        assertNull(RelationshipTypeRules.create(store.context, "current client", TagKind.BUSINESS, "tag"))
        assertNull(RelationshipTypeRules.create(store.context, "   ", TagKind.BUSINESS, "tag"))
        val created = assertNotNull(RelationshipTypeRules.create(store.context, " Nutrition Client ", TagKind.BUSINESS, "fork.knife"))
        assertEquals("Nutrition Client", created.name)
        assertEquals(maxOrder + 10, created.sortOrder)
        assertFalse(created.isBuiltIn)
        assertEquals(created, RelationshipTypeRules.types(TagKind.BUSINESS, store.fetch()).last())
        assertEquals(
            "Business already has a type called \"Lead\".",
            RelationshipTypeRules.duplicateWarning(" Lead ", TagKind.BUSINESS),
        )
    }

    @Test
    fun renamingChecksOthersButNotItself() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val all = store.fetch<RelationshipTag>()
        val lead = all.first { it.name == "Lead" }
        assertFalse(RelationshipTypeRules.update(lead, "Vendor", "flame", 0, all))
        assertEquals("Lead", lead.name)
        assertTrue(RelationshipTypeRules.update(lead, "Prospect", "target", 30, all))
        assertEquals("Prospect", lead.name)
        assertEquals("target", lead.symbolName)
        assertEquals(30, lead.cadenceDays)
        assertTrue(RelationshipTypeRules.update(lead, "Prospect", "target", 0, all))
        assertNull(lead.cadenceDays)
        // Still found by its key after a rename.
        assertEquals(lead.id, KeeprStore.tag("Lead", TagKind.BUSINESS, store.context).id)
    }

    @Test
    fun deletingATypeUnmarksPeopleOnly() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val vendor = store.fetch<RelationshipTag>().first { it.name == "Vendor" }
        val priya = Make.person(store.context, given = "Priya")
        priya.tags = listOf(vendor)
        store.save()
        assertEquals("One person is marked with this type. They stay in Keepr — only the type goes.", RelationshipTypeRules.deleteMessage(vendor.peopleList.size))
        store.context.delete(vendor)
        store.save()
        assertTrue(priya.tagList.isEmpty())
        assertFalse(priya.isDeleted)
    }

    // MARK: Links

    @Test
    fun candidatesExcludeSelfAndExistingLinks() {
        val store = TestStore()
        val alex = Make.person(store.context, given = "Alex")
        val linda = Make.person(store.context, given = "Linda")
        val ben = Make.person(store.context, given = "Ben")
        LinkRules.link(store.context, alex, linda, LinkRole.named("Parent"), "")
        val candidates = LinkRules.candidates(alex, store.fetch())
        assertEquals(listOf(ben.id), candidates.map { it.id })
    }

    @Test
    fun linkReadsCorrectlyFromBothEnds() {
        val store = TestStore()
        val alex = Make.person(store.context, given = "Alex", family = "Nguyen")
        val linda = Make.person(store.context, given = "Linda", family = "Clark")
        val link = LinkRules.link(store.context, alex, linda, LinkRole.named("Parent"), "  Lives nearby ")
        store.save()
        assertEquals("Lives nearby", link.note)
        assertEquals("Parent", alex.connections.single().label)
        assertEquals("Child", linda.connections.single().label)
        assertEquals(
            "Linda Clark shows up as Parent on Alex Nguyen's profile, and Alex Nguyen shows up as Child on theirs.",
            LinkRules.explanation(alex, linda, LinkRole.named("Parent")),
        )
        assertEquals(" ", LinkRules.explanation(alex, null, LinkRules.customRole("   ")))
    }

    @Test
    fun blankNoteIsStoredAsNothing() {
        val store = TestStore()
        val a = Make.person(store.context, given = "A")
        val b = Make.person(store.context, given = "B")
        assertNull(LinkRules.link(store.context, a, b, LinkRules.customRole(" Coach "), "   ").note)
        assertEquals("Coach", a.connections.single().label)
        assertEquals("Coach", b.connections.single().label)
    }
}

package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.model.LinkRole
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Translated from KeeprTests/PersonLinkTests.swift. A link joins two people
 * with one record, and the label has to read correctly from both ends —
 * getting it wrong would tell someone their mother is their child.
 */
class PersonLinkTest {

    private fun link(store: TestStore, a: Person, b: Person, role: LinkRole): PersonLink {
        val link = PersonLink(personA = a, personB = b, labelAToB = role.name, labelBToA = role.inverse)
        store.context.insert(link)
        return link
    }

    @Test
    fun linkReadsBothWays() {
        val store = TestStore()
        val alex = Make.person(store.context, given = "Alex", family = "Nguyen")
        val linda = Make.person(store.context, given = "Linda", family = "Clark")
        link(store, alex, linda, LinkRole.named("Parent"))
        store.save()

        val fromAlex = assertNotNull(alex.connections.firstOrNull())
        assertEquals(linda.id, fromAlex.person.id)
        assertEquals("Parent", fromAlex.label)

        val fromLinda = assertNotNull(linda.connections.firstOrNull())
        assertEquals(alex.id, fromLinda.person.id)
        assertEquals("Child", fromLinda.label)
    }

    @Test
    fun oneRecordPerPair() {
        val store = TestStore()
        val alex = Make.person(store.context, given = "Alex", family = "Nguyen")
        val linda = Make.person(store.context, given = "Linda", family = "Clark")
        link(store, alex, linda, LinkRole.named("Parent"))
        store.save()

        assertEquals(1, store.fetch<PersonLink>().size)
        assertEquals(1, alex.connections.size)
        assertEquals(1, linda.connections.size)
    }

    @Test
    fun isLinkedIsSymmetric() {
        val store = TestStore()
        val one = Make.person(store.context, given = "One", family = "Person")
        val two = Make.person(store.context, given = "Two", family = "Person")
        link(store, one, two, LinkRole.named("Friend"))
        store.save()

        assertTrue(one.isLinked(two))
        assertTrue(two.isLinked(one))
        val three = Make.person(store.context, given = "Three", family = "Person")
        assertFalse(one.isLinked(three))
    }

    @Test
    fun deletingAPersonRemovesTheirLinks() {
        val store = TestStore()
        val one = Make.person(store.context, given = "One", family = "Person")
        val two = Make.person(store.context, given = "Two", family = "Person")
        link(store, one, two, LinkRole.named("Colleague"))
        store.save()

        store.context.delete(one)
        store.save()

        assertTrue(store.fetch<PersonLink>().isEmpty())
        assertTrue(two.connections.isEmpty())
    }

    @Test
    fun unlinkingClearsBothEnds() {
        val store = TestStore()
        val one = Make.person(store.context, given = "One", family = "Person")
        val two = Make.person(store.context, given = "Two", family = "Person")
        val record = link(store, one, two, LinkRole.named("Manager"))
        store.save()

        // "one's manager is two" reads back as two's direct report.
        assertEquals("Manager", one.connections.firstOrNull()?.label)
        assertEquals("Direct Report", two.connections.firstOrNull()?.label)

        store.context.delete(record)
        store.save()

        assertTrue(one.connections.isEmpty())
        assertTrue(two.connections.isEmpty())
    }

    @Test
    fun connectionsAreSorted() {
        val store = TestStore()
        val hub = Make.person(store.context, given = "Hub", family = "Person")
        val zane = Make.person(store.context, given = "Zane", family = "Zimmer")
        val abby = Make.person(store.context, given = "Abby", family = "Adams")
        link(store, hub, zane, LinkRole.named("Friend"))
        link(store, abby, hub, LinkRole.named("Friend"))
        store.save()

        assertEquals(listOf("Adams", "Zimmer"), hub.connections.map { it.person.familyName })
    }
}

/** The role catalog is what stops "Manager" showing up as "Manager" on the employee's profile too. */
class LinkRoleTest {

    @Test
    fun asymmetricRolesInvert() {
        assertEquals("Child", LinkRole.named("Parent").inverse)
        assertEquals("Parent", LinkRole.named("Child").inverse)
        assertEquals("Direct Report", LinkRole.named("Manager").inverse)
        assertEquals("Referred", LinkRole.named("Referred By").inverse)
    }

    @Test
    fun symmetricRolesStayPut() {
        for (name in listOf("Spouse", "Sibling", "Friend", "Colleague", "Neighbor")) {
            assertTrue(LinkRole.named(name).isSymmetric, "$name should read the same from both ends")
        }
    }

    @Test
    fun customRolesAreSymmetric() {
        val role = LinkRole.custom("Training Partner")
        assertEquals("Training Partner", role.name)
        assertEquals("Training Partner", role.inverse)
    }

    @Test
    fun contactLabelsMap() {
        assertEquals("Parent", LinkRole.forContactLabel("mother").name)
        assertEquals("Parent", LinkRole.forContactLabel("Mother").name)
        assertEquals("Child", LinkRole.forContactLabel("daughter").name)
        assertEquals("Spouse", LinkRole.forContactLabel("wife").name)
        assertEquals("Sibling", LinkRole.forContactLabel("brother").name)
    }

    @Test
    fun unknownLabelsSurviveIntact() {
        val role = LinkRole.forContactLabel("godmother")
        assertEquals("Godmother", role.name)
        assertTrue(role.isSymmetric)
    }

    @Test
    fun reversingSwaps() {
        val reversed = LinkRole.named("Parent").reversed
        assertEquals("Child", reversed.name)
        assertEquals("Parent", reversed.inverse)
    }
}

package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.model.TagKind
import com.aulorvy.keepr.core.persistence.KeeprStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Translated from KeeprTests/RelationshipTagTests.swift. Built-in types can be
 * renamed by the user, which makes any lookup by visible name a latent
 * duplicate bug.
 */
class RelationshipTagTest {

    @Test
    fun seedingAssignsBuiltInKeys() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)

        val tags = store.fetch<RelationshipTag>()
        assertEquals(RelationshipTag.builtInCatalog.size, tags.size)
        assertTrue(tags.all { it.isBuiltIn })
        assertTrue(tags.all { it.builtInKey != null })
        val family = assertNotNull(tags.firstOrNull { it.builtInKey == "Family" })
        assertEquals("Family", family.name)
        assertEquals(TagKind.PERSONAL, family.kind)
    }

    @Test
    fun seedingIsIdempotent() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        assertEquals(RelationshipTag.builtInCatalog.size, store.fetch<RelationshipTag>().size)
    }

    @Test
    fun deletedBuiltInsStayDeleted() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val vendor = assertNotNull(store.fetch<RelationshipTag>().firstOrNull { it.builtInKey == "Vendor" })
        store.context.delete(vendor)
        store.save()

        KeeprStore.seedIfNeeded(store.context, store.defaults)

        assertFalse(store.fetch<RelationshipTag>().any { it.builtInKey == "Vendor" })
    }

    @Test
    fun newBuiltInsArriveOnUpdate() {
        val store = TestStore()
        // An install seeded before "Mentor", "Advisor" and "Candidate" existed.
        RelationshipTag.builtInCatalog.take(4).forEachIndexed { index, builtIn ->
            store.context.insert(
                RelationshipTag(builtIn.name, builtIn.kind, isBuiltIn = true, sortOrder = index * 10, symbolName = builtIn.symbolName, builtInKey = builtIn.name),
            )
        }
        store.save()

        KeeprStore.seedIfNeeded(store.context, store.defaults)

        val keys = store.fetch<RelationshipTag>().mapNotNull { it.builtInKey }.toSet()
        assertEquals(RelationshipTag.builtInCatalog.size, keys.size)
        assertTrue("Mentor" in keys)
        assertEquals(1, store.fetch<RelationshipTag>().count { it.builtInKey == "Family" }, "nothing already present is duplicated")
    }

    @Test
    fun keylessBuiltInsAreRecognized() {
        val store = TestStore()
        RelationshipTag.builtInCatalog.take(13).forEachIndexed { index, builtIn ->
            store.context.insert(RelationshipTag(builtIn.name, builtIn.kind, isBuiltIn = true, sortOrder = index * 10, symbolName = builtIn.symbolName))
        }
        store.save()

        KeeprStore.seedIfNeeded(store.context, store.defaults)

        val tags = store.fetch<RelationshipTag>()
        assertEquals(RelationshipTag.builtInCatalog.size, tags.size, "no second set")
        assertTrue(tags.all { it.builtInKey != null }, "old rows are given their key")
        val names = tags.map { it.name }
        assertEquals(names.size, names.toSet().size, "and no name appears twice")
    }

    @Test
    fun duplicatesAreMergedOnLaunch() {
        val store = TestStore()
        Make.tag(store.context, name = "Vendor", isBuiltIn = true)
        val inUse = Make.tag(store.context, name = "Vendor", isBuiltIn = true)
        val person = Make.person(store.context, given = "Priya", family = "Raman")
        Make.attach(inUse, person)
        store.save()

        KeeprStore.repairTagCatalog(store.context)

        val vendors = store.fetch<RelationshipTag>().filter { it.name == "Vendor" }
        assertEquals(1, vendors.size)
        assertEquals(inUse.id, vendors.firstOrNull()?.id, "the copy people are actually marked with survives")
        assertEquals(1, person.tagList.size, "the person is moved, not left with a deleted type")
        assertTrue(person.hasTag("Vendor"))
    }

    @Test
    fun mergingDoesNotDoubleMark() {
        val store = TestStore()
        val first = Make.tag(store.context, name = "Lead", isBuiltIn = true)
        val second = Make.tag(store.context, name = "Lead", isBuiltIn = true)
        val person = Make.person(store.context, given = "Dana", family = "Whitfield")
        Make.attach(first, person)
        Make.attach(second, person)
        store.save()

        KeeprStore.repairTagCatalog(store.context)

        assertEquals(1, person.tagList.size)
        assertTrue(person.hasTag("Lead"))
    }

    @Test
    fun sameNameDifferentKindIsNotADuplicate() {
        val store = TestStore()
        Make.tag(store.context, name = "Gym", kind = TagKind.BUSINESS)
        Make.tag(store.context, name = "Gym", kind = TagKind.PERSONAL)
        store.save()

        KeeprStore.repairTagCatalog(store.context)

        assertEquals(2, store.fetch<RelationshipTag>().size)
    }

    @Test
    fun deletionSticksWithoutAnyInsert() {
        val store = TestStore()
        for (builtIn in RelationshipTag.builtInCatalog) {
            store.context.insert(RelationshipTag(builtIn.name, builtIn.kind, isBuiltIn = true, symbolName = builtIn.symbolName, builtInKey = builtIn.name))
        }
        store.save()
        KeeprStore.seedIfNeeded(store.context, store.defaults)

        val team = assertNotNull(store.fetch<RelationshipTag>().firstOrNull { it.builtInKey == "Team" })
        store.context.delete(team)
        store.save()

        KeeprStore.seedIfNeeded(store.context, store.defaults)

        assertFalse(store.fetch<RelationshipTag>().any { it.name == "Team" })
    }

    @Test
    fun wipingRestoresTheCatalog() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)

        KeeprStore.deleteAllData(store.context, store.defaults)

        assertEquals(RelationshipTag.builtInCatalog.size, store.fetch<RelationshipTag>().size)
    }

    @Test
    fun renamedBuiltInIsStillFound() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val family = assertNotNull(store.fetch<RelationshipTag>().firstOrNull { it.builtInKey == "Family" })
        family.name = "Familia"
        store.save()

        // What contact import does when it decides someone is family.
        val resolved = KeeprStore.tag("Family", TagKind.PERSONAL, store.context)
        store.save()

        assertEquals(family.id, resolved.id)
        assertEquals("Familia", resolved.name)
        val personalTags = store.fetch<RelationshipTag>().filter { it.kind == TagKind.PERSONAL }
        val familyLike = personalTags.filter { it.builtInKey == "Family" || it.name == "Family" }
        assertEquals(1, familyLike.size, "renaming a built-in must not spawn a second one")
    }

    @Test
    fun missingTagIsRecreated() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val vendor = assertNotNull(store.fetch<RelationshipTag>().firstOrNull { it.builtInKey == "Vendor" })
        store.context.delete(vendor)
        store.save()

        val recreated = KeeprStore.tag("Vendor", TagKind.BUSINESS, store.context)
        store.save()

        assertEquals("Vendor", recreated.name)
        assertEquals(1, store.fetch<RelationshipTag>().count { it.name == "Vendor" })
    }

    @Test
    fun customTagsAreDistinctPerKind() {
        val store = TestStore()
        val business = KeeprStore.tag("Gym", TagKind.BUSINESS, store.context)
        val personal = KeeprStore.tag("Gym", TagKind.PERSONAL, store.context)
        store.save()

        assertNotEquals(business.id, personal.id)
        assertNull(business.builtInKey)
        assertEquals(false, business.isBuiltIn)
    }
}

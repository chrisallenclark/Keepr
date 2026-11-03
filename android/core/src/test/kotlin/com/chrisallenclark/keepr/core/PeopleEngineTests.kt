package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.PeopleEngine
import com.chrisallenclark.keepr.core.domain.PeopleSort
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.model.TagKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Translated from KeeprTests/PeopleEngineTests.swift.
class PeopleEngineTests {

    // MARK: Context filtering

    /** A .both person is visible in business and personal mode */
    @Test
    fun bothContextAppearsInEitherMode() {
        val store = TestStore()
        val both = Make.person(store.context, given = "Bea", family = "Both", relationship = RelationshipContext.BOTH)

        assertEquals(1, PeopleEngine.visible(listOf(both), ContextMode.BUSINESS).size)
        assertEquals(1, PeopleEngine.visible(listOf(both), ContextMode.PERSONAL).size)
    }

    /** A business person never appears in personal mode */
    @Test
    fun businessPersonHiddenInPersonalMode() {
        val store = TestStore()
        val work = Make.person(store.context, given = "Wes", family = "Work", relationship = RelationshipContext.BUSINESS)
        val home = Make.person(store.context, given = "Hal", family = "Home", relationship = RelationshipContext.PERSONAL)

        val personal = PeopleEngine.visible(listOf(work, home), ContextMode.PERSONAL)
        assertEquals(listOf(home.id), personal.map { it.id })

        val business = PeopleEngine.visible(listOf(work, home), ContextMode.BUSINESS)
        assertEquals(listOf(work.id), business.map { it.id })
    }

    /** Archived people are excluded from every mode */
    @Test
    fun archivedPeopleExcluded() {
        val store = TestStore()
        val archived = Make.person(
            store.context,
            given = "Ada",
            family = "Archived",
            relationship = RelationshipContext.BOTH,
            status = RelationshipStatus.ARCHIVED,
        )
        val active = Make.person(store.context, given = "Ann", family = "Active", relationship = RelationshipContext.BOTH)

        assertEquals(listOf(active.id), PeopleEngine.visible(listOf(archived, active), ContextMode.BUSINESS).map { it.id })
        assertEquals(listOf(active.id), PeopleEngine.visible(listOf(archived, active), ContextMode.PERSONAL).map { it.id })
    }

    // MARK: Tag / favorites filtering

    /** filter narrows to people carrying the named tag */
    @Test
    fun tagFiltering() {
        val store = TestStore()
        val client = Make.person(store.context, given = "Cal", family = "Client", relationship = RelationshipContext.BUSINESS)
        val other = Make.person(store.context, given = "Otto", family = "Other", relationship = RelationshipContext.BUSINESS)
        val tag = Make.tag(store.context, name = "Current Client", kind = TagKind.BUSINESS)
        Make.attach(tag, to = client)

        val filtered = PeopleEngine.filter(listOf(client, other), mode = ContextMode.BUSINESS, tagName = "Current Client")
        assertEquals(listOf(client.id), filtered.map { it.id })
    }

    /** favoritesOnly keeps only favorites */
    @Test
    fun favoritesOnlyFiltering() {
        val store = TestStore()
        val favorite = Make.person(store.context, given = "Fay", family = "Favorite", isFavorite = true)
        val plain = Make.person(store.context, given = "Pat", family = "Plain")

        val filtered = PeopleEngine.filter(listOf(favorite, plain), mode = ContextMode.PERSONAL, favoritesOnly = true)
        assertEquals(listOf(favorite.id), filtered.map { it.id })
    }

    /** filter combines tag, favorites and query */
    @Test
    fun combinedFiltering() {
        val store = TestStore()
        val tag = Make.tag(store.context, name = "Lead", kind = TagKind.BUSINESS)
        val match = Make.person(
            store.context,
            given = "Mia",
            family = "Match",
            relationship = RelationshipContext.BUSINESS,
            company = "Acme",
            isFavorite = true,
        )
        val notFavorite = Make.person(
            store.context,
            given = "Nia",
            family = "Nofav",
            relationship = RelationshipContext.BUSINESS,
            company = "Acme",
        )
        Make.attach(tag, to = match)
        Make.attach(tag, to = notFavorite)

        val filtered = PeopleEngine.filter(
            listOf(match, notFavorite),
            mode = ContextMode.BUSINESS,
            tagName = "Lead",
            query = "acme",
            favoritesOnly = true,
        )
        assertEquals(listOf(match.id), filtered.map { it.id })
    }

    /** An empty or whitespace query does not filter anything out */
    @Test
    fun blankQueryIsIgnored() {
        val store = TestStore()
        val a = Make.person(store.context, given = "Al", family = "Alpha")
        val b = Make.person(store.context, given = "Bo", family = "Bravo")

        assertEquals(2, PeopleEngine.filter(listOf(a, b), mode = ContextMode.PERSONAL, query = "   ").size)
        assertEquals(2, PeopleEngine.filter(listOf(a, b), mode = ContextMode.PERSONAL, query = "").size)
    }

    // MARK: matches

    /** matches hits full name, preferred name, company, job title and tag name */
    @Test
    fun matchesScansEveryField() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Michael",
            family = "Nguyen",
            preferred = "Mike",
            company = "Acme Industries",
            jobTitle = "Head of Roofing",
        )
        val tag = Make.tag(store.context, name = "Current Client", kind = TagKind.BUSINESS)
        Make.attach(tag, to = person)

        assertTrue(PeopleEngine.matches(person, query = "Nguyen"))
        assertTrue(PeopleEngine.matches(person, query = "Mike"))
        assertTrue(PeopleEngine.matches(person, query = "Acme"))
        assertTrue(PeopleEngine.matches(person, query = "Roofing"))
        assertTrue(PeopleEngine.matches(person, query = "Current Client"))
        assertFalse(PeopleEngine.matches(person, query = "Plumbing"))
    }

    /** matches is case-insensitive */
    @Test
    fun matchesIgnoresCase() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Michael",
            family = "Nguyen",
            company = "Acme Industries",
        )

        assertTrue(PeopleEngine.matches(person, query = "ACME"))
        assertTrue(PeopleEngine.matches(person, query = "nguyen"))
    }

    // MARK: Sorting

    /** PeopleSort.name sorts by family name then display name */
    @Test
    fun sortByName() {
        val store = TestStore()
        val adams = Make.person(store.context, given = "Zed", family = "Adams")
        val annSmith = Make.person(store.context, given = "Ann", family = "Smith")
        val bobSmith = Make.person(store.context, given = "Bob", family = "Smith")

        val sorted = PeopleEngine.sort(listOf(bobSmith, annSmith, adams), PeopleSort.NAME)
        assertEquals(listOf(adams.id, annSmith.id, bobSmith.id), sorted.map { it.id })
    }

    /** PeopleSort.recent puts never-contacted people last */
    @Test
    fun sortByRecent() {
        val store = TestStore()
        val yesterday = Make.person(
            store.context,
            given = "Rae",
            family = "Recent",
            lastInteractionAt = TestDates.days(-1),
        )
        val lastWeek = Make.person(
            store.context,
            given = "Oli",
            family = "Older",
            lastInteractionAt = TestDates.days(-5),
        )
        val neverA = Make.person(store.context, given = "Nan", family = "Aaa")
        val neverB = Make.person(store.context, given = "Ned", family = "Bbb")

        val sorted = PeopleEngine.sort(listOf(neverB, lastWeek, neverA, yesterday), PeopleSort.RECENT)
        assertEquals(listOf(yesterday.id, lastWeek.id, neverA.id, neverB.id), sorted.map { it.id })
    }

    /** PeopleSort.needsFollowUp puts people with no open follow-up last */
    @Test
    fun sortByNeedsFollowUp() {
        val store = TestStore()
        val soon = Make.person(store.context, given = "Sam", family = "Soon")
        val later = Make.person(store.context, given = "Lee", family = "Later")
        val onlyCompleted = Make.person(store.context, given = "Cam", family = "Aaa")
        val none = Make.person(store.context, given = "Nel", family = "Bbb")

        Make.followUp(store.context, due = TestDates.dayStart(1), person = soon)
        Make.followUp(store.context, due = TestDates.dayStart(3), person = later)
        Make.followUp(
            store.context,
            due = TestDates.dayStart(-1),
            isCompleted = true,
            person = onlyCompleted,
        )

        val sorted = PeopleEngine.sort(listOf(none, later, onlyCompleted, soon), PeopleSort.NEEDS_FOLLOW_UP)
        assertEquals(listOf(soon.id, later.id, onlyCompleted.id, none.id), sorted.map { it.id })
    }

    /** PeopleSort.priority puts favorites first, then high priority */
    @Test
    fun sortByPriority() {
        val store = TestStore()
        val favoriteHigh = Make.person(
            store.context,
            given = "Fay",
            family = "Yankee",
            priority = Priority.HIGH,
            isFavorite = true,
        )
        val favoriteNormal = Make.person(
            store.context,
            given = "Fen",
            family = "Zeta",
            isFavorite = true,
        )
        val plainHigh = Make.person(store.context, given = "Hal", family = "Alpha", priority = Priority.HIGH)
        val plainNormal = Make.person(store.context, given = "Nia", family = "Beta")

        val sorted = PeopleEngine.sort(
            listOf(plainNormal, favoriteNormal, plainHigh, favoriteHigh),
            PeopleSort.PRIORITY,
        )
        assertEquals(
            listOf(favoriteHigh.id, favoriteNormal.id, plainHigh.id, plainNormal.id),
            sorted.map { it.id },
        )
    }

    /** PeopleSort.newest is newest createdAt first */
    @Test
    fun sortByNewest() {
        val store = TestStore()
        val oldest = Make.person(
            store.context,
            given = "Ollie",
            family = "Oldest",
            createdAt = TestDates.days(-30),
        )
        val middle = Make.person(
            store.context,
            given = "Mel",
            family = "Middle",
            createdAt = TestDates.days(-10),
        )
        val newest = Make.person(
            store.context,
            given = "Noa",
            family = "Newest",
            createdAt = TestDates.days(-1),
        )

        val sorted = PeopleEngine.sort(listOf(middle, oldest, newest), PeopleSort.NEWEST)
        assertEquals(listOf(newest.id, middle.id, oldest.id), sorted.map { it.id })
    }

    // MARK: Sections

    /** alphabeticalSections groups by first letter and sorts # last */
    @Test
    fun alphabeticalSectionsGrouping() {
        val store = TestStore()
        val adams = Make.person(store.context, given = "Zed", family = "Adams")
        val alvarez = Make.person(store.context, given = "Ana", family = "Alvarez")
        val baker = Make.person(store.context, given = "Bud", family = "Baker")
        val numeric = Make.person(store.context, given = "Vee", family = "9Volt")

        val sections = PeopleEngine.alphabeticalSections(listOf(baker, numeric, alvarez, adams))

        assertEquals(listOf("A", "B", "#"), sections.map { it.key })
        assertEquals(listOf(adams.id, alvarez.id), sections.firstOrNull()?.people?.map { it.id })
        assertEquals(listOf(numeric.id), sections.lastOrNull()?.people?.map { it.id })
    }

    /** alphabeticalSections lowercases-insensitively and returns no empty sections */
    @Test
    fun alphabeticalSectionsAreCaseInsensitive() {
        val store = TestStore()
        val lower = Make.person(store.context, given = "Cid", family = "carter")
        val upper = Make.person(store.context, given = "Cal", family = "Carver")

        val sections = PeopleEngine.alphabeticalSections(listOf(upper, lower))
        assertEquals(listOf("C"), sections.map { it.key })
        assertEquals(2, sections.firstOrNull()?.people?.size)
        assertTrue(sections.all { it.people.isNotEmpty() })
    }
}

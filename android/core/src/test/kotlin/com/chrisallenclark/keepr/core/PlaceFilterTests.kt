package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.FilterFacet
import com.chrisallenclark.keepr.core.domain.PeopleEngine
import com.chrisallenclark.keepr.core.domain.PlaceFilter
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Translated from KeeprTests/PlaceFilterTests.swift.

/**
 * Type and place are two independent axes, and the whole feature rests on them
 * crossing correctly: a gym holds clients *and* the colleague who works there,
 * and "all my clients" spans every gym plus the ones trained at home.
 */
class PlaceFilterTests {

    /**
     * The user's actual situation: one gym with a client and a fellow trainer,
     * a second gym, home clients, and someone filed nowhere.
     */
    private class World(
        val store: TestStore,
        val lifeTime: PersonGroup,
        val otherGym: PersonGroup,
        val home: PersonGroup,
        val clientAtLifeTime: Person,
        val trainerAtLifeTime: Person,
        val clientAtOtherGym: Person,
        val clientAtHome: Person,
        val unplacedClient: Person,
        val client: RelationshipTag,
        val team: RelationshipTag,
    )

    private fun makeWorld(): World {
        val store = TestStore()

        val client = Make.tag(store.context, name = "Current Client", sortOrder = 0)
        val team = Make.tag(store.context, name = "Team", sortOrder = 10)

        fun group(name: String, order: Int): PersonGroup {
            val group = PersonGroup(name = name, sortOrder = order)
            store.context.insert(group)
            return group
        }

        val lifeTime = group("Life Time", 0)
        val otherGym = group("Iron House", 10)
        val home = group("Home", 20)

        fun person(given: String, tag: RelationshipTag?, places: List<PersonGroup>): Person {
            val person = Make.person(
                store.context,
                given = given,
                family = given,
                relationship = RelationshipContext.BUSINESS,
            )
            if (tag != null) Make.attach(tag, to = person)
            person.groups = places
            return person
        }

        val world = World(
            store = store,
            lifeTime = lifeTime,
            otherGym = otherGym,
            home = home,
            clientAtLifeTime = person("Stanley", tag = client, places = listOf(lifeTime)),
            trainerAtLifeTime = person("Colleague", tag = team, places = listOf(lifeTime)),
            clientAtOtherGym = person("Ironclient", tag = client, places = listOf(otherGym)),
            clientAtHome = person("Homeclient", tag = client, places = listOf(home)),
            unplacedClient = person("Nowhere", tag = client, places = emptyList()),
            client = client,
            team = team,
        )
        store.save()
        return world
    }

    private fun names(people: List<Person>): Set<String> = people.map { it.givenName }.toSet()

    /** Foundation's `uuidString`, which is upper-case. */
    private val java.util.UUID.uuidString: String get() = toString().uppercase()

    // MARK: Crossing the two axes

    /** A place shows everyone there, whatever they are to you */
    @Test
    fun placeShowsEveryType() {
        val world = makeWorld()

        val atLifeTime = PeopleEngine.filter(
            world.store.fetch<Person>(),
            mode = ContextMode.BUSINESS,
            place = PlaceFilter.Group(world.lifeTime.id),
        )

        assertEquals(setOf("Stanley", "Colleague"), names(atLifeTime))
    }

    /** A type spans every place, including people trained at home */
    @Test
    fun typeSpansEveryPlace() {
        val world = makeWorld()

        val clients = PeopleEngine.filter(
            world.store.fetch<Person>(),
            mode = ContextMode.BUSINESS,
            tagName = "Current Client",
        )

        assertEquals(setOf("Stanley", "Ironclient", "Homeclient", "Nowhere"), names(clients))
    }

    /** Both together narrows to clients at one gym */
    @Test
    fun bothTogetherNarrows() {
        val world = makeWorld()

        val clientsAtLifeTime = PeopleEngine.filter(
            world.store.fetch<Person>(),
            mode = ContextMode.BUSINESS,
            tagName = "Current Client",
            place = PlaceFilter.Group(world.lifeTime.id),
        )

        assertEquals(setOf("Stanley"), names(clientsAtLifeTime))
    }

    /** Unplaced finds the people no place has been said about */
    @Test
    fun unplacedIsItsOwnAnswer() {
        val world = makeWorld()

        val unplaced = PeopleEngine.filter(
            world.store.fetch<Person>(),
            mode = ContextMode.BUSINESS,
            place = PlaceFilter.Unplaced,
        )

        assertEquals(setOf("Nowhere"), names(unplaced))
        assertTrue(world.unplacedClient.groupList.isEmpty())
    }

    // MARK: Chip counts

    /** Type counts reflect the place already chosen */
    @Test
    fun typeCountsAreScopedToThePlace() {
        val world = makeWorld()
        val people = world.store.fetch<Person>()

        val everywhere = PeopleEngine.typeFacets(
            PeopleEngine.filter(people, mode = ContextMode.BUSINESS),
            tags = listOf(world.client, world.team),
            mode = ContextMode.BUSINESS,
        )
        assertEquals(4, everywhere.firstOrNull { it.id == "Current Client" }?.count)

        val atLifeTime = PeopleEngine.typeFacets(
            PeopleEngine.filter(people, mode = ContextMode.BUSINESS, place = PlaceFilter.Group(world.lifeTime.id)),
            tags = listOf(world.client, world.team),
            mode = ContextMode.BUSINESS,
        )
        assertEquals(1, atLifeTime.firstOrNull { it.id == "Current Client" }?.count)
        assertEquals(1, atLifeTime.firstOrNull { it.id == "Team" }?.count)
    }

    /** Place counts reflect the type already chosen */
    @Test
    fun placeCountsAreScopedToTheType() {
        val world = makeWorld()
        val people = world.store.fetch<Person>()

        val forClients = PeopleEngine.placeFacets(
            PeopleEngine.filter(people, mode = ContextMode.BUSINESS, tagName = "Current Client"),
            groups = listOf(world.lifeTime, world.otherGym, world.home),
            mode = ContextMode.BUSINESS,
        )

        assertEquals(1, forClients.firstOrNull { it.name == "Life Time" }?.count)
        assertEquals(1, forClients.firstOrNull { it.name == "Home" }?.count)
        assertEquals(1, forClients.firstOrNull { it.id == FilterFacet.unplacedID }?.count)
    }

    /** A chip that would show nobody isn't offered */
    @Test
    fun emptyFacetsAreDropped() {
        val world = makeWorld()
        val people = world.store.fetch<Person>()

        val atHome = PeopleEngine.placeFacets(
            PeopleEngine.filter(people, mode = ContextMode.BUSINESS, tagName = "Team"),
            groups = listOf(world.lifeTime, world.otherGym, world.home),
            mode = ContextMode.BUSINESS,
        )

        assertEquals(listOf("Life Time"), atHome.map { it.name }, "only the gym has anyone on the team")
    }

    /** The chip you're standing on survives even when it empties */
    @Test
    fun selectedFacetIsKept() {
        val world = makeWorld()
        val people = world.store.fetch<Person>()

        val facets = PeopleEngine.placeFacets(
            PeopleEngine.filter(people, mode = ContextMode.BUSINESS, tagName = "Team"),
            groups = listOf(world.lifeTime, world.otherGym, world.home),
            mode = ContextMode.BUSINESS,
            keeping = world.home.id.uuidString,
        )

        val home = assertNotNull(facets.firstOrNull { it.name == "Home" })
        assertEquals(0, home.count, "kept, so the user can tap it off again")
    }

    // MARK: One place, split by type

    /** A place lists its people under each type they hold */
    @Test
    fun placeSectionsSplitByType() {
        val world = makeWorld()

        val sections = PeopleEngine.byType(world.lifeTime.memberList)

        assertEquals(listOf("Current Client", "Team"), sections.map { it.title })
        assertEquals(listOf("Stanley"), sections.firstOrNull()?.people?.map { it.givenName })
        assertEquals(listOf("Colleague"), sections.lastOrNull()?.people?.map { it.givenName })
    }

    /** Someone who is two things appears under both, rather than one being picked for them */
    @Test
    fun multipleTypesAppearTwice() {
        val world = makeWorld()
        Make.attach(world.team, to = world.clientAtLifeTime)
        world.store.save()

        val sections = PeopleEngine.byType(world.lifeTime.memberList)
        val stanleySections = sections.filter { section ->
            section.people.any { it.givenName == "Stanley" }
        }

        assertEquals(2, stanleySections.size)
    }

    /** People with no type land in a final section instead of disappearing */
    @Test
    fun untypedPeopleStillShow() {
        val world = makeWorld()
        val untyped = Make.person(world.store.context, given = "Unsorted", family = "Person")
        untyped.groups = listOf(world.lifeTime)
        world.store.save()

        val sections = PeopleEngine.byType(world.lifeTime.memberList)

        assertEquals("No Type Yet", sections.lastOrNull()?.title)
        assertEquals(listOf("Unsorted"), sections.lastOrNull()?.people?.map { it.givenName })
    }

    // MARK: Filter identity

    /** A place filter survives the round trip through a chip id */
    @Test
    fun placeFilterRoundTrips() {
        val world = makeWorld()
        val id = world.lifeTime.id

        assertEquals<PlaceFilter>(PlaceFilter.Group(id), PlaceFilter(facetID = PlaceFilter.Group(id).facetID))
        assertEquals<PlaceFilter>(PlaceFilter.Unplaced, PlaceFilter(facetID = PlaceFilter.Unplaced.facetID))
        assertEquals<PlaceFilter>(PlaceFilter.All, PlaceFilter(facetID = PlaceFilter.All.facetID))
        assertEquals<PlaceFilter>(PlaceFilter.All, PlaceFilter(facetID = "not-a-uuid"), "a stale id falls back to everyone")
    }
}

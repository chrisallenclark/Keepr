import Foundation
import SwiftData
import Testing

@testable import Keepr

/// Type and place are two independent axes, and the whole feature rests on them
/// crossing correctly: a gym holds clients *and* the colleague who works there,
/// and "all my clients" spans every gym plus the ones trained at home.
@Suite("Type and place filtering")
@MainActor
struct PlaceFilterTests {

    /// The user's actual situation: one gym with a client and a fellow trainer,
    /// a second gym, home clients, and someone filed nowhere.
    private struct World {
        let store: TestStore
        let lifeTime: PersonGroup
        let otherGym: PersonGroup
        let home: PersonGroup
        let clientAtLifeTime: Person
        let trainerAtLifeTime: Person
        let clientAtOtherGym: Person
        let clientAtHome: Person
        let unplacedClient: Person
        let client: RelationshipTag
        let team: RelationshipTag
    }

    private func makeWorld() throws -> World {
        let store = try TestStore()

        let client = Make.tag(store.context, name: "Current Client", sortOrder: 0)
        let team = Make.tag(store.context, name: "Team", sortOrder: 10)

        func group(_ name: String, _ order: Int) -> PersonGroup {
            let group = PersonGroup(name: name, sortOrder: order)
            store.context.insert(group)
            return group
        }

        let lifeTime = group("Life Time", 0)
        let otherGym = group("Iron House", 10)
        let home = group("Home", 20)

        func person(_ given: String, tag: RelationshipTag?, places: [PersonGroup]) -> Person {
            let person = Make.person(
                store.context,
                given: given,
                family: given,
                relationship: .business
            )
            if let tag { Make.attach(tag, to: person) }
            person.groups = places
            return person
        }

        let world = World(
            store: store,
            lifeTime: lifeTime,
            otherGym: otherGym,
            home: home,
            clientAtLifeTime: person("Stanley", tag: client, places: [lifeTime]),
            trainerAtLifeTime: person("Colleague", tag: team, places: [lifeTime]),
            clientAtOtherGym: person("Ironclient", tag: client, places: [otherGym]),
            clientAtHome: person("Homeclient", tag: client, places: [home]),
            unplacedClient: person("Nowhere", tag: client, places: []),
            client: client,
            team: team
        )
        try store.save()
        return world
    }

    private func names(_ people: [Person]) -> Set<String> {
        Set(people.map(\.givenName))
    }

    // MARK: - Crossing the two axes

    @Test("A place shows everyone there, whatever they are to you")
    func placeShowsEveryType() throws {
        let world = try makeWorld()

        let atLifeTime = PeopleEngine.filter(
            try world.store.fetch(Person.self),
            mode: .business,
            place: .group(world.lifeTime.id)
        )

        #expect(names(atLifeTime) == ["Stanley", "Colleague"])
    }

    @Test("A type spans every place, including people trained at home")
    func typeSpansEveryPlace() throws {
        let world = try makeWorld()

        let clients = PeopleEngine.filter(
            try world.store.fetch(Person.self),
            mode: .business,
            tagName: "Current Client"
        )

        #expect(names(clients) == ["Stanley", "Ironclient", "Homeclient", "Nowhere"])
    }

    @Test("Both together narrows to clients at one gym")
    func bothTogetherNarrows() throws {
        let world = try makeWorld()

        let clientsAtLifeTime = PeopleEngine.filter(
            try world.store.fetch(Person.self),
            mode: .business,
            tagName: "Current Client",
            place: .group(world.lifeTime.id)
        )

        #expect(names(clientsAtLifeTime) == ["Stanley"])
    }

    @Test("Unplaced finds the people no place has been said about")
    func unplacedIsItsOwnAnswer() throws {
        let world = try makeWorld()

        let unplaced = PeopleEngine.filter(
            try world.store.fetch(Person.self),
            mode: .business,
            place: .unplaced
        )

        #expect(names(unplaced) == ["Nowhere"])
        #expect(world.unplacedClient.groupList.isEmpty)
    }

    // MARK: - Chip counts

    @Test("Type counts reflect the place already chosen")
    func typeCountsAreScopedToThePlace() throws {
        let world = try makeWorld()
        let people = try world.store.fetch(Person.self)

        let everywhere = PeopleEngine.typeFacets(
            for: PeopleEngine.filter(people, mode: .business),
            tags: [world.client, world.team],
            mode: .business
        )
        #expect(everywhere.first { $0.id == "Current Client" }?.count == 4)

        let atLifeTime = PeopleEngine.typeFacets(
            for: PeopleEngine.filter(people, mode: .business, place: .group(world.lifeTime.id)),
            tags: [world.client, world.team],
            mode: .business
        )
        #expect(atLifeTime.first { $0.id == "Current Client" }?.count == 1)
        #expect(atLifeTime.first { $0.id == "Team" }?.count == 1)
    }

    @Test("Place counts reflect the type already chosen")
    func placeCountsAreScopedToTheType() throws {
        let world = try makeWorld()
        let people = try world.store.fetch(Person.self)

        let forClients = PeopleEngine.placeFacets(
            for: PeopleEngine.filter(people, mode: .business, tagName: "Current Client"),
            groups: [world.lifeTime, world.otherGym, world.home],
            mode: .business
        )

        #expect(forClients.first { $0.name == "Life Time" }?.count == 1)
        #expect(forClients.first { $0.name == "Home" }?.count == 1)
        #expect(forClients.first { $0.id == FilterFacet.unplacedID }?.count == 1)
    }

    @Test("A chip that would show nobody isn't offered")
    func emptyFacetsAreDropped() throws {
        let world = try makeWorld()
        let people = try world.store.fetch(Person.self)

        let atHome = PeopleEngine.placeFacets(
            for: PeopleEngine.filter(people, mode: .business, tagName: "Team"),
            groups: [world.lifeTime, world.otherGym, world.home],
            mode: .business
        )

        #expect(atHome.map(\.name) == ["Life Time"], "only the gym has anyone on the team")
    }

    @Test("The chip you're standing on survives even when it empties")
    func selectedFacetIsKept() throws {
        let world = try makeWorld()
        let people = try world.store.fetch(Person.self)

        let facets = PeopleEngine.placeFacets(
            for: PeopleEngine.filter(people, mode: .business, tagName: "Team"),
            groups: [world.lifeTime, world.otherGym, world.home],
            mode: .business,
            keeping: world.home.id.uuidString
        )

        let home = try #require(facets.first { $0.name == "Home" })
        #expect(home.count == 0, "kept, so the user can tap it off again")
    }

    // MARK: - One place, split by type

    @Test("A place lists its people under each type they hold")
    func placeSectionsSplitByType() throws {
        let world = try makeWorld()

        let sections = PeopleEngine.byType(world.lifeTime.memberList)

        #expect(sections.map(\.title) == ["Current Client", "Team"])
        #expect(sections.first?.people.map(\.givenName) == ["Stanley"])
        #expect(sections.last?.people.map(\.givenName) == ["Colleague"])
    }

    @Test("Someone who is two things appears under both, rather than one being picked for them")
    func multipleTypesAppearTwice() throws {
        let world = try makeWorld()
        Make.attach(world.team, to: world.clientAtLifeTime)
        try world.store.save()

        let sections = PeopleEngine.byType(world.lifeTime.memberList)
        let stanleySections = sections.filter { section in
            section.people.contains { $0.givenName == "Stanley" }
        }

        #expect(stanleySections.count == 2)
    }

    @Test("People with no type land in a final section instead of disappearing")
    func untypedPeopleStillShow() throws {
        let world = try makeWorld()
        let untyped = Make.person(world.store.context, given: "Unsorted", family: "Person")
        untyped.groups = [world.lifeTime]
        try world.store.save()

        let sections = PeopleEngine.byType(world.lifeTime.memberList)

        #expect(sections.last?.title == "No Type Yet")
        #expect(sections.last?.people.map(\.givenName) == ["Unsorted"])
    }

    // MARK: - Filter identity

    @Test("A place filter survives the round trip through a chip id")
    func placeFilterRoundTrips() throws {
        let world = try makeWorld()
        let id = world.lifeTime.id

        #expect(PlaceFilter(facetID: PlaceFilter.group(id).facetID) == .group(id))
        #expect(PlaceFilter(facetID: PlaceFilter.unplaced.facetID) == .unplaced)
        #expect(PlaceFilter(facetID: PlaceFilter.all.facetID) == .all)
        #expect(PlaceFilter(facetID: "not-a-uuid") == .all, "a stale id falls back to everyone")
    }
}

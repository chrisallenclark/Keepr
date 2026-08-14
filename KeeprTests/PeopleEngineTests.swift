import Foundation
import SwiftData
import Testing

@testable import Keepr

@MainActor
@Suite("PeopleEngine")
struct PeopleEngineTests {

    // MARK: - Context filtering

    @Test("A .both person is visible in business and personal mode")
    func bothContextAppearsInEitherMode() throws {
        let store = try TestStore()
        let both = Make.person(store.context, given: "Bea", family: "Both", relationship: .both)

        #expect(PeopleEngine.visible([both], in: .business).count == 1)
        #expect(PeopleEngine.visible([both], in: .personal).count == 1)
    }

    @Test("A business person never appears in personal mode")
    func businessPersonHiddenInPersonalMode() throws {
        let store = try TestStore()
        let work = Make.person(store.context, given: "Wes", family: "Work", relationship: .business)
        let home = Make.person(store.context, given: "Hal", family: "Home", relationship: .personal)

        let personal = PeopleEngine.visible([work, home], in: .personal)
        #expect(personal.map(\.id) == [home.id])

        let business = PeopleEngine.visible([work, home], in: .business)
        #expect(business.map(\.id) == [work.id])
    }

    @Test("Archived people are excluded from every mode")
    func archivedPeopleExcluded() throws {
        let store = try TestStore()
        let archived = Make.person(
            store.context,
            given: "Ada",
            family: "Archived",
            relationship: .both,
            status: .archived
        )
        let active = Make.person(store.context, given: "Ann", family: "Active", relationship: .both)

        #expect(PeopleEngine.visible([archived, active], in: .business).map(\.id) == [active.id])
        #expect(PeopleEngine.visible([archived, active], in: .personal).map(\.id) == [active.id])
    }

    // MARK: - Tag / favorites filtering

    @Test("filter narrows to people carrying the named tag")
    func tagFiltering() throws {
        let store = try TestStore()
        let client = Make.person(store.context, given: "Cal", family: "Client", relationship: .business)
        let other = Make.person(store.context, given: "Otto", family: "Other", relationship: .business)
        let tag = Make.tag(store.context, name: "Current Client", kind: .business)
        Make.attach(tag, to: client)

        let filtered = PeopleEngine.filter([client, other], mode: .business, tagName: "Current Client")
        #expect(filtered.map(\.id) == [client.id])
    }

    @Test("favoritesOnly keeps only favorites")
    func favoritesOnlyFiltering() throws {
        let store = try TestStore()
        let favorite = Make.person(store.context, given: "Fay", family: "Favorite", isFavorite: true)
        let plain = Make.person(store.context, given: "Pat", family: "Plain")

        let filtered = PeopleEngine.filter([favorite, plain], mode: .personal, favoritesOnly: true)
        #expect(filtered.map(\.id) == [favorite.id])
    }

    @Test("filter combines tag, favorites and query")
    func combinedFiltering() throws {
        let store = try TestStore()
        let tag = Make.tag(store.context, name: "Lead", kind: .business)
        let match = Make.person(
            store.context,
            given: "Mia",
            family: "Match",
            relationship: .business,
            company: "Acme",
            isFavorite: true
        )
        let notFavorite = Make.person(
            store.context,
            given: "Nia",
            family: "Nofav",
            relationship: .business,
            company: "Acme"
        )
        Make.attach(tag, to: match)
        Make.attach(tag, to: notFavorite)

        let filtered = PeopleEngine.filter(
            [match, notFavorite],
            mode: .business,
            tagName: "Lead",
            query: "acme",
            favoritesOnly: true
        )
        #expect(filtered.map(\.id) == [match.id])
    }

    @Test("An empty or whitespace query does not filter anything out")
    func blankQueryIsIgnored() throws {
        let store = try TestStore()
        let a = Make.person(store.context, given: "Al", family: "Alpha")
        let b = Make.person(store.context, given: "Bo", family: "Bravo")

        #expect(PeopleEngine.filter([a, b], mode: .personal, query: "   ").count == 2)
        #expect(PeopleEngine.filter([a, b], mode: .personal, query: "").count == 2)
    }

    // MARK: - matches

    @Test("matches hits full name, preferred name, company, job title and tag name")
    func matchesScansEveryField() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Michael",
            family: "Nguyen",
            preferred: "Mike",
            company: "Acme Industries",
            jobTitle: "Head of Roofing"
        )
        let tag = Make.tag(store.context, name: "Current Client", kind: .business)
        Make.attach(tag, to: person)

        #expect(PeopleEngine.matches(person, query: "Nguyen"))
        #expect(PeopleEngine.matches(person, query: "Mike"))
        #expect(PeopleEngine.matches(person, query: "Acme"))
        #expect(PeopleEngine.matches(person, query: "Roofing"))
        #expect(PeopleEngine.matches(person, query: "Current Client"))
        #expect(!PeopleEngine.matches(person, query: "Plumbing"))
    }

    @Test("matches is case-insensitive")
    func matchesIgnoresCase() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Michael",
            family: "Nguyen",
            company: "Acme Industries"
        )

        #expect(PeopleEngine.matches(person, query: "ACME"))
        #expect(PeopleEngine.matches(person, query: "nguyen"))
    }

    // MARK: - Sorting

    @Test("PeopleSort.name sorts by family name then display name")
    func sortByName() throws {
        let store = try TestStore()
        let adams = Make.person(store.context, given: "Zed", family: "Adams")
        let annSmith = Make.person(store.context, given: "Ann", family: "Smith")
        let bobSmith = Make.person(store.context, given: "Bob", family: "Smith")

        let sorted = PeopleEngine.sort([bobSmith, annSmith, adams], by: .name)
        #expect(sorted.map(\.id) == [adams.id, annSmith.id, bobSmith.id])
    }

    @Test("PeopleSort.recent puts never-contacted people last")
    func sortByRecent() throws {
        let store = try TestStore()
        let yesterday = Make.person(
            store.context,
            given: "Rae",
            family: "Recent",
            lastInteractionAt: TestDates.days(-1)
        )
        let lastWeek = Make.person(
            store.context,
            given: "Oli",
            family: "Older",
            lastInteractionAt: TestDates.days(-5)
        )
        let neverA = Make.person(store.context, given: "Nan", family: "Aaa")
        let neverB = Make.person(store.context, given: "Ned", family: "Bbb")

        let sorted = PeopleEngine.sort([neverB, lastWeek, neverA, yesterday], by: .recent)
        #expect(sorted.map(\.id) == [yesterday.id, lastWeek.id, neverA.id, neverB.id])
    }

    @Test("PeopleSort.needsFollowUp puts people with no open follow-up last")
    func sortByNeedsFollowUp() throws {
        let store = try TestStore()
        let soon = Make.person(store.context, given: "Sam", family: "Soon")
        let later = Make.person(store.context, given: "Lee", family: "Later")
        let onlyCompleted = Make.person(store.context, given: "Cam", family: "Aaa")
        let none = Make.person(store.context, given: "Nel", family: "Bbb")

        _ = Make.followUp(store.context, due: TestDates.dayStart(1), person: soon)
        _ = Make.followUp(store.context, due: TestDates.dayStart(3), person: later)
        _ = Make.followUp(
            store.context,
            due: TestDates.dayStart(-1),
            isCompleted: true,
            person: onlyCompleted
        )

        let sorted = PeopleEngine.sort([none, later, onlyCompleted, soon], by: .needsFollowUp)
        #expect(sorted.map(\.id) == [soon.id, later.id, onlyCompleted.id, none.id])
    }

    @Test("PeopleSort.priority puts favorites first, then high priority")
    func sortByPriority() throws {
        let store = try TestStore()
        let favoriteHigh = Make.person(
            store.context,
            given: "Fay",
            family: "Yankee",
            priority: .high,
            isFavorite: true
        )
        let favoriteNormal = Make.person(
            store.context,
            given: "Fen",
            family: "Zeta",
            isFavorite: true
        )
        let plainHigh = Make.person(store.context, given: "Hal", family: "Alpha", priority: .high)
        let plainNormal = Make.person(store.context, given: "Nia", family: "Beta")

        let sorted = PeopleEngine.sort(
            [plainNormal, favoriteNormal, plainHigh, favoriteHigh],
            by: .priority
        )
        #expect(
            sorted.map(\.id) == [favoriteHigh.id, favoriteNormal.id, plainHigh.id, plainNormal.id]
        )
    }

    @Test("PeopleSort.newest is newest createdAt first")
    func sortByNewest() throws {
        let store = try TestStore()
        let oldest = Make.person(
            store.context,
            given: "Ollie",
            family: "Oldest",
            createdAt: TestDates.days(-30)
        )
        let middle = Make.person(
            store.context,
            given: "Mel",
            family: "Middle",
            createdAt: TestDates.days(-10)
        )
        let newest = Make.person(
            store.context,
            given: "Noa",
            family: "Newest",
            createdAt: TestDates.days(-1)
        )

        let sorted = PeopleEngine.sort([middle, oldest, newest], by: .newest)
        #expect(sorted.map(\.id) == [newest.id, middle.id, oldest.id])
    }

    // MARK: - Sections

    @Test("alphabeticalSections groups by first letter and sorts # last")
    func alphabeticalSectionsGrouping() throws {
        let store = try TestStore()
        let adams = Make.person(store.context, given: "Zed", family: "Adams")
        let alvarez = Make.person(store.context, given: "Ana", family: "Alvarez")
        let baker = Make.person(store.context, given: "Bud", family: "Baker")
        let numeric = Make.person(store.context, given: "Vee", family: "9Volt")

        let sections = PeopleEngine.alphabeticalSections([baker, numeric, alvarez, adams])

        #expect(sections.map({ $0.key }) == ["A", "B", "#"])
        #expect(sections.first?.people.map(\.id) == [adams.id, alvarez.id])
        #expect(sections.last?.people.map(\.id) == [numeric.id])
    }

    @Test("alphabeticalSections lowercases-insensitively and returns no empty sections")
    func alphabeticalSectionsAreCaseInsensitive() throws {
        let store = try TestStore()
        let lower = Make.person(store.context, given: "Cid", family: "carter")
        let upper = Make.person(store.context, given: "Cal", family: "Carver")

        let sections = PeopleEngine.alphabeticalSections([upper, lower])
        #expect(sections.map({ $0.key }) == ["C"])
        #expect(sections.first?.people.count == 2)
        #expect(sections.allSatisfy({ !$0.people.isEmpty }))
    }
}

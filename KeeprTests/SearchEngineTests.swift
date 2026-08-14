import Foundation
import SwiftData
import Testing

@testable import Keepr

@MainActor
@Suite("SearchEngine")
struct SearchEngineTests {

    /// Builds one person per `SearchField`, each matching "delta" through a
    /// different route and nothing else.
    private func makeRankingFixture(_ store: TestStore) -> [Person] {
        let context = store.context

        let byName = Make.person(
            context,
            given: "Delta",
            family: "Nguyen",
            company: "Acme",
            jobTitle: "Engineer"
        )
        let byCompany = Make.person(context, given: "Zoe", family: "Quinn", company: "Delta Corp")
        let byTag = Make.person(context, given: "Yara", family: "Reed")
        Make.attach(Make.tag(context, name: "Delta Team", kind: .business), to: byTag)

        let byMemory = Make.person(context, given: "Xavier", family: "Stone")
        _ = Make.memory(context, content: "Flies delta routes weekly", person: byMemory)

        let byInteraction = Make.person(context, given: "Wes", family: "Turner")
        _ = Make.interaction(context, summary: "Reviewed delta pricing", person: byInteraction)

        let byNote = Make.person(
            context,
            given: "Vera",
            family: "Upton",
            notes: "Prefers delta over gamma"
        )

        let byFollowUp = Make.person(context, given: "Uma", family: "Vance")
        _ = Make.followUp(context, title: "Send delta report", due: TestDates.dayStart(2), person: byFollowUp)

        return [byFollowUp, byNote, byInteraction, byMemory, byTag, byCompany, byName]
    }

    // MARK: - Ranking

    @Test("Fields rank name > company > tag > memory > interaction > note > follow-up")
    func fieldRanking() throws {
        let store = try TestStore()
        let people = makeRankingFixture(store)

        let results = SearchEngine.search("delta", in: people)

        #expect(
            results.map(\.field) == [.name, .company, .tag, .memory, .interaction, .note, .followUp]
        )
    }

    @Test("Within one field, results are ordered by sort key")
    func sameFieldOrderedBySortKey() throws {
        let store = try TestStore()
        let baker = Make.person(store.context, given: "Delta", family: "Baker")
        let adams = Make.person(store.context, given: "Delta", family: "Adams")

        let results = SearchEngine.search("delta", in: [baker, adams])
        #expect(results.map(\.person.id) == [adams.id, baker.id])
    }

    // MARK: - Field-by-field matching

    @Test("A preferred-name match counts as a name match, with the subtitle as snippet")
    func preferredNameMatch() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Michael",
            family: "Nguyen",
            preferred: "Mikey",
            company: "Acme",
            jobTitle: "Owner"
        )

        let result = try #require(SearchEngine.bestMatch(for: person, query: "mikey"))
        #expect(result.field == .name)
        #expect(result.snippet == "Owner · Acme")
    }

    @Test("A job title match counts as a company match")
    func jobTitleMatch() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Zoe",
            family: "Quinn",
            company: "Acme",
            jobTitle: "Roofing Estimator"
        )

        let result = try #require(SearchEngine.bestMatch(for: person, query: "estimator"))
        #expect(result.field == .company)
        #expect(result.snippet == "Roofing Estimator · Acme")
    }

    @Test("A tag match reports the tag name as its snippet")
    func tagMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Yara", family: "Reed")
        Make.attach(Make.tag(store.context, name: "Referral Source", kind: .business), to: person)

        let result = try #require(SearchEngine.bestMatch(for: person, query: "referral"))
        #expect(result.field == .tag)
        #expect(result.snippet == "Referral Source")
    }

    @Test("A memory match reports the memory content as its snippet")
    func memoryMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Xavier", family: "Stone")
        _ = Make.memory(store.context, content: "Owns a roofing company", person: person)

        let result = try #require(SearchEngine.bestMatch(for: person, query: "roofing"))
        #expect(result.field == .memory)
        #expect(result.snippet == "Owns a roofing company")
    }

    @Test("An interaction summary match reports the interaction headline")
    func interactionSummaryMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Wes", family: "Turner")
        _ = Make.interaction(store.context, summary: "Talked about pricing", person: person)

        let result = try #require(SearchEngine.bestMatch(for: person, query: "pricing"))
        #expect(result.field == .interaction)
        #expect(result.snippet == "Talked about pricing")
    }

    @Test("An interaction raw note match reports the interaction headline")
    func interactionRawNoteMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Wes", family: "Turner")
        _ = Make.interaction(store.context, rawNote: "He mentioned the warehouse move", person: person)

        let result = try #require(SearchEngine.bestMatch(for: person, query: "warehouse"))
        #expect(result.field == .interaction)
        #expect(result.snippet == "He mentioned the warehouse move")
    }

    @Test("A notes match reports the note as its snippet")
    func notesMatch() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Vera",
            family: "Upton",
            notes: "Allergic to shellfish"
        )

        let result = try #require(SearchEngine.bestMatch(for: person, query: "shellfish"))
        #expect(result.field == .note)
        #expect(result.snippet == "Allergic to shellfish")
    }

    @Test("A howWeMet match is a note match and falls back to howWeMet for the snippet")
    func howWeMetMatch() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Vera",
            family: "Upton",
            howWeMet: "Met at the Denver trade show"
        )

        let result = try #require(SearchEngine.bestMatch(for: person, query: "trade show"))
        #expect(result.field == .note)
        #expect(result.snippet == "Met at the Denver trade show")
    }

    @Test("A follow-up note match still reports the follow-up title as its snippet")
    func followUpMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Uma", family: "Vance")
        _ = Make.followUp(
            store.context,
            title: "Ping about the numbers",
            note: "mention the skylight quote",
            due: TestDates.dayStart(2),
            person: person
        )

        let result = try #require(SearchEngine.bestMatch(for: person, query: "skylight"))
        #expect(result.field == .followUp)
        #expect(result.snippet == "Ping about the numbers")
    }

    @Test("A person who matches nothing produces no result")
    func noMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Ann", family: "Adams")

        #expect(SearchEngine.bestMatch(for: person, query: "zzzz") == nil)
        #expect(SearchEngine.search("zzzz", in: [person]).isEmpty)
    }

    @Test("Search is case-insensitive")
    func searchIgnoresCase() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Xavier", family: "Stone")
        _ = Make.memory(store.context, content: "Owns a roofing company", person: person)

        #expect(SearchEngine.search("ROOFING", in: [person]).count == 1)
    }

    // MARK: - Context independence

    @Test("Search ignores the business/personal switch")
    func searchIgnoresContextMode() throws {
        let store = try TestStore()
        let workPerson = Make.person(
            store.context,
            given: "Delta",
            family: "Nguyen",
            relationship: .business
        )
        let homePerson = Make.person(
            store.context,
            given: "Hal",
            family: "Home",
            relationship: .personal
        )
        let people = [workPerson, homePerson]

        // The People list would hide the business person in personal mode…
        #expect(PeopleEngine.visible(people, in: .personal).map(\.id) == [homePerson.id])
        // …but search still finds them.
        #expect(SearchEngine.search("delta", in: people).map(\.person.id) == [workPerson.id])
    }

    // MARK: - Empty queries

    @Test("An empty or whitespace-only query returns nothing")
    func blankQueryReturnsNothing() throws {
        let store = try TestStore()
        let people = makeRankingFixture(store)

        #expect(SearchEngine.search("", in: people).isEmpty)
        #expect(SearchEngine.search("   ", in: people).isEmpty)
        #expect(SearchEngine.search("\n\t ", in: people).isEmpty)
    }

    // MARK: - grouped

    @Test("grouped follows SearchField.allCases order and drops empty groups")
    func groupedOrderAndEmptyGroups() throws {
        let store = try TestStore()
        let people = makeRankingFixture(store)
        let results = SearchEngine.search("delta", in: people)

        let groups = SearchEngine.grouped(results)
        #expect(groups.map({ $0.field }) == SearchField.allCases)
        #expect(groups.allSatisfy({ $0.results.count == 1 }))
    }

    @Test("grouped omits fields nothing matched on")
    func groupedDropsUnmatchedFields() throws {
        let store = try TestStore()
        let byName = Make.person(store.context, given: "Delta", family: "Nguyen")
        let byMemory = Make.person(store.context, given: "Xavier", family: "Stone")
        _ = Make.memory(store.context, content: "Flies delta routes", person: byMemory)

        let groups = SearchEngine.grouped(SearchEngine.search("delta", in: [byMemory, byName]))
        #expect(groups.map({ $0.field }) == [.name, .memory])
        #expect(groups.first?.results.map(\.person.id) == [byName.id])
    }

    @Test("SearchField ranks by declaration order")
    func searchFieldComparable() {
        #expect(SearchField.name < SearchField.company)
        #expect(SearchField.company < SearchField.tag)
        #expect(SearchField.tag < SearchField.memory)
        #expect(SearchField.memory < SearchField.interaction)
        #expect(SearchField.interaction < SearchField.note)
        #expect(SearchField.note < SearchField.followUp)
    }
}

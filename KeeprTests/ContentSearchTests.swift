import Foundation
import SwiftData
import Testing
import UIKit

@testable import Keepr

/// The People list has to find people by what you know about them, not just by
/// what they're called. Someone searching "soccer" is looking for the client
/// whose daughter plays soccer — they already know they don't have a contact
/// named Soccer.
@Suite("Searching by what you know")
@MainActor
struct ContentSearchTests {

    @Test("A word from a memory finds the person")
    func memoryContentMatches() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake", relationship: .business)
        _ = Make.memory(
            store.context,
            content: "Daughter plays travel soccer on weekends",
            category: .family,
            person: person
        )

        #expect(PeopleEngine.matches(person, query: "soccer"))
    }

    @Test("The row can say why they matched")
    func matchReasonIsReturned() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake", relationship: .business)
        _ = Make.memory(
            store.context,
            content: "Daughter plays travel soccer on weekends",
            person: person
        )

        let reason = try #require(PeopleEngine.reason(for: person, query: "soccer"))
        #expect(reason.field == .memory)
        #expect(reason.snippet == "Daughter plays travel soccer on weekends")
    }

    @Test("A name match needs no explanation")
    func nameMatchesOutrankContent() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake", family: "Soccer")
        _ = Make.memory(store.context, content: "Plays soccer", person: person)

        #expect(PeopleEngine.reason(for: person, query: "soccer")?.field == .name)
    }

    @Test("Searching the People list reaches memories, not just names")
    func peopleListFiltersOnContent() throws {
        let store = try TestStore()
        let withMemory = Make.person(store.context, given: "Jake", relationship: .business)
        _ = Make.memory(store.context, content: "Son plays soccer", person: withMemory)
        let without = Make.person(store.context, given: "Dana", relationship: .business)

        let results = PeopleEngine.filter(
            [withMemory, without],
            mode: .business,
            query: "soccer"
        )

        #expect(results.map(\.givenName) == ["Jake"])
    }

    @Test("An interaction note is searchable too")
    func interactionNotesMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake")
        _ = Make.interaction(
            store.context,
            rawNote: "Talked about his bad knee before the squat session",
            person: person
        )

        let reason = try #require(PeopleEngine.reason(for: person, query: "knee"))
        #expect(reason.field == .interaction)
    }

    @Test("A file is found by what you named it")
    func fileTitlesMatch() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake")
        _ = Make.file(store.context, title: "InBody scan week 1", person: person)

        let reason = try #require(PeopleEngine.reason(for: person, query: "inbody"))
        #expect(reason.field == .file)
        #expect(reason.snippet == "InBody scan week 1")
    }

    @Test("An empty query explains nothing rather than everything")
    func emptyQueryHasNoReason() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake")

        #expect(PeopleEngine.reason(for: person, query: "   ") == nil)
    }

    @Test("Matches are case- and accent-insensitive")
    func matchingIsForgiving() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake")
        _ = Make.memory(store.context, content: "Loves Pilates", person: person)

        #expect(PeopleEngine.matches(person, query: "pilates"))
        #expect(PeopleEngine.matches(person, query: "PILATES"))
    }

    @Test("Nobody matches a word nobody mentioned")
    func irrelevantQueryFindsNothing() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake")
        _ = Make.memory(store.context, content: "Owns a roofing company", person: person)

        #expect(PeopleEngine.matches(person, query: "lacrosse") == false)
        #expect(PeopleEngine.reason(for: person, query: "lacrosse") == nil)
    }

    @Test("Every search-field symbol is a real SF Symbol")
    func fieldSymbolsExist() {
        for field in SearchField.allCases {
            #expect(UIImage(systemName: field.symbolName) != nil, "\(field.symbolName) does not exist")
        }
    }
}

/// The categories a fact can land in. Added because a trainer's brain dump was
/// filing injuries, availability and wins under "Other", which is the same as
/// not filing them.
@Suite("Memory categories")
@MainActor
struct MemoryCategoryTests {

    private let extractor = HeuristicCaptureExtractor()

    private func category(_ text: String) -> MemoryCategory {
        HeuristicCaptureExtractor.category(of: text)
    }

    @Test("An injury is health, not other")
    func injuriesAreHealth() {
        #expect(category("bad knee from college football") == .health)
        #expect(category("had shoulder surgery two years ago") == .health)
        #expect(category("allergic to shellfish") == .health)
    }

    @Test("A win is progress, and a plan is still a goal")
    func progressIsNotGoals() {
        #expect(category("lost 22 pounds since January") == .progress)
        #expect(category("wants to lose 20 pounds before the wedding") == .goals)
    }

    @Test("When they can train is its own shelf")
    func availabilityIsSchedule() {
        #expect(category("only trains mornings before work") == .schedule)
        #expect(category("travels every other week for work") == .schedule)
    }

    @Test("An introduction they could make is an opportunity")
    func introductionsAreOpportunities() {
        #expect(category("could introduce me to his gym owner friend") == .opportunity)
        #expect(category("knows someone at the hospital") == .opportunity)
    }

    @Test("How someone takes things is worth its own shelf")
    func communicationStyleIsCaptured() {
        #expect(category("blunt, no small talk") == .style)
        #expect(category("never answers calls, responds to texts fast") == .style)
    }

    @Test("Work still wins for an ordinary work fact")
    func workStillWorks() {
        #expect(category("owns a roofing company") == .work)
    }

    @Test("Nothing recognizable still lands in other rather than guessing")
    func unknownStaysOther() {
        #expect(category("purple front door") == .other)
    }

    @Test("Every category has a symbol, a title and an explanation")
    func categoriesAreFullyDescribed() {
        for category in MemoryCategory.allCases {
            #expect(UIImage(systemName: category.symbolName) != nil, "\(category.symbolName) missing")
            #expect(category.title.isEmpty == false)
            #expect(category.explanation.isEmpty == false)
        }
    }

    @Test("Every category appears exactly once in the display order")
    func orderCoversEveryCategory() {
        let ordered = Set(MemoryEngine.categoryOrder)

        #expect(ordered.count == MemoryEngine.categoryOrder.count, "no category listed twice")
        #expect(ordered == Set(MemoryCategory.allCases), "a missing category would never be shown")
    }

    @Test("A dump from a consultation spreads across the new shelves")
    func realDumpIsSpread() async {
        let dump = """
            Owns a roofing company with nine guys
            Bad lower back, cleared by his doctor for deadlifts
            Lost 14 pounds since we started
            Only trains before 7am
            Could introduce me to the gym owner in Maple Grove
            Blunt, hates being sold to
            """

        let facts = await extractor.facts(from: dump)
        let categories = Set(facts.map(\.category))

        #expect(facts.count == 6, "every line has to survive")
        #expect(categories.contains(.health))
        #expect(categories.contains(.progress))
        #expect(categories.contains(.opportunity))
        #expect(categories.contains(.style))
        #expect(categories.contains(.other) == false, "none of these should be unfiled")
    }

    // MARK: - Re-filing

    @Test("Facts stuck in Other are offered a home")
    func refileFindsMisfiledFacts() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let injury = Make.memory(
            store.context,
            content: "Bad knee, no running",
            category: .other,
            person: person
        )
        let nonsense = Make.memory(
            store.context,
            content: "Purple front door",
            category: .other,
            person: person
        )

        let proposals = MemoryEngine.refilable(person.memoryList) {
            HeuristicCaptureExtractor.category(of: $0)
        }

        let touchesNonsense = proposals.contains { $0.memory.id == nonsense.id }

        #expect(proposals.count == 1)
        #expect(proposals.first?.memory.id == injury.id)
        #expect(proposals.first?.category == .health)
        #expect(touchesNonsense == false)
    }

    @Test("Facts the user already filed by hand are left alone")
    func refileNeverOverwritesADecision() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = Make.memory(
            store.context,
            content: "Owns a roofing company",
            category: .personal,
            person: person
        )

        let proposals = MemoryEngine.refilable(person.memoryList) {
            HeuristicCaptureExtractor.category(of: $0)
        }

        #expect(proposals.isEmpty, "a deliberate filing decision is not a mistake to repair")
    }

    @Test("Archived facts aren't dragged back into view")
    func refileSkipsArchived() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = Make.memory(
            store.context,
            content: "Bad knee",
            category: .other,
            isArchived: true,
            person: person
        )

        let proposals = MemoryEngine.refilable(person.memoryList) {
            HeuristicCaptureExtractor.category(of: $0)
        }

        #expect(proposals.isEmpty)
    }
}

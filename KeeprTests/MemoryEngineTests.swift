import Foundation
import SwiftData
import Testing

@testable import Keepr

/// A brain dump can put fifteen facts on someone in one go. What decides
/// whether that helps or ruins the profile is what gets shown and what gets
/// filed — so these are the rules that keep a full record readable.
@Suite("What you know")
@MainActor
struct MemoryEngineTests {

    private func memory(
        _ store: TestStore,
        _ content: String,
        _ category: MemoryCategory,
        importance: Priority = .normal,
        createdAt: Date = TestDates.now,
        person: Person
    ) -> Memory {
        Make.memory(
            store.context,
            content: content,
            category: category,
            importance: importance,
            createdAt: createdAt,
            person: person
        )
    }

    // MARK: - What the profile shows

    @Test("The profile shows a handful, not everything")
    func headlineIsCapped() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        for index in 0..<12 {
            _ = memory(store, "Fact \(index)", .other, person: person)
        }

        #expect(MemoryEngine.headline(person.visibleMemories).count == MemoryEngine.headlineLimit)
    }

    @Test("It spreads across categories, so it reads like a person")
    func headlineSpansCategories() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        // Three family facts added most recently would otherwise crowd out
        // everything else and make the profile look like a family tree.
        _ = memory(store, "Two kids", .family, createdAt: TestDates.days(-1), person: person)
        _ = memory(store, "Wife is Ana", .family, createdAt: TestDates.days(-1), person: person)
        _ = memory(store, "Sister nearby", .family, createdAt: TestDates.days(-1), person: person)
        _ = memory(store, "Runs a roofing crew", .work, createdAt: TestDates.days(-2), person: person)
        _ = memory(store, "Wants to lose 20 lb", .goals, createdAt: TestDates.days(-3), person: person)

        let categories = Set(MemoryEngine.headline(person.visibleMemories).map(\.category))

        #expect(categories.count == 3, "one from each of three categories")
        #expect(categories.contains(.work))
        #expect(categories.contains(.goals))
    }

    @Test("Starred facts come first, whatever their category")
    func starredWinsFirstPlace() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = memory(store, "Ordinary", .work, createdAt: TestDates.now, person: person)
        let starred = memory(
            store,
            "Allergic to shellfish",
            .preferences,
            importance: .high,
            createdAt: TestDates.days(-30),
            person: person
        )

        #expect(MemoryEngine.headline(person.visibleMemories).first?.id == starred.id)
    }

    @Test("With only one category, depth is allowed rather than showing one line")
    func repeatsAreAllowedOnceEveryCategoryHasATurn() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        for index in 0..<4 {
            _ = memory(store, "Work fact \(index)", .work, person: person)
        }

        #expect(MemoryEngine.headline(person.visibleMemories).count == 3)
    }

    @Test("Archived facts are held back from the profile")
    func archivedIsNotShown() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let archived = Make.memory(
            store.context,
            content: "Old news",
            isArchived: true,
            person: person
        )

        #expect(MemoryEngine.headline([archived]).isEmpty)
        #expect(MemoryEngine.grouped([archived]).isEmpty)
    }

    // MARK: - The full list

    @Test("Everything is filed by category, in reading order")
    func groupsFollowReadingOrder() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = memory(store, "Loves fishing", .interests, person: person)
        _ = memory(store, "Runs a roofing crew", .work, person: person)
        _ = memory(store, "Two kids", .family, person: person)

        let groups = MemoryEngine.grouped(person.visibleMemories)

        #expect(groups.map(\.category) == [.work, .family, .interests])
        #expect(groups.allSatisfy { $0.count == 1 })
    }

    @Test("Within a category, starred facts sit at the top")
    func groupsSortByImportance() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = memory(store, "Plain", .work, createdAt: TestDates.now, person: person)
        _ = memory(store, "Crucial", .work, importance: .high, createdAt: TestDates.days(-40), person: person)

        let group = try #require(MemoryEngine.grouped(person.visibleMemories).first)

        #expect(group.memories.first?.content == "Crucial")
    }

    @Test("Empty categories aren't shown as zeroes")
    func emptyCategoriesAreDropped() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = memory(store, "Runs a roofing crew", .work, person: person)

        #expect(MemoryEngine.grouped(person.visibleMemories).count == 1)
    }

    @Test("Counts are written the way they'll be read")
    func totalsReadNaturally() {
        #expect(MemoryEngine.totalLabel(1) == "1 thing")
        #expect(MemoryEngine.totalLabel(14) == "14 things")
    }
}

/// The brain dump has a different contract from a quick note: a note should
/// suggest little and stay quiet when unsure, but a dump has to keep
/// everything, because facts silently dropped are worse than none at all.
@Suite("Brain dump")
struct BrainDumpExtractionTests {

    private let extractor = HeuristicCaptureExtractor()

    @Test("Every line survives, including ones no rule recognizes")
    func nothingIsSilentlyDropped() async {
        let dump = """
            Runs a roofing crew of nine
            Daughter getting married in the spring
            Purple front door
            """

        let facts = await extractor.facts(from: dump)

        #expect(facts.count == 3)
        #expect(facts.contains { $0.content.contains("Purple front door") })
    }

    @Test("Dictation with no punctuation still comes apart")
    func spokenInputIsSplit() async {
        let spoken = "he owns a roofing company and he's got two kids "
            + "also he wants to lose 20 pounds before the wedding"

        let facts = await extractor.facts(from: spoken)

        #expect(facts.count >= 3, "spoken joins have to count as breaks")
        #expect(facts.contains { $0.content.localizedCaseInsensitiveContains("roofing") })
        #expect(facts.contains { $0.content.localizedCaseInsensitiveContains("20 pounds") })
    }

    @Test("Facts land in sensible categories")
    func categoriesAreAssigned() async {
        let dump = """
            Owns a roofing company
            Two kids, 7 and 10
            Wants to lose 20 lb
            Allergic to shellfish
            Birthday is March 14
            """

        let facts = await extractor.facts(from: dump)
        let byCategory = Dictionary(grouping: facts, by: \.category)

        #expect(byCategory[.work]?.isEmpty == false)
        #expect(byCategory[.family]?.isEmpty == false)
        #expect(byCategory[.goals]?.isEmpty == false)
        #expect(byCategory[.preferences]?.isEmpty == false)
        #expect(byCategory[.importantDate]?.isEmpty == false)
    }

    @Test("A short list with commas stays one fact, not three")
    func shortListsAreNotOverSplit() async {
        let facts = await extractor.facts(from: "Two kids, 7 and 10")

        #expect(facts.count == 1)
    }

    @Test("Everything arrives switched on, ready to keep")
    func draftsStartSelected() async {
        let facts = await extractor.facts(from: "Runs a roofing crew\nLoves fishing")

        #expect(facts.allSatisfy(\.isSelected))
    }

    @Test("An empty dump produces nothing rather than an empty card")
    func emptyInputIsEmpty() async {
        #expect(await extractor.facts(from: "   \n  ").isEmpty)
    }

    @Test("A long dump isn't truncated to a handful")
    func longDumpsSurvive() async {
        let lines = (1...20).map { "Fact number \($0) about them" }.joined(separator: "\n")

        let facts = await extractor.facts(from: lines)

        #expect(facts.count == 20)
    }
}

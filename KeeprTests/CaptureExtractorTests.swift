import Foundation
import Testing

@testable import Keepr

/// The on-device extractor is the most rule-heavy code in the app and the part
/// a future model-backed extractor has to match, so its behaviour is pinned here.
@Suite("Heuristic capture extraction")
struct CaptureExtractorTests {

    private let extractor = HeuristicCaptureExtractor()
    private let calendar = TestDates.calendar
    private let now = TestDates.now

    /// The example the product is designed around.
    private let canonical = """
        Talked to Jake at Life Time. He owns a roofing company, wants to lose 20 pounds \
        and said to text him next week about training.
        """

    // MARK: - Word boundaries

    @Test("Keywords match on word boundaries, not substrings")
    func wordBoundaryMatching() {
        let haystack = HeuristicCaptureExtractor.matchable("Told him something about it.")
        #expect(HeuristicCaptureExtractor.containsWord(haystack, "something"))
        // "something" contains the letters of "met" — that must not count.
        #expect(!HeuristicCaptureExtractor.containsWord(haystack, "met"))
    }

    @Test("Punctuation doesn't hide a keyword")
    func matchableStripsPunctuation() {
        let haystack = HeuristicCaptureExtractor.matchable("We met, finally!")
        #expect(HeuristicCaptureExtractor.containsWord(haystack, "met"))
    }

    @Test("A note with no interaction verb stays Other rather than guessing")
    func kindDefaultsToOther() {
        #expect(HeuristicCaptureExtractor.kind(of: "Told him something about pricing") == .other)
    }

    @Test("Interaction kind comes from the verb used", arguments: [
        ("Called Sarah about the invoice", InteractionKind.call),
        ("Texted Mike the address", InteractionKind.text),
        ("Emailed the proposal over", InteractionKind.email),
        ("Zoom with the team", InteractionKind.meeting),
        ("Ran into Dana at the store", InteractionKind.inPerson)
    ])
    func kindDetection(input: String, expected: InteractionKind) {
        #expect(HeuristicCaptureExtractor.kind(of: input) == expected)
    }

    // MARK: - Names

    @Test("A capitalized name after a conversational verb is picked up")
    func personNameDetection() {
        #expect(HeuristicCaptureExtractor.personName(in: canonical) == "Jake")
        #expect(HeuristicCaptureExtractor.personName(in: "Met with Sarah Miller today") == "Sarah Miller")
        #expect(HeuristicCaptureExtractor.personName(in: "Called Mom") == "Mom")
    }

    @Test("No name is suggested when nothing looks like one")
    func personNameAbsent() {
        #expect(HeuristicCaptureExtractor.personName(in: "Need to order more coffee filters") == nil)
        #expect(HeuristicCaptureExtractor.personName(in: "talked to the bank again") == nil)
    }

    // MARK: - Segmentation

    @Test("A sentence splits into its separate claims")
    func clauseSplitting() {
        let clauses = HeuristicCaptureExtractor.clauses(
            "He owns a roofing company, wants to lose 20 pounds and said to text him next week."
        )
        #expect(clauses == [
            "He owns a roofing company",
            "wants to lose 20 pounds",
            "said to text him next week"
        ])
    }

    @Test("Summary is the first sentence")
    func summaryIsFirstSentence() {
        #expect(HeuristicCaptureExtractor.summary(from: canonical) == "Talked to Jake at Life Time.")
    }

    @Test("A long single sentence is truncated on a word boundary")
    func summaryTruncates() {
        let long = String(repeating: "word ", count: 60)
        let summary = HeuristicCaptureExtractor.summary(from: long, limit: 40)
        #expect(summary.count <= 41)
        #expect(summary.hasSuffix("…"))
    }

    // MARK: - Facts

    @Test("Statements about the person become categorized memories")
    func memoryExtraction() throws {
        let owns = try #require(HeuristicCaptureExtractor.memory(from: "He owns a roofing company"))
        #expect(owns.content == "Owns a roofing company")
        #expect(owns.category == .work)

        let goal = try #require(HeuristicCaptureExtractor.memory(from: "wants to lose 20 pounds"))
        #expect(goal.content == "Wants to lose 20 pounds")
        #expect(goal.category == .goals)

        let family = try #require(HeuristicCaptureExtractor.memory(from: "his daughter is getting married"))
        #expect(family.category == .importantDate)
    }

    @Test("A clause that states nothing about the person is not a memory")
    func memoryRejectsNonFacts() {
        #expect(HeuristicCaptureExtractor.memory(from: "it was a good chat") == nil)
        #expect(HeuristicCaptureExtractor.memory(from: "ok") == nil)
    }

    // MARK: - Actions

    @Test("Promise phrasing is recognised as an action, statements aren't")
    func actionDetection() {
        #expect(HeuristicCaptureExtractor.isAction("said to text him next week"))
        #expect(HeuristicCaptureExtractor.isAction("I need to send the proposal"))
        #expect(!HeuristicCaptureExtractor.isAction("He owns a roofing company"))
    }

    @Test("Action titles drop the lead-in and read as instructions")
    func actionTitles() {
        #expect(
            HeuristicCaptureExtractor.actionTitle(from: "said to text him next week about training")
                == "Text him next week about training"
        )
        #expect(
            HeuristicCaptureExtractor.actionTitle(from: "I need to send the proposal")
                == "Send the proposal"
        )
    }

    // MARK: - Dates

    @Test("Relative phrases resolve against the supplied now, at start of day")
    func relativeDueDates() {
        let nextWeek = HeuristicCaptureExtractor.dueDate(
            in: "text him next week", now: now, calendar: calendar
        )
        #expect(nextWeek == TestDates.dayStart(7))

        let tomorrow = HeuristicCaptureExtractor.dueDate(
            in: "call her tomorrow", now: now, calendar: calendar
        )
        #expect(tomorrow == TestDates.dayStart(1))
    }

    @Test("No date phrase means no invented deadline")
    func absentDueDate() {
        #expect(
            HeuristicCaptureExtractor.dueDate(in: "text him about training", now: now, calendar: calendar)
                == nil
        )
    }

    @Test("The default due date is three days out")
    func defaultDue() {
        #expect(HeuristicCaptureExtractor.defaultDue(from: now, calendar: calendar) == TestDates.dayStart(3))
    }

    // MARK: - End to end

    @Test("The canonical note becomes a person, two facts and a dated follow-up")
    func canonicalExtraction() async throws {
        let draft = await extractor.extract(from: canonical, now: now)

        #expect(draft.suggestedName == "Jake")
        #expect(draft.summary == "Talked to Jake at Life Time.")

        let contents = draft.memories.map(\.content)
        #expect(contents.contains("Owns a roofing company"))
        #expect(contents.contains("Wants to lose 20 pounds"))
        #expect(draft.memories.allSatisfy(\.isSelected))

        let followUp = try #require(draft.followUp)
        #expect(followUp.title == "Text him next week about training")
        #expect(followUp.hasTime == false)
        #expect(draft.hasSuggestions)
    }

    @Test("Empty input produces an empty draft rather than a guess")
    func emptyInput() async {
        let draft = await extractor.extract(from: "   ", now: now)
        #expect(draft.summary.isEmpty)
        #expect(draft.memories.isEmpty)
        #expect(draft.followUp == nil)
        #expect(draft.occurredAt == now)
    }

    @Test("Duplicate facts are collapsed and suggestions are capped")
    func draftsAreDeduplicated() async {
        let repeated = Array(repeating: "He owns a roofing company.", count: 8).joined(separator: " ")
        let draft = await extractor.extract(from: repeated, now: now)
        #expect(draft.memories.count == 1)
    }
}

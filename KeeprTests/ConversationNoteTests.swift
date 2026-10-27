import Foundation
import SwiftData
import Testing
import UIKit

@testable import Keepr

/// Titles are what make a pile of pasted write-ups usable a year later, and
/// they're the one thing the user won't type. So the guess has to be right
/// often and never embarrassing.
@Suite("Note titles")
struct NoteTitlerTests {

    // MARK: - An existing heading wins

    @Test("A heading at the top is the title")
    func headingIsUsedVerbatim() {
        let text = """
            Consultation — Jake Mathers
            Goals: drop 20 lb before the wedding in June.
            """

        #expect(NoteTitler.suggest(from: text) == "Consultation — Jake Mathers")
    }

    @Test("Markdown and bullet decoration is stripped off a heading")
    func headingDecorationIsRemoved() {
        #expect(NoteTitler.heading(in: "## Quarterly check-in\nNumbers were good.")
                == "Quarterly check-in")
        #expect(NoteTitler.heading(in: "• Pricing conversation\nHe pushed back.")
                == "Pricing conversation")
    }

    @Test("A sentence is not a heading, however short")
    func prosePassesThrough() {
        #expect(NoteTitler.heading(in: "He wants to start in January.") == nil)
        #expect(NoteTitler.heading(in: "Jake called about the roof, and I said I'd check.") == nil)
    }

    @Test("A bare document label is not a title")
    func documentLabelsAreRejected() {
        #expect(NoteTitler.heading(in: "Summary\nHe owns a roofing company.") == nil)
        #expect(NoteTitler.heading(in: "Meeting Notes\nWe talked pricing.") == nil)
        #expect(NoteTitler.heading(in: "Action Items\nSend the quote.") == nil)
    }

    @Test("A one-word first line is a label, not a heading")
    func singleWordIsNotAHeading() {
        #expect(NoteTitler.heading(in: "Transcript\nSo how have you been?") == nil)
    }

    @Test("A very long first line is prose, not a heading")
    func longFirstLineIsNotAHeading() {
        let long = String(repeating: "word ", count: 30)
        #expect(NoteTitler.heading(in: long + "\nmore") == nil)
    }

    // MARK: - Falling back to what kind of conversation it was

    @Test("With no heading, the kind of conversation plus the date is the title")
    func kindAndDateIsUsed() {
        let text = "Ran the initial consultation with him today. He wants to drop 20 lb."

        let title = NoteTitler.suggest(from: text, on: TestDates.now)
        #expect(title.hasPrefix("Consultation · "))
    }

    @Test("The most specific kind wins over a vaguer one later on")
    func specificKindWins() {
        let text = "Did his consultation this morning, then a quick meeting with his wife."

        #expect(NoteTitler.kind(in: text) == "Consultation")
    }

    @Test("A kind mentioned far down the page doesn't retitle the note")
    func distantKeywordsAreIgnored() {
        let text = "He pushed back hard on the number. "
            + String(repeating: "Filler sentence here. ", count: 40)
            + "We should book a consultation."

        #expect(NoteTitler.kind(in: text) == nil)
    }

    // MARK: - Falling back to the opening line

    @Test("With nothing else to go on, the opening sentence is the title")
    func openingSentenceIsUsed() {
        let text = "He pushed back hard on the number and wants to think about it."

        let title = NoteTitler.suggest(from: text)
        #expect(title.hasPrefix("He pushed back hard"))
        #expect(title.contains("·") == false, "this isn't a kind-and-date title")
    }

    @Test("A long opening sentence is cut at a word, not mid-word")
    func openingSentenceIsTruncatedCleanly() throws {
        let text = "He pushed back hard on the number because his partner wants "
            + "to see the whole year budgeted before they commit to anything."

        let title = try #require(NoteTitler.openingSentence(in: text))
        #expect(title.hasSuffix("…"))
        #expect(title.count <= 54)
        #expect(title.contains("  ") == false)
    }

    @Test("Empty text suggests nothing rather than a guess")
    func emptyTextHasNoTitle() {
        #expect(NoteTitler.suggest(from: "") == "")
        #expect(NoteTitler.suggest(from: "   \n  ") == "")
    }

    @Test("A note with only screenshots still gets a readable name")
    func imageOnlyNotesAreNamed() {
        #expect(NoteTitler.imageOnlyTitle(on: TestDates.now).hasPrefix("Notes · "))
    }

    @Test("Every note source has a real symbol and a title")
    func sourcesAreDescribed() {
        for source in NoteSource.allCases {
            #expect(UIImage(systemName: source.symbolName) != nil, "\(source.symbolName) missing")
            #expect(source.title.isEmpty == false)
        }
    }
}

/// The record itself: how notes sort, what a collapsed card shows, and what
/// happens to the pieces when something is deleted.
@Suite("Conversation notes")
@MainActor
struct ConversationNoteTests {

    @Test("Pinned notes lead, then the most recent")
    func pinnedFirstThenRecent() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = Make.note(store.context, title: "Old", occurredAt: TestDates.days(-90), person: person)
        _ = Make.note(store.context, title: "Newest", occurredAt: TestDates.days(-1), person: person)
        _ = Make.note(
            store.context,
            title: "The consultation",
            occurredAt: TestDates.days(-60),
            isPinned: true,
            person: person
        )

        #expect(person.noteList.map(\.title) == ["The consultation", "Newest", "Old"])
    }

    @Test("A collapsed card shows a flattened preview, not blank lines")
    func previewFlattensLineBreaks() throws {
        let store = try TestStore()
        let note = Make.note(
            store.context,
            title: "Consultation",
            body: "Goals\n\n- Drop 20 lb\n\n- Bench 225"
        )

        #expect(note.preview == "Goals · - Drop 20 lb · - Bench 225")
        #expect(note.preview.contains("\n") == false)
    }

    @Test("A long preview is cut at a word boundary")
    func previewIsTruncated() throws {
        let store = try TestStore()
        let note = Make.note(
            store.context,
            title: "Long one",
            body: String(repeating: "something worth remembering ", count: 40)
        )

        #expect(note.preview.hasSuffix("…"))
        #expect(note.preview.count <= 182)
    }

    @Test("The card says how much is behind it")
    func lengthLabelCountsWords() throws {
        let store = try TestStore()
        let note = Make.note(store.context, title: "Notes", body: "one two three")

        #expect(note.lengthLabel == "3 words")
    }

    @Test("A note of screenshots reports the images instead of a word count")
    func lengthLabelHandlesImageOnlyNotes() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let note = Make.note(store.context, title: "Screens", body: "", person: person)
        _ = Make.file(store.context, title: "Shot", person: person, conversationNote: note)

        #expect(note.hasBody == false)
        #expect(note.lengthLabel == "1 image")
    }

    @Test("A note with both says so")
    func lengthLabelCombines() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let note = Make.note(store.context, title: "Both", body: "one two", person: person)
        _ = Make.file(store.context, title: "A", person: person, conversationNote: note)
        _ = Make.file(store.context, title: "B", person: person, conversationNote: note)

        #expect(note.lengthLabel == "2 words · 2 images")
    }

    @Test("A note's screenshots don't also show up loose in Files")
    func noteImagesAreNotLooseFiles() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let note = Make.note(store.context, title: "Summary", person: person)
        _ = Make.file(store.context, title: "Screenshot", person: person, conversationNote: note)
        _ = Make.file(store.context, title: "InBody scan", person: person)

        #expect(person.fileList.map(\.title) == ["InBody scan"])
        #expect(note.fileList.count == 1)
    }

    @Test("Deleting a note takes its screenshots with it")
    func deletingANoteRemovesItsImages() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let note = Make.note(store.context, title: "Summary", person: person)
        _ = Make.file(store.context, title: "Screenshot", person: person, conversationNote: note)
        try store.context.save()

        store.context.delete(note)
        try store.context.save()

        let files = try store.context.fetch(FetchDescriptor<PersonFile>())
        #expect(files.isEmpty)
    }

    @Test("Deleting someone removes their notes")
    func notesCascadeFromPerson() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = Make.note(store.context, title: "Consultation", person: person)
        try store.context.save()

        store.context.delete(person)
        try store.context.save()

        let notes = try store.context.fetch(FetchDescriptor<ConversationNote>())
        #expect(notes.isEmpty)
    }

    @Test("A note outlives the interaction it was logged with")
    func notesOutliveTheirInteraction() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let interaction = Make.interaction(store.context, kind: .meeting, person: person)
        _ = Make.note(
            store.context,
            title: "Consultation",
            person: person,
            sourceInteraction: interaction
        )
        try store.context.save()

        store.context.delete(interaction)
        try store.context.save()

        #expect(person.noteList.count == 1)
        #expect(person.noteList.first?.sourceInteraction == nil)
    }

    // MARK: - Search

    @Test("A phrase from inside a write-up finds the person")
    func noteBodiesAreSearchable() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake")
        _ = Make.note(
            store.context,
            title: "Consultation · Mar 3",
            body: "He mentioned his brother runs a landscaping crew in Maple Grove.",
            person: person
        )

        let reason = try #require(PeopleEngine.reason(for: person, query: "landscaping"))
        #expect(reason.field == .writeUp)
        #expect(reason.snippet == "Consultation · Mar 3", "a line from mid-transcript reads as nonsense alone")
    }

    @Test("A note title is searchable too")
    func noteTitlesAreSearchable() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake")
        _ = Make.note(store.context, title: "Pricing conversation", person: person)

        #expect(PeopleEngine.matches(person, query: "pricing"))
    }
}

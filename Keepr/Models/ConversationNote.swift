import Foundation
import SwiftData

/// A write-up of a conversation, kept whole.
///
/// Distinct from `Interaction`, which records *that* a conversation happened,
/// and from `Memory`, which holds one fact pulled out of it. This is the
/// document: a consultation write-up, a recorder's summary, meeting notes
/// pasted in from somewhere else. The user didn't author the words and
/// shouldn't have to retype them.
///
/// Kept as its own model rather than a long `Interaction.rawNote` for one
/// practical reason: the timeline loads every interaction it shows, and a
/// thousand-word transcript in that path would make a profile slower every
/// time another meeting is added. `body` is external-storage so a list of note
/// titles never pulls the text in.
@Model
final class ConversationNote {

    var id: UUID = UUID()

    /// What this was. Suggested from the text, always editable — a note with a
    /// bad title is a note nobody opens again.
    var title: String = ""

    /// The write-up itself, exactly as it arrived. Never rewritten.
    ///
    /// A plain stored string: `.externalStorage` is specified for binary data,
    /// and a model that might not load is a worse trade than a large row.
    /// Keeping notes out of `Interaction` already keeps them off the timeline's
    /// path, which was the expensive part.
    var body: String = ""

    var sourceRaw: String = NoteSource.pasted.rawValue

    /// When the conversation happened, not when it was pasted in.
    var occurredAt: Date = Date()
    var createdAt: Date = Date()
    var updatedAt: Date = Date()

    /// Pinned notes lead on the profile — the consultation you keep going back
    /// to shouldn't sink as newer notes arrive.
    var isPinned: Bool = false

    var person: Person?

    /// The logged interaction this note belongs to, when the user chose to log
    /// one at the same time.
    var sourceInteraction: Interaction?

    /// Screenshots of the write-up, for summaries that only exist as pictures.
    @Relationship(deleteRule: .cascade, inverse: \PersonFile.conversationNote)
    var files: [PersonFile]?

    init(
        title: String,
        body: String,
        source: NoteSource = .pasted,
        occurredAt: Date = Date(),
        createdAt: Date = Date(),
        isPinned: Bool = false,
        person: Person? = nil,
        sourceInteraction: Interaction? = nil
    ) {
        self.id = UUID()
        self.title = title
        self.body = body
        self.sourceRaw = source.rawValue
        self.occurredAt = occurredAt
        self.createdAt = createdAt
        self.updatedAt = createdAt
        self.isPinned = isPinned
        self.person = person
        self.sourceInteraction = sourceInteraction
        self.files = []
    }
}

extension ConversationNote {

    var source: NoteSource {
        get { NoteSource(rawValue: sourceRaw) ?? .pasted }
        set { sourceRaw = newValue.rawValue }
    }

    var fileList: [PersonFile] {
        (files ?? []).sorted { $0.capturedAt < $1.capturedAt }
    }

    /// The two or three lines shown on a collapsed card.
    ///
    /// Takes the start of the body with its line breaks flattened, so a
    /// bulleted summary doesn't show as one bullet and three blank lines.
    var preview: String {
        let flattened = body
            .split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")

        guard flattened.count > 180 else { return flattened }
        let cut = flattened.prefix(180)
        guard let lastSpace = cut.lastIndex(of: " ") else { return String(cut) + "…" }
        return String(cut[cut.startIndex..<lastSpace]) + "…"
    }

    /// "1,240 words" — how much is behind the card, so a tap is an informed one.
    var lengthLabel: String {
        let words = body.split(whereSeparator: \.isWhitespace).count
        if words == 0 { return fileList.isEmpty ? "Empty" : imageLabel }
        let counted = words == 1 ? "1 word" : "\(words.formatted()) words"
        return fileList.isEmpty ? counted : "\(counted) · \(imageLabel)"
    }

    private var imageLabel: String {
        let count = fileList.count
        return count == 1 ? "1 image" : "\(count) images"
    }

    var hasBody: Bool {
        !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var searchableText: String {
        [title, body].joined(separator: " ")
    }

    func touch(_ date: Date = Date()) {
        updatedAt = date
    }
}

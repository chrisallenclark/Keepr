import Foundation
import SwiftData

/// A meaningful interaction the user deliberately recorded.
///
/// Keepr never reads Messages, Mail or call history — this timeline is entirely
/// user-authored.
@Model
final class Interaction {

    var id: UUID = UUID()

    /// When the interaction happened (user-editable).
    var occurredAt: Date = Date()
    /// When it was recorded.
    var createdAt: Date = Date()

    var kindRaw: String = InteractionKind.other.rawValue

    /// Optional short headline, e.g. "Talked about pricing".
    var title: String?
    /// Exactly what the user typed or dictated. Never rewritten.
    var rawNote: String?
    /// A condensed version — hand-written today, extractor-suggested later.
    var summary: String?

    /// True when this came in through Quick Capture rather than the person's profile.
    var isQuickCapture: Bool = false

    var person: Person?

    @Relationship(deleteRule: .nullify, inverse: \Memory.sourceInteraction)
    var memories: [Memory]?

    @Relationship(deleteRule: .nullify, inverse: \FollowUp.sourceInteraction)
    var followUps: [FollowUp]?

    init(
        kind: InteractionKind = .other,
        occurredAt: Date = Date(),
        title: String? = nil,
        rawNote: String? = nil,
        summary: String? = nil,
        isQuickCapture: Bool = false,
        person: Person? = nil
    ) {
        self.id = UUID()
        self.kindRaw = kind.rawValue
        self.occurredAt = occurredAt
        self.createdAt = Date()
        self.title = title
        self.rawNote = rawNote
        self.summary = summary
        self.isQuickCapture = isQuickCapture
        self.person = person
        self.memories = []
        self.followUps = []
    }
}

extension Interaction {

    var kind: InteractionKind {
        get { InteractionKind(rawValue: kindRaw) ?? .other }
        set { kindRaw = newValue.rawValue }
    }

    var memoryList: [Memory] { memories ?? [] }
    var followUpList: [FollowUp] { followUps ?? [] }

    /// Best single line to show in a timeline row.
    var headline: String {
        for candidate in [title, summary, rawNote] {
            if let text = candidate?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
                return text
            }
        }
        return kind.title
    }

    /// Secondary line, only when it adds something the headline doesn't.
    var detail: String? {
        let head = headline
        for candidate in [summary, rawNote] {
            if let text = candidate?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty, text != head {
                return text
            }
        }
        return nil
    }

    /// Free-text used by search.
    var searchableText: String {
        [title, summary, rawNote, kind.title]
            .compactMap { $0 }
            .joined(separator: " ")
    }
}

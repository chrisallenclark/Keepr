import Foundation
import SwiftData

/// A single structured fact worth remembering about someone.
///
/// Deliberately not one big free-text blob: category and importance are what let
/// the profile stay scannable and let search get smarter later.
@Model
final class Memory {

    var id: UUID = UUID()

    /// One fact, one sentence. "Owns a roofing company."
    var content: String = ""

    /// What the fact is *about*, when it's the kind of fact that has a name —
    /// "Favorite restaurant", "Daughter's soccer team", "Travel plans".
    ///
    /// Optional, and optional on purpose. Plenty of what's worth remembering
    /// about someone is just a sentence, and forcing every one of those into a
    /// key/value pair would mean inventing a label to store "she's been through
    /// a rough year". A labeled fact becomes its own row on the profile; an
    /// unlabeled one stays a bullet, and both are equally first-class.
    var label: String?

    var categoryRaw: String = MemoryCategory.other.rawValue
    var importanceRaw: String = Priority.normal.rawValue

    var createdAt: Date = Date()
    var updatedAt: Date = Date()

    /// Archived memories stay searchable but drop off the profile.
    var isArchived: Bool = false

    var person: Person?
    /// The interaction this fact came out of, when it came from one.
    var sourceInteraction: Interaction?

    init(
        content: String,
        label: String? = nil,
        category: MemoryCategory = .other,
        importance: Priority = .normal,
        createdAt: Date = Date(),
        person: Person? = nil,
        sourceInteraction: Interaction? = nil
    ) {
        self.id = UUID()
        self.content = content
        self.label = label.flatMap(\.nilIfBlank)
        self.categoryRaw = category.rawValue
        self.importanceRaw = importance.rawValue
        self.createdAt = createdAt
        self.updatedAt = createdAt
        self.isArchived = false
        self.person = person
        self.sourceInteraction = sourceInteraction
    }
}

extension Memory {

    var category: MemoryCategory {
        get { MemoryCategory(rawValue: categoryRaw) ?? .other }
        set { categoryRaw = newValue.rawValue }
    }

    var importance: Priority {
        get { Priority(rawValue: importanceRaw) ?? .normal }
        set { importanceRaw = newValue.rawValue }
    }

    /// True when this fact reads as "label → value" on the profile.
    var isLabeled: Bool { label?.isEmpty == false }
}

/// A proposed memory produced by capture extraction, before the user confirms it.
/// Nothing reaches a person's record without passing through this review step.
struct MemoryDraft: Identifiable, Hashable, Sendable {
    var id = UUID()
    var content: String
    /// Proposed label, when extraction was confident enough to name the fact.
    /// Editable before it's saved, like everything else in a draft.
    var label: String?
    var category: MemoryCategory = .other
    var importance: Priority = .normal
    var isSelected: Bool = true
}

extension MemoryDraft {
    /// The label as a plain string, for a text field.
    ///
    /// A `TextField` wants `String` and the model wants `String?`, and the
    /// difference between "" and nil is exactly the difference between a fact
    /// with an empty heading and one with none. This is the single place that
    /// conversion happens.
    var labelText: String {
        get { label ?? "" }
        set { label = newValue.nilIfBlank }
    }
}

// MARK: - Labels

extension String {
    /// Trimmed, or `nil` when there was nothing but whitespace.
    ///
    /// A stored empty string and a missing label would render differently for
    /// no reason a user could explain, so there is only ever one of them.
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

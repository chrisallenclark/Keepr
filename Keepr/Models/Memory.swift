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
        category: MemoryCategory = .other,
        importance: Priority = .normal,
        createdAt: Date = Date(),
        person: Person? = nil,
        sourceInteraction: Interaction? = nil
    ) {
        self.id = UUID()
        self.content = content
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
}

/// A proposed memory produced by capture extraction, before the user confirms it.
/// Nothing reaches a person's record without passing through this review step.
struct MemoryDraft: Identifiable, Hashable, Sendable {
    var id = UUID()
    var content: String
    var category: MemoryCategory = .other
    var importance: Priority = .normal
    var isSelected: Bool = true
}

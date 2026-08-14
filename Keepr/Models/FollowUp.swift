import Foundation
import SwiftData

/// Something the user promised or intends to do about a person.
@Model
final class FollowUp {

    var id: UUID = UUID()

    /// What to do. "Text about training after his vacation."
    var title: String = ""
    var note: String?

    /// Day it's due. When `hasTime` is false this is the start of that day.
    var dueDate: Date = Date()
    /// True when the user picked a specific time, not just a day.
    var hasTime: Bool = false

    var priorityRaw: String = Priority.normal.rawValue

    var isCompleted: Bool = false
    var completedAt: Date?

    var createdAt: Date = Date()
    /// Set when the user pushes a follow-up out; kept for history, not shown.
    var lastSnoozedAt: Date?

    /// Identifier of the pending `UNNotificationRequest`, so it can be cancelled.
    var notificationIdentifier: String?

    var person: Person?
    var sourceInteraction: Interaction?

    init(
        title: String,
        note: String? = nil,
        dueDate: Date,
        hasTime: Bool = false,
        priority: Priority = .normal,
        person: Person? = nil,
        sourceInteraction: Interaction? = nil,
        createdAt: Date = Date()
    ) {
        self.id = UUID()
        self.title = title
        self.note = note
        self.dueDate = dueDate
        self.hasTime = hasTime
        self.priorityRaw = priority.rawValue
        self.isCompleted = false
        self.createdAt = createdAt
        self.person = person
        self.sourceInteraction = sourceInteraction
    }
}

extension FollowUp {

    var priority: Priority {
        get { Priority(rawValue: priorityRaw) ?? .normal }
        set { priorityRaw = newValue.rawValue }
    }

    func isOverdue(now: Date = Date(), calendar: Calendar = .current) -> Bool {
        guard !isCompleted else { return false }
        if hasTime { return dueDate < now }
        return calendar.startOfDay(for: dueDate) < calendar.startOfDay(for: now)
    }

    func isDueToday(now: Date = Date(), calendar: Calendar = .current) -> Bool {
        guard !isCompleted else { return false }
        return calendar.isDate(dueDate, inSameDayAs: now)
    }

    /// Overdue or due today — the things that belong in "Needs Attention".
    func needsAttention(now: Date = Date(), calendar: Calendar = .current) -> Bool {
        isOverdue(now: now, calendar: calendar) || isDueToday(now: now, calendar: calendar)
    }

    func complete(at date: Date = Date()) {
        isCompleted = true
        completedAt = date
    }

    func reopen() {
        isCompleted = false
        completedAt = nil
    }

    /// Pushes the due date out, preserving time-of-day when one was set.
    func snooze(byDays days: Int, from now: Date = Date(), calendar: Calendar = .current) {
        let base = max(dueDate, now)
        let target = calendar.date(byAdding: .day, value: days, to: base) ?? base
        if hasTime {
            dueDate = target
        } else {
            dueDate = calendar.startOfDay(for: target)
        }
        lastSnoozedAt = now
    }
}

/// A proposed follow-up produced by capture extraction, before the user confirms it.
struct FollowUpDraft: Identifiable, Hashable, Sendable {
    var id = UUID()
    var title: String
    var dueDate: Date
    var hasTime: Bool = false
    var priority: Priority = .normal
    var isSelected: Bool = true
}

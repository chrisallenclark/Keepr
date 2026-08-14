import Foundation

/// Sections of the Follow Up tab, in the order they're shown.
enum FollowUpBucket: String, CaseIterable, Identifiable, Hashable {
    case overdue
    case today
    case thisWeek
    case later
    case completed

    var id: String { rawValue }

    var title: String {
        switch self {
        case .overdue: "Overdue"
        case .today: "Today"
        case .thisWeek: "This Week"
        case .later: "Later"
        case .completed: "Completed"
        }
    }
}

/// One section of the Follow Up tab.
struct FollowUpSection: Identifiable {
    let bucket: FollowUpBucket
    let items: [FollowUp]

    var id: String { bucket.rawValue }
    var title: String { bucket.title }
}

/// Pure due-date logic. Every rule the UI relies on lives here.
enum FollowUpEngine {

    /// Only follow-ups attached to people visible in the current mode.
    static func visible(_ followUps: [FollowUp], in mode: ContextMode) -> [FollowUp] {
        followUps.filter { followUp in
            guard let person = followUp.person else { return false }
            return person.status == .active && person.context.matches(mode)
        }
    }

    /// Overdue first, then due today. Within each, high priority and then
    /// earliest due date win.
    static func needingAttention(
        _ followUps: [FollowUp],
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> [FollowUp] {
        followUps
            .filter { $0.needsAttention(now: now, calendar: calendar) }
            .sorted { lhs, rhs in
                let lOverdue = lhs.isOverdue(now: now, calendar: calendar)
                let rOverdue = rhs.isOverdue(now: now, calendar: calendar)
                if lOverdue != rOverdue { return lOverdue }
                if lhs.priority.weight != rhs.priority.weight {
                    return lhs.priority.weight > rhs.priority.weight
                }
                return lhs.dueDate < rhs.dueDate
            }
    }

    /// Open follow-ups due after today, within the given window.
    static func upcoming(
        _ followUps: [FollowUp],
        withinDays days: Int,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> [FollowUp] {
        let startOfTomorrow = calendar.date(
            byAdding: .day,
            value: 1,
            to: calendar.startOfDay(for: now)
        ) ?? now
        guard let end = calendar.date(byAdding: .day, value: days, to: calendar.startOfDay(for: now))
        else { return [] }

        return followUps
            .filter { !$0.isCompleted && $0.dueDate >= startOfTomorrow && $0.dueDate <= end }
            .sorted { $0.dueDate < $1.dueDate }
    }

    static func bucket(
        for followUp: FollowUp,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> FollowUpBucket {
        if followUp.isCompleted { return .completed }
        if followUp.isOverdue(now: now, calendar: calendar) { return .overdue }
        if followUp.isDueToday(now: now, calendar: calendar) { return .today }

        let endOfWeek = calendar.date(
            byAdding: .day,
            value: 7,
            to: calendar.startOfDay(for: now)
        ) ?? now
        return followUp.dueDate <= endOfWeek ? .thisWeek : .later
    }

    /// Grouped for the Follow Up tab. Empty buckets are dropped; completed
    /// items are limited so the list doesn't become an archive.
    static func grouped(
        _ followUps: [FollowUp],
        now: Date = Date(),
        calendar: Calendar = .current,
        completedLimit: Int = 20
    ) -> [FollowUpSection] {
        var buckets: [FollowUpBucket: [FollowUp]] = [:]
        for followUp in followUps {
            buckets[bucket(for: followUp, now: now, calendar: calendar), default: []].append(followUp)
        }

        return FollowUpBucket.allCases.compactMap { bucket in
            guard var items = buckets[bucket], !items.isEmpty else { return nil }
            if bucket == .completed {
                items.sort { ($0.completedAt ?? $0.dueDate) > ($1.completedAt ?? $1.dueDate) }
                items = Array(items.prefix(completedLimit))
            } else {
                items.sort { lhs, rhs in
                    if lhs.dueDate != rhs.dueDate { return lhs.dueDate < rhs.dueDate }
                    return lhs.priority.weight > rhs.priority.weight
                }
            }
            return FollowUpSection(bucket: bucket, items: items)
        }
    }

    // MARK: - Suggested due dates

    /// The presets offered when creating a follow-up.
    enum DuePreset: String, CaseIterable, Identifiable {
        case tomorrow
        case inThreeDays
        case nextWeek
        case inTwoWeeks
        case nextMonth

        var id: String { rawValue }

        var title: String {
            switch self {
            case .tomorrow: "Tomorrow"
            case .inThreeDays: "In 3 Days"
            case .nextWeek: "Next Week"
            case .inTwoWeeks: "In 2 Weeks"
            case .nextMonth: "Next Month"
            }
        }

        var days: Int {
            switch self {
            case .tomorrow: 1
            case .inThreeDays: 3
            case .nextWeek: 7
            case .inTwoWeeks: 14
            case .nextMonth: 30
            }
        }

        func date(from now: Date = Date(), calendar: Calendar = .current) -> Date {
            let start = calendar.startOfDay(for: now)
            return calendar.date(byAdding: .day, value: days, to: start) ?? start
        }
    }
}

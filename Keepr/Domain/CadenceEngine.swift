import Foundation

/// Where a person stands against the rhythm you set for them.
struct CadenceStatus: Equatable {
    /// The interval being applied, in days.
    let days: Int
    /// Where it came from, for showing "every 30 days · Current Client".
    let source: String?
    /// When the next contact is due.
    let dueDate: Date
    /// Positive when overdue, negative when there's still time.
    let daysOverdue: Int

    var isOverdue: Bool { daysOverdue >= 0 }

    /// "Due today", "3 days over", "in 5 days" — the phrase a person reads.
    var summary: String {
        switch daysOverdue {
        case 0: "Due today"
        case 1: "1 day over"
        case let over where over > 1: "\(over) days over"
        case -1: "Due tomorrow"
        case let under: "In \(-under) days"
        }
    }
}

/// One person and where they stand. A struct because SwiftUI lists need
/// something identifiable, and because the status is worth computing once.
struct CadenceItem: Identifiable {
    let person: Person
    let status: CadenceStatus

    var id: UUID { person.id }
}

/// Turns "clients every 30 days" into "these four are overdue".
///
/// The whole design rests on one decision: cadence is a property of a
/// *relationship type*, not of a person. Deciding once that clients are worth
/// a month is a minute's work; deciding it 60 times, once per client, is a
/// chore nobody finishes. A person-level override exists for the handful who are
/// genuinely different, and setting theirs to zero means "never chase me about
/// this one" — a real answer, not an omission.
///
/// Nothing here invents urgency. If no type has a cadence, the app says nothing.
enum CadenceEngine {

    /// Intervals offered in the pickers. Real rhythms people keep, not a slider
    /// that invites fiddling.
    static let presets: [Int] = [7, 14, 30, 60, 90, 180, 365]

    /// The interval to apply to one person: their own if they have one, else the
    /// most demanding of their types.
    ///
    /// Most demanding wins because someone who is both a Current Client (30) and
    /// a Friend (90) needs contact every 30 days — the shorter promise is the one
    /// you actually made.
    static func cadence(for person: Person) -> (days: Int, source: String?)? {
        if let own = person.cadenceDays {
            return own <= 0 ? nil : (own, nil)
        }

        let candidates = person.tagList.compactMap { tag -> (Int, String)? in
            guard let days = tag.cadenceDays, days > 0 else { return nil }
            return (days, tag.name)
        }
        guard let best = candidates.min(by: { $0.0 < $1.0 }) else { return nil }
        return (best.0, best.1)
    }

    /// Where a person stands right now, or nil when no rhythm applies to them.
    ///
    /// The clock runs from the last logged interaction, or from when they were
    /// added if there's never been one — so a client imported today isn't
    /// instantly overdue, and logging anything resets it with no extra step.
    static func status(
        for person: Person,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> CadenceStatus? {
        guard person.status == .active, let cadence = cadence(for: person) else { return nil }

        let anchor = person.lastInteractionAt ?? person.createdAt
        guard let due = calendar.date(byAdding: .day, value: cadence.days, to: anchor) else {
            return nil
        }

        let days = calendar.dateComponents(
            [.day],
            from: calendar.startOfDay(for: due),
            to: calendar.startOfDay(for: now)
        ).day ?? 0

        return CadenceStatus(
            days: cadence.days,
            source: cadence.source,
            dueDate: due,
            daysOverdue: days
        )
    }

    /// Everyone who is due or overdue, most overdue first.
    ///
    /// People with an open follow-up are left out: a plan already exists, and
    /// listing them twice would make the screen feel like it's nagging.
    static func due(
        _ people: [Person],
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> [CadenceItem] {
        people
            .compactMap { person -> CadenceItem? in
                guard person.openFollowUps.isEmpty,
                      let status = status(for: person, now: now, calendar: calendar),
                      status.isOverdue
                else { return nil }
                return CadenceItem(person: person, status: status)
            }
            .sorted { lhs, rhs in
                lhs.status.daysOverdue == rhs.status.daysOverdue
                    ? lhs.person.sortKey < rhs.person.sortKey
                    : lhs.status.daysOverdue > rhs.status.daysOverdue
            }
    }

    /// True when this person is governed by a rhythm, so the generic
    /// "going quiet" heuristic should keep quiet about them.
    static func hasCadence(_ person: Person) -> Bool {
        cadence(for: person) != nil
    }

    /// "Every 30 days" / "Every 3 months" — how an interval is written.
    static func label(forDays days: Int) -> String {
        switch days {
        case 1: "Every day"
        case 7: "Every week"
        case 14: "Every 2 weeks"
        case 30: "Every month"
        case 60: "Every 2 months"
        case 90: "Every 3 months"
        case 180: "Every 6 months"
        case 365: "Every year"
        default: "Every \(days) days"
        }
    }
}

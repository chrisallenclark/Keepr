import Foundation

/// Everything the Today screen shows, computed in one pass.
struct TodayDigest {
    var needsAttention: [FollowUp] = []
    var upcoming: [FollowUp] = []
    var goingQuiet: [Person] = []
    var recent: [Person] = []

    var isEmpty: Bool {
        needsAttention.isEmpty && upcoming.isEmpty && goingQuiet.isEmpty && recent.isEmpty
    }

    /// True when there's genuinely nothing to act on — the calm state.
    var isCaughtUp: Bool { needsAttention.isEmpty }
}

/// Pure logic behind Today. No engagement mechanics: nothing here invents
/// urgency, it only reflects what the user already told the app.
enum TodayEngine {

    /// How long without a logged interaction before a relationship reads as
    /// going quiet. Business relationships drift faster than personal ones.
    static func quietThreshold(for mode: ContextMode) -> Int {
        switch mode {
        case .business: 21
        case .personal: 60
        }
    }

    /// How far ahead "Upcoming" looks.
    static let upcomingWindowDays = 7

    static let goingQuietLimit = 5
    static let recentLimit = 6
    static let upcomingLimit = 5

    static func digest(
        people: [Person],
        mode: ContextMode,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> TodayDigest {
        let visible = PeopleEngine.visible(people, in: mode)
        let followUps = visible.flatMap(\.followUpList)

        var digest = TodayDigest()
        digest.needsAttention = FollowUpEngine.needingAttention(followUps, now: now, calendar: calendar)
        digest.upcoming = Array(
            FollowUpEngine.upcoming(
                followUps,
                withinDays: upcomingWindowDays,
                now: now,
                calendar: calendar
            ).prefix(upcomingLimit)
        )
        digest.goingQuiet = Array(
            goingQuiet(visible, mode: mode, now: now, calendar: calendar).prefix(goingQuietLimit)
        )
        digest.recent = Array(recentlyActive(visible, now: now, calendar: calendar).prefix(recentLimit))
        return digest
    }

    /// Relationships that have drifted: nothing logged for a while and no plan
    /// in place. People with an open follow-up are excluded — they're handled.
    static func goingQuiet(
        _ people: [Person],
        mode: ContextMode,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> [Person] {
        let threshold = quietThreshold(for: mode)
        guard let cutoff = calendar.date(byAdding: .day, value: -threshold, to: now) else { return [] }

        return people
            .filter { person in
                guard person.openFollowUps.isEmpty else { return false }

                if let last = person.lastInteractionAt {
                    return last < cutoff
                }
                // Never contacted: only surface people the user flagged as mattering,
                // so a fresh contact import doesn't fill the screen.
                guard person.isFavorite || person.priority == .high else { return false }
                return person.createdAt < cutoff
            }
            .sorted { lhs, rhs in
                let l = lhs.lastInteractionAt ?? lhs.createdAt
                let r = rhs.lastInteractionAt ?? rhs.createdAt
                return l < r
            }
    }

    /// People interacted with in the last two weeks, most recent first.
    static func recentlyActive(
        _ people: [Person],
        withinDays days: Int = 14,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> [Person] {
        guard let cutoff = calendar.date(byAdding: .day, value: -days, to: now) else { return [] }
        return people
            .compactMap { person -> (Person, Date)? in
                guard let last = person.lastInteractionAt, last >= cutoff else { return nil }
                return (person, last)
            }
            .sorted { $0.1 > $1.1 }
            .map { $0.0 }
    }
}

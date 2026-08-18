import Foundation
import SwiftData

/// One person you've reached out to and not heard back from.
struct WaitingItem: Identifiable {
    let person: Person
    let since: Date
    let days: Int

    var id: UUID { person.id }

    /// "Sent today", "3 days ago" — how long the ball has been in their court.
    var summary: String {
        switch days {
        case 0: "Sent today"
        case 1: "Sent yesterday"
        default: "Sent \(days) days ago"
        }
    }
}

/// Reaching out is not the same as talking.
///
/// A text nobody answered still means you did your part: the rhythm should
/// reset, because chasing someone twice in an hour is worse than waiting. But it
/// isn't a conversation, so it's recorded as an attempt and the person joins a
/// "waiting on a reply" list until something actually comes back. Collapsing the
/// two would either nag you for doing the right thing, or quietly tell you a
/// relationship is healthy when you've been talking to yourself.
@MainActor
enum Outreach {

    /// Records an attempt: logged, clock reset, ball in their court.
    @discardableResult
    static func markReachedOut(
        _ person: Person,
        kind: InteractionKind = .text,
        note: String? = nil,
        at date: Date = Date(),
        in context: ModelContext
    ) -> Interaction {
        let interaction = Interaction(
            kind: kind,
            occurredAt: date,
            summary: note ?? "Reached out — no reply yet",
            awaitingReply: true,
            person: person
        )
        context.insert(interaction)

        person.lastInteractionAt = date
        person.awaitingReplySince = date
        person.touch()
        return interaction
    }

    /// They came back. Clears the waiting flag without inventing an interaction —
    /// the user logs what was actually said, if anything was.
    static func markReplied(_ person: Person, at date: Date = Date()) {
        person.awaitingReplySince = nil
        person.touch()
    }

    /// Everyone still waiting, longest first.
    static func waiting(
        _ people: [Person],
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> [WaitingItem] {
        people
            .compactMap { person -> WaitingItem? in
                guard person.status == .active, let since = person.awaitingReplySince else {
                    return nil
                }
                let days = calendar.dateComponents(
                    [.day],
                    from: calendar.startOfDay(for: since),
                    to: calendar.startOfDay(for: now)
                ).day ?? 0
                return WaitingItem(person: person, since: since, days: max(0, days))
            }
            .sorted { $0.since < $1.since }
    }
}

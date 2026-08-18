import Foundation
import SwiftData

/// Turns rhythms into notifications, one per day rather than one per person.
///
/// iOS can't run code when a notification is due, so everything is worked out
/// ahead of time and the whole schedule is rebuilt whenever the app is open.
/// That's fine: a rhythm's due date only moves when you log something, and
/// logging something happens in the app.
///
/// Two rules keep this from becoming a nag:
///
/// - **One notification per day.** Four clients due on Thursday is one alert
///   saying four, not four alerts. A phone that buzzes six times before
///   breakfast gets its notifications switched off.
/// - **The standing backlog is raised weekly, not daily.** People already past
///   their rhythm when you set it up would otherwise generate the same alert
///   every morning forever.
@MainActor
enum CadenceNotificationScheduler {

    static let identifierPrefix = "cadence-"
    /// Morning, not midnight — a notification you see when you might act on it.
    static let hour = 9
    /// How far ahead to schedule. iOS caps pending notifications at 64.
    static let horizonDays = 45
    /// How often the already-overdue pile is worth raising again.
    static let backlogInterval = 7

    private static let lastBacklogKey = "keepr.lastCadenceBacklogNudge"

    /// Builds the reminders for the next few weeks.
    ///
    /// Anyone already overdue lands in one "backlog" reminder, scheduled a week
    /// after the last one so it can't turn into a daily drumbeat.
    static func reminders(
        for people: [Person],
        now: Date = Date(),
        calendar: Calendar = .current,
        lastBacklogNudge: Date? = nil
    ) -> [LocalReminder] {
        let active = people.filter { $0.status == .active && $0.openFollowUps.isEmpty }

        var byDay: [Date: [Person]] = [:]
        var overdue: [Person] = []

        for person in active {
            guard let status = CadenceEngine.status(for: person, now: now, calendar: calendar) else {
                continue
            }
            if status.isOverdue {
                overdue.append(person)
                continue
            }
            guard let fire = fireDate(on: status.dueDate, calendar: calendar),
                  fire > now,
                  let horizon = calendar.date(byAdding: .day, value: horizonDays, to: now),
                  fire <= horizon
            else { continue }
            byDay[calendar.startOfDay(for: fire), default: []].append(person)
        }

        var result = byDay
            .sorted { $0.key < $1.key }
            .compactMap { day, people -> LocalReminder? in
                guard let fire = fireDate(on: day, calendar: calendar) else { return nil }
                return reminder(
                    for: people,
                    fireDate: fire,
                    identifierSuffix: identifier(for: day, calendar: calendar)
                )
            }

        if !overdue.isEmpty,
           let fire = backlogFireDate(now: now, calendar: calendar, lastNudge: lastBacklogNudge),
           let backlog = reminder(for: overdue, fireDate: fire, identifierSuffix: "backlog") {
            result.append(backlog)
        }
        return result
    }

    /// Rebuilds the whole schedule. Cheap enough to run on every launch.
    static func sync(
        people: [Person],
        using service: NotificationScheduling,
        remindersEnabled: Bool,
        now: Date = Date(),
        calendar: Calendar = .current,
        defaults: UserDefaults = .standard
    ) {
        let lastNudge = defaults.object(forKey: lastBacklogKey) as? Date
        let built = remindersEnabled
            ? reminders(for: people, now: now, calendar: calendar, lastBacklogNudge: lastNudge)
            : []

        if let backlog = built.first(where: { $0.identifier.hasSuffix("backlog") }) {
            defaults.set(backlog.fireDate, forKey: lastBacklogKey)
        }

        Task {
            await service.cancelReminders(withPrefix: identifierPrefix)
            for reminder in built {
                await service.scheduleReminder(reminder)
            }
        }
    }

    // MARK: - Content

    /// One name reads as a task; several read as a number, which is the honest
    /// summary — with the first few names so it's still worth glancing at.
    static func reminder(
        for people: [Person],
        fireDate: Date,
        identifierSuffix: String
    ) -> LocalReminder? {
        guard !people.isEmpty else { return nil }
        let names = people.map(\.displayName).sorted()

        let title: String
        let body: String

        if names.count == 1, let only = people.first {
            title = "Reach out to \(names[0])"
            body = lastContactPhrase(for: only)
        } else {
            title = "\(names.count) people to reach out to"
            let shown = names.prefix(3).joined(separator: ", ")
            body = names.count > 3 ? "\(shown) and \(names.count - 3) more" : shown
        }

        return LocalReminder(
            identifier: identifierPrefix + identifierSuffix,
            title: title,
            body: body,
            fireDate: fireDate
        )
    }

    private static func lastContactPhrase(for person: Person) -> String {
        guard let last = person.lastInteractionAt else {
            return "You haven't logged anything with them yet."
        }
        return "Last spoke \(RelativeDate.past(last).lowercased())."
    }

    // MARK: - Dates

    private static func fireDate(on day: Date, calendar: Calendar) -> Date? {
        calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day)
    }

    /// Tomorrow morning, or a week after the last backlog nudge — whichever is
    /// later.
    private static func backlogFireDate(
        now: Date,
        calendar: Calendar,
        lastNudge: Date?
    ) -> Date? {
        guard let tomorrow = calendar.date(byAdding: .day, value: 1, to: now),
              let earliest = fireDate(on: tomorrow, calendar: calendar)
        else { return nil }

        guard let lastNudge else { return earliest }
        guard let next = calendar.date(byAdding: .day, value: backlogInterval, to: lastNudge),
              next > earliest
        else { return earliest }
        return next
    }

    private static func identifier(for day: Date, calendar: Calendar) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: day)
        return "\(parts.year ?? 0)-\(parts.month ?? 0)-\(parts.day ?? 0)"
    }
}

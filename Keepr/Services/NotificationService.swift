import Foundation
import OSLog
import UserNotifications

/// A value-type description of a reminder, so the model layer (which isn't
/// `Sendable`) never crosses a concurrency boundary.
struct FollowUpReminder: Sendable, Equatable {
    let identifier: String
    let personName: String
    let title: String
    let fireDate: Date

    /// All-day follow-ups fire at 9am rather than midnight.
    static let defaultHour = 9
}

/// Any local notification this app sends: a title, a body, a moment.
///
/// Follow-ups and rhythms both end up here. iOS can't run code at delivery
/// time, so everything is worked out in advance and rescheduled whenever the
/// app is open — which is why the content has to be plain data.
struct LocalReminder: Sendable, Equatable {
    let identifier: String
    let title: String
    let body: String
    let fireDate: Date
}

protocol NotificationScheduling: Sendable {
    func authorizationStatus() async -> UNAuthorizationStatus
    /// Presents the system prompt. Returns whether it was granted.
    @discardableResult func requestAuthorization() async -> Bool
    /// Schedules (or replaces) the reminder. Past dates are ignored.
    func schedule(_ reminder: FollowUpReminder) async
    func scheduleReminder(_ reminder: LocalReminder) async
    func cancel(identifier: String) async
    /// Clears a whole family of reminders — used to rebuild the rhythm schedule
    /// from scratch every time something changes.
    func cancelReminders(withPrefix prefix: String) async
    func cancelAll() async
}

// MARK: - Live

struct LiveNotificationService: NotificationScheduling {

    private var center: UNUserNotificationCenter { .current() }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    @discardableResult
    func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            Logger.notifications.error("Notification authorization request failed.")
            return false
        }
    }

    func schedule(_ reminder: FollowUpReminder) async {
        // The person's name and the user's own words — nothing generated,
        // nothing nagging. Seeing who it's about is the whole value of the
        // notification; "You have a follow-up" would be useless.
        await scheduleReminder(
            LocalReminder(
                identifier: reminder.identifier,
                title: reminder.personName,
                body: reminder.title,
                fireDate: reminder.fireDate
            )
        )
    }

    func scheduleReminder(_ reminder: LocalReminder) async {
        await cancel(identifier: reminder.identifier)
        guard reminder.fireDate > Date() else { return }
        guard await authorizationStatus() == .authorized else { return }

        let content = UNMutableNotificationContent()
        content.title = reminder.title
        content.body = reminder.body
        content.sound = .default
        content.interruptionLevel = .active

        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: reminder.fireDate
        )
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        let request = UNNotificationRequest(
            identifier: reminder.identifier,
            content: content,
            trigger: trigger
        )

        do {
            try await center.add(request)
        } catch {
            Logger.notifications.error("Failed to schedule a reminder.")
        }
    }

    func cancelReminders(withPrefix prefix: String) async {
        let pending = await center.pendingNotificationRequests()
        let matching = pending.map(\.identifier).filter { $0.hasPrefix(prefix) }
        guard !matching.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: matching)
    }

    func cancel(identifier: String) async {
        center.removePendingNotificationRequests(withIdentifiers: [identifier])
        center.removeDeliveredNotifications(withIdentifiers: [identifier])
    }

    func cancelAll() async {
        center.removeAllPendingNotificationRequests()
    }
}

// MARK: - Preview / test double

/// Records what it was asked to do; schedules nothing.
final class StubNotificationService: NotificationScheduling, @unchecked Sendable {
    var status: UNAuthorizationStatus = .authorized
    private(set) var scheduled: [FollowUpReminder] = []
    private(set) var reminders: [LocalReminder] = []
    private(set) var cancelled: [String] = []

    func authorizationStatus() async -> UNAuthorizationStatus { status }
    func requestAuthorization() async -> Bool { status == .authorized }

    func schedule(_ reminder: FollowUpReminder) async {
        scheduled.removeAll { $0.identifier == reminder.identifier }
        scheduled.append(reminder)
    }

    func scheduleReminder(_ reminder: LocalReminder) async {
        reminders.removeAll { $0.identifier == reminder.identifier }
        reminders.append(reminder)
    }

    func cancel(identifier: String) async {
        cancelled.append(identifier)
        scheduled.removeAll { $0.identifier == identifier }
        reminders.removeAll { $0.identifier == identifier }
    }

    func cancelReminders(withPrefix prefix: String) async {
        let matching = reminders.map(\.identifier).filter { $0.hasPrefix(prefix) }
        cancelled.append(contentsOf: matching)
        reminders.removeAll { $0.identifier.hasPrefix(prefix) }
    }

    func cancelAll() async {
        cancelled.append(contentsOf: scheduled.map(\.identifier))
        cancelled.append(contentsOf: reminders.map(\.identifier))
        scheduled.removeAll()
        reminders.removeAll()
    }
}

// MARK: - Model bridge

/// Turns `FollowUp` records into reminders and keeps the two in sync.
///
/// Main-actor bound because it reads SwiftData models.
@MainActor
enum FollowUpScheduler {

    static func reminder(for followUp: FollowUp, calendar: Calendar = .current) -> FollowUpReminder? {
        guard !followUp.isCompleted else { return nil }
        guard let person = followUp.person else { return nil }

        let fireDate: Date
        if followUp.hasTime {
            fireDate = followUp.dueDate
        } else {
            fireDate = calendar.date(
                bySettingHour: FollowUpReminder.defaultHour,
                minute: 0,
                second: 0,
                of: followUp.dueDate
            ) ?? followUp.dueDate
        }

        return FollowUpReminder(
            identifier: identifier(for: followUp),
            personName: person.displayName,
            title: followUp.title,
            fireDate: fireDate
        )
    }

    static func identifier(for followUp: FollowUp) -> String {
        "followup-\(followUp.id.uuidString)"
    }

    /// Call after any change to a follow-up's due date, title or completion.
    static func sync(
        _ followUp: FollowUp,
        using service: NotificationScheduling,
        remindersEnabled: Bool
    ) {
        let identifier = identifier(for: followUp)
        followUp.notificationIdentifier = identifier

        guard remindersEnabled, let reminder = reminder(for: followUp) else {
            Task { await service.cancel(identifier: identifier) }
            return
        }
        Task { await service.schedule(reminder) }
    }

    static func cancel(_ followUp: FollowUp, using service: NotificationScheduling) {
        let identifier = followUp.notificationIdentifier ?? identifier(for: followUp)
        Task { await service.cancel(identifier: identifier) }
    }
}

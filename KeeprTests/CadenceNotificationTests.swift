import Foundation
import SwiftData
import Testing

@testable import Keepr

/// Notifications are the one part of this app that can interrupt someone's day,
/// so the rules that keep them rare matter more than the ones that make them
/// fire.
@Suite("Rhythm notifications")
@MainActor
struct CadenceNotificationTests {

    private func client(
        _ store: TestStore,
        _ name: String,
        lastInteraction: Date?,
        tag: RelationshipTag
    ) -> Person {
        let person = Make.person(
            store.context,
            given: name,
            family: "Client",
            relationship: .business,
            createdAt: TestDates.days(-200),
            lastInteractionAt: lastInteraction
        )
        Make.attach(tag, to: person)
        return person
    }

    private func tag(_ store: TestStore, days: Int) -> RelationshipTag {
        let tag = Make.tag(store.context, name: "Current Client")
        tag.cadenceDays = days
        return tag
    }

    private func build(_ people: [Person], lastBacklogNudge: Date? = nil) -> [LocalReminder] {
        CadenceNotificationScheduler.reminders(
            for: people,
            now: TestDates.now,
            calendar: TestDates.calendar,
            lastBacklogNudge: lastBacklogNudge
        )
    }

    @Test("One person due names them, so the notification is worth reading")
    func singlePersonIsNamed() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        // Spoke 20 days ago, so due in 10.
        let person = client(store, "Stanley", lastInteraction: TestDates.days(-20), tag: clients)

        let reminders = build([person])

        let reminder = try #require(reminders.first)
        #expect(reminder.title == "Reach out to Stanley Client")
        #expect(reminder.body.contains("Last spoke"))
    }

    @Test("Several due the same day become one notification with the count")
    func sameDayBecomesOneNotification() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        let people = ["Ann", "Ben", "Cara", "Dev"].map {
            client(store, $0, lastInteraction: TestDates.days(-20), tag: clients)
        }

        let reminders = build(people)

        #expect(reminders.count == 1, "one buzz, not four")
        let reminder = try #require(reminders.first)
        #expect(reminder.title == "4 people to reach out to")
        #expect(reminder.body.contains("and 1 more"), "a few names, then a count")
    }

    @Test("Different days get their own notification")
    func differentDaysAreSeparate() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        let soon = client(store, "Soon", lastInteraction: TestDates.days(-25), tag: clients)
        let later = client(store, "Later", lastInteraction: TestDates.days(-20), tag: clients)

        let reminders = build([soon, later])

        #expect(reminders.count == 2)
        #expect(reminders[0].fireDate < reminders[1].fireDate)
    }

    @Test("Notifications land in the morning, not at midnight")
    func firesInTheMorning() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        let person = client(store, "Stanley", lastInteraction: TestDates.days(-20), tag: clients)

        let reminder = try #require(build([person]).first)

        let hour = TestDates.calendar.component(.hour, from: reminder.fireDate)
        #expect(hour == CadenceNotificationScheduler.hour)
    }

    @Test("People already overdue are raised once, together")
    func backlogIsOneNotification() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        let people = ["Ann", "Ben"].map {
            client(store, $0, lastInteraction: TestDates.days(-90), tag: clients)
        }

        let reminders = build(people)

        #expect(reminders.count == 1)
        #expect(reminders.first?.identifier.hasSuffix("backlog") == true)
        #expect(reminders.first?.title == "2 people to reach out to")
    }

    @Test("The backlog isn't raised again the next morning")
    func backlogIsNotADailyDrumbeat() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        let person = client(store, "Ann", lastInteraction: TestDates.days(-90), tag: clients)

        // Yesterday's nudge already went out.
        let reminders = build([person], lastBacklogNudge: TestDates.days(-1))
        let backlog = try #require(reminders.first { $0.identifier.hasSuffix("backlog") })

        let daysOut = TestDates.calendar.dateComponents(
            [.day],
            from: TestDates.calendar.startOfDay(for: TestDates.now),
            to: TestDates.calendar.startOfDay(for: backlog.fireDate)
        ).day ?? 0
        #expect(daysOut >= CadenceNotificationScheduler.backlogInterval - 1)
    }

    @Test("Someone with a plan already isn't notified about twice")
    func openFollowUpSuppressesIt() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        let person = client(store, "Stanley", lastInteraction: TestDates.days(-20), tag: clients)
        _ = Make.followUp(store.context, due: TestDates.days(3), person: person)

        #expect(build([person]).isEmpty)
    }

    @Test("No rhythm means no notifications at all")
    func silentWithoutCadence() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Nobody",
            family: "Special",
            relationship: .business,
            lastInteractionAt: TestDates.days(-200)
        )

        #expect(build([person]).isEmpty)
    }

    @Test("Turning reminders off clears the schedule rather than rebuilding it")
    func disabledMeansNothingScheduled() throws {
        let store = try TestStore()
        let clients = tag(store, days: 30)
        let person = client(store, "Stanley", lastInteraction: TestDates.days(-20), tag: clients)
        let service = StubNotificationService()

        CadenceNotificationScheduler.sync(
            people: [person],
            using: service,
            remindersEnabled: false,
            now: TestDates.now,
            calendar: TestDates.calendar,
            defaults: store.defaults
        )

        #expect(service.reminders.isEmpty)
    }
}

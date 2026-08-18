import Foundation
import SwiftData
import Testing

@testable import Keepr

/// "I texted them and heard nothing" is a real state, and the app gets it wrong
/// in both directions if it's collapsed into logging a conversation: either it
/// nags you for doing the right thing, or it tells you a relationship is
/// healthy when you've been talking to yourself.
@Suite("Reaching out")
@MainActor
struct OutreachTests {

    private func person(_ store: TestStore, lastInteraction: Date? = nil) -> Person {
        Make.person(
            store.context,
            given: "Stan",
            family: "Ley",
            relationship: .business,
            createdAt: TestDates.days(-200),
            lastInteractionAt: lastInteraction
        )
    }

    private func clientTag(_ store: TestStore, days: Int) -> RelationshipTag {
        let tag = Make.tag(store.context, name: "Current Client")
        tag.cadenceDays = days
        return tag
    }

    @Test("Reaching out counts as your part done, so the rhythm resets")
    func outreachResetsTheRhythm() throws {
        let store = try TestStore()
        let subject = person(store, lastInteraction: TestDates.days(-45))
        Make.attach(clientTag(store, days: 30), to: subject)
        #expect(CadenceEngine.due([subject], now: TestDates.now, calendar: TestDates.calendar).count == 1)

        Outreach.markReachedOut(subject, at: TestDates.now, in: store.context)

        #expect(CadenceEngine.due([subject], now: TestDates.now, calendar: TestDates.calendar).isEmpty)
    }

    @Test("But it's recorded as an attempt, not a conversation")
    func outreachIsMarkedAsAwaiting() throws {
        let store = try TestStore()
        let subject = person(store)

        let interaction = Outreach.markReachedOut(subject, at: TestDates.now, in: store.context)
        try store.save()

        #expect(interaction.awaitingReply)
        #expect(subject.awaitingReplySince == TestDates.now)
        #expect(subject.lastInteractionAt == TestDates.now)
    }

    @Test("They show up as waiting, longest wait first")
    func waitingListIsOrdered() throws {
        let store = try TestStore()
        let recent = Make.person(store.context, given: "Recent", family: "Person")
        let old = Make.person(store.context, given: "Old", family: "Person")
        Outreach.markReachedOut(recent, at: TestDates.days(-1), in: store.context)
        Outreach.markReachedOut(old, at: TestDates.days(-9), in: store.context)

        let waiting = Outreach.waiting([recent, old], now: TestDates.now, calendar: TestDates.calendar)

        #expect(waiting.map(\.person.givenName) == ["Old", "Recent"])
        #expect(waiting.first?.days == 9)
        #expect(waiting.first?.summary == "Sent 9 days ago")
        #expect(waiting.last?.summary == "Sent yesterday")
    }

    @Test("Once they reply, the waiting stops without inventing a conversation")
    func replyingClearsTheWait() throws {
        let store = try TestStore()
        let subject = person(store)
        Outreach.markReachedOut(subject, at: TestDates.days(-3), in: store.context)

        Outreach.markReplied(subject)

        #expect(subject.awaitingReplySince == nil)
        #expect(Outreach.waiting([subject], now: TestDates.now).isEmpty)
        #expect(subject.interactionList.count == 1, "no phantom interaction is added")
    }

    @Test("Archived people don't sit on the waiting list forever")
    func archivedPeopleDropOff() throws {
        let store = try TestStore()
        let subject = person(store)
        Outreach.markReachedOut(subject, at: TestDates.days(-3), in: store.context)
        subject.status = .archived

        #expect(Outreach.waiting([subject], now: TestDates.now).isEmpty)
    }

    @Test("Someone you never chased isn't waiting on anything")
    func noOutreachMeansNoWait() throws {
        let store = try TestStore()
        let subject = person(store, lastInteraction: TestDates.days(-2))

        #expect(Outreach.waiting([subject], now: TestDates.now).isEmpty)
    }
}

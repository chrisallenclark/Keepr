import Foundation
import SwiftData
import Testing

@testable import Keepr

/// The catch-up queue is the one screen that tells the user they have work to
/// do, so it has to be right about who's actually waiting — and silent about
/// everyone else.
@Suite("Review queue")
@MainActor
struct ReviewQueueTests {

    private func contact(
        _ identifier: String,
        given: String = "New",
        family: String = "Contact",
        organization: String? = nil
    ) -> ContactSummary {
        ContactSummary(
            identifier: identifier,
            givenName: given,
            familyName: family,
            organizationName: organization,
            jobTitle: nil,
            phoneNumbers: [],
            emailAddresses: [],
            thumbnailData: nil
        )
    }

    // MARK: - Who needs categorizing

    @Test("Someone with no relationship type is waiting")
    func untaggedPeopleAreQueued() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Un", family: "Categorized")

        #expect(ReviewQueue.needsCategorizing(person))
    }

    @Test("A relationship type takes them out of the queue")
    func taggedPeopleAreDone() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Sorted", family: "Person")
        Make.attach(Make.tag(store.context, name: "Current Client"), to: person)

        #expect(!ReviewQueue.needsCategorizing(person))
    }

    @Test("Archived people are not chased")
    func archivedPeopleAreLeftAlone() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Old", family: "Contact", status: .archived)

        #expect(!ReviewQueue.needsCategorizing(person))
    }

    @Test("\"Not now\" sticks — a waved-off person isn't asked about again")
    func skippedPeopleStaySkipped() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Later", family: "Person")
        #expect(ReviewQueue.needsCategorizing(person))

        person.reviewedAt = TestDates.now

        #expect(!ReviewQueue.needsCategorizing(person))
    }

    // MARK: - Building the queue

    @Test("New contacts come before the backlog, because that memory is fading")
    func newContactsSortFirst() throws {
        let store = try TestStore()
        let existing = Make.person(store.context, given: "Old", family: "Person")

        let items = ReviewQueue.items(
            newContacts: [contact("new-1")],
            people: [existing]
        )

        #expect(items.count == 2)
        #expect(items.first?.isNew == true)
        #expect(items.last?.isNew == false)
    }

    @Test("A contact already imported isn't offered again as new")
    func importedContactsAreNotNew() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Already",
            family: "Here",
            contactIdentifier: "new-1"
        )
        Make.attach(Make.tag(store.context, name: "Vendor"), to: person)

        let items = ReviewQueue.items(
            newContacts: [contact("new-1")],
            people: [person]
        )

        #expect(items.isEmpty)
    }

    @Test("An imported but uncategorized contact appears once, not twice")
    func importedAndUncategorizedAppearsOnce() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Already",
            family: "Here",
            contactIdentifier: "new-1"
        )

        let items = ReviewQueue.items(
            newContacts: [contact("new-1")],
            people: [person]
        )

        #expect(items.count == 1)
        #expect(items.first?.isNew == false)
    }

    @Test("The backlog is newest first — recent additions are the ones you can still place")
    func backlogIsNewestFirst() throws {
        let store = try TestStore()
        let older = Make.person(
            store.context,
            given: "Older",
            family: "Person",
            createdAt: TestDates.days(-30)
        )
        let newer = Make.person(
            store.context,
            given: "Newer",
            family: "Person",
            createdAt: TestDates.days(-1)
        )

        let items = ReviewQueue.items(newContacts: [], people: [older, newer])

        #expect(items.map(\.name) == ["Newer Person", "Older Person"])
    }

    @Test("Suggestions ride along with each item, for people and contacts alike")
    func itemsCarrySuggestions() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Dana",
            family: "Whitfield",
            company: "Whitfield Interiors"
        )

        let items = ReviewQueue.items(
            newContacts: [contact("new-1", organization: "Martinez Roofing")],
            people: [person]
        )

        #expect(items.count == 2)
        #expect(items.allSatisfy { $0.suggestion != nil })
        #expect(items.first?.suggestion?.context == .business)
    }

    @Test("Nothing to do produces an empty queue rather than an empty section")
    func nothingWaitingIsEmpty() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Sorted", family: "Person")
        Make.attach(Make.tag(store.context, name: "Friend", kind: .personal), to: person)

        #expect(ReviewQueue.items(newContacts: [], people: [person]).isEmpty)
    }

    // MARK: - Summary line

    @Test("The banner says nothing when there's nothing waiting")
    func summaryIsSilentWhenEmpty() {
        #expect(ReviewQueue.summary(newCount: 0, backlogCount: 0) == nil)
    }

    @Test("Counts are spelled out in words, singular and plural")
    func summaryReadsProperly() throws {
        let one = try #require(ReviewQueue.summary(newCount: 1, backlogCount: 0))
        #expect(one == "1 new contact since you last looked")

        let many = try #require(ReviewQueue.summary(newCount: 5, backlogCount: 0))
        #expect(many.hasPrefix("5 new contacts"))

        let backlog = try #require(ReviewQueue.summary(newCount: 0, backlogCount: 1))
        #expect(backlog == "1 person has no relationship type yet")

        let both = try #require(ReviewQueue.summary(newCount: 2, backlogCount: 7))
        #expect(both == "2 new contacts, and 7 more to categorize")
    }
}

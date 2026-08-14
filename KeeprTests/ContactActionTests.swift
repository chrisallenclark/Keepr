import Foundation
import Testing

@testable import Keepr

@Suite("Reaching people", .serialized)
@MainActor
struct CommunicationLauncherTests {

    @Test("Formatted numbers are reduced to something tel: and sms: accept")
    func dialableStripsFormatting() {
        #expect(CommunicationLauncher.dialable("(561) 555-0142") == "5615550142")
        #expect(CommunicationLauncher.dialable("+1 561-555-0142") == "+15615550142")
        #expect(CommunicationLauncher.dialable("555.0142 ext 2") == "5550142")
    }

    @Test("Each method builds the right system URL")
    func urlsPerMethod() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Jake", family: "Martinez")
        person.phoneNumbers = ["(561) 555-0142"]
        person.emailAddresses = ["jake@example.com"]

        #expect(CommunicationLauncher.url(for: .call, person: person)?.absoluteString == "tel:5615550142")
        #expect(CommunicationLauncher.url(for: .message, person: person)?.absoluteString == "sms:5615550142")
        #expect(
            CommunicationLauncher.url(for: .email, person: person)?.absoluteString
                == "mailto:jake@example.com"
        )
    }

    @Test("Missing details produce no URL and no availability, rather than a dead button")
    func unavailableMethods() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "No", family: "Details")

        for method in ContactMethod.allCases {
            #expect(CommunicationLauncher.url(for: method, person: person) == nil)
            #expect(!CommunicationLauncher.isAvailable(method, for: person))
        }
    }
}

@Suite("Relative dates")
struct RelativeDateTests {

    private let calendar = TestDates.calendar
    private let now = TestDates.now

    @Test("Past phrasing")
    func pastPhrasing() {
        #expect(RelativeDate.past(TestDates.days(0), now: now, calendar: calendar) == "Today")
        #expect(RelativeDate.past(TestDates.days(-1), now: now, calendar: calendar) == "Yesterday")
        #expect(RelativeDate.past(TestDates.days(-3), now: now, calendar: calendar) == "3 days ago")
    }

    @Test("Due phrasing")
    func duePhrasing() {
        #expect(RelativeDate.due(TestDates.days(0), now: now, calendar: calendar) == "Today")
        #expect(RelativeDate.due(TestDates.days(1), now: now, calendar: calendar) == "Tomorrow")
        #expect(RelativeDate.due(TestDates.days(-1), now: now, calendar: calendar) == "Yesterday")
        #expect(RelativeDate.due(TestDates.days(-4), now: now, calendar: calendar) == "4 days ago")
    }

    @Test("A person with nothing logged says so instead of showing a date")
    func lastContactWithNoInteractions() {
        #expect(RelativeDate.lastContact(nil, now: now, calendar: calendar) == "No interactions logged")
    }
}

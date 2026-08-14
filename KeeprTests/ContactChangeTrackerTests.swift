import Foundation
import Testing

@testable import Keepr

/// An in-memory stand-in for `UserDefaults`, so these tests never write to the
/// machine running them.
private final class MemoryStore: KeyValueStoring {
    private var data: [String: Data] = [:]
    private var flags: [String: Bool] = [:]

    func storedData(forKey key: String) -> Data? { data[key] }
    func storeData(_ value: Data?, forKey key: String) { data[key] = value }
    func storedFlag(forKey key: String) -> Bool { flags[key] ?? false }
    func storeFlag(_ value: Bool, forKey key: String) { flags[key] = value }
}

/// Noticing new contacts is only useful if it stays quiet the rest of the time.
/// These tests are mostly about the not-nagging half.
@Suite("New contact detection")
@MainActor
struct ContactChangeTrackerTests {

    private func contact(_ identifier: String) -> ContactSummary {
        ContactSummary(
            identifier: identifier,
            givenName: "Test",
            familyName: identifier,
            organizationName: nil,
            jobTitle: nil,
            phoneNumbers: [],
            emailAddresses: [],
            thumbnailData: nil
        )
    }

    private func contacts(_ identifiers: String...) -> [ContactSummary] {
        identifiers.map(contact)
    }

    @Test("The first look reports nothing — an address book is not a to-do list")
    func firstRunEstablishesABaseline() {
        let tracker = ContactChangeTracker(store: MemoryStore())

        let result = tracker.newContacts(in: contacts("a", "b", "c"))

        #expect(result.isEmpty)
        #expect(tracker.hasBaseline)
    }

    @Test("Contacts added after the baseline are reported")
    func laterAdditionsAreNoticed() {
        let tracker = ContactChangeTracker(store: MemoryStore())
        _ = tracker.newContacts(in: contacts("a", "b"))

        let result = tracker.newContacts(in: contacts("a", "b", "c", "d"))

        #expect(result.map(\.identifier) == ["c", "d"])
    }

    @Test("The same new contact is reported until it's answered for, then never again")
    func acknowledgingStopsTheNagging() {
        let tracker = ContactChangeTracker(store: MemoryStore())
        _ = tracker.newContacts(in: contacts("a"))

        #expect(tracker.newContacts(in: contacts("a", "b")).count == 1)
        #expect(tracker.newContacts(in: contacts("a", "b")).count == 1, "unanswered stays open")

        tracker.acknowledge(contacts("b"))

        #expect(tracker.newContacts(in: contacts("a", "b")).isEmpty)
    }

    @Test("State survives a relaunch, because it lives in the store not in memory")
    func stateIsPersisted() {
        let store = MemoryStore()
        let first = ContactChangeTracker(store: store)
        _ = first.newContacts(in: contacts("a"))

        let second = ContactChangeTracker(store: store)

        #expect(second.hasBaseline)
        #expect(second.newContacts(in: contacts("a", "b")).map(\.identifier) == ["b"])
    }

    @Test("A contact deleted and re-added counts as new again")
    func deletedContactsAreForgotten() {
        let tracker = ContactChangeTracker(store: MemoryStore())
        _ = tracker.newContacts(in: contacts("a", "b"))

        // "b" leaves the address book...
        #expect(tracker.newContacts(in: contacts("a")).isEmpty)
        // ...and comes back.
        #expect(tracker.newContacts(in: contacts("a", "b")).map(\.identifier) == ["b"])
    }

    @Test("Erasing everything re-baselines rather than leaving stale identifiers")
    func resetClearsTheBaseline() {
        let tracker = ContactChangeTracker(store: MemoryStore())
        _ = tracker.newContacts(in: contacts("a"))

        tracker.reset()

        #expect(!tracker.hasBaseline)
        #expect(tracker.newContacts(in: contacts("a", "b")).isEmpty, "the next look re-baselines")
        #expect(tracker.newContacts(in: contacts("a", "b", "c")).map(\.identifier) == ["c"])
    }

    @Test("Acknowledging nothing is not an error")
    func acknowledgingAnEmptyListIsHarmless() {
        let tracker = ContactChangeTracker(store: MemoryStore())
        _ = tracker.newContacts(in: contacts("a"))

        tracker.acknowledge([String]())

        #expect(tracker.newContacts(in: contacts("a", "b")).map(\.identifier) == ["b"])
    }
}

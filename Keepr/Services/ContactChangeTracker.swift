import Foundation

/// The slice of `UserDefaults` this app needs, as a protocol so the tracker can
/// be tested without touching a real defaults database.
/// Distinct method names rather than overloads: `store(nil, forKey:)` would be
/// ambiguous between a `Data?` and a `Bool` overload, and the compiler is right
/// to complain.
protocol KeyValueStoring: AnyObject {
    func storedData(forKey key: String) -> Data?
    func storeData(_ data: Data?, forKey key: String)
    func storedFlag(forKey key: String) -> Bool
    func storeFlag(_ value: Bool, forKey key: String)
}

extension UserDefaults: KeyValueStoring {
    func storedData(forKey key: String) -> Data? { data(forKey: key) }
    func storeData(_ data: Data?, forKey key: String) { set(data, forKey: key) }
    func storedFlag(forKey key: String) -> Bool { bool(forKey: key) }
    func storeFlag(_ value: Bool, forKey key: String) { set(value, forKey: key) }
}

/// Notices contacts added to the phone since Keepr last looked.
///
/// The point is the window right after you meet someone: you save their number,
/// and for about a day you still remember which bar it was and what they do. A
/// week later that's gone. So the app watches for cards it hasn't seen before
/// and asks while the answer is still available.
///
/// Two rules keep this from being annoying:
///
/// - **The first run establishes a baseline and reports nothing.** An address
///   book of 900 contacts is not 900 new people to categorize, and a screen
///   that opens by demanding 900 decisions gets closed forever.
/// - **Anything the user has answered — or waved off — is remembered as seen.**
///   The same contact is never raised twice.
///
/// Only identifiers are stored. No names, no numbers, nothing readable.
@MainActor
final class ContactChangeTracker {

    private enum Key {
        static let known = "keepr.knownContactIdentifiers"
        static let hasBaseline = "keepr.hasContactBaseline"
    }

    static let shared = ContactChangeTracker()

    private let store: KeyValueStoring
    private var known: Set<String>

    init(store: KeyValueStoring = UserDefaults.standard) {
        self.store = store
        let data = store.storedData(forKey: Key.known) ?? Data()
        self.known = (try? JSONDecoder().decode(Set<String>.self, from: data)) ?? []
    }

    var hasBaseline: Bool { store.storedFlag(forKey: Key.hasBaseline) }

    /// Contacts that weren't there the last time Keepr looked.
    ///
    /// Also drops identifiers that have left the address book, so deleting and
    /// re-adding a contact surfaces them again — which is the right answer, and
    /// keeps the stored set from growing forever.
    func newContacts(in contacts: [ContactSummary]) -> [ContactSummary] {
        let current = Set(contacts.map(\.identifier))

        guard hasBaseline else {
            record(current)
            store.storeFlag(true, forKey: Key.hasBaseline)
            return []
        }

        let unseen = contacts.filter { !known.contains($0.identifier) }
        let pruned = known.intersection(current)
        if pruned != known { record(pruned) }
        return unseen
    }

    /// Marks contacts as dealt with, whether they were categorized or waved off.
    func acknowledge(_ identifiers: [String]) {
        guard !identifiers.isEmpty else { return }
        record(known.union(identifiers))
    }

    func acknowledge(_ contacts: [ContactSummary]) {
        acknowledge(contacts.map(\.identifier))
    }

    /// Forgets everything, so the next pass re-baselines. Used by Erase All Data —
    /// leaving a stale identifier set behind after a wipe would mean a newly
    /// re-added contact was silently treated as old.
    func reset() {
        known = []
        store.storeData(nil, forKey: Key.known)
        store.storeFlag(false, forKey: Key.hasBaseline)
    }

    private func record(_ identifiers: Set<String>) {
        known = identifiers
        store.storeData(try? JSONEncoder().encode(identifiers), forKey: Key.known)
    }
}

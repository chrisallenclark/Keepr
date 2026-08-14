import Security
import Contacts
import Foundation
import OSLog

/// The app's view of Contacts permission, including iOS 18's limited access.
enum ContactAccess: Equatable, Sendable {
    case notDetermined
    case denied
    case restricted
    /// iOS 18+: the user picked specific contacts to share.
    case limited
    case authorized

    var canRead: Bool { self == .authorized || self == .limited }

    /// Shown when the user has said no. Never scolds — the app works without this.
    var explanation: String? {
        switch self {
        case .authorized, .limited, .notDetermined:
            nil
        case .denied:
            "Contact access is off. You can still add people by hand, or turn it on in Settings."
        case .restricted:
            "Contact access isn't available on this device. You can still add people by hand."
        }
    }
}

/// A labelled relation from a contact card, e.g. label "mother", name "Jane Smith".
struct ContactRelation: Hashable, Sendable {
    let label: String
    let name: String
}

/// A minimal, value-type snapshot of an Apple contact.
///
/// Only the fields Keepr actually shows or acts on. Nothing else is read.
struct ContactSummary: Identifiable, Hashable, Sendable {
    let identifier: String
    let givenName: String
    let familyName: String
    let organizationName: String?
    let jobTitle: String?
    let phoneNumbers: [String]
    let emailAddresses: [String]
    let thumbnailData: Data?
    var birthday: Date?
    var postalAddress: String?
    /// Labelled relations already on the card — "mother: Jane Smith".
    var relations: [ContactRelation] = []
    /// The card's own Notes field. Populated only when Apple has granted the
    /// contacts-notes entitlement; nil in every other case.
    var note: String?

    var id: String { identifier }

    var fullName: String {
        let name = [givenName, familyName]
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return name.isEmpty ? (organizationName ?? "No Name") : name
    }

    var subtitle: String? {
        let value = [jobTitle, organizationName]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        return value.isEmpty ? phoneNumbers.first : value
    }

    var initials: String {
        let parts = fullName.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }
        let joined = parts.joined().uppercased()
        return joined.isEmpty ? "?" : joined
    }

    var sortKey: String {
        let key = familyName.isEmpty ? fullName : familyName
        return key.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
    }
}

/// Everything the app needs from Contacts. Injected so previews and tests never
/// touch the real contact store.
protocol ContactStoreProviding: Sendable {
    func authorizationStatus() -> ContactAccess
    /// Presents the system prompt. Call only after the user has been told why.
    func requestAccess() async -> ContactAccess
    func fetchContacts() async -> [ContactSummary]
    func contact(withIdentifier identifier: String) async -> ContactSummary?
}

// MARK: - Live

/// An actor so contact enumeration never runs on the main thread — fetching a
/// large address book is slow enough to drop frames.
actor LiveContactStore: ContactStoreProviding {

    private let store = CNContactStore()

    private static let keys: [CNKeyDescriptor] = [
        CNContactIdentifierKey,
        CNContactGivenNameKey,
        CNContactFamilyNameKey,
        CNContactOrganizationNameKey,
        CNContactJobTitleKey,
        CNContactPhoneNumbersKey,
        CNContactEmailAddressesKey,
        CNContactThumbnailImageDataKey,
        CNContactBirthdayKey,
        CNContactPostalAddressesKey,
        CNContactRelationsKey
    ].map { $0 as CNKeyDescriptor }

    /// Apple gates the Notes field behind `com.apple.developer.contacts.notes`,
    /// which has to be applied for and approved per app. Requesting the key
    /// without it makes the whole fetch fail, so it's only added once the
    /// entitlement is actually present in the running binary.
    private static var keysIncludingNoteIfEntitled: [CNKeyDescriptor] {
        guard hasNotesEntitlement else { return keys }
        return keys + [CNContactNoteKey as CNKeyDescriptor]
    }

    static var hasNotesEntitlement: Bool {
        guard let task = SecTaskCreateFromSelf(nil) else { return false }
        let value = SecTaskCopyValueForEntitlement(
            task,
            "com.apple.developer.contacts.notes" as CFString,
            nil
        )
        return (value as? Bool) == true
    }

    nonisolated func authorizationStatus() -> ContactAccess {
        Self.access(from: CNContactStore.authorizationStatus(for: .contacts))
    }

    func requestAccess() async -> ContactAccess {
        let granted: Bool = await withCheckedContinuation { continuation in
            store.requestAccess(for: .contacts) { granted, _ in
                continuation.resume(returning: granted)
            }
        }
        // Re-read rather than trusting the boolean: on iOS 18 "limited" comes
        // back as granted, and we want to know which it was.
        let status = Self.access(from: CNContactStore.authorizationStatus(for: .contacts))
        if !granted, status == .notDetermined { return .denied }
        return status
    }

    func fetchContacts() async -> [ContactSummary] {
        guard authorizationStatus().canRead else { return [] }

        let request = CNContactFetchRequest(keysToFetch: Self.keysIncludingNoteIfEntitled)
        request.sortOrder = .userDefault
        request.unifyResults = true

        var results: [ContactSummary] = []
        do {
            try store.enumerateContacts(with: request) { contact, _ in
                results.append(Self.summary(from: contact))
            }
        } catch {
            Logger.contacts.error("Contact enumeration failed.")
            return []
        }
        return results.filter { !$0.fullName.isEmpty }
    }

    func contact(withIdentifier identifier: String) async -> ContactSummary? {
        guard authorizationStatus().canRead else { return nil }
        let predicate = CNContact.predicateForContacts(withIdentifiers: [identifier])
        guard let match = try? store.unifiedContacts(matching: predicate, keysToFetch: Self.keysIncludingNoteIfEntitled).first
        else { return nil }
        return Self.summary(from: match)
    }

    // MARK: Mapping

    private static func summary(from contact: CNContact) -> ContactSummary {
        var summary = ContactSummary(
            identifier: contact.identifier,
            givenName: contact.givenName,
            familyName: contact.familyName,
            organizationName: contact.organizationName.isEmpty ? nil : contact.organizationName,
            jobTitle: contact.jobTitle.isEmpty ? nil : contact.jobTitle,
            phoneNumbers: contact.phoneNumbers.map(\.value.stringValue),
            emailAddresses: contact.emailAddresses.map { $0.value as String },
            thumbnailData: contact.thumbnailImageData
        )

        // Every read below is guarded: asking a CNContact for a key that wasn't
        // fetched raises an exception rather than returning nil.
        if contact.isKeyAvailable(CNContactBirthdayKey),
           let components = contact.birthday {
            summary.birthday = Calendar.current.date(from: components)
        }

        if contact.isKeyAvailable(CNContactPostalAddressesKey),
           let address = contact.postalAddresses.first {
            let formatted = CNPostalAddressFormatter.string(
                from: address.value,
                style: .mailingAddress
            )
            summary.postalAddress = formatted.isEmpty
                ? nil
                : formatted.replacingOccurrences(of: "\n", with: ", ")
        }

        if contact.isKeyAvailable(CNContactRelationsKey) {
            summary.relations = contact.contactRelations.compactMap { relation in
                let raw = relation.label ?? ""
                let label = CNLabeledValue<CNContactRelation>.localizedString(forLabel: raw)
                let name = relation.value.name.trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return nil }
                return ContactRelation(label: label, name: name)
            }
        }

        if contact.isKeyAvailable(CNContactNoteKey) {
            let note = contact.note.trimmingCharacters(in: .whitespacesAndNewlines)
            summary.note = note.isEmpty ? nil : note
        }

        return summary
    }

    private static func access(from status: CNAuthorizationStatus) -> ContactAccess {
        // Limited access is iOS 18+, so it's checked separately rather than as a
        // switch case the older SDK wouldn't know about.
        if #available(iOS 18.0, *), status == .limited { return .limited }

        switch status {
        case .authorized: return .authorized
        case .denied: return .denied
        case .restricted: return .restricted
        case .notDetermined: return .notDetermined
        default: return .notDetermined
        }
    }
}

// MARK: - Preview / test double

/// Deterministic fake used by previews and tests. Never touches Contacts.
struct StubContactStore: ContactStoreProviding {
    var access: ContactAccess = .authorized
    var contacts: [ContactSummary] = StubContactStore.defaultContacts

    func authorizationStatus() -> ContactAccess { access }
    func requestAccess() async -> ContactAccess { access }
    func fetchContacts() async -> [ContactSummary] { access.canRead ? contacts : [] }

    func contact(withIdentifier identifier: String) async -> ContactSummary? {
        contacts.first { $0.identifier == identifier }
    }

    static let defaultContacts: [ContactSummary] = [
        .init(
            identifier: "stub-1", givenName: "Jake", familyName: "Martinez",
            organizationName: "Martinez Roofing", jobTitle: "Owner",
            phoneNumbers: ["(561) 555-0142"], emailAddresses: ["jake@martinezroofing.com"],
            thumbnailData: nil
        ),
        .init(
            identifier: "stub-2", givenName: "Sarah", familyName: "Miller",
            organizationName: "Coastal Design Co.", jobTitle: "Creative Director",
            phoneNumbers: ["(561) 555-0198"], emailAddresses: [], thumbnailData: nil
        ),
        .init(
            identifier: "stub-3", givenName: "Alex", familyName: "Nguyen",
            organizationName: nil, jobTitle: nil,
            phoneNumbers: ["(561) 555-0155"], emailAddresses: [], thumbnailData: nil
        ),
        .init(
            identifier: "stub-4", givenName: "Linda", familyName: "Clark",
            organizationName: nil, jobTitle: nil,
            phoneNumbers: ["(407) 555-0121"], emailAddresses: [], thumbnailData: nil
        )
    ]
}

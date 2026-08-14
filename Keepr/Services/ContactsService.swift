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
        CNContactThumbnailImageDataKey
    ].map { $0 as CNKeyDescriptor }

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

        let request = CNContactFetchRequest(keysToFetch: Self.keys)
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
        guard let match = try? store.unifiedContacts(matching: predicate, keysToFetch: Self.keys).first
        else { return nil }
        return Self.summary(from: match)
    }

    // MARK: Mapping

    private static func summary(from contact: CNContact) -> ContactSummary {
        ContactSummary(
            identifier: contact.identifier,
            givenName: contact.givenName,
            familyName: contact.familyName,
            organizationName: contact.organizationName.isEmpty ? nil : contact.organizationName,
            jobTitle: contact.jobTitle.isEmpty ? nil : contact.jobTitle,
            phoneNumbers: contact.phoneNumbers.map(\.value.stringValue),
            emailAddresses: contact.emailAddresses.map { $0.value as String },
            thumbnailData: contact.thumbnailImageData
        )
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

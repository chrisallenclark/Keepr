import Foundation

/// The ways Keepr can reach someone.
///
/// All of these hand off to a system app through a documented URL scheme.
/// Keepr never reads Messages, Mail or call history — see docs/ARCHITECTURE.md §5.
enum ContactMethod: String, CaseIterable, Identifiable {
    case message
    case call
    case email

    var id: String { rawValue }

    var title: String {
        switch self {
        case .message: "Message"
        case .call: "Call"
        case .email: "Email"
        }
    }

    var symbolName: String {
        switch self {
        case .message: "message.fill"
        case .call: "phone.fill"
        case .email: "envelope.fill"
        }
    }
}

enum CommunicationLauncher {

    /// The URL that opens the right system app, or nil when the person has no
    /// number or address for it.
    static func url(for method: ContactMethod, person: Person) -> URL? {
        switch method {
        case .message:
            guard let phone = person.primaryPhone else { return nil }
            return url(scheme: "sms", value: dialable(phone))
        case .call:
            guard let phone = person.primaryPhone else { return nil }
            return url(scheme: "tel", value: dialable(phone))
        case .email:
            guard let email = person.primaryEmail else { return nil }
            return url(scheme: "mailto", value: email)
        }
    }

    static func isAvailable(_ method: ContactMethod, for person: Person) -> Bool {
        switch method {
        case .message, .call: person.primaryPhone != nil
        case .email: person.primaryEmail != nil
        }
    }

    /// Strips formatting `tel:`/`sms:` can't handle, keeping "+" for country codes.
    ///
    /// Anything from the first letter onward is dropped, so "555-0142 ext 2"
    /// dials the number rather than silently appending the extension's digits
    /// and calling someone else.
    static func dialable(_ phoneNumber: String) -> String {
        let dialPart = phoneNumber.prefix { !$0.isLetter }
        let allowed = CharacterSet(charactersIn: "+0123456789")
        return String(String(dialPart).unicodeScalars.filter { allowed.contains($0) })
    }

    private static func url(scheme: String, value: String) -> URL? {
        let encoded = value.addingPercentEncoding(
            withAllowedCharacters: .urlQueryAllowed
        ) ?? value
        return URL(string: "\(scheme):\(encoded)")
    }
}

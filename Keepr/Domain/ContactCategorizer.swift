import Foundation

/// What the importer proposes for one contact, before the user confirms it.
struct CategorySuggestion: Equatable {
    var context: RelationshipContext
    /// Name of a built-in `RelationshipTag`, or nil when nothing fits well.
    var tagName: String?
    /// Why the guess was made, shown under the contact so it can be judged
    /// rather than trusted.
    var reason: String
    var confidence: Confidence

    enum Confidence: Int, Comparable {
        case low
        case medium
        case high

        static func < (lhs: Confidence, rhs: Confidence) -> Bool {
            lhs.rawValue < rhs.rawValue
        }
    }
}

/// Guesses whether a contact is a business or personal relationship.
///
/// The bar for guessing is deliberately high. A wrong suggestion that arrives
/// pre-selected is worse than no suggestion: it gets accepted in bulk and
/// quietly poisons the data. So every rule here keys off something the user
/// actually typed into the contact card — an employer, a job title, a work
/// email domain, a family relation — and anything else comes back `nil` for
/// the user to decide.
enum ContactCategorizer {

    /// Consumer mail providers. An address at one of these says nothing about
    /// whether someone is a work contact.
    static let personalEmailDomains: Set<String> = [
        "gmail.com", "googlemail.com", "yahoo.com", "ymail.com", "hotmail.com",
        "outlook.com", "live.com", "msn.com", "icloud.com", "me.com", "mac.com",
        "aol.com", "proton.me", "protonmail.com", "gmx.com", "zoho.com",
        "comcast.net", "att.net", "verizon.net", "sbcglobal.net", "cox.net"
    ]

    /// Relation labels on a contact card that mean family.
    static let familyRelationLabels: Set<String> = [
        "mother", "father", "parent", "brother", "sister", "sibling", "child",
        "son", "daughter", "spouse", "husband", "wife", "partner",
        "grandmother", "grandfather", "grandparent", "grandchild"
    ]

    /// Job titles that read as someone running a business — worth flagging as a
    /// lead rather than a generic professional contact.
    static let ownerTitles: [String] = [
        "owner", "founder", "co-founder", "ceo", "president", "principal",
        "partner", "proprietor", "director", "managing"
    ]

    static func suggestion(for contact: ContactSummary) -> CategorySuggestion? {
        // Family stated on the card is the strongest personal signal there is.
        if let relation = contact.relations.first(where: {
            familyRelationLabels.contains($0.label.lowercased())
        }) {
            return CategorySuggestion(
                context: .personal,
                tagName: "Family",
                reason: "Listed as \(relation.label.lowercased()) on the contact card",
                confidence: .high
            )
        }

        let title = contact.jobTitle?.trimmingCharacters(in: .whitespaces) ?? ""
        let company = contact.organizationName?.trimmingCharacters(in: .whitespaces) ?? ""

        if !company.isEmpty || !title.isEmpty {
            let lowerTitle = title.lowercased()
            let isOwner = ownerTitles.contains { lowerTitle.contains($0) }

            let reason: String
            if !company.isEmpty, !title.isEmpty {
                reason = "\(title) at \(company)"
            } else if !company.isEmpty {
                reason = "Works at \(company)"
            } else {
                reason = title
            }

            return CategorySuggestion(
                context: .business,
                tagName: isOwner ? "Potential Client" : "Professional Contact",
                reason: reason,
                confidence: .high
            )
        }

        // A work email domain implies an employer the card didn't spell out.
        if let domain = workEmailDomain(in: contact.emailAddresses) {
            return CategorySuggestion(
                context: .business,
                tagName: "Professional Contact",
                reason: "Work email at \(domain)",
                confidence: .medium
            )
        }

        // Nothing on the card distinguishes a friend from an acquaintance from
        // a plumber, so don't pretend otherwise.
        return nil
    }

    /// The first email domain that isn't a consumer mail provider.
    static func workEmailDomain(in addresses: [String]) -> String? {
        for address in addresses {
            let parts = address.lowercased().split(separator: "@")
            guard parts.count == 2 else { continue }
            let domain = String(parts[1])
            if !personalEmailDomains.contains(domain) {
                return domain
            }
        }
        return nil
    }

    /// Suggestions for a whole import, keyed by contact identifier. Contacts the
    /// rules can't place are simply absent.
    static func suggestions(for contacts: [ContactSummary]) -> [String: CategorySuggestion] {
        var result: [String: CategorySuggestion] = [:]
        for contact in contacts {
            if let suggestion = suggestion(for: contact) {
                result[contact.identifier] = suggestion
            }
        }
        return result
    }
}

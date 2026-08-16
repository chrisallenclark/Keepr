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

    /// Groups read off the card — ids, since two groups can share a name across
    /// contexts and the user's own record is what matters.
    var groupIDs: [String] = []
    /// Those groups' names, for showing the suggestion without a lookup.
    var groupNames: [String] = []
    /// Types beyond `tagName`, when the card named more than one.
    var extraTagNames: [String] = []

    /// The name with recognized shorthand removed — "Stanley", not
    /// "Stanley LT Client". Nil when nothing needed removing.
    var cleanedGivenName: String?
    var cleanedFamilyName: String?
    /// What the card actually said, kept so nothing is silently lost.
    var originalName: String?

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

    /// The full read on one contact: what the user's own shorthand says, and
    /// then — only where that's silent — what the card's employer and family
    /// fields imply.
    ///
    /// Shorthand wins because it's a decision the user already made. "Stanley LT
    /// Client" is them telling you he's a Life Time client; an employer field is
    /// just a fact about his day job.
    static func suggestion(
        for contact: ContactSummary,
        vocabulary: MarkerVocabulary
    ) -> CategorySuggestion? {
        let markers = ContactMarkerParser.parse(contact, vocabulary: vocabulary)
        // A type the user deleted is a decision. Inferring "Vendor" from an
        // employer field and quietly recreating it would undo that, so the
        // inference keeps its context and drops the type it can't offer.
        let inferred = suggestion(for: contact).map { suggestion -> CategorySuggestion in
            // An empty vocabulary means nobody told us what exists, not that
            // everything was deleted.
            guard !vocabulary.types.isEmpty,
                  let name = suggestion.tagName,
                  !vocabulary.types.contains(where: { $0.display == name })
            else { return suggestion }
            var trimmed = suggestion
            trimmed.tagName = nil
            return trimmed
        }

        guard !markers.isEmpty else { return inferred }

        // A type named on the card decides the context; otherwise fall back to
        // whatever the rest of the card implied, and finally to business — a
        // group with no type at all is most often a work arrangement.
        let namedType = markers.typeNames.first
        let context = namedType
            .flatMap { name in kindOfTag(named: name, vocabulary: vocabulary) }
            .map { RelationshipContext(kind: $0) }
            ?? inferred?.context
            ?? .business

        var suggestion = CategorySuggestion(
            context: context,
            tagName: namedType ?? inferred?.tagName,
            reason: markers.reasons.joined(separator: " · "),
            confidence: .high
        )
        suggestion.groupIDs = markers.groupIDs
        suggestion.groupNames = markers.groupNames
        suggestion.extraTagNames = Array(markers.typeNames.dropFirst())

        if markers.changedName {
            suggestion.cleanedGivenName = markers.givenName
            suggestion.cleanedFamilyName = markers.familyName
            suggestion.originalName = contact.fullName
        }
        return suggestion
    }

    /// Which side a named type sits on, so "Client" lands in Business and
    /// "Friend" doesn't. Custom types carry their own kind, so this works for
    /// a type the user invented as well as a built-in.
    private static func kindOfTag(
        named name: String,
        vocabulary: MarkerVocabulary
    ) -> TagKind? {
        vocabulary.types.first { $0.display == name }?.kind
    }

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

        return suggestion(
            jobTitle: contact.jobTitle,
            company: contact.organizationName,
            emails: contact.emailAddresses
        )
    }

    /// The same rules applied to someone already in Keepr.
    ///
    /// People imported before there was any auto-categorization — or added by
    /// hand and never classified — are exactly who the review queue is for, and
    /// they deserve the same suggestions a fresh import would get.
    static func suggestion(for person: Person) -> CategorySuggestion? {
        suggestion(
            jobTitle: person.jobTitle,
            company: person.company,
            emails: person.emailAddresses
        )
    }

    /// The employer/title/email rules, shared by both entry points.
    static func suggestion(
        jobTitle rawTitle: String?,
        company rawCompany: String?,
        emails: [String]
    ) -> CategorySuggestion? {
        let title = rawTitle?.trimmingCharacters(in: .whitespaces) ?? ""
        let company = rawCompany?.trimmingCharacters(in: .whitespaces) ?? ""

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
        if let domain = workEmailDomain(in: emails) {
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

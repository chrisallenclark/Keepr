import Foundation
import SwiftData

/// A named connection between two specific people — "Jane is Alex's mother",
/// "Priya reports to Dana".
///
/// This is the third axis, and deliberately not a group. A `PersonGroup` is a
/// bucket everyone in it shares ("the gym"); a link is pairwise and directional,
/// and the label reads differently from each end. Modelling that as a group
/// would either lose the label or need a group per pair.
///
/// One record serves both ends. `labelAToB` is what B is to A, `labelBToA` is
/// what A is to B; storing both rather than deriving one keeps "Manager" and
/// "Direct Report" honest, and lets a user write their own pair of words.
@Model
final class PersonLink {

    var id: UUID = UUID()

    var personA: Person?
    var personB: Person?

    /// What B is to A. On A's profile the row reads "Jane Smith · Mother".
    var labelAToB: String = ""
    /// What A is to B. On B's profile the row reads "Alex Nguyen · Child".
    var labelBToA: String = ""

    /// Optional line of context — "introduced us at the Delray mixer".
    var note: String?

    var createdAt: Date = Date()

    init(
        personA: Person? = nil,
        personB: Person? = nil,
        labelAToB: String,
        labelBToA: String,
        note: String? = nil,
        createdAt: Date = Date()
    ) {
        self.id = UUID()
        self.personA = personA
        self.personB = personB
        self.labelAToB = labelAToB
        self.labelBToA = labelBToA
        self.note = note
        self.createdAt = createdAt
    }
}

extension PersonLink {

    func involves(_ person: Person) -> Bool {
        personA?.id == person.id || personB?.id == person.id
    }

    /// The person at the other end, or nil if this link doesn't involve `person`
    /// (or the other end has been deleted).
    func other(than person: Person) -> Person? {
        if personA?.id == person.id { return personB }
        if personB?.id == person.id { return personA }
        return nil
    }

    /// What the person at the other end is to `person`.
    func label(from person: Person) -> String {
        personA?.id == person.id ? labelAToB : labelBToA
    }

    /// True when this link already joins these two, in either direction.
    func joins(_ one: Person, _ two: Person) -> Bool {
        (personA?.id == one.id && personB?.id == two.id)
            || (personA?.id == two.id && personB?.id == one.id)
    }
}

// MARK: - Resolved connection

/// A link seen from one person's side: who's at the other end and what they are.
///
/// The views want "Jane Smith · Mother", not a record with two ends they have to
/// work out the orientation of every time.
struct PersonConnection: Identifiable, Hashable {
    let link: PersonLink
    let person: Person
    let label: String

    var id: UUID { link.id }

    static func == (lhs: PersonConnection, rhs: PersonConnection) -> Bool {
        lhs.link.id == rhs.link.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(link.id)
    }
}

// MARK: - Roles

/// A pair of words for the two ends of a link.
///
/// Built-ins cover the relationships that come off a contact card or out of a
/// business day; anything else is free text, because nobody's family or
/// org chart fits a fixed list.
struct LinkRole: Hashable, Identifiable, Sendable {

    /// What the other person is to you.
    let name: String
    /// What you are to them.
    let inverse: String
    let symbolName: String

    var id: String { name }

    var isSymmetric: Bool { name == inverse }

    /// The same role read from the other end.
    var reversed: LinkRole {
        LinkRole(name: inverse, inverse: name, symbolName: symbolName)
    }

    /// A role the user typed. Symmetric, because there's no way to guess the
    /// other word — and "Trainer / Trainer" is less wrong than a bad guess.
    static func custom(_ name: String) -> LinkRole {
        LinkRole(name: name, inverse: name, symbolName: "link")
    }

    static let family: [LinkRole] = [
        .init(name: "Parent", inverse: "Child", symbolName: "figure.2.and.child.holdinghands"),
        .init(name: "Child", inverse: "Parent", symbolName: "figure.2.and.child.holdinghands"),
        .init(name: "Spouse", inverse: "Spouse", symbolName: "heart"),
        .init(name: "Partner", inverse: "Partner", symbolName: "heart"),
        .init(name: "Sibling", inverse: "Sibling", symbolName: "person.2"),
        .init(name: "Grandparent", inverse: "Grandchild", symbolName: "house"),
        .init(name: "Grandchild", inverse: "Grandparent", symbolName: "house"),
        .init(name: "Family", inverse: "Family", symbolName: "house")
    ]

    static let social: [LinkRole] = [
        .init(name: "Friend", inverse: "Friend", symbolName: "hand.wave"),
        .init(name: "Neighbor", inverse: "Neighbor", symbolName: "building.2"),
        .init(name: "Knows", inverse: "Knows", symbolName: "person.2")
    ]

    static let work: [LinkRole] = [
        .init(name: "Colleague", inverse: "Colleague", symbolName: "briefcase"),
        .init(name: "Manager", inverse: "Direct Report", symbolName: "briefcase"),
        .init(name: "Direct Report", inverse: "Manager", symbolName: "briefcase"),
        .init(name: "Assistant", inverse: "Works With", symbolName: "briefcase"),
        .init(name: "Referred By", inverse: "Referred", symbolName: "arrow.triangle.branch"),
        .init(name: "Referred", inverse: "Referred By", symbolName: "arrow.triangle.branch")
    ]

    static var catalog: [LinkRole] { family + social + work }

    struct Section: Identifiable {
        let title: String
        let roles: [LinkRole]
        var id: String { title }
    }

    static let sections: [Section] = [
        .init(title: "Family", roles: family),
        .init(title: "Social", roles: social),
        .init(title: "Work", roles: work)
    ]

    static func named(_ name: String) -> LinkRole {
        catalog.first { $0.name.localizedCaseInsensitiveCompare(name) == .orderedSame }
            ?? .custom(name)
    }

    /// Maps a relation label off an Apple contact card onto a role. Unknown
    /// labels come back as themselves rather than being forced into "Family" —
    /// a card can say anything, and "Godmother" is not a guess worth making.
    static func forContactLabel(_ label: String) -> LinkRole {
        let key = label
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()

        switch key {
        case "mother", "father", "parent", "mom", "dad":
            return named("Parent")
        case "son", "daughter", "child":
            return named("Child")
        case "brother", "sister", "sibling":
            return named("Sibling")
        case "spouse", "husband", "wife":
            return named("Spouse")
        case "partner", "girlfriend", "boyfriend":
            return named("Partner")
        case "grandmother", "grandfather", "grandparent":
            return named("Grandparent")
        case "grandchild", "grandson", "granddaughter":
            return named("Grandchild")
        case "friend":
            return named("Friend")
        case "manager":
            return named("Manager")
        case "assistant":
            return named("Assistant")
        default:
            return .custom(key.capitalized)
        }
    }
}

import Foundation

/// What this user calls the second axis.
///
/// The concept is fixed — a named list a person can belong to more than one of —
/// but the right word for it isn't. A personal trainer has Life Time, HYP and
/// Meal Prep and calls them businesses; the next person has Hinge, Tinder and
/// Bumble and calls them apps; someone else has a gym, a bar and a college and
/// calls them places. Making the noun a setting costs one stored string and
/// stops the app arguing with everyone about a word.
struct GroupVocabulary: Hashable, Identifiable {

    let singular: String
    let plural: String

    var id: String { singular }

    static let `default` = GroupVocabulary(singular: "Group", plural: "Groups")

    static let presets: [GroupVocabulary] = [
        .default,
        .init(singular: "Business", plural: "Businesses"),
        .init(singular: "Place", plural: "Places"),
        .init(singular: "App", plural: "Apps"),
        .init(singular: "Circle", plural: "Circles"),
        .init(singular: "Source", plural: "Sources")
    ]

    /// "a" or "an", so "Create a Business" and "Create an App" both read right.
    static func article(for singular: String) -> String {
        let first = singular.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().first
        return "aeiou".contains(first ?? "z") ? "an" : "a"
    }

    /// The plural for a stored singular, falling back to a naive "+s" for a word
    /// the user typed themselves.
    static func plural(for singular: String) -> String {
        if let preset = presets.first(where: {
            $0.singular.localizedCaseInsensitiveCompare(singular) == .orderedSame
        }) {
            return preset.plural
        }
        let trimmed = singular.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return `default`.plural }
        if trimmed.lowercased().hasSuffix("s") { return trimmed }
        if trimmed.lowercased().hasSuffix("y"), trimmed.count > 1 {
            return String(trimmed.dropLast()) + "ies"
        }
        return trimmed + "s"
    }
}

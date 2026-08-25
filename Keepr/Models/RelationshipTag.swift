import Foundation
import SwiftData

/// How the user classifies a relationship — "Current Client", "Close Friend".
///
/// A model rather than an enum so the taxonomy can grow (users add their own,
/// we add more built-ins) without a schema migration.
@Model
final class RelationshipTag {

    var id: UUID = UUID()

    var name: String = ""
    var kindRaw: String = TagKind.business.rawValue

    /// Built-ins are seeded on first launch and can't be deleted.
    var isBuiltIn: Bool = false
    /// Stable identity for a built-in, unaffected by renaming.
    ///
    /// Names are what the user sees and is free to change; code that needs to
    /// find "the Family tag" must not go looking for the string "Family", or a
    /// rename silently creates a duplicate on the next import.
    var builtInKey: String?
    /// Other things you call this, comma-separated: "LT Client, Training Client".
    /// Used when reading markers off a contact card at import.
    var aliases: String?
    /// How often someone of this type is worth contacting, in days. `nil` means
    /// no expectation — the app stays quiet about them.
    ///
    /// This is where cadence belongs: "clients every 30 days" is a decision made
    /// once about a category, not re-entered for every client.
    var cadenceDays: Int?

    /// Controls order in pickers and on rows; built-ins are spaced by 10.
    var sortOrder: Int = 0
    /// SF Symbol shown in filter menus.
    var symbolName: String = "tag"
    /// Which of `Theme.Tint` this type is shown in, by raw value.
    ///
    /// Optional so it can be added without a migration, and so a type that has
    /// never been given one still resolves to a stable color derived from its
    /// name rather than to nothing. Stored as the tint's string rather than a
    /// color, which keeps the model free of UI types and lets the palette be
    /// retuned later without touching anyone's data.
    var colorKey: String?

    var createdAt: Date = Date()

    var people: [Person]?

    init(
        name: String,
        kind: TagKind,
        isBuiltIn: Bool = false,
        sortOrder: Int = 0,
        symbolName: String = "tag",
        builtInKey: String? = nil,
        aliases: String? = nil,
        colorKey: String? = nil
    ) {
        self.id = UUID()
        self.name = name
        self.kindRaw = kind.rawValue
        self.isBuiltIn = isBuiltIn
        self.builtInKey = builtInKey
        self.aliases = aliases
        self.sortOrder = sortOrder
        self.symbolName = symbolName
        self.colorKey = colorKey
        self.createdAt = Date()
        self.people = []
    }
}

extension RelationshipTag {

    var kind: TagKind {
        get { TagKind(rawValue: kindRaw) ?? .business }
        set { kindRaw = newValue.rawValue }
    }

    var peopleList: [Person] { people ?? [] }

    /// The color this type is shown in.
    ///
    /// Falls back to a stable choice derived from the name, so a type the user
    /// invented looks as considered as a built-in without anyone having to pick
    /// a swatch. Keyed on `builtInKey ?? name` so renaming "Family" doesn't also
    /// recolor it.
    var tint: Theme.Tint {
        Theme.Tint.resolve(key: colorKey, fallbackSeed: builtInKey ?? name)
    }
}

// MARK: - Built-in catalog

extension RelationshipTag {

    /// Seeded once on first launch. Editing this list later only affects new installs;
    /// treat it as a starting point, not a source of truth.
    struct BuiltIn {
        let name: String
        let kind: TagKind
        let symbolName: String
        let tint: Theme.Tint
    }

    /// Tints group by meaning rather than reaching for variety. Green is
    /// everyone who might bring work in, teal is everyone you work alongside,
    /// orange is anything with heat on it, and graphite is the settled and the
    /// merely filed. Blue is reserved for the two that pay — a current client
    /// and an investor — so it stays worth noticing.
    static let builtInCatalog: [BuiltIn] = [
        // Business
        .init(name: "Current Client", kind: .business, symbolName: "checkmark.seal", tint: .blue),
        .init(name: "Past Client", kind: .business, symbolName: "clock.arrow.circlepath", tint: .graphite),
        .init(name: "Potential Client", kind: .business, symbolName: "sparkles", tint: .green),
        .init(name: "Lead", kind: .business, symbolName: "flame", tint: .orange),
        .init(name: "Business Partner", kind: .business, symbolName: "person.2", tint: .teal),
        .init(name: "Referral Source", kind: .business, symbolName: "arrow.triangle.branch", tint: .green),
        .init(name: "Vendor", kind: .business, symbolName: "shippingbox", tint: .graphite),
        .init(name: "Team", kind: .business, symbolName: "person.3", tint: .teal),
        .init(name: "Colleague", kind: .business, symbolName: "person.2.circle", tint: .teal),
        .init(name: "Professional Contact", kind: .business, symbolName: "briefcase", tint: .graphite),
        .init(name: "Investor", kind: .business, symbolName: "chart.line.uptrend.xyaxis", tint: .blue),
        .init(name: "Mentor", kind: .business, symbolName: "graduationcap", tint: .purple),
        .init(name: "Advisor", kind: .business, symbolName: "lightbulb", tint: .purple),
        .init(name: "Candidate", kind: .business, symbolName: "person.badge.plus", tint: .orange),
        // Personal
        .init(name: "Family", kind: .personal, symbolName: "house", tint: .orange),
        .init(name: "Close Friend", kind: .personal, symbolName: "heart", tint: .pink),
        .init(name: "Friend", kind: .personal, symbolName: "hand.wave", tint: .purple),
        .init(name: "Acquaintance", kind: .personal, symbolName: "person", tint: .graphite)
    ]

}

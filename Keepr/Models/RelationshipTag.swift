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
    /// Controls order in pickers and on rows; built-ins are spaced by 10.
    var sortOrder: Int = 0
    /// SF Symbol shown in filter menus.
    var symbolName: String = "tag"

    var createdAt: Date = Date()

    var people: [Person]?

    init(
        name: String,
        kind: TagKind,
        isBuiltIn: Bool = false,
        sortOrder: Int = 0,
        symbolName: String = "tag"
    ) {
        self.id = UUID()
        self.name = name
        self.kindRaw = kind.rawValue
        self.isBuiltIn = isBuiltIn
        self.sortOrder = sortOrder
        self.symbolName = symbolName
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
}

// MARK: - Built-in catalog

extension RelationshipTag {

    /// Seeded once on first launch. Editing this list later only affects new installs;
    /// treat it as a starting point, not a source of truth.
    struct BuiltIn {
        let name: String
        let kind: TagKind
        let symbolName: String
    }

    static let builtInCatalog: [BuiltIn] = [
        // Business
        .init(name: "Current Client", kind: .business, symbolName: "checkmark.seal"),
        .init(name: "Potential Client", kind: .business, symbolName: "sparkles"),
        .init(name: "Lead", kind: .business, symbolName: "flame"),
        .init(name: "Business Partner", kind: .business, symbolName: "person.2"),
        .init(name: "Referral Source", kind: .business, symbolName: "arrow.triangle.branch"),
        .init(name: "Vendor", kind: .business, symbolName: "shippingbox"),
        .init(name: "Team", kind: .business, symbolName: "person.3"),
        .init(name: "Professional Contact", kind: .business, symbolName: "briefcase"),
        .init(name: "Investor", kind: .business, symbolName: "chart.line.uptrend.xyaxis"),
        // Personal
        .init(name: "Family", kind: .personal, symbolName: "house"),
        .init(name: "Close Friend", kind: .personal, symbolName: "heart"),
        .init(name: "Friend", kind: .personal, symbolName: "hand.wave"),
        .init(name: "Acquaintance", kind: .personal, symbolName: "person")
    ]

    /// Quick filters offered above the People list, per context.
    static func quickFilterNames(for mode: ContextMode) -> [String] {
        switch mode {
        case .business: ["Current Client", "Potential Client", "Lead", "Business Partner"]
        case .personal: ["Family", "Close Friend", "Friend"]
        }
    }
}

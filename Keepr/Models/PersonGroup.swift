import Foundation
import SwiftData

/// A circle of people who belong together for a reason that isn't a
/// relationship *type*: the gym you train at, a bar you met people in, a
/// dating app, a conference, a client's office.
///
/// This is deliberately a second axis. A `RelationshipTag` says *what someone
/// is to you* ("Current Client"); a group says *where they came from* or *who
/// they're with*. Someone can be a Current Client from the gym who is also a
/// Close Friend — one person, one tag set, one group set, no duplication.
@Model
final class PersonGroup {

    var id: UUID = UUID()

    var name: String = ""
    /// SF Symbol chosen by the user when they create the group.
    var symbolName: String = "person.3"
    /// Optional line of context — "Delray" under "Life Time".
    var detail: String?

    /// Other things you call this, comma-separated: "LT, LTF, Life Time Delray".
    ///
    /// This is how import learns your shorthand. A contact card whose last-name
    /// field reads "LT Client" only means Life Time if something tells the app
    /// that LT is Life Time, and the only reliable source for that is you.
    var aliases: String?

    /// Groups can be business, personal, or relevant to both.
    var contextRaw: String = RelationshipContext.both.rawValue

    var createdAt: Date = Date()
    var sortOrder: Int = 0

    /// Matches `Person.isSampleData`, so removing the sample set doesn't strand
    /// an empty "Life Time" nobody created.
    var isSampleData: Bool = false

    var members: [Person]?

    init(
        name: String,
        symbolName: String = "person.3",
        detail: String? = nil,
        aliases: String? = nil,
        context: RelationshipContext = .both,
        sortOrder: Int = 0,
        isSampleData: Bool = false
    ) {
        self.id = UUID()
        self.name = name
        self.symbolName = symbolName
        self.detail = detail
        self.aliases = aliases
        self.contextRaw = context.rawValue
        self.createdAt = Date()
        self.sortOrder = sortOrder
        self.isSampleData = isSampleData
        self.members = []
    }
}

extension PersonGroup {

    var context: RelationshipContext {
        get { RelationshipContext(rawValue: contextRaw) ?? .both }
        set { contextRaw = newValue.rawValue }
    }

    var memberList: [Person] { members ?? [] }

    var memberCount: Int { memberList.count }

    /// Groups shown while a given mode is active.
    func matches(_ mode: ContextMode) -> Bool {
        context.matches(mode)
    }

    /// Starting points offered the first time someone opens Groups, so the
    /// feature isn't an empty screen with a plus button.
    struct Suggestion {
        let name: String
        let symbolName: String
        let context: RelationshipContext
    }

    static let suggestions: [Suggestion] = [
        .init(name: "Gym", symbolName: "figure.run", context: .both),
        .init(name: "Home", symbolName: "house", context: .both),
        .init(name: "Work", symbolName: "building.2", context: .business),
        .init(name: "Neighborhood", symbolName: "building.2", context: .personal),
        .init(name: "College", symbolName: "graduationcap", context: .personal),
        .init(name: "Conference", symbolName: "person.badge.plus", context: .business),
        .init(name: "Nights Out", symbolName: "moon.stars", context: .personal),
        .init(name: "Dating", symbolName: "heart", context: .personal)
    ]
}

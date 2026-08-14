import Foundation

// MARK: - Context

/// The lens the user is currently looking through. This is the organizing
/// principle of the whole app and is persisted between launches.
enum ContextMode: String, CaseIterable, Identifiable, Codable, Sendable {
    case business
    case personal

    var id: String { rawValue }

    var title: String {
        switch self {
        case .business: "Business"
        case .personal: "Personal"
        }
    }

    var symbolName: String {
        switch self {
        case .business: "briefcase"
        case .personal: "house"
        }
    }

    var other: ContextMode {
        self == .business ? .personal : .business
    }
}

/// Where a person sits. A person marked `.both` shows up in either mode —
/// there is still only ever one record for them.
enum RelationshipContext: String, CaseIterable, Identifiable, Codable, Sendable {
    case business
    case personal
    case both

    var id: String { rawValue }

    init(mode: ContextMode) {
        self = mode == .business ? .business : .personal
    }

    var title: String {
        switch self {
        case .business: "Business"
        case .personal: "Personal"
        case .both: "Both"
        }
    }

    var symbolName: String {
        switch self {
        case .business: "briefcase"
        case .personal: "house"
        case .both: "circle.hexagongrid"
        }
    }

    func matches(_ mode: ContextMode) -> Bool {
        self == .both || rawValue == mode.rawValue
    }

    /// Adding an existing person to another context widens them to `.both`
    /// rather than creating a second record.
    func adding(_ mode: ContextMode) -> RelationshipContext {
        self == .both || self != RelationshipContext(mode: mode) ? .both : self
    }
}

// MARK: - Priority

enum Priority: String, CaseIterable, Identifiable, Codable, Sendable {
    case low
    case normal
    case high

    var id: String { rawValue }

    var title: String {
        switch self {
        case .low: "Low"
        case .normal: "Normal"
        case .high: "High"
        }
    }

    /// Higher sorts first.
    var weight: Int {
        switch self {
        case .low: 0
        case .normal: 1
        case .high: 2
        }
    }

    var symbolName: String? {
        self == .high ? "exclamationmark" : nil
    }
}

// MARK: - Relationship status

enum RelationshipStatus: String, CaseIterable, Identifiable, Codable, Sendable {
    case active
    case archived

    var id: String { rawValue }

    var title: String {
        switch self {
        case .active: "Active"
        case .archived: "Archived"
        }
    }
}

// MARK: - Interaction

enum InteractionKind: String, CaseIterable, Identifiable, Codable, Sendable {
    case inPerson
    case call
    case text
    case email
    case meeting
    case other

    var id: String { rawValue }

    var title: String {
        switch self {
        case .inPerson: "In Person"
        case .call: "Call"
        case .text: "Text"
        case .email: "Email"
        case .meeting: "Meeting"
        case .other: "Other"
        }
    }

    var symbolName: String {
        switch self {
        case .inPerson: "figure.wave"
        case .call: "phone"
        case .text: "message"
        case .email: "envelope"
        case .meeting: "person.2"
        case .other: "circle.dotted"
        }
    }
}

// MARK: - Memory

enum MemoryCategory: String, CaseIterable, Identifiable, Codable, Sendable {
    case work
    case family
    case interests
    case goals
    case preferences
    case personal
    case business
    case importantDate
    case other

    var id: String { rawValue }

    var title: String {
        switch self {
        case .work: "Work"
        case .family: "Family"
        case .interests: "Interests"
        case .goals: "Goals"
        case .preferences: "Preferences"
        case .personal: "Personal"
        case .business: "Business"
        case .importantDate: "Important Date"
        case .other: "Other"
        }
    }

    var symbolName: String {
        switch self {
        case .work: "building.2"
        case .family: "figure.2.and.child.holdinghands"
        case .interests: "sparkles"
        case .goals: "target"
        case .preferences: "heart"
        case .personal: "person"
        case .business: "briefcase"
        case .importantDate: "calendar"
        case .other: "note.text"
        }
    }
}

// MARK: - Tags

enum TagKind: String, CaseIterable, Identifiable, Codable, Sendable {
    case business
    case personal

    var id: String { rawValue }

    var title: String {
        switch self {
        case .business: "Business"
        case .personal: "Personal"
        }
    }

    init(mode: ContextMode) {
        self = mode == .business ? .business : .personal
    }
}

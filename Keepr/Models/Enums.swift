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

    /// The side a relationship type sits on.
    init(kind: TagKind) {
        self = kind == .business ? .business : .personal
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

/// How an interaction happened.
///
/// Raw values are stored, so cases can be added without touching existing data —
/// this list has already grown once and should be expected to grow again. It
/// stays a fixed list rather than a user-editable model because "how did you
/// talk" has a real ceiling: past about a dozen options, a picker costs more
/// time than choosing precisely saves. What someone *said* is the part that
/// varies infinitely, and that's free text.
enum InteractionKind: String, CaseIterable, Identifiable, Codable, Sendable {
    case inPerson
    case call
    case video
    case text
    case dm
    case email
    case meeting
    case meal
    case event
    case other

    var id: String { rawValue }

    var title: String {
        switch self {
        case .inPerson: "In Person"
        case .call: "Call"
        case .video: "Video Call"
        case .text: "Text"
        case .dm: "DM"
        case .email: "Email"
        case .meeting: "Meeting"
        case .meal: "Coffee or Meal"
        case .event: "Event"
        case .other: "Other"
        }
    }

    var symbolName: String {
        switch self {
        case .inPerson: "figure.wave"
        case .call: "phone"
        case .video: "video"
        case .text: "message"
        case .dm: "bubble.left.and.bubble.right"
        case .email: "envelope"
        case .meeting: "person.2"
        case .meal: "fork.knife"
        case .event: "calendar"
        case .other: "circle.dotted"
        }
    }
}

// MARK: - Memory

/// The shelves a remembered fact can sit on.
///
/// Deliberately a fixed list rather than free-form: the profile only stays
/// scannable if "what else is on file" can be said in a row of counts, and that
/// needs a vocabulary small enough to hold in your head. Cases are only added
/// when a whole kind of fact was landing in `other` — the signal that a shelf
/// is missing, not that the list is too short.
enum MemoryCategory: String, CaseIterable, Identifiable, Codable, Sendable {
    case work
    case family
    case health
    case progress
    case schedule
    case style
    case opportunity
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
        case .health: "Health"
        case .progress: "Progress"
        case .schedule: "Schedule"
        case .style: "How They Are"
        case .opportunity: "Opportunity"
        case .interests: "Interests"
        case .goals: "Goals"
        case .preferences: "Preferences"
        case .personal: "Personal"
        case .business: "Business"
        case .importantDate: "Important Date"
        case .other: "Other"
        }
    }

    /// One line explaining what belongs here, shown when picking a category by
    /// hand. Without it "Progress" and "Goals" look like the same shelf.
    var explanation: String {
        switch self {
        case .work: "What they do and who they do it for"
        case .family: "Partner, kids, parents, who matters to them"
        case .health: "Injuries, conditions, allergies, what to work around"
        case .progress: "Wins and milestones — what's changed since you met"
        case .schedule: "When they're free, when they train, when they travel"
        case .style: "How they communicate and what they respond to"
        case .opportunity: "Who they could introduce you to, what they might need"
        case .interests: "What they do for fun"
        case .goals: "What they're trying to achieve"
        case .preferences: "Likes, dislikes, how they want things done"
        case .personal: "Where they live, where they're from, their story"
        case .business: "Money, contracts, pricing, the commercial side"
        case .importantDate: "Birthdays, anniversaries, dates to know"
        case .other: "Everything that doesn't fit a shelf yet"
        }
    }

    var symbolName: String {
        switch self {
        case .work: "building.2"
        case .family: "figure.2.and.child.holdinghands"
        case .health: "heart.text.square"
        case .progress: "chart.line.uptrend.xyaxis"
        case .schedule: "clock"
        case .style: "quote.bubble"
        case .opportunity: "arrow.triangle.branch"
        case .interests: "sparkles"
        case .goals: "target"
        case .preferences: "hand.thumbsup"
        case .personal: "person"
        case .business: "briefcase"
        case .importantDate: "calendar"
        case .other: "note.text"
        }
    }
}

// MARK: - Conversation notes

/// Where a write-up came from. Shown on the card, because "from a recording"
/// and "you typed this" deserve different trust.
enum NoteSource: String, CaseIterable, Identifiable, Codable, Sendable {
    case recorder
    case pasted
    case typed
    case screenshot

    var id: String { rawValue }

    var title: String {
        switch self {
        case .recorder: "From a recording"
        case .pasted: "Pasted in"
        case .typed: "Written here"
        case .screenshot: "Screenshots"
        }
    }

    var symbolName: String {
        switch self {
        case .recorder: "waveform"
        case .pasted: "doc.on.clipboard"
        case .typed: "pencil"
        case .screenshot: "photo.on.rectangle.angled"
        }
    }
}

// MARK: - Files

/// What kind of file is on a profile. Drives the icon and whether it gets a
/// thumbnail or a filename row.
enum PersonFileKind: String, CaseIterable, Identifiable, Codable, Sendable {
    case image
    case pdf
    case document
    case other

    var id: String { rawValue }

    var title: String {
        switch self {
        case .image: "Photo"
        case .pdf: "PDF"
        case .document: "Document"
        case .other: "File"
        }
    }

    var symbolName: String {
        switch self {
        case .image: "photo"
        case .pdf: "doc.richtext"
        case .document: "doc.text"
        case .other: "doc"
        }
    }

    /// Best guess from a filename extension, for files picked out of Files.
    init(fileExtension: String) {
        switch fileExtension.lowercased() {
        case "jpg", "jpeg", "png", "heic", "heif", "gif", "webp", "tiff":
            self = .image
        case "pdf":
            self = .pdf
        case "txt", "rtf", "doc", "docx", "pages", "csv", "numbers", "xls", "xlsx":
            self = .document
        default:
            self = .other
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

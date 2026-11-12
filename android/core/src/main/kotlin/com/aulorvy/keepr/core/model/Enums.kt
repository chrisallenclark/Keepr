package com.aulorvy.keepr.core.model

// Translated from Keepr/Models/Enums.swift. Raw values are what's stored, and
// they're identical to iOS's so the two apps' data stays interchangeable.

/** The lens the user is currently looking through. Persisted between launches. */
enum class ContextMode(val rawValue: String, val title: String, val symbolName: String) {
    BUSINESS("business", "Business", "briefcase"),
    PERSONAL("personal", "Personal", "house");

    val other: ContextMode get() = if (this == BUSINESS) PERSONAL else BUSINESS

    companion object {
        fun from(raw: String?): ContextMode? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * Where a person sits. A person marked [BOTH] shows up in either mode — there
 * is still only ever one record for them.
 */
enum class RelationshipContext(val rawValue: String, val title: String, val symbolName: String) {
    BUSINESS("business", "Business", "briefcase"),
    PERSONAL("personal", "Personal", "house"),
    BOTH("both", "Both", "circle.hexagongrid");

    fun matches(mode: ContextMode): Boolean = this == BOTH || rawValue == mode.rawValue

    /**
     * Adding an existing person to another context widens them to [BOTH]
     * rather than creating a second record.
     */
    fun adding(mode: ContextMode): RelationshipContext =
        if (this == BOTH || this != of(mode)) BOTH else this

    companion object {
        fun from(raw: String?): RelationshipContext? = entries.firstOrNull { it.rawValue == raw }
        fun of(mode: ContextMode): RelationshipContext = if (mode == ContextMode.BUSINESS) BUSINESS else PERSONAL
        /** The side a relationship type sits on. */
        fun of(kind: TagKind): RelationshipContext = if (kind == TagKind.BUSINESS) BUSINESS else PERSONAL
    }
}

enum class Priority(val rawValue: String, val title: String, val weight: Int) {
    LOW("low", "Low", 0),
    NORMAL("normal", "Normal", 1),
    HIGH("high", "High", 2);

    val symbolName: String? get() = if (this == HIGH) "exclamationmark" else null

    companion object {
        fun from(raw: String?): Priority? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class RelationshipStatus(val rawValue: String, val title: String) {
    ACTIVE("active", "Active"),
    ARCHIVED("archived", "Archived");

    companion object {
        fun from(raw: String?): RelationshipStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * How an interaction happened. Raw values are stored, so cases can be added
 * without touching existing data.
 */
enum class InteractionKind(val rawValue: String, val title: String, val symbolName: String) {
    IN_PERSON("inPerson", "In Person", "figure.wave"),
    CALL("call", "Call", "phone"),
    VIDEO("video", "Video Call", "video"),
    TEXT("text", "Text", "message"),
    DM("dm", "DM", "bubble.left.and.bubble.right"),
    EMAIL("email", "Email", "envelope"),
    MEETING("meeting", "Meeting", "person.2"),
    MEAL("meal", "Coffee or Meal", "fork.knife"),
    EVENT("event", "Event", "calendar"),
    OTHER("other", "Other", "circle.dotted");

    companion object {
        fun from(raw: String?): InteractionKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** The shelves a remembered fact can sit on. */
enum class MemoryCategory(
    val rawValue: String,
    val title: String,
    /** One line explaining what belongs here, shown when picking by hand. */
    val explanation: String,
    val symbolName: String,
) {
    WORK("work", "Work", "What they do and who they do it for", "building.2"),
    FAMILY("family", "Family", "Partner, kids, parents, who matters to them", "figure.2.and.child.holdinghands"),
    HEALTH("health", "Health", "Injuries, conditions, allergies, what to work around", "heart.text.square"),
    PROGRESS("progress", "Progress", "Wins and milestones — what's changed since you met", "chart.line.uptrend.xyaxis"),
    SCHEDULE("schedule", "Schedule", "When they're free, when they train, when they travel", "clock"),
    STYLE("style", "How They Are", "How they communicate and what they respond to", "quote.bubble"),
    OPPORTUNITY("opportunity", "Opportunity", "Who they could introduce you to, what they might need", "arrow.triangle.branch"),
    INTERESTS("interests", "Interests", "What they do for fun", "sparkles"),
    GOALS("goals", "Goals", "What they're trying to achieve", "target"),
    PREFERENCES("preferences", "Preferences", "Likes, dislikes, how they want things done", "hand.thumbsup"),
    PERSONAL("personal", "Personal", "Where they live, where they're from, their story", "person"),
    BUSINESS("business", "Business", "Money, contracts, pricing, the commercial side", "briefcase"),
    IMPORTANT_DATE("importantDate", "Important Date", "Birthdays, anniversaries, dates to know", "calendar"),
    OTHER("other", "Other", "Everything that doesn't fit a shelf yet", "note.text");

    companion object {
        fun from(raw: String?): MemoryCategory? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** Where a write-up came from. */
enum class NoteSource(val rawValue: String, val title: String, val symbolName: String) {
    RECORDER("recorder", "From a recording", "waveform"),
    PASTED("pasted", "Pasted in", "doc.on.clipboard"),
    TYPED("typed", "Written here", "pencil"),
    SCREENSHOT("screenshot", "Screenshots", "photo.on.rectangle.angled");

    companion object {
        fun from(raw: String?): NoteSource? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** What kind of file is on a profile. */
enum class PersonFileKind(val rawValue: String, val title: String, val symbolName: String) {
    IMAGE("image", "Photo", "photo"),
    PDF("pdf", "PDF", "doc.richtext"),
    DOCUMENT("document", "Document", "doc.text"),
    OTHER("other", "File", "doc");

    companion object {
        fun from(raw: String?): PersonFileKind? = entries.firstOrNull { it.rawValue == raw }

        /** Best guess from a filename extension. */
        fun forExtension(fileExtension: String): PersonFileKind = when (fileExtension.lowercase()) {
            "jpg", "jpeg", "png", "heic", "heif", "gif", "webp", "tiff" -> IMAGE
            "pdf" -> PDF
            "txt", "rtf", "doc", "docx", "pages", "csv", "numbers", "xls", "xlsx" -> DOCUMENT
            else -> OTHER
        }
    }
}

enum class TagKind(val rawValue: String, val title: String) {
    BUSINESS("business", "Business"),
    PERSONAL("personal", "Personal");

    companion object {
        fun from(raw: String?): TagKind? = entries.firstOrNull { it.rawValue == raw }
        fun of(mode: ContextMode): TagKind = if (mode == ContextMode.BUSINESS) BUSINESS else PERSONAL
    }
}

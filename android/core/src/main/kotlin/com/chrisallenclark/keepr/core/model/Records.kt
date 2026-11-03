package com.chrisallenclark.keepr.core.model

import com.chrisallenclark.keepr.core.services.graphemes
import com.chrisallenclark.keepr.core.services.isNewlineCharacter
import com.chrisallenclark.keepr.core.services.isWhitespaceCharacter
import com.chrisallenclark.keepr.core.services.splitCharacters
import com.chrisallenclark.keepr.core.services.trimmingWhitespaces
import java.time.Instant
import java.util.UUID

/*
 * The other eight records, translated from the Swift files in Keepr/Models. Stored
 * properties, defaults and delete rules match the SwiftData models; enums are
 * stored by raw value with typed accessors.
 */

/** A logged meaningful interaction — only ever what the user records. */
class Interaction(
    kind: InteractionKind = InteractionKind.OTHER,
    occurredAt: Instant = Instant.now(),
    title: String? = null,
    rawNote: String? = null,
    summary: String? = null,
    isQuickCapture: Boolean = false,
    awaitingReply: Boolean = false,
    person: Person? = null,
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var occurredAt: Instant = occurredAt
    var createdAt: Instant = Instant.now()
    var kindRaw: String = kind.rawValue
    var title: String? = title
    var rawNote: String? = rawNote
    var summary: String? = summary
    var isQuickCapture: Boolean = isQuickCapture
    /** Outreach that's waiting on an answer. */
    var awaitingReply: Boolean = awaitingReply

    var person: Person? = null
        set(value) { relink(field, value) { it.interactionStore }; field = value }

    internal val memoryStore = mutableListOf<Memory>()
    internal val fileStore = mutableListOf<PersonFile>()
    internal val noteStore = mutableListOf<ConversationNote>()
    internal val followUpStore = mutableListOf<FollowUp>()

    var memories: List<Memory>
        get() = memoryStore.toList()
        set(value) = replaceChildren(memories, value, { it.sourceInteraction = null }, { it.sourceInteraction = this })
    var files: List<PersonFile>
        get() = fileStore.toList()
        set(value) = replaceChildren(files, value, { it.sourceInteraction = null }, { it.sourceInteraction = this })
    var conversationNotes: List<ConversationNote>
        get() = noteStore.toList()
        set(value) = replaceChildren(conversationNotes, value, { it.sourceInteraction = null }, { it.sourceInteraction = this })
    var followUps: List<FollowUp>
        get() = followUpStore.toList()
        set(value) = replaceChildren(followUps, value, { it.sourceInteraction = null }, { it.sourceInteraction = this })

    init {
        this.person = person
    }

    override fun related(): List<Model> = listOfNotNull(person) + memoryStore + fileStore + noteStore + followUpStore

    override fun applyDeleteRules(context: ModelContext) {
        person = null
        memories = emptyList()
        files = emptyList()
        conversationNotes = emptyList()
        followUps = emptyList()
    }

    var kind: InteractionKind
        get() = InteractionKind.from(kindRaw) ?: InteractionKind.OTHER
        set(value) { kindRaw = value.rawValue }

    val memoryList: List<Memory> get() = memories
    val followUpList: List<FollowUp> get() = followUps

    /** The first of title, summary or note that has text; else the kind. */
    val headline: String
        get() {
            for (candidate in listOf(title, summary, rawNote)) {
                val text = candidate?.trimmed()
                if (!text.isNullOrEmpty()) return text
            }
            return kind.title
        }

    /** Summary or note, when it says something the headline doesn't. */
    val detail: String?
        get() {
            val head = headline
            for (candidate in listOf(summary, rawNote)) {
                val text = candidate?.trimmed()
                if (!text.isNullOrEmpty() && text != head) return text
            }
            return null
        }

    val searchableText: String
        get() = listOfNotNull(title, summary, rawNote, kind.title).joinToString(" ")
}

/** A structured fact worth remembering about someone. */
class Memory(
    content: String,
    category: MemoryCategory = MemoryCategory.OTHER,
    importance: Priority = Priority.NORMAL,
    createdAt: Instant = Instant.now(),
    person: Person? = null,
    sourceInteraction: Interaction? = null,
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var content: String = content
    var categoryRaw: String = category.rawValue
    var importanceRaw: String = importance.rawValue
    var createdAt: Instant = createdAt
    var updatedAt: Instant = createdAt
    var isArchived: Boolean = false

    var person: Person? = null
        set(value) { relink(field, value) { it.memoryStore }; field = value }
    /** The interaction it came from; nullified if that's deleted — the memory outlives its note. */
    var sourceInteraction: Interaction? = null
        set(value) { relink(field, value) { it.memoryStore }; field = value }

    init {
        this.person = person
        this.sourceInteraction = sourceInteraction
    }

    override fun related(): List<Model> = listOfNotNull(person, sourceInteraction)

    override fun applyDeleteRules(context: ModelContext) {
        person = null
        sourceInteraction = null
    }

    var category: MemoryCategory
        get() = MemoryCategory.from(categoryRaw) ?: MemoryCategory.OTHER
        set(value) { categoryRaw = value.rawValue }

    var importance: Priority
        get() = Priority.from(importanceRaw) ?: Priority.NORMAL
        set(value) { importanceRaw = value.rawValue }
}

/** A proposed memory the user confirms before anything is saved. */
data class MemoryDraft(
    val content: String,
    val category: MemoryCategory = MemoryCategory.OTHER,
    val importance: Priority = Priority.NORMAL,
    val isSelected: Boolean = true,
    val id: UUID = UUID.randomUUID(),
)

/** Something to do for someone, by a date. */
class FollowUp(
    title: String,
    note: String? = null,
    dueDate: Instant,
    hasTime: Boolean = false,
    priority: Priority = Priority.NORMAL,
    person: Person? = null,
    sourceInteraction: Interaction? = null,
    createdAt: Instant = Instant.now(),
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var title: String = title
    var note: String? = note
    var dueDate: Instant = dueDate
    /** False means "some time that day"; the due date is then the day's start. */
    var hasTime: Boolean = hasTime
    var priorityRaw: String = priority.rawValue
    var isCompleted: Boolean = false
    var completedAt: Instant? = null
    var createdAt: Instant = createdAt
    var lastSnoozedAt: Instant? = null
    var notificationIdentifier: String? = null

    var person: Person? = null
        set(value) { relink(field, value) { it.followUpStore }; field = value }
    var sourceInteraction: Interaction? = null
        set(value) { relink(field, value) { it.followUpStore }; field = value }

    init {
        this.person = person
        this.sourceInteraction = sourceInteraction
    }

    override fun related(): List<Model> = listOfNotNull(person, sourceInteraction)

    override fun applyDeleteRules(context: ModelContext) {
        person = null
        sourceInteraction = null
    }

    var priority: Priority
        get() = Priority.from(priorityRaw) ?: Priority.NORMAL
        set(value) { priorityRaw = value.rawValue }

    fun isOverdue(now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): Boolean {
        if (isCompleted) return false
        if (hasTime) return dueDate < now
        return calendar.startOfDay(dueDate) < calendar.startOfDay(now)
    }

    fun isDueToday(now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): Boolean {
        if (isCompleted) return false
        return calendar.isSameDay(dueDate, now)
    }

    fun needsAttention(now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): Boolean =
        isOverdue(now, calendar) || isDueToday(now, calendar)

    fun complete(at: Instant = Instant.now()) {
        isCompleted = true
        completedAt = at
    }

    fun reopen() {
        isCompleted = false
        completedAt = null
    }

    /** Pushes the due date [days] from whichever is later, the due date or now. */
    fun snooze(byDays: Int, from: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()) {
        val base = maxOf(dueDate, from)
        val target = calendar.addingDays(byDays, base)
        dueDate = if (hasTime) target else calendar.startOfDay(target)
        lastSnoozedAt = from
    }
}

/** A proposed follow-up the user confirms before anything is saved. */
data class FollowUpDraft(
    val title: String,
    val dueDate: Instant,
    val hasTime: Boolean = false,
    val priority: Priority = Priority.NORMAL,
    val isSelected: Boolean = true,
    val id: UUID = UUID.randomUUID(),
)

/** A relationship type: "Current Client", "Investor". A model so the taxonomy can evolve. */
class RelationshipTag(
    name: String,
    kind: TagKind,
    isBuiltIn: Boolean = false,
    sortOrder: Int = 0,
    symbolName: String = "tag",
    builtInKey: String? = null,
    aliases: String? = null,
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var name: String = name
    var kindRaw: String = kind.rawValue
    var isBuiltIn: Boolean = isBuiltIn
    /** How a built-in is found, never by visible name — renaming must not duplicate it. */
    var builtInKey: String? = builtInKey
    /** Comma-separated shorthand the user writes on contact cards. */
    var aliases: String? = aliases
    /** Days between contacts for people of this type; nil means no rhythm. */
    var cadenceDays: Int? = null
    var sortOrder: Int = sortOrder
    var symbolName: String = symbolName
    var createdAt: Instant = Instant.now()

    internal val peopleStore = mutableListOf<Person>()

    var people: List<Person>
        get() = peopleStore.toList()
        set(value) = replaceMany(peopleStore, value) { it.tagStore }

    override fun related(): List<Model> = peopleStore.toList()

    override fun applyDeleteRules(context: ModelContext) {
        people = emptyList()
    }

    var kind: TagKind
        get() = TagKind.from(kindRaw) ?: TagKind.BUSINESS
        set(value) { kindRaw = value.rawValue }

    val peopleList: List<Person> get() = people

    data class BuiltIn(val name: String, val kind: TagKind, val symbolName: String)

    companion object {
        val builtInCatalog: List<BuiltIn> = listOf(
            BuiltIn("Current Client", TagKind.BUSINESS, "checkmark.seal"),
            BuiltIn("Past Client", TagKind.BUSINESS, "clock.arrow.circlepath"),
            BuiltIn("Potential Client", TagKind.BUSINESS, "sparkles"),
            BuiltIn("Lead", TagKind.BUSINESS, "flame"),
            BuiltIn("Business Partner", TagKind.BUSINESS, "person.2"),
            BuiltIn("Referral Source", TagKind.BUSINESS, "arrow.triangle.branch"),
            BuiltIn("Vendor", TagKind.BUSINESS, "shippingbox"),
            BuiltIn("Team", TagKind.BUSINESS, "person.3"),
            BuiltIn("Colleague", TagKind.BUSINESS, "person.2.circle"),
            BuiltIn("Professional Contact", TagKind.BUSINESS, "briefcase"),
            BuiltIn("Investor", TagKind.BUSINESS, "chart.line.uptrend.xyaxis"),
            BuiltIn("Mentor", TagKind.BUSINESS, "graduationcap"),
            BuiltIn("Advisor", TagKind.BUSINESS, "lightbulb"),
            BuiltIn("Candidate", TagKind.BUSINESS, "person.badge.plus"),
            BuiltIn("Family", TagKind.PERSONAL, "house"),
            BuiltIn("Close Friend", TagKind.PERSONAL, "heart"),
            BuiltIn("Friend", TagKind.PERSONAL, "hand.wave"),
            BuiltIn("Acquaintance", TagKind.PERSONAL, "person"),
        )
    }
}

/**
 * What a relationship comes through — a gym, a business, an app. The user
 * chooses the word for it ("Group", "Business", "App").
 */
class PersonGroup(
    name: String,
    symbolName: String = "person.3",
    detail: String? = null,
    aliases: String? = null,
    context: RelationshipContext = RelationshipContext.BOTH,
    sortOrder: Int = 0,
    isSampleData: Boolean = false,
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var name: String = name
    var symbolName: String = symbolName
    var detail: String? = detail
    var aliases: String? = aliases
    var contextRaw: String = context.rawValue
    var createdAt: Instant = Instant.now()
    var sortOrder: Int = sortOrder
    var isSampleData: Boolean = isSampleData

    internal val memberStore = mutableListOf<Person>()

    var members: List<Person>
        get() = memberStore.toList()
        set(value) = replaceMany(memberStore, value) { it.groupStore }

    override fun related(): List<Model> = memberStore.toList()

    override fun applyDeleteRules(context: ModelContext) {
        members = emptyList()
    }

    var context: RelationshipContext
        get() = RelationshipContext.from(contextRaw) ?: RelationshipContext.BOTH
        set(value) { contextRaw = value.rawValue }

    val memberList: List<Person> get() = members
    val memberCount: Int get() = memberStore.size

    fun matches(mode: ContextMode): Boolean = context.matches(mode)

    data class Suggestion(val name: String, val symbolName: String, val context: RelationshipContext)

    companion object {
        val suggestions: List<Suggestion> = listOf(
            Suggestion("Gym", "figure.run", RelationshipContext.BOTH),
            Suggestion("Home", "house", RelationshipContext.BOTH),
            Suggestion("Work", "building.2", RelationshipContext.BUSINESS),
            Suggestion("Neighborhood", "building.2", RelationshipContext.PERSONAL),
            Suggestion("College", "graduationcap", RelationshipContext.PERSONAL),
            Suggestion("Conference", "person.badge.plus", RelationshipContext.BUSINESS),
            Suggestion("Nights Out", "moon.stars", RelationshipContext.PERSONAL),
            Suggestion("Dating", "heart", RelationshipContext.PERSONAL),
        )
    }
}

/**
 * Who someone is to another person. One record serves both profiles, with a
 * label for each direction so "Manager" doesn't read as "Manager" from the
 * report's side.
 */
class PersonLink(
    personA: Person? = null,
    personB: Person? = null,
    labelAToB: String,
    labelBToA: String,
    note: String? = null,
    createdAt: Instant = Instant.now(),
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var personA: Person? = null
        set(value) { relink(field, value) { it.linkAStore }; field = value }
    var personB: Person? = null
        set(value) { relink(field, value) { it.linkBStore }; field = value }
    var labelAToB: String = labelAToB
    var labelBToA: String = labelBToA
    var note: String? = note
    var createdAt: Instant = createdAt

    init {
        this.personA = personA
        this.personB = personB
    }

    override fun related(): List<Model> = listOfNotNull(personA, personB)

    override fun applyDeleteRules(context: ModelContext) {
        personA = null
        personB = null
    }

    fun involves(person: Person): Boolean = personA?.id == person.id || personB?.id == person.id

    fun other(than: Person): Person? = when {
        personA?.id == than.id -> personB
        personB?.id == than.id -> personA
        else -> null
    }

    fun label(from: Person): String = if (personA?.id == from.id) labelAToB else labelBToA

    fun joins(one: Person, two: Person): Boolean =
        (personA?.id == one.id && personB?.id == two.id) || (personA?.id == two.id && personB?.id == one.id)
}

/** A write-up the user didn't type: a recorder's summary, pasted notes, screenshots. */
class ConversationNote(
    title: String,
    body: String,
    source: NoteSource = NoteSource.PASTED,
    occurredAt: Instant = Instant.now(),
    createdAt: Instant = Instant.now(),
    isPinned: Boolean = false,
    person: Person? = null,
    sourceInteraction: Interaction? = null,
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var title: String = title
    var body: String = body
    var sourceRaw: String = source.rawValue
    var occurredAt: Instant = occurredAt
    var createdAt: Instant = createdAt
    var updatedAt: Instant = createdAt
    var isPinned: Boolean = isPinned

    var person: Person? = null
        set(value) { relink(field, value) { it.noteStore }; field = value }
    var sourceInteraction: Interaction? = null
        set(value) { relink(field, value) { it.noteStore }; field = value }

    internal val fileStore = mutableListOf<PersonFile>()

    var files: List<PersonFile>
        get() = fileStore.toList()
        set(value) = replaceChildren(files, value, { it.conversationNote = null }, { it.conversationNote = this })

    init {
        this.person = person
        this.sourceInteraction = sourceInteraction
    }

    override fun related(): List<Model> = listOfNotNull(person, sourceInteraction) + fileStore

    override fun applyDeleteRules(context: ModelContext) {
        fileStore.toList().forEach { context.delete(it) } // cascade
        person = null
        sourceInteraction = null
    }

    var source: NoteSource
        get() = NoteSource.from(sourceRaw) ?: NoteSource.PASTED
        set(value) { sourceRaw = value.rawValue }

    /** Images in capture order. */
    val fileList: List<PersonFile> get() = files.sortedBy { it.capturedAt }

    /** The body on one line, cut at a word near 180 characters. */
    val preview: String
        get() {
            // Swift counts and cuts Characters (grapheme clusters), not UTF-16 units.
            val flattened = body
                .splitCharacters(::isNewlineCharacter)
                .map { it.trimmingWhitespaces() }
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            val characters = flattened.graphemes()
            if (characters.size <= 180) return flattened
            val cut = characters.take(180)
            val lastSpace = cut.lastIndexOf(" ")
            if (lastSpace < 0) return cut.joinToString("") + "…"
            return cut.subList(0, lastSpace).joinToString("") + "…"
        }

    val lengthLabel: String
        get() {
            val words = body.splitCharacters(::isWhitespaceCharacter).size
            if (words == 0) return if (fileList.isEmpty()) "Empty" else imageLabel
            val counted = if (words == 1) "1 word" else "${"%,d".format(words)} words"
            return if (fileList.isEmpty()) counted else "$counted · $imageLabel"
        }

    private val imageLabel: String
        get() {
            val count = fileList.size
            return if (count == 1) "1 image" else "$count images"
        }

    val hasBody: Boolean get() = body.trimmed().isNotEmpty()

    val searchableText: String get() = listOf(title, body).joinToString(" ")

    fun touch(date: Instant = Instant.now()) {
        updatedAt = date
    }
}

/** A photo, PDF or document kept on someone's profile, or inside a note. */
class PersonFile(
    title: String,
    kind: PersonFileKind = PersonFileKind.IMAGE,
    fileExtension: String = "jpg",
    data: ByteArray? = null,
    thumbnailData: ByteArray? = null,
    byteCount: Int = 0,
    capturedAt: Instant = Instant.now(),
    createdAt: Instant = Instant.now(),
    note: String? = null,
    person: Person? = null,
    sourceInteraction: Interaction? = null,
    conversationNote: ConversationNote? = null,
    override val id: UUID = UUID.randomUUID(),
) : Model() {
    var title: String = title
    var kindRaw: String = kind.rawValue
    var fileExtension: String = fileExtension

    private var memoryData: ByteArray? = data

    /**
     * The file itself. Optional because a sync may deliver the record before the bytes.
     *
     * Either held in memory (just imported) or read on demand through
     * [dataSource] — Android keeps file contents beside the database rather
     * than inside it, so a launch never loads every PDF on every profile.
     * Reading through the source can touch disk: do it off the main thread.
     */
    var data: ByteArray?
        get() = memoryData ?: dataSource?.invoke()
        set(value) {
            memoryData = value
            dataSource = null
        }

    /**
     * Where the bytes are read from when they aren't held in memory. Never
     * cached: a previewed PDF is read again next time rather than kept
     * resident for the rest of the session.
     */
    var dataSource: (() -> ByteArray?)? = null

    /** The bytes only if they're held in memory — never reads through [dataSource]. */
    val heldBytes: ByteArray? get() = memoryData

    /** True when there are bytes to read, without reading them. */
    val hasData: Boolean get() = memoryData != null || dataSource != null

    /** Stops holding the bytes in memory; from now on they're read through [source]. */
    fun releaseData(source: () -> ByteArray?) {
        memoryData = null
        dataSource = source
    }
    var thumbnailData: ByteArray? = thumbnailData
    var byteCount: Int = byteCount
    var capturedAt: Instant = capturedAt
    var createdAt: Instant = createdAt
    var note: String? = note

    var person: Person? = null
        set(value) { relink(field, value) { it.fileStore }; field = value }
    var sourceInteraction: Interaction? = null
        set(value) { relink(field, value) { it.fileStore }; field = value }
    var conversationNote: ConversationNote? = null
        set(value) { relink(field, value) { it.fileStore }; field = value }

    init {
        this.person = person
        this.sourceInteraction = sourceInteraction
        this.conversationNote = conversationNote
    }

    override fun related(): List<Model> = listOfNotNull(person, sourceInteraction, conversationNote)

    override fun applyDeleteRules(context: ModelContext) {
        person = null
        sourceInteraction = null
        conversationNote = null
    }

    var kind: PersonFileKind
        get() = PersonFileKind.from(kindRaw) ?: PersonFileKind.OTHER
        set(value) { kindRaw = value.rawValue }

    /** "1.2 MB", in Apple's decimal file units. */
    val sizeLabel: String get() = ByteCountFormat.file(byteCount.toLong())

    /** The title made safe for a filename, plus the extension. */
    val suggestedFileName: String
        get() {
            val safe = title.filter { it.isLetterOrDigit() || it.isWhitespace() }.trimmedSpaces()
            val base = safe.ifEmpty { "File" }
            return if (fileExtension.isEmpty()) base else "$base.$fileExtension"
        }

    val isImage: Boolean get() = kind == PersonFileKind.IMAGE

    val belongsToNote: Boolean get() = conversationNote != null
}

/** `ByteCountFormatter` with `.file` style: decimal units, "Zero KB" for nothing. */
object ByteCountFormat {
    fun file(bytes: Long): String {
        if (bytes == 0L) return "Zero KB"
        if (bytes < 1000) return if (bytes == 1L) "1 byte" else "$bytes bytes"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1000 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        val text = if (unit == 0) "%.0f".format(value) else if (value >= 100) "%.0f".format(value) else "%.1f".format(value).removeSuffix(".0")
        return "$text ${units[unit]}"
    }
}

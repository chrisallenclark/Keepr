package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.FollowUp
import com.aulorvy.keepr.core.model.Interaction
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.KeeprCalendar
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.ModelContext
import com.aulorvy.keepr.core.model.NoteSource
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.core.model.PersonFileKind
import com.aulorvy.keepr.core.model.Priority
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.RelationshipStatus
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.model.TagKind
import java.time.Instant

// Translated from KeeprTests/TestSupport.swift.

/** A throwaway in-memory store; one per test keeps tests isolated. */
class TestStore {
    val context = ModelContext()
    /** Seeding records which built-ins it has offered, so each test gets its own. */
    val defaults = InMemoryPreferences()

    fun save() = context.save()

    inline fun <reified T : com.aulorvy.keepr.core.model.Model> fetch(): List<T> = context.fetch()
}

/** UserDefaults stand-in for tests. */
class InMemoryPreferences : com.aulorvy.keepr.core.persistence.Preferences {
    private val values = mutableMapOf<String, Any>()
    override fun stringList(key: String): List<String>? = values[key] as? List<String>
    override fun setStringList(key: String, value: List<String>) { values[key] = value }
    override fun string(key: String): String? = values[key] as? String
    override fun setString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    override fun bool(key: String): Boolean = values[key] as? Boolean ?: false
    override fun setBool(key: String, value: Boolean) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/**
 * Every date in the suite is derived from one fixed reference instant in UTC,
 * so nothing can drift with the wall clock, the machine's locale or DST.
 */
object TestDates {
    /** Gregorian, UTC, English — passed explicitly into every engine call. */
    val calendar: KeeprCalendar = KeeprCalendar.utc

    fun date(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        calendar.date(year, month, day, hour, minute)

    /** The fixed "now" for the whole suite: Sunday 15 June 2025, 12:00 UTC. */
    val now: Instant = date(2025, 6, 15, 12, 0)

    /** `now` shifted by whole days, keeping the 12:00 time of day. */
    fun days(offset: Int, from: Instant = now): Instant = calendar.addingDays(offset, from)

    /** Midnight of the day `offset` days away from `now`. */
    fun dayStart(offset: Int = 0): Instant = calendar.startOfDay(days(offset))

    /** A wall-clock time on the day `dayOffset` days away from `now`. */
    fun time(hour: Int, minute: Int = 0, dayOffset: Int = 0): Instant =
        calendar.settingTime(hour, minute, 0, days(dayOffset))
}

/** Small builders that insert into a context so inverses are maintained. */
object Make {
    fun person(
        context: ModelContext,
        given: String = "Test",
        family: String = "Person",
        preferred: String? = null,
        relationship: RelationshipContext = RelationshipContext.PERSONAL,
        company: String? = null,
        jobTitle: String? = null,
        priority: Priority = Priority.NORMAL,
        isFavorite: Boolean = false,
        status: RelationshipStatus = RelationshipStatus.ACTIVE,
        howWeMet: String? = null,
        introducedBy: String? = null,
        notes: String? = null,
        contactIdentifier: String? = null,
        createdAt: Instant = TestDates.now,
        lastInteractionAt: Instant? = null,
    ): Person {
        val person = Person(
            givenName = given,
            familyName = family,
            preferredName = preferred,
            context = relationship,
            contactIdentifier = contactIdentifier,
            company = company,
            jobTitle = jobTitle,
            priority = priority,
            isFavorite = isFavorite,
            howWeMet = howWeMet,
            introducedBy = introducedBy,
            notes = notes,
            createdAt = createdAt,
        )
        context.insert(person)
        person.status = status
        person.lastInteractionAt = lastInteractionAt
        return person
    }

    fun tag(
        context: ModelContext,
        name: String,
        kind: TagKind = TagKind.BUSINESS,
        isBuiltIn: Boolean = false,
        sortOrder: Int = 0,
        symbolName: String = "tag",
    ): RelationshipTag {
        val tag = RelationshipTag(name, kind, isBuiltIn, sortOrder, symbolName)
        context.insert(tag)
        return tag
    }

    /** Attaches from the person's side; the tag's people list follows. */
    fun attach(tag: RelationshipTag, to: Person) {
        if (to.tags.any { it === tag }) return
        to.tags = to.tags + tag
    }

    fun interaction(
        context: ModelContext,
        kind: InteractionKind = InteractionKind.OTHER,
        occurredAt: Instant = TestDates.now,
        title: String? = null,
        rawNote: String? = null,
        summary: String? = null,
        isQuickCapture: Boolean = false,
        person: Person? = null,
    ): Interaction {
        val interaction = Interaction(kind, occurredAt, title, rawNote, summary, isQuickCapture)
        context.insert(interaction)
        interaction.person = person
        return interaction
    }

    fun memory(
        context: ModelContext,
        content: String,
        category: MemoryCategory = MemoryCategory.OTHER,
        importance: Priority = Priority.NORMAL,
        createdAt: Instant = TestDates.now,
        isArchived: Boolean = false,
        person: Person? = null,
        sourceInteraction: Interaction? = null,
    ): Memory {
        val memory = Memory(content, category, importance, createdAt)
        context.insert(memory)
        memory.isArchived = isArchived
        memory.person = person
        memory.sourceInteraction = sourceInteraction
        return memory
    }

    fun file(
        context: ModelContext,
        title: String,
        kind: PersonFileKind = PersonFileKind.IMAGE,
        fileExtension: String = "jpg",
        data: ByteArray? = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
        byteCount: Int = 3,
        capturedAt: Instant = TestDates.now,
        note: String? = null,
        person: Person? = null,
        conversationNote: ConversationNote? = null,
    ): PersonFile {
        val file = PersonFile(title, kind, fileExtension, data, byteCount = byteCount, capturedAt = capturedAt, note = note)
        context.insert(file)
        file.person = person
        file.conversationNote = conversationNote
        return file
    }

    fun note(
        context: ModelContext,
        title: String,
        body: String = "",
        source: NoteSource = NoteSource.PASTED,
        occurredAt: Instant = TestDates.now,
        isPinned: Boolean = false,
        person: Person? = null,
        sourceInteraction: Interaction? = null,
    ): ConversationNote {
        val note = ConversationNote(title, body, source, occurredAt, isPinned = isPinned)
        context.insert(note)
        note.person = person
        note.sourceInteraction = sourceInteraction
        return note
    }

    fun followUp(
        context: ModelContext,
        title: String = "Follow up",
        note: String? = null,
        due: Instant,
        hasTime: Boolean = false,
        priority: Priority = Priority.NORMAL,
        isCompleted: Boolean = false,
        completedAt: Instant? = null,
        createdAt: Instant = TestDates.now,
        person: Person? = null,
        sourceInteraction: Interaction? = null,
    ): FollowUp {
        val followUp = FollowUp(title, note, due, hasTime, priority, createdAt = createdAt)
        context.insert(followUp)
        followUp.person = person
        followUp.sourceInteraction = sourceInteraction
        if (isCompleted) followUp.complete(completedAt ?: TestDates.now)
        return followUp
    }
}

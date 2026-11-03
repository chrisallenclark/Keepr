package com.chrisallenclark.keepr.core.services

import com.chrisallenclark.keepr.core.domain.Outreach
import com.chrisallenclark.keepr.core.domain.PeopleEngine
import com.chrisallenclark.keepr.core.domain.PeopleSort
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryDraft
import com.chrisallenclark.keepr.core.model.ModelContext
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.localizedCaseInsensitiveCompare
import com.chrisallenclark.keepr.core.model.trimmed
import com.chrisallenclark.keepr.core.model.trimmedSpaces
import java.time.Instant

// The non-view logic of Keepr/Features/Capture/QuickCaptureView.swift,
// Keepr/Features/Capture/PersonPicker.swift and
// Keepr/Features/Person/BrainDumpView.swift, lifted out of the views so it can
// be tested without a device.

/** Who a capture is about: an existing person, or the name of someone to add. */
data class CaptureSubject(val person: Person? = null, val newPersonName: String = "") {
    /** `canSave` on iOS: someone chosen, or a non-blank name to create. */
    val isChosen: Boolean get() = person != null || newPersonName.trimmedSpaces().isNotEmpty()
}

/** What saving a quick capture wrote, so the caller can schedule the reminder. */
data class QuickCaptureResult(val person: Person, val interaction: Interaction, val memories: List<Memory>, val followUp: FollowUp?)

object QuickCaptureWorkflow {

    private fun same(a: String, b: String): Boolean = localizedCaseInsensitiveCompare(a, b) == 0

    /**
     * Preselects an existing person when the name in the note matches one,
     * otherwise offers to create them. Never creates anything on its own.
     * Only people visible in [mode] are matched.
     */
    fun matchSuggestedPerson(name: String?, people: List<Person>, mode: ContextMode): CaptureSubject {
        if (name.isNullOrEmpty()) return CaptureSubject()
        val match = PeopleEngine.visible(people, mode).firstOrNull {
            same(it.displayName, name) || same(it.fullName, name) || same(it.givenName, name)
        }
        return if (match != null) CaptureSubject(person = match) else CaptureSubject(newPersonName = name)
    }

    /**
     * The picker's list: the whole address book, not just the current mode —
     * a capture about a friend shouldn't be impossible to file while you're in
     * Business. Most recent first, or by name while searching.
     */
    fun pickerResults(people: List<Person>, query: String): List<Person> {
        val trimmed = query.trimmed()
        val matching = if (trimmed.isEmpty()) people else people.filter { PeopleEngine.matches(it, trimmed) }
        return PeopleEngine.sort(matching, if (trimmed.isEmpty()) PeopleSort.RECENT else PeopleSort.NAME)
    }

    /** What the picker's "Add …" row would create: the query, else the spotted name. */
    fun proposedNewName(query: String, suggestedName: String?): String {
        val trimmed = query.trimmed()
        return trimmed.ifEmpty { suggestedName ?: "" }
    }

    /** The "Add …" row shows unless the name is empty or someone listed already has it. */
    fun offersNewPerson(proposedName: String, results: List<Person>): Boolean =
        proposedName.isNotEmpty() && results.none { same(it.displayName, proposedName) }

    /** A new person from a typed name: first word given, the rest family, in the current mode. */
    fun createPerson(name: String, mode: ContextMode, context: ModelContext, now: Instant = Instant.now()): Person? {
        val trimmed = name.trimmed()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(" ").filter { it.isNotEmpty() }
        val person = Person(
            givenName = parts.firstOrNull() ?: trimmed,
            familyName = if (parts.size > 1) parts.drop(1).joinToString(" ") else "",
            context = RelationshipContext.of(mode),
            createdAt = now,
        )
        context.insert(person)
        return person
    }

    /**
     * Writes the reviewed draft: an interaction holding the whole note, the
     * memories left switched on, and the follow-up if it's kept and titled.
     * Returns null when nobody was chosen. The caller saves the context and
     * schedules the follow-up's reminder.
     */
    fun save(
        draft: CaptureDraft,
        text: String,
        subject: CaptureSubject,
        mode: ContextMode,
        context: ModelContext,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): QuickCaptureResult? {
        val person = subject.person ?: createPerson(subject.newPersonName, mode, context, now) ?: return null
        val rawNote = text.trimmed()

        val interaction = Interaction(
            kind = draft.kind,
            occurredAt = draft.occurredAt,
            rawNote = rawNote,
            summary = draft.summary.ifEmpty { null },
            isQuickCapture = true,
            person = person,
        )
        interaction.createdAt = now
        context.insert(interaction)

        val memories = draft.memories.filter { it.isSelected }.map { memory ->
            Memory(
                content = memory.content,
                category = memory.category,
                importance = memory.importance,
                createdAt = now,
                person = person,
                sourceInteraction = interaction,
            ).also { context.insert(it) }
        }

        val followUpDraft = draft.followUp
        val followUp = if (followUpDraft != null && followUpDraft.isSelected && followUpDraft.title.trimmedSpaces().isNotEmpty()) {
            FollowUp(
                title = followUpDraft.title,
                dueDate = calendar.startOfDay(followUpDraft.dueDate),
                priority = followUpDraft.priority,
                person = person,
                sourceInteraction = interaction,
                createdAt = now,
            ).also { context.insert(it) }
        } else {
            null
        }

        val last = person.lastInteractionAt
        if (last == null || last < draft.occurredAt) person.lastInteractionAt = draft.occurredAt
        Outreach.markReplied(person, draft.occurredAt)
        person.touch(now)
        return QuickCaptureResult(person, interaction, memories, followUp)
    }
}

object BrainDumpWorkflow {

    /** What an optional logged interaction is called. */
    const val INTERACTION_SUMMARY = "Caught up — notes added"

    fun keptCount(drafts: List<MemoryDraft>): Int = drafts.count { it.isSelected }

    /** "Keep All" switches everything on; once all are on it reads "Keep None" and switches them off. */
    fun toggleAll(drafts: List<MemoryDraft>): List<MemoryDraft> {
        val turningOff = keptCount(drafts) == drafts.size
        return drafts.map { it.copy(isSelected = !turningOff) }
    }

    /**
     * Files the kept facts on [person]; blank ones are skipped. With
     * [alsoLogInteraction] an in-person interaction is recorded now, the facts
     * point at it, and the rhythm restarts. The caller saves the context.
     */
    fun save(
        person: Person,
        drafts: List<MemoryDraft>,
        alsoLogInteraction: Boolean,
        context: ModelContext,
        now: Instant = Instant.now(),
    ): List<Memory> {
        var source: Interaction? = null
        if (alsoLogInteraction) {
            val interaction = Interaction(
                kind = InteractionKind.IN_PERSON,
                occurredAt = now,
                summary = INTERACTION_SUMMARY,
                person = person,
            )
            interaction.createdAt = now
            context.insert(interaction)
            person.lastInteractionAt = now
            Outreach.markReplied(person, now)
            source = interaction
        }

        val memories = drafts.filter { it.isSelected }.mapNotNull { draft ->
            val content = draft.content.trimmed()
            if (content.isEmpty()) return@mapNotNull null
            Memory(
                content = content,
                category = draft.category,
                importance = draft.importance,
                createdAt = now,
                person = person,
                sourceInteraction = source,
            ).also { context.insert(it) }
        }

        person.touch(now)
        return memories
    }
}

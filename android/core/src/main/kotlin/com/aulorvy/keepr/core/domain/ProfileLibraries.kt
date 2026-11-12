package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.Interaction
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.KeeprCalendar
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.ModelContext
import com.aulorvy.keepr.core.model.NoteSource
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.core.model.Priority
import com.aulorvy.keepr.core.model.foldedContains
import com.aulorvy.keepr.core.services.HeuristicCaptureExtractor
import java.time.Instant

// The model work behind three iOS views — NoteComposerView/NoteCard/NotesListView,
// PersonFilesView and MemoryLibraryView — lifted out of the views so both
// platforms' rules are tested in one place. The views keep only drawing.

/** Conversation write-ups: composing, pinning, finding. Translated from NoteComposerView.swift and NoteCard.swift. */
object NoteEditor {

    /** `NotesListView.notes`: everything when the query is blank, else title-or-body matches. */
    fun filter(notes: List<ConversationNote>, query: String): List<ConversationNote> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return notes
        return notes.filter { it.searchableText.foldedContains(trimmed) }
    }

    /**
     * The title the composer offers while the user hasn't typed one: guessed
     * from the text, or "Screenshots · Mar 3"-style when there are only images.
     */
    fun suggestedTitle(
        body: String,
        hasImages: Boolean,
        date: Instant,
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): String {
        val trimmed = body.trim()
        return when {
            trimmed.isNotEmpty() -> NoteTitler.suggest(trimmed, date, calendar)
            hasImages -> NoteTitler.imageOnlyTitle(date, calendar)
            else -> ""
        }
    }

    /** Save is enabled once there's text, or any image (new or already on the note). */
    fun canSave(body: String, pendingImageCount: Int, existing: ConversationNote?): Boolean =
        body.trim().isNotEmpty() || pendingImageCount > 0 || (existing?.fileList?.isNotEmpty() ?: false)

    /**
     * `NoteComposerView.save()`: creates or updates the note, files any new
     * screenshots inside it, and — for a new note — optionally logs it as an
     * interaction, which resets their rhythm like any other conversation.
     * The caller saves the context.
     */
    fun save(
        context: ModelContext,
        person: Person,
        existing: ConversationNote?,
        title: String,
        body: String,
        occurredAt: Instant,
        source: NoteSource,
        images: List<PersonFilePayload> = emptyList(),
        logAs: InteractionKind? = null,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): ConversationNote {
        val trimmedBody = body.trim()
        val finalTitle = title.trim()
        val resolvedTitle = finalTitle.ifEmpty { NoteTitler.imageOnlyTitle(occurredAt, calendar) }

        val note: ConversationNote
        if (existing != null) {
            existing.title = resolvedTitle
            existing.body = trimmedBody
            existing.occurredAt = occurredAt
            existing.source = source
            existing.touch(now)
            note = existing
        } else {
            note = ConversationNote(
                title = resolvedTitle,
                body = trimmedBody,
                source = source,
                occurredAt = occurredAt,
                createdAt = now,
                person = person,
            )
            context.insert(note)
        }

        for (payload in images) {
            val file = PersonFile(
                title = resolvedTitle,
                kind = payload.kind,
                fileExtension = payload.fileExtension,
                data = payload.data,
                thumbnailData = payload.thumbnail,
                byteCount = payload.byteCount,
                capturedAt = occurredAt,
                createdAt = now,
                person = person,
                conversationNote = note,
            )
            context.insert(file)
        }

        if (logAs != null && existing == null) {
            val interaction = Interaction(kind = logAs, occurredAt = occurredAt, title = resolvedTitle, person = person)
            context.insert(interaction)
            note.sourceInteraction = interaction

            val last = person.lastInteractionAt
            if (last == null || last < occurredAt) person.lastInteractionAt = occurredAt
            Outreach.markReplied(person, occurredAt)
        }

        person.touch(now)
        return note
    }

    /** `NoteCard.togglePin()`. */
    fun togglePin(note: ConversationNote, now: Instant = Instant.now()) {
        note.isPinned = !note.isPinned
        note.touch(now)
    }
}

/** Files on a profile. Translated from PersonFilesView.swift. */
object PersonFileLibrary {

    /** A picked file's name without its extension — what iOS titles it after. */
    fun baseName(fileName: String): String {
        val name = fileName.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** A picked file's extension, lowercased; empty when it has none. */
    fun extension(fileName: String): String {
        val name = fileName.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    /**
     * Prepares a file picked from the system's document picker, as
     * `handleFileImport` does: kept verbatim (a PDF gets a first-page
     * thumbnail), images through the photo path, and an unrecognised extension
     * kept rather than replaced with "dat".
     */
    fun document(data: ByteArray, fileExtension: String, codec: ImageCodec): PersonFilePayload {
        val payload = PersonFileImporter.file(data, fileExtension, codec)
        val ext = fileExtension.trim().trimStart('.').lowercase()
        if (payload.fileExtension == "dat" && ext.isNotEmpty()) {
            return PersonFilePayload(payload.data, payload.thumbnail, payload.kind, ext)
        }
        return payload
    }

    /**
     * Inserts one prepared file. Titles default to kind and date; a file
     * picked out of Files keeps its own name, which is usually already
     * meaningful. The caller saves the context.
     */
    fun insert(
        context: ModelContext,
        person: Person,
        payload: PersonFilePayload,
        titledAfter: String? = null,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): PersonFile {
        val title = titledAfter?.takeIf { it.isNotBlank() } ?: PersonFileImporter.defaultTitle(
            payload.kind,
            date = now,
            existingTitles = person.fileList.map { it.title },
            calendar = calendar,
        )
        val file = PersonFile(
            title = title,
            kind = payload.kind,
            fileExtension = payload.fileExtension,
            data = payload.data,
            thumbnailData = payload.thumbnail,
            byteCount = payload.byteCount,
            capturedAt = now,
            createdAt = now,
            person = person,
        )
        context.insert(file)
        person.touch(now)
        return file
    }

    /**
     * `PersonFileDetailsEditor`: name, date and note. A blank name keeps the
     * old one rather than leaving a row with no title.
     */
    fun updateDetails(file: PersonFile, title: String, capturedAt: Instant, note: String) {
        title.trim().takeIf { it.isNotEmpty() }?.let { file.title = it }
        file.capturedAt = capturedAt
        file.note = note.trim().ifEmpty { null }
    }

    /** `PersonFilesView.delete`. The caller saves. */
    fun delete(context: ModelContext, file: PersonFile, now: Instant = Instant.now()) {
        val owner = file.person
        context.delete(file)
        owner?.touch(now)
    }

    /** "1 file" / "3 files" — the profile's Files card. */
    fun countLabel(count: Int): String = if (count == 1) "1 file" else "$count files"
}

/** Everything you know about one person, filed. Translated from MemoryLibraryView.swift. */
object MemoryLibrary {

    /** The library's sections: visible facts grouped by category, narrowed by [query]. */
    fun groups(person: Person, query: String = ""): List<MemoryGroup> {
        val all = person.visibleMemories
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return MemoryEngine.grouped(all)
        return MemoryEngine.grouped(all.filter { it.content.foldedContains(trimmed) })
    }

    /** Archived facts, newest first — kept so an archive can be undone. */
    fun archived(person: Person, query: String = ""): List<Memory> {
        val trimmed = query.trim()
        return person.memoryList
            .filter { it.isArchived && (trimmed.isEmpty() || it.content.foldedContains(trimmed)) }
            .sortedByDescending { it.updatedAt }
    }

    /** Facts in Other that today's rules would now place somewhere. */
    fun refilable(person: Person): List<RefileProposal> =
        MemoryEngine.refilable(person.memoryList) { HeuristicCaptureExtractor.category(it) }

    /** Files every proposal; returns how many moved. The caller saves. */
    fun refile(proposals: List<RefileProposal>, now: Instant = Instant.now()): Int {
        proposals.forEach { proposal ->
            proposal.memory.category = proposal.category
            proposal.memory.updatedAt = now
        }
        return proposals.size
    }

    /** "One fact moved out of Other." / "3 facts moved out of Other." */
    fun refileMessage(count: Int): String =
        if (count == 1) "One fact moved out of Other." else "$count facts moved out of Other."

    fun move(memory: Memory, to: MemoryCategory, now: Instant = Instant.now()) {
        memory.category = to
        memory.updatedAt = now
    }

    fun toggleImportance(memory: Memory, now: Instant = Instant.now()) {
        memory.importance = if (memory.importance == Priority.HIGH) Priority.NORMAL else Priority.HIGH
        memory.updatedAt = now
    }

    fun setArchived(memory: Memory, archived: Boolean, now: Instant = Instant.now()) {
        memory.isArchived = archived
        memory.updatedAt = now
    }

    /** Rewrites a fact. Blank text is refused (returns false) rather than saved as an empty fact. */
    fun edit(memory: Memory, content: String, now: Instant = Instant.now()): Boolean {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed != memory.content) {
            memory.content = trimmed
            memory.updatedAt = now
        }
        return true
    }

    /** Where a fact came from, so an old note can explain itself. */
    fun provenance(memory: Memory, now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String {
        val source = memory.sourceInteraction
        val relative = RelativeDate.past(memory.createdAt, now, calendar)
        if (source != null) return "From ${source.headline.lowercase()} · $relative"
        return "Added ${relative.lowercase()}"
    }
}

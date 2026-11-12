package com.aulorvy.keepr.data

import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.FollowUp
import com.aulorvy.keepr.core.model.Interaction
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.ModelContext
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.PersonLink
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.model.TagKind
import java.time.Instant
import java.util.UUID

/*
 * Records ⇄ rows. Rows are built with modifiedAt = 0 so two snapshots of an
 * unchanged record compare equal; the writer stamps the real time.
 */

private fun Instant.ms(): Long = toEpochMilli()
private fun Long.instant(): Instant = Instant.ofEpochMilli(this)
private fun List<String>.joined(): String = joinToString("\n")
private fun String.lines(): List<String> = if (isEmpty()) emptyList() else split("\n")

fun Person.toRow() = PersonRow(
    id = id.toString(), contactIdentifier = contactIdentifier, givenName = givenName, familyName = familyName,
    preferredName = preferredName, company = company, jobTitle = jobTitle, workNote = workNote,
    phoneNumbers = phoneNumbers.joined(), emailAddresses = emailAddresses.joined(), photoData = photoData,
    contextRaw = contextRaw, priorityRaw = priorityRaw, statusRaw = statusRaw, isFavorite = isFavorite,
    createdAt = createdAt.ms(), updatedAt = updatedAt.ms(), lastInteractionAt = lastInteractionAt?.ms(),
    howWeMet = howWeMet, introducedBy = introducedBy, notes = notes, birthday = birthday?.ms(),
    postalAddress = postalAddress, contactNote = contactNote, cadenceDays = cadenceDays,
    awaitingReplySince = awaitingReplySince?.ms(), reviewedAt = reviewedAt?.ms(), isSampleData = isSampleData,
)

fun Interaction.toRow() = InteractionRow(
    id = id.toString(), personId = person?.id?.toString(), occurredAt = occurredAt.ms(), createdAt = createdAt.ms(),
    kindRaw = kindRaw, title = title, rawNote = rawNote, summary = summary,
    isQuickCapture = isQuickCapture, awaitingReply = awaitingReply,
)

fun Memory.toRow() = MemoryRow(
    id = id.toString(), personId = person?.id?.toString(), sourceInteractionId = sourceInteraction?.id?.toString(),
    content = content, categoryRaw = categoryRaw, importanceRaw = importanceRaw,
    createdAt = createdAt.ms(), updatedAt = updatedAt.ms(), isArchived = isArchived,
)

fun FollowUp.toRow() = FollowUpRow(
    id = id.toString(), personId = person?.id?.toString(), sourceInteractionId = sourceInteraction?.id?.toString(),
    title = title, note = note, dueDate = dueDate.ms(), hasTime = hasTime, priorityRaw = priorityRaw,
    isCompleted = isCompleted, completedAt = completedAt?.ms(), createdAt = createdAt.ms(),
    lastSnoozedAt = lastSnoozedAt?.ms(), notificationIdentifier = notificationIdentifier,
)

fun ConversationNote.toRow() = ConversationNoteRow(
    id = id.toString(), personId = person?.id?.toString(), sourceInteractionId = sourceInteraction?.id?.toString(),
    title = title, body = body, sourceRaw = sourceRaw, occurredAt = occurredAt.ms(), createdAt = createdAt.ms(),
    updatedAt = updatedAt.ms(), isPinned = isPinned,
)

fun PersonFile.toRow() = PersonFileRow(
    id = id.toString(), personId = person?.id?.toString(), sourceInteractionId = sourceInteraction?.id?.toString(),
    conversationNoteId = conversationNote?.id?.toString(), title = title, kindRaw = kindRaw,
    fileExtension = fileExtension, data = heldBytes, thumbnailData = thumbnailData, byteCount = byteCount,
    capturedAt = capturedAt.ms(), createdAt = createdAt.ms(), note = note,
)

fun RelationshipTag.toRow() = RelationshipTagRow(
    id = id.toString(), name = name, kindRaw = kindRaw, isBuiltIn = isBuiltIn, builtInKey = builtInKey,
    aliases = aliases, cadenceDays = cadenceDays, sortOrder = sortOrder, symbolName = symbolName, createdAt = createdAt.ms(),
)

fun PersonGroup.toRow() = PersonGroupRow(
    id = id.toString(), name = name, symbolName = symbolName, detail = detail, aliases = aliases,
    contextRaw = contextRaw, createdAt = createdAt.ms(), sortOrder = sortOrder, isSampleData = isSampleData,
)

fun PersonLink.toRow() = PersonLinkRow(
    id = id.toString(), personAId = personA?.id?.toString(), personBId = personB?.id?.toString(),
    labelAToB = labelAToB, labelBToA = labelBToA, note = note, createdAt = createdAt.ms(),
)

/** Every row the context currently holds. */
fun ModelContext.toRows(): Rows {
    val people = fetch<Person>()
    return Rows(
        people = people.map { it.toRow() },
        interactions = fetch<Interaction>().map { it.toRow() },
        memories = fetch<Memory>().map { it.toRow() },
        followUps = fetch<FollowUp>().map { it.toRow() },
        notes = fetch<ConversationNote>().map { it.toRow() },
        files = fetch<PersonFile>().map { it.toRow() },
        tags = fetch<RelationshipTag>().map { it.toRow() },
        groups = fetch<PersonGroup>().map { it.toRow() },
        links = fetch<PersonLink>().map { it.toRow() },
        personTags = people.flatMap { p -> p.tags.mapIndexed { i, t -> PersonTagRow(p.id.toString(), t.id.toString(), i) } },
        groupMembers = people.flatMap { p -> p.groups.mapIndexed { i, g -> GroupMemberRow(p.id.toString(), g.id.toString(), i) } },
    )
}

/** Rebuilds the in-memory graph from the rows loaded at launch. */
fun Rows.buildGraph(context: ModelContext) {
    fun uuid(id: String): UUID = UUID.fromString(id)

    val peopleById = people.associate { row ->
        row.id to Person(id = uuid(row.id), createdAt = row.createdAt.instant(), isSampleData = row.isSampleData).apply {
            contactIdentifier = row.contactIdentifier
            givenName = row.givenName
            familyName = row.familyName
            preferredName = row.preferredName
            company = row.company
            jobTitle = row.jobTitle
            workNote = row.workNote
            phoneNumbers = row.phoneNumbers.lines()
            emailAddresses = row.emailAddresses.lines()
            photoData = row.photoData
            contextRaw = row.contextRaw
            priorityRaw = row.priorityRaw
            statusRaw = row.statusRaw
            isFavorite = row.isFavorite
            updatedAt = row.updatedAt.instant()
            lastInteractionAt = row.lastInteractionAt?.instant()
            howWeMet = row.howWeMet
            introducedBy = row.introducedBy
            notes = row.notes
            birthday = row.birthday?.instant()
            postalAddress = row.postalAddress
            contactNote = row.contactNote
            cadenceDays = row.cadenceDays
            awaitingReplySince = row.awaitingReplySince?.instant()
            reviewedAt = row.reviewedAt?.instant()
        }
    }
    val tagsById = tags.associate { row ->
        row.id to RelationshipTag(
            name = row.name, kind = TagKind.BUSINESS, isBuiltIn = row.isBuiltIn, sortOrder = row.sortOrder,
            symbolName = row.symbolName, builtInKey = row.builtInKey, aliases = row.aliases, id = uuid(row.id),
        ).apply {
            kindRaw = row.kindRaw
            cadenceDays = row.cadenceDays
            createdAt = row.createdAt.instant()
        }
    }
    val groupsById = groups.associate { row ->
        row.id to PersonGroup(
            name = row.name, symbolName = row.symbolName, detail = row.detail, aliases = row.aliases,
            sortOrder = row.sortOrder, isSampleData = row.isSampleData, id = uuid(row.id),
        ).apply {
            contextRaw = row.contextRaw
            createdAt = row.createdAt.instant()
        }
    }
    val interactionsById = interactions.associate { row ->
        row.id to Interaction(
            occurredAt = row.occurredAt.instant(), title = row.title, rawNote = row.rawNote, summary = row.summary,
            isQuickCapture = row.isQuickCapture, awaitingReply = row.awaitingReply, id = uuid(row.id),
        ).apply {
            kindRaw = row.kindRaw
            createdAt = row.createdAt.instant()
            person = row.personId?.let(peopleById::get)
        }
    }
    val notesById = notes.associate { row ->
        row.id to ConversationNote(
            title = row.title, body = row.body, occurredAt = row.occurredAt.instant(), createdAt = row.createdAt.instant(),
            isPinned = row.isPinned, id = uuid(row.id),
        ).apply {
            sourceRaw = row.sourceRaw
            updatedAt = row.updatedAt.instant()
            person = row.personId?.let(peopleById::get)
            sourceInteraction = row.sourceInteractionId?.let(interactionsById::get)
        }
    }
    val memoryModels = memories.map { row ->
        Memory(content = row.content, createdAt = row.createdAt.instant(), id = uuid(row.id)).apply {
            categoryRaw = row.categoryRaw
            importanceRaw = row.importanceRaw
            updatedAt = row.updatedAt.instant()
            isArchived = row.isArchived
            person = row.personId?.let(peopleById::get)
            sourceInteraction = row.sourceInteractionId?.let(interactionsById::get)
        }
    }
    val followUpModels = followUps.map { row ->
        FollowUp(
            title = row.title, note = row.note, dueDate = row.dueDate.instant(), hasTime = row.hasTime,
            createdAt = row.createdAt.instant(), id = uuid(row.id),
        ).apply {
            priorityRaw = row.priorityRaw
            isCompleted = row.isCompleted
            completedAt = row.completedAt?.instant()
            lastSnoozedAt = row.lastSnoozedAt?.instant()
            notificationIdentifier = row.notificationIdentifier
            person = row.personId?.let(peopleById::get)
            sourceInteraction = row.sourceInteractionId?.let(interactionsById::get)
        }
    }
    val fileModels = files.map { row ->
        PersonFile(
            title = row.title, fileExtension = row.fileExtension, data = row.data, thumbnailData = row.thumbnailData,
            byteCount = row.byteCount, capturedAt = row.capturedAt.instant(), createdAt = row.createdAt.instant(),
            note = row.note, id = uuid(row.id),
        ).apply {
            kindRaw = row.kindRaw
            person = row.personId?.let(peopleById::get)
            sourceInteraction = row.sourceInteractionId?.let(interactionsById::get)
            conversationNote = row.conversationNoteId?.let(notesById::get)
        }
    }
    val linkModels = links.map { row ->
        PersonLink(
            personA = row.personAId?.let(peopleById::get), personB = row.personBId?.let(peopleById::get),
            labelAToB = row.labelAToB, labelBToA = row.labelBToA, note = row.note,
            createdAt = row.createdAt.instant(), id = uuid(row.id),
        )
    }

    personTags.groupBy { it.personId }.forEach { (personId, rows) ->
        peopleById[personId]?.tags = rows.sortedBy { it.position }.mapNotNull { tagsById[it.tagId] }
    }
    groupMembers.groupBy { it.personId }.forEach { (personId, rows) ->
        peopleById[personId]?.groups = rows.sortedBy { it.position }.mapNotNull { groupsById[it.groupId] }
    }

    (peopleById.values + tagsById.values + groupsById.values + interactionsById.values + notesById.values +
        memoryModels + followUpModels + fileModels + linkModels).forEach(context::insert)
}

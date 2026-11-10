package com.chrisallenclark.keepr.backup

import com.chrisallenclark.keepr.core.backup.BackupFormat
import com.chrisallenclark.keepr.core.backup.BackupRecords
import com.chrisallenclark.keepr.core.backup.DeletionRecord
import com.chrisallenclark.keepr.core.backup.FileRecord
import com.chrisallenclark.keepr.core.backup.FollowUpRecord
import com.chrisallenclark.keepr.core.backup.GroupMemberRecord
import com.chrisallenclark.keepr.core.backup.GroupRecord
import com.chrisallenclark.keepr.core.backup.InteractionRecord
import com.chrisallenclark.keepr.core.backup.LinkRecord
import com.chrisallenclark.keepr.core.backup.MemoryRecord
import com.chrisallenclark.keepr.core.backup.NoteRecord
import com.chrisallenclark.keepr.core.backup.PersonRecord
import com.chrisallenclark.keepr.core.backup.PersonTagRecord
import com.chrisallenclark.keepr.core.backup.SettingValue
import com.chrisallenclark.keepr.core.backup.TagRecord
import com.chrisallenclark.keepr.data.ConversationNoteRow
import com.chrisallenclark.keepr.data.DeletionRow
import com.chrisallenclark.keepr.data.FollowUpRow
import com.chrisallenclark.keepr.data.GroupMemberRow
import com.chrisallenclark.keepr.data.InteractionRow
import com.chrisallenclark.keepr.data.MemoryRow
import com.chrisallenclark.keepr.data.PersonFileRow
import com.chrisallenclark.keepr.data.PersonGroupRow
import com.chrisallenclark.keepr.data.PersonLinkRow
import com.chrisallenclark.keepr.data.PersonRow
import com.chrisallenclark.keepr.data.PersonTagRow
import com.chrisallenclark.keepr.data.RelationshipTagRow
import com.chrisallenclark.keepr.data.Rows

/*
 * Database rows ⇄ the backup's records (core/.../backup/BackupRecords.kt):
 * the same fields under the same names. Byte columns become blob names; the
 * bytes travel as their own entries.
 */

/**
 * Records for every row. [hasFileBytes] says whether a file's contents
 * exist (in the blob store, or still inline in its row).
 */
internal fun Rows.toBackupRecords(
    deletions: List<DeletionRow>,
    settings: Map<String, SettingValue>,
    hasFileBytes: (PersonFileRow) -> Boolean,
): BackupRecords = BackupRecords(
    people = people.map { it.toRecord() },
    interactions = interactions.map { it.toRecord() },
    memories = memories.map { it.toRecord() },
    followUps = followUps.map { it.toRecord() },
    notes = notes.map { it.toRecord() },
    files = files.map { it.toRecord(hasFileBytes(it)) },
    tags = tags.map { it.toRecord() },
    groups = groups.map { it.toRecord() },
    links = links.map { it.toRecord() },
    personTags = personTags.map { PersonTagRecord(personId = it.personId, tagId = it.tagId, position = it.position) },
    groupMembers = groupMembers.map { GroupMemberRecord(personId = it.personId, groupId = it.groupId, position = it.position) },
    deletions = deletions.map { DeletionRecord(id = it.id, recordType = it.recordType, deletedAt = it.deletedAt) },
    settings = settings,
)

private fun PersonRow.toRecord() = PersonRecord(
    id = id, contactIdentifier = contactIdentifier, givenName = givenName, familyName = familyName,
    preferredName = preferredName, company = company, jobTitle = jobTitle, workNote = workNote,
    phoneNumbers = phoneNumbers, emailAddresses = emailAddresses,
    photoBlob = if (photoData != null) BackupFormat.personPhotoBlob(id) else null,
    contextRaw = contextRaw, priorityRaw = priorityRaw, statusRaw = statusRaw, isFavorite = isFavorite,
    createdAt = createdAt, updatedAt = updatedAt, lastInteractionAt = lastInteractionAt, howWeMet = howWeMet,
    introducedBy = introducedBy, notes = notes, birthday = birthday, postalAddress = postalAddress,
    contactNote = contactNote, cadenceDays = cadenceDays, awaitingReplySince = awaitingReplySince,
    reviewedAt = reviewedAt, isSampleData = isSampleData, modifiedAt = modifiedAt,
)

private fun InteractionRow.toRecord() = InteractionRecord(
    id = id, personId = personId, occurredAt = occurredAt, createdAt = createdAt, kindRaw = kindRaw, title = title,
    rawNote = rawNote, summary = summary, isQuickCapture = isQuickCapture, awaitingReply = awaitingReply,
    modifiedAt = modifiedAt,
)

private fun MemoryRow.toRecord() = MemoryRecord(
    id = id, personId = personId, sourceInteractionId = sourceInteractionId, content = content,
    categoryRaw = categoryRaw, importanceRaw = importanceRaw, createdAt = createdAt, updatedAt = updatedAt,
    isArchived = isArchived, modifiedAt = modifiedAt,
)

private fun FollowUpRow.toRecord() = FollowUpRecord(
    id = id, personId = personId, sourceInteractionId = sourceInteractionId, title = title, note = note,
    dueDate = dueDate, hasTime = hasTime, priorityRaw = priorityRaw, isCompleted = isCompleted,
    completedAt = completedAt, createdAt = createdAt, lastSnoozedAt = lastSnoozedAt,
    notificationIdentifier = notificationIdentifier, modifiedAt = modifiedAt,
)

private fun ConversationNoteRow.toRecord() = NoteRecord(
    id = id, personId = personId, sourceInteractionId = sourceInteractionId, title = title, body = body,
    sourceRaw = sourceRaw, occurredAt = occurredAt, createdAt = createdAt, updatedAt = updatedAt,
    isPinned = isPinned, modifiedAt = modifiedAt,
)

private fun PersonFileRow.toRecord(hasBytes: Boolean) = FileRecord(
    id = id, personId = personId, sourceInteractionId = sourceInteractionId, conversationNoteId = conversationNoteId,
    title = title, kindRaw = kindRaw, fileExtension = fileExtension,
    dataBlob = if (hasBytes) BackupFormat.fileDataBlob(id) else null,
    thumbnailBlob = if (thumbnailData != null) BackupFormat.fileThumbnailBlob(id) else null,
    byteCount = byteCount, capturedAt = capturedAt, createdAt = createdAt, note = note, modifiedAt = modifiedAt,
)

private fun RelationshipTagRow.toRecord() = TagRecord(
    id = id, name = name, kindRaw = kindRaw, isBuiltIn = isBuiltIn, builtInKey = builtInKey, aliases = aliases,
    cadenceDays = cadenceDays, sortOrder = sortOrder, symbolName = symbolName, createdAt = createdAt,
    modifiedAt = modifiedAt,
)

private fun PersonGroupRow.toRecord() = GroupRecord(
    id = id, name = name, symbolName = symbolName, detail = detail, aliases = aliases, contextRaw = contextRaw,
    createdAt = createdAt, sortOrder = sortOrder, isSampleData = isSampleData, modifiedAt = modifiedAt,
)

private fun PersonLinkRow.toRecord() = LinkRecord(
    id = id, personAId = personAId, personBId = personBId, labelAToB = labelAToB, labelBToA = labelBToA,
    note = note, createdAt = createdAt, modifiedAt = modifiedAt,
)

/**
 * Rows for every record. [blob] reads a staged blob's bytes. With
 * [inlineFileData] (a database without a blob store) a file's contents go
 * into its row; otherwise they're installed beside the database and the row
 * holds none, as the app always stores them.
 */
internal fun BackupRecords.toRows(inlineFileData: Boolean, blob: (String) -> ByteArray): Rows = Rows(
    people = people.map { r ->
        PersonRow(
            id = r.id, contactIdentifier = r.contactIdentifier, givenName = r.givenName, familyName = r.familyName,
            preferredName = r.preferredName, company = r.company, jobTitle = r.jobTitle, workNote = r.workNote,
            phoneNumbers = r.phoneNumbers, emailAddresses = r.emailAddresses, photoData = r.photoBlob?.let(blob),
            contextRaw = r.contextRaw, priorityRaw = r.priorityRaw, statusRaw = r.statusRaw, isFavorite = r.isFavorite,
            createdAt = r.createdAt, updatedAt = r.updatedAt, lastInteractionAt = r.lastInteractionAt,
            howWeMet = r.howWeMet, introducedBy = r.introducedBy, notes = r.notes, birthday = r.birthday,
            postalAddress = r.postalAddress, contactNote = r.contactNote, cadenceDays = r.cadenceDays,
            awaitingReplySince = r.awaitingReplySince, reviewedAt = r.reviewedAt, isSampleData = r.isSampleData,
            modifiedAt = r.modifiedAt,
        )
    },
    interactions = interactions.map { r ->
        InteractionRow(
            id = r.id, personId = r.personId, occurredAt = r.occurredAt, createdAt = r.createdAt, kindRaw = r.kindRaw,
            title = r.title, rawNote = r.rawNote, summary = r.summary, isQuickCapture = r.isQuickCapture,
            awaitingReply = r.awaitingReply, modifiedAt = r.modifiedAt,
        )
    },
    memories = memories.map { r ->
        MemoryRow(
            id = r.id, personId = r.personId, sourceInteractionId = r.sourceInteractionId, content = r.content,
            categoryRaw = r.categoryRaw, importanceRaw = r.importanceRaw, createdAt = r.createdAt,
            updatedAt = r.updatedAt, isArchived = r.isArchived, modifiedAt = r.modifiedAt,
        )
    },
    followUps = followUps.map { r ->
        FollowUpRow(
            id = r.id, personId = r.personId, sourceInteractionId = r.sourceInteractionId, title = r.title,
            note = r.note, dueDate = r.dueDate, hasTime = r.hasTime, priorityRaw = r.priorityRaw,
            isCompleted = r.isCompleted, completedAt = r.completedAt, createdAt = r.createdAt,
            lastSnoozedAt = r.lastSnoozedAt, notificationIdentifier = r.notificationIdentifier, modifiedAt = r.modifiedAt,
        )
    },
    notes = notes.map { r ->
        ConversationNoteRow(
            id = r.id, personId = r.personId, sourceInteractionId = r.sourceInteractionId, title = r.title,
            body = r.body, sourceRaw = r.sourceRaw, occurredAt = r.occurredAt, createdAt = r.createdAt,
            updatedAt = r.updatedAt, isPinned = r.isPinned, modifiedAt = r.modifiedAt,
        )
    },
    files = files.map { r ->
        PersonFileRow(
            id = r.id, personId = r.personId, sourceInteractionId = r.sourceInteractionId,
            conversationNoteId = r.conversationNoteId, title = r.title, kindRaw = r.kindRaw,
            fileExtension = r.fileExtension, data = if (inlineFileData) r.dataBlob?.let(blob) else null,
            thumbnailData = r.thumbnailBlob?.let(blob), byteCount = r.byteCount, capturedAt = r.capturedAt,
            createdAt = r.createdAt, note = r.note, modifiedAt = r.modifiedAt,
        )
    },
    tags = tags.map { r ->
        RelationshipTagRow(
            id = r.id, name = r.name, kindRaw = r.kindRaw, isBuiltIn = r.isBuiltIn, builtInKey = r.builtInKey,
            aliases = r.aliases, cadenceDays = r.cadenceDays, sortOrder = r.sortOrder, symbolName = r.symbolName,
            createdAt = r.createdAt, modifiedAt = r.modifiedAt,
        )
    },
    groups = groups.map { r ->
        PersonGroupRow(
            id = r.id, name = r.name, symbolName = r.symbolName, detail = r.detail, aliases = r.aliases,
            contextRaw = r.contextRaw, createdAt = r.createdAt, sortOrder = r.sortOrder, isSampleData = r.isSampleData,
            modifiedAt = r.modifiedAt,
        )
    },
    links = links.map { r ->
        PersonLinkRow(
            id = r.id, personAId = r.personAId, personBId = r.personBId, labelAToB = r.labelAToB,
            labelBToA = r.labelBToA, note = r.note, createdAt = r.createdAt, modifiedAt = r.modifiedAt,
        )
    },
    personTags = personTags.map { PersonTagRow(personId = it.personId, tagId = it.tagId, position = it.position) },
    groupMembers = groupMembers.map { GroupMemberRow(personId = it.personId, groupId = it.groupId, position = it.position) },
)

internal fun BackupRecords.deletionRows(): List<DeletionRow> =
    deletions.map { DeletionRow(id = it.id, recordType = it.recordType, deletedAt = it.deletedAt) }

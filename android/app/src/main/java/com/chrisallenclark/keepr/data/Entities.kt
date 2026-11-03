package com.chrisallenclark.keepr.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The Room schema: one table per Keepr record, plus join tables for the two
 * many-to-many relationships. Columns mirror the iOS SwiftData models field
 * for field (same names, same raw enum strings), and foreign keys reproduce
 * the iOS delete rules — CASCADE where SwiftData cascades, SET NULL where it
 * nullifies.
 *
 * Built for sync from the start: every row keeps the UUID the record was
 * created with (the same id iOS would use), a `modifiedAt` stamped by the
 * writer whenever the row changes, and deletions leave a row in `deletions`.
 * Dates are epoch milliseconds; lists of strings are newline-separated.
 */

@Entity(tableName = "people")
data class PersonRow(
    @PrimaryKey val id: String,
    val contactIdentifier: String?,
    val givenName: String,
    val familyName: String,
    val preferredName: String?,
    val company: String?,
    val jobTitle: String?,
    val workNote: String?,
    val phoneNumbers: String,
    val emailAddresses: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val photoData: ByteArray?,
    val contextRaw: String,
    val priorityRaw: String,
    val statusRaw: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastInteractionAt: Long?,
    val howWeMet: String?,
    val introducedBy: String?,
    val notes: String?,
    val birthday: Long?,
    val postalAddress: String?,
    val contactNote: String?,
    val cadenceDays: Int?,
    val awaitingReplySince: Long?,
    val reviewedAt: Long?,
    val isSampleData: Boolean,
    val modifiedAt: Long = 0,
)

@Entity(
    tableName = "interactions",
    foreignKeys = [ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class InteractionRow(
    @PrimaryKey val id: String,
    val personId: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val kindRaw: String,
    val title: String?,
    val rawNote: String?,
    val summary: String?,
    val isQuickCapture: Boolean,
    val awaitingReply: Boolean,
    val modifiedAt: Long = 0,
)

@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InteractionRow::class, parentColumns = ["id"], childColumns = ["sourceInteractionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("personId"), Index("sourceInteractionId")],
)
data class MemoryRow(
    @PrimaryKey val id: String,
    val personId: String?,
    val sourceInteractionId: String?,
    val content: String,
    val categoryRaw: String,
    val importanceRaw: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean,
    val modifiedAt: Long = 0,
)

@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InteractionRow::class, parentColumns = ["id"], childColumns = ["sourceInteractionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("personId"), Index("sourceInteractionId")],
)
data class FollowUpRow(
    @PrimaryKey val id: String,
    val personId: String?,
    val sourceInteractionId: String?,
    val title: String,
    val note: String?,
    val dueDate: Long,
    val hasTime: Boolean,
    val priorityRaw: String,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val lastSnoozedAt: Long?,
    val notificationIdentifier: String?,
    val modifiedAt: Long = 0,
)

@Entity(
    tableName = "conversation_notes",
    foreignKeys = [
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InteractionRow::class, parentColumns = ["id"], childColumns = ["sourceInteractionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("personId"), Index("sourceInteractionId")],
)
data class ConversationNoteRow(
    @PrimaryKey val id: String,
    val personId: String?,
    val sourceInteractionId: String?,
    val title: String,
    val body: String,
    val sourceRaw: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val modifiedAt: Long = 0,
)

@Entity(
    tableName = "person_files",
    foreignKeys = [
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InteractionRow::class, parentColumns = ["id"], childColumns = ["sourceInteractionId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ConversationNoteRow::class, parentColumns = ["id"], childColumns = ["conversationNoteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId"), Index("sourceInteractionId"), Index("conversationNoteId")],
)
data class PersonFileRow(
    @PrimaryKey val id: String,
    val personId: String?,
    val sourceInteractionId: String?,
    val conversationNoteId: String?,
    val title: String,
    val kindRaw: String,
    val fileExtension: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray?,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val thumbnailData: ByteArray?,
    val byteCount: Int,
    val capturedAt: Long,
    val createdAt: Long,
    val note: String?,
    val modifiedAt: Long = 0,
)

@Entity(tableName = "relationship_tags")
data class RelationshipTagRow(
    @PrimaryKey val id: String,
    val name: String,
    val kindRaw: String,
    val isBuiltIn: Boolean,
    val builtInKey: String?,
    val aliases: String?,
    val cadenceDays: Int?,
    val sortOrder: Int,
    val symbolName: String,
    val createdAt: Long,
    val modifiedAt: Long = 0,
)

@Entity(tableName = "person_groups")
data class PersonGroupRow(
    @PrimaryKey val id: String,
    val name: String,
    val symbolName: String,
    val detail: String?,
    val aliases: String?,
    val contextRaw: String,
    val createdAt: Long,
    val sortOrder: Int,
    val isSampleData: Boolean,
    val modifiedAt: Long = 0,
)

@Entity(
    tableName = "person_links",
    foreignKeys = [
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personAId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personBId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personAId"), Index("personBId")],
)
data class PersonLinkRow(
    @PrimaryKey val id: String,
    val personAId: String?,
    val personBId: String?,
    val labelAToB: String,
    val labelBToA: String,
    val note: String?,
    val createdAt: Long,
    val modifiedAt: Long = 0,
)

/** Person ⇄ relationship type. Deleting either side removes the pairing (iOS: nullify). */
@Entity(
    tableName = "person_tags",
    primaryKeys = ["personId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RelationshipTagRow::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class PersonTagRow(val personId: String, val tagId: String, val position: Int)

/** Person ⇄ group. */
@Entity(
    tableName = "person_group_members",
    primaryKeys = ["personId", "groupId"],
    foreignKeys = [
        ForeignKey(entity = PersonRow::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonGroupRow::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("groupId")],
)
data class GroupMemberRow(val personId: String, val groupId: String, val position: Int)

/** A tombstone per deleted record, so a future sync can tell other devices. */
@Entity(tableName = "deletions")
data class DeletionRow(
    @PrimaryKey val id: String,
    val recordType: String,
    val deletedAt: Long,
)

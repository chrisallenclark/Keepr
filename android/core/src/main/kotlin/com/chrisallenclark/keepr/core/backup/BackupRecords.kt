package com.chrisallenclark.keepr.core.backup

/*
 * records.json: every row of every table, field for field as the Android
 * database stores them (app/.../data/Entities.kt) — same names, same raw enum
 * strings, dates as epoch milliseconds, string lists newline-separated.
 * Byte columns (a person's photo, a file's contents and thumbnail) are not
 * inlined; each names a blob entry stored beside records.json instead.
 *
 * Readers ignore fields they don't know, so a later version can add columns
 * without breaking this one; a missing optional field reads as null.
 */

data class PersonRecord(
    val id: String,
    val contactIdentifier: String?,
    val givenName: String,
    val familyName: String,
    val preferredName: String?,
    val company: String?,
    val jobTitle: String?,
    val workNote: String?,
    val phoneNumbers: String,
    val emailAddresses: String,
    /** Blob entry holding `photoData`, or null. */
    val photoBlob: String?,
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
    val modifiedAt: Long,
)

data class InteractionRecord(
    val id: String,
    val personId: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val kindRaw: String,
    val title: String?,
    val rawNote: String?,
    val summary: String?,
    val isQuickCapture: Boolean,
    val awaitingReply: Boolean,
    val modifiedAt: Long,
)

data class MemoryRecord(
    val id: String,
    val personId: String?,
    val sourceInteractionId: String?,
    val content: String,
    val categoryRaw: String,
    val importanceRaw: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean,
    val modifiedAt: Long,
)

data class FollowUpRecord(
    val id: String,
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
    val modifiedAt: Long,
)

data class NoteRecord(
    val id: String,
    val personId: String?,
    val sourceInteractionId: String?,
    val title: String,
    val body: String,
    val sourceRaw: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val modifiedAt: Long,
)

data class FileRecord(
    val id: String,
    val personId: String?,
    val sourceInteractionId: String?,
    val conversationNoteId: String?,
    val title: String,
    val kindRaw: String,
    val fileExtension: String,
    /** Blob entry holding the file's bytes, or null (a record whose bytes never arrived). */
    val dataBlob: String?,
    /** Blob entry holding the thumbnail, or null. */
    val thumbnailBlob: String?,
    val byteCount: Int,
    val capturedAt: Long,
    val createdAt: Long,
    val note: String?,
    val modifiedAt: Long,
)

data class TagRecord(
    val id: String,
    val name: String,
    val kindRaw: String,
    val isBuiltIn: Boolean,
    val builtInKey: String?,
    val aliases: String?,
    val cadenceDays: Int?,
    val sortOrder: Int,
    val symbolName: String,
    val createdAt: Long,
    val modifiedAt: Long,
)

data class GroupRecord(
    val id: String,
    val name: String,
    val symbolName: String,
    val detail: String?,
    val aliases: String?,
    val contextRaw: String,
    val createdAt: Long,
    val sortOrder: Int,
    val isSampleData: Boolean,
    val modifiedAt: Long,
)

data class LinkRecord(
    val id: String,
    val personAId: String?,
    val personBId: String?,
    val labelAToB: String,
    val labelBToA: String,
    val note: String?,
    val createdAt: Long,
    val modifiedAt: Long,
)

data class PersonTagRecord(val personId: String, val tagId: String, val position: Int)

data class GroupMemberRecord(val personId: String, val groupId: String, val position: Int)

/** A deletion tombstone, kept so a future sync can still tell other devices what was deleted. */
data class DeletionRecord(val id: String, val recordType: String, val deletedAt: Long)

/** One stored setting: text, a switch, or a list of text. */
sealed class SettingValue {
    data class Text(val value: String) : SettingValue()
    data class Switch(val value: Boolean) : SettingValue()
    data class TextList(val value: List<String>) : SettingValue()
}

/** Everything in records.json. */
data class BackupRecords(
    val people: List<PersonRecord> = emptyList(),
    val interactions: List<InteractionRecord> = emptyList(),
    val memories: List<MemoryRecord> = emptyList(),
    val followUps: List<FollowUpRecord> = emptyList(),
    val notes: List<NoteRecord> = emptyList(),
    val files: List<FileRecord> = emptyList(),
    val tags: List<TagRecord> = emptyList(),
    val groups: List<GroupRecord> = emptyList(),
    val links: List<LinkRecord> = emptyList(),
    val personTags: List<PersonTagRecord> = emptyList(),
    val groupMembers: List<GroupMemberRecord> = emptyList(),
    val deletions: List<DeletionRecord> = emptyList(),
    /** The whitelisted preferences (see [BackupSettings]), by key. */
    val settings: Map<String, SettingValue> = emptyMap(),
) {
    /** How many of each, under the names the manifest uses. */
    val counts: Map<String, Int>
        get() = linkedMapOf(
            "people" to people.size,
            "interactions" to interactions.size,
            "memories" to memories.size,
            "followUps" to followUps.size,
            "notes" to notes.size,
            "files" to files.size,
            "tags" to tags.size,
            "groups" to groups.size,
            "links" to links.size,
            "personTags" to personTags.size,
            "groupMembers" to groupMembers.size,
            "deletions" to deletions.size,
        )

    /** Every blob entry the records point at. */
    val referencedBlobs: Set<String>
        get() = buildSet {
            people.forEach { it.photoBlob?.let(::add) }
            files.forEach { file ->
                file.dataBlob?.let(::add)
                file.thumbnailBlob?.let(::add)
            }
        }

    /**
     * Checks the records form a graph the database will accept: ids are
     * unique, and every reference points at a record that's there. A backup
     * that fails this is refused before anything on the phone is touched.
     */
    fun validate() {
        fun unique(table: String, ids: List<String>) {
            val seen = HashSet<String>()
            ids.forEach { if (!seen.add(it)) throw BackupException.Invalid("$table has two records with id $it") }
        }
        unique("people", people.map { it.id })
        unique("interactions", interactions.map { it.id })
        unique("memories", memories.map { it.id })
        unique("followUps", followUps.map { it.id })
        unique("notes", notes.map { it.id })
        unique("files", files.map { it.id })
        unique("tags", tags.map { it.id })
        unique("groups", groups.map { it.id })
        unique("links", links.map { it.id })
        unique("personTags", personTags.map { "${it.personId}/${it.tagId}" })
        unique("groupMembers", groupMembers.map { "${it.personId}/${it.groupId}" })
        unique("deletions", deletions.map { it.id })

        val personIds = people.mapTo(HashSet()) { it.id }
        val interactionIds = interactions.mapTo(HashSet()) { it.id }
        val noteIds = notes.mapTo(HashSet()) { it.id }
        val tagIds = tags.mapTo(HashSet()) { it.id }
        val groupIds = groups.mapTo(HashSet()) { it.id }
        fun ref(table: String, id: String, field: String, value: String?, among: Set<String>) {
            if (value != null && value !in among) throw BackupException.Invalid("$table $id points at a missing $field $value")
        }
        interactions.forEach { ref("interaction", it.id, "person", it.personId, personIds) }
        memories.forEach {
            ref("memory", it.id, "person", it.personId, personIds)
            ref("memory", it.id, "interaction", it.sourceInteractionId, interactionIds)
        }
        followUps.forEach {
            ref("follow-up", it.id, "person", it.personId, personIds)
            ref("follow-up", it.id, "interaction", it.sourceInteractionId, interactionIds)
        }
        notes.forEach {
            ref("note", it.id, "person", it.personId, personIds)
            ref("note", it.id, "interaction", it.sourceInteractionId, interactionIds)
        }
        files.forEach {
            ref("file", it.id, "person", it.personId, personIds)
            ref("file", it.id, "interaction", it.sourceInteractionId, interactionIds)
            ref("file", it.id, "note", it.conversationNoteId, noteIds)
        }
        links.forEach {
            ref("link", it.id, "person", it.personAId, personIds)
            ref("link", it.id, "person", it.personBId, personIds)
        }
        personTags.forEach {
            ref("person tag", it.personId, "person", it.personId, personIds)
            ref("person tag", it.personId, "type", it.tagId, tagIds)
        }
        groupMembers.forEach {
            ref("group member", it.personId, "person", it.personId, personIds)
            ref("group member", it.personId, "group", it.groupId, groupIds)
        }
    }

    // MARK: - JSON

    fun toJson(): String = Json.write(
        linkedMapOf(
            "people" to people.map { p ->
                linkedMapOf(
                    "id" to p.id, "contactIdentifier" to p.contactIdentifier, "givenName" to p.givenName,
                    "familyName" to p.familyName, "preferredName" to p.preferredName, "company" to p.company,
                    "jobTitle" to p.jobTitle, "workNote" to p.workNote, "phoneNumbers" to p.phoneNumbers,
                    "emailAddresses" to p.emailAddresses, "photoBlob" to p.photoBlob, "contextRaw" to p.contextRaw,
                    "priorityRaw" to p.priorityRaw, "statusRaw" to p.statusRaw, "isFavorite" to p.isFavorite,
                    "createdAt" to p.createdAt, "updatedAt" to p.updatedAt, "lastInteractionAt" to p.lastInteractionAt,
                    "howWeMet" to p.howWeMet, "introducedBy" to p.introducedBy, "notes" to p.notes, "birthday" to p.birthday,
                    "postalAddress" to p.postalAddress, "contactNote" to p.contactNote, "cadenceDays" to p.cadenceDays,
                    "awaitingReplySince" to p.awaitingReplySince, "reviewedAt" to p.reviewedAt,
                    "isSampleData" to p.isSampleData, "modifiedAt" to p.modifiedAt,
                )
            },
            "interactions" to interactions.map { i ->
                linkedMapOf(
                    "id" to i.id, "personId" to i.personId, "occurredAt" to i.occurredAt, "createdAt" to i.createdAt,
                    "kindRaw" to i.kindRaw, "title" to i.title, "rawNote" to i.rawNote, "summary" to i.summary,
                    "isQuickCapture" to i.isQuickCapture, "awaitingReply" to i.awaitingReply, "modifiedAt" to i.modifiedAt,
                )
            },
            "memories" to memories.map { m ->
                linkedMapOf(
                    "id" to m.id, "personId" to m.personId, "sourceInteractionId" to m.sourceInteractionId,
                    "content" to m.content, "categoryRaw" to m.categoryRaw, "importanceRaw" to m.importanceRaw,
                    "createdAt" to m.createdAt, "updatedAt" to m.updatedAt, "isArchived" to m.isArchived,
                    "modifiedAt" to m.modifiedAt,
                )
            },
            "followUps" to followUps.map { f ->
                linkedMapOf(
                    "id" to f.id, "personId" to f.personId, "sourceInteractionId" to f.sourceInteractionId,
                    "title" to f.title, "note" to f.note, "dueDate" to f.dueDate, "hasTime" to f.hasTime,
                    "priorityRaw" to f.priorityRaw, "isCompleted" to f.isCompleted, "completedAt" to f.completedAt,
                    "createdAt" to f.createdAt, "lastSnoozedAt" to f.lastSnoozedAt,
                    "notificationIdentifier" to f.notificationIdentifier, "modifiedAt" to f.modifiedAt,
                )
            },
            "notes" to notes.map { n ->
                linkedMapOf(
                    "id" to n.id, "personId" to n.personId, "sourceInteractionId" to n.sourceInteractionId,
                    "title" to n.title, "body" to n.body, "sourceRaw" to n.sourceRaw, "occurredAt" to n.occurredAt,
                    "createdAt" to n.createdAt, "updatedAt" to n.updatedAt, "isPinned" to n.isPinned,
                    "modifiedAt" to n.modifiedAt,
                )
            },
            "files" to files.map { f ->
                linkedMapOf(
                    "id" to f.id, "personId" to f.personId, "sourceInteractionId" to f.sourceInteractionId,
                    "conversationNoteId" to f.conversationNoteId, "title" to f.title, "kindRaw" to f.kindRaw,
                    "fileExtension" to f.fileExtension, "dataBlob" to f.dataBlob, "thumbnailBlob" to f.thumbnailBlob,
                    "byteCount" to f.byteCount, "capturedAt" to f.capturedAt, "createdAt" to f.createdAt,
                    "note" to f.note, "modifiedAt" to f.modifiedAt,
                )
            },
            "tags" to tags.map { t ->
                linkedMapOf(
                    "id" to t.id, "name" to t.name, "kindRaw" to t.kindRaw, "isBuiltIn" to t.isBuiltIn,
                    "builtInKey" to t.builtInKey, "aliases" to t.aliases, "cadenceDays" to t.cadenceDays,
                    "sortOrder" to t.sortOrder, "symbolName" to t.symbolName, "createdAt" to t.createdAt,
                    "modifiedAt" to t.modifiedAt,
                )
            },
            "groups" to groups.map { g ->
                linkedMapOf(
                    "id" to g.id, "name" to g.name, "symbolName" to g.symbolName, "detail" to g.detail,
                    "aliases" to g.aliases, "contextRaw" to g.contextRaw, "createdAt" to g.createdAt,
                    "sortOrder" to g.sortOrder, "isSampleData" to g.isSampleData, "modifiedAt" to g.modifiedAt,
                )
            },
            "links" to links.map { l ->
                linkedMapOf(
                    "id" to l.id, "personAId" to l.personAId, "personBId" to l.personBId, "labelAToB" to l.labelAToB,
                    "labelBToA" to l.labelBToA, "note" to l.note, "createdAt" to l.createdAt, "modifiedAt" to l.modifiedAt,
                )
            },
            "personTags" to personTags.map { linkedMapOf("personId" to it.personId, "tagId" to it.tagId, "position" to it.position) },
            "groupMembers" to groupMembers.map { linkedMapOf("personId" to it.personId, "groupId" to it.groupId, "position" to it.position) },
            "deletions" to deletions.map { linkedMapOf("id" to it.id, "recordType" to it.recordType, "deletedAt" to it.deletedAt) },
            "settings" to settings.mapValues { (_, value) ->
                when (value) {
                    is SettingValue.Text -> linkedMapOf("type" to "string", "value" to value.value)
                    is SettingValue.Switch -> linkedMapOf("type" to "bool", "value" to value.value)
                    is SettingValue.TextList -> linkedMapOf("type" to "stringList", "value" to value.value)
                }
            },
        ),
    )

    companion object {
        fun fromJson(text: String): BackupRecords {
            val root = JsonObject.parse(text, "records.json")
            return BackupRecords(
                people = root.objects("people").map { o ->
                    PersonRecord(
                        id = o.string("id"), contactIdentifier = o.optString("contactIdentifier"),
                        givenName = o.string("givenName"), familyName = o.string("familyName"),
                        preferredName = o.optString("preferredName"), company = o.optString("company"),
                        jobTitle = o.optString("jobTitle"), workNote = o.optString("workNote"),
                        phoneNumbers = o.optString("phoneNumbers") ?: "", emailAddresses = o.optString("emailAddresses") ?: "",
                        photoBlob = o.optString("photoBlob"), contextRaw = o.string("contextRaw"),
                        priorityRaw = o.string("priorityRaw"), statusRaw = o.string("statusRaw"),
                        isFavorite = o.optBool("isFavorite") ?: false, createdAt = o.long("createdAt"),
                        updatedAt = o.long("updatedAt"), lastInteractionAt = o.optLong("lastInteractionAt"),
                        howWeMet = o.optString("howWeMet"), introducedBy = o.optString("introducedBy"),
                        notes = o.optString("notes"), birthday = o.optLong("birthday"),
                        postalAddress = o.optString("postalAddress"), contactNote = o.optString("contactNote"),
                        cadenceDays = o.optInt("cadenceDays"), awaitingReplySince = o.optLong("awaitingReplySince"),
                        reviewedAt = o.optLong("reviewedAt"), isSampleData = o.optBool("isSampleData") ?: false,
                        modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                interactions = root.objects("interactions").map { o ->
                    InteractionRecord(
                        id = o.string("id"), personId = o.optString("personId"), occurredAt = o.long("occurredAt"),
                        createdAt = o.long("createdAt"), kindRaw = o.string("kindRaw"), title = o.optString("title"),
                        rawNote = o.optString("rawNote"), summary = o.optString("summary"),
                        isQuickCapture = o.optBool("isQuickCapture") ?: false, awaitingReply = o.optBool("awaitingReply") ?: false,
                        modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                memories = root.objects("memories").map { o ->
                    MemoryRecord(
                        id = o.string("id"), personId = o.optString("personId"),
                        sourceInteractionId = o.optString("sourceInteractionId"), content = o.string("content"),
                        categoryRaw = o.string("categoryRaw"), importanceRaw = o.string("importanceRaw"),
                        createdAt = o.long("createdAt"), updatedAt = o.long("updatedAt"),
                        isArchived = o.optBool("isArchived") ?: false, modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                followUps = root.objects("followUps").map { o ->
                    FollowUpRecord(
                        id = o.string("id"), personId = o.optString("personId"),
                        sourceInteractionId = o.optString("sourceInteractionId"), title = o.string("title"),
                        note = o.optString("note"), dueDate = o.long("dueDate"), hasTime = o.optBool("hasTime") ?: false,
                        priorityRaw = o.string("priorityRaw"), isCompleted = o.optBool("isCompleted") ?: false,
                        completedAt = o.optLong("completedAt"), createdAt = o.long("createdAt"),
                        lastSnoozedAt = o.optLong("lastSnoozedAt"), notificationIdentifier = o.optString("notificationIdentifier"),
                        modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                notes = root.objects("notes").map { o ->
                    NoteRecord(
                        id = o.string("id"), personId = o.optString("personId"),
                        sourceInteractionId = o.optString("sourceInteractionId"), title = o.string("title"),
                        body = o.string("body"), sourceRaw = o.string("sourceRaw"), occurredAt = o.long("occurredAt"),
                        createdAt = o.long("createdAt"), updatedAt = o.long("updatedAt"),
                        isPinned = o.optBool("isPinned") ?: false, modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                files = root.objects("files").map { o ->
                    FileRecord(
                        id = o.string("id"), personId = o.optString("personId"),
                        sourceInteractionId = o.optString("sourceInteractionId"),
                        conversationNoteId = o.optString("conversationNoteId"), title = o.string("title"),
                        kindRaw = o.string("kindRaw"), fileExtension = o.string("fileExtension"),
                        dataBlob = o.optString("dataBlob"), thumbnailBlob = o.optString("thumbnailBlob"),
                        byteCount = o.optInt("byteCount") ?: 0, capturedAt = o.long("capturedAt"),
                        createdAt = o.long("createdAt"), note = o.optString("note"), modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                tags = root.objects("tags").map { o ->
                    TagRecord(
                        id = o.string("id"), name = o.string("name"), kindRaw = o.string("kindRaw"),
                        isBuiltIn = o.optBool("isBuiltIn") ?: false, builtInKey = o.optString("builtInKey"),
                        aliases = o.optString("aliases"), cadenceDays = o.optInt("cadenceDays"),
                        sortOrder = o.optInt("sortOrder") ?: 0, symbolName = o.string("symbolName"),
                        createdAt = o.long("createdAt"), modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                groups = root.objects("groups").map { o ->
                    GroupRecord(
                        id = o.string("id"), name = o.string("name"), symbolName = o.string("symbolName"),
                        detail = o.optString("detail"), aliases = o.optString("aliases"), contextRaw = o.string("contextRaw"),
                        createdAt = o.long("createdAt"), sortOrder = o.optInt("sortOrder") ?: 0,
                        isSampleData = o.optBool("isSampleData") ?: false, modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                links = root.objects("links").map { o ->
                    LinkRecord(
                        id = o.string("id"), personAId = o.optString("personAId"), personBId = o.optString("personBId"),
                        labelAToB = o.string("labelAToB"), labelBToA = o.string("labelBToA"), note = o.optString("note"),
                        createdAt = o.long("createdAt"), modifiedAt = o.optLong("modifiedAt") ?: 0,
                    )
                },
                personTags = root.objects("personTags").map { o ->
                    PersonTagRecord(o.string("personId"), o.string("tagId"), o.optInt("position") ?: 0)
                },
                groupMembers = root.objects("groupMembers").map { o ->
                    GroupMemberRecord(o.string("personId"), o.string("groupId"), o.optInt("position") ?: 0)
                },
                deletions = root.objects("deletions").map { o ->
                    DeletionRecord(o.string("id"), o.string("recordType"), o.long("deletedAt"))
                },
                settings = root.optObj("settings")?.let(::settings).orEmpty(),
            )
        }

        /** Settings of a type this version doesn't know are skipped, not refused. */
        private fun settings(obj: JsonObject): Map<String, SettingValue> {
            val result = LinkedHashMap<String, SettingValue>()
            for (key in obj.map.keys) {
                val entry = obj.optObj(key) ?: continue
                val value: SettingValue? = when (entry.optString("type")) {
                    "string" -> entry.optString("value")?.let { SettingValue.Text(it) }
                    "bool" -> entry.optBool("value")?.let { SettingValue.Switch(it) }
                    "stringList" -> entry.optArray("value")?.let { list -> SettingValue.TextList(list.filterIsInstance<String>()) }
                    else -> null
                }
                if (value != null) result[key] = value
            }
            return result
        }
    }
}

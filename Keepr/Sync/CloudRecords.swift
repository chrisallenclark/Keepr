import Foundation
import SwiftData

/// Turns the on-device store into cloud records, and the other way around.
///
/// Sample data is left on the device. It is a demo set, and uploading it would
/// put fictional people into the account the assistant reads.
@MainActor
enum CloudRecords {

    static let schemaVersion = 1

    static func export(from context: ModelContext) throws -> [CloudRecord] {
        let people = try fetch(Person.self, context)
        let realPeople = Set(people.filter { !$0.isSampleData }.map(\.id))
        let tags = try fetch(RelationshipTag.self, context)
        alignBuiltinTags(tags)

        var records: [CloudRecord] = []
        records += tags.map(tagRecord)
        records += try fetch(PersonGroup.self, context).filter { !$0.isSampleData }.map(groupRecord)
        records += people.filter { !$0.isSampleData }.map(personRecord)
        records += try fetch(Interaction.self, context).compactMap { interaction in
            guard belongs(interaction.person, to: realPeople) else { return nil }
            return interactionRecord(interaction)
        }
        records += try fetch(Memory.self, context).compactMap { memory in
            guard belongs(memory.person, to: realPeople) else { return nil }
            return memoryRecord(memory)
        }
        records += try fetch(FollowUp.self, context).compactMap { followUp in
            guard belongs(followUp.person, to: realPeople) else { return nil }
            return followUpRecord(followUp)
        }
        records += try fetch(ConversationNote.self, context).compactMap { note in
            guard belongs(note.person, to: realPeople) else { return nil }
            return noteRecord(note)
        }
        records += try fetch(PersonFile.self, context).compactMap { file in
            guard belongs(file.person, to: realPeople) else { return nil }
            return fileRecord(file)
        }
        records += try fetch(PersonLink.self, context).compactMap { link in
            guard belongs(link.personA, to: realPeople), belongs(link.personB, to: realPeople) else { return nil }
            return linkRecord(link)
        }
        return records
    }

    /// A nil person is an orphan and is synced so it isn't trapped on one phone.
    /// A sample person is not.
    private static func belongs(_ person: Person?, to realPeople: Set<UUID>) -> Bool {
        guard let person else { return true }
        return realPeople.contains(person.id)
    }

    static func alignBuiltinTags(_ tags: [RelationshipTag]) {
        for tag in tags where tag.isBuiltIn {
            guard let key = tag.builtInKey, !key.isEmpty else { continue }
            let stable = StableID.builtinTag(key)
            if tag.id != stable { tag.id = stable }
        }
    }

    static func setModifiedAt(_ record: CloudRecord, _ date: Date, in context: ModelContext) throws {
        let when = CloudTime.normalize(date)
        switch record.collection {
        case .people: try find(Person.self, record.id, context)?.modifiedAt = when
        case .interactions: try find(Interaction.self, record.id, context)?.modifiedAt = when
        case .memories: try find(Memory.self, record.id, context)?.modifiedAt = when
        case .followUps: try find(FollowUp.self, record.id, context)?.modifiedAt = when
        case .files: try find(PersonFile.self, record.id, context)?.modifiedAt = when
        case .notes: try find(ConversationNote.self, record.id, context)?.modifiedAt = when
        case .tags: try find(RelationshipTag.self, record.id, context)?.modifiedAt = when
        case .groups: try find(PersonGroup.self, record.id, context)?.modifiedAt = when
        case .links: try find(PersonLink.self, record.id, context)?.modifiedAt = when
        case .deletions, .preferences, .tokens: break
        }
    }

    static func delete(_ recordKey: String, in context: ModelContext) throws {
        let parts = recordKey.split(separator: "/", maxSplits: 1).map(String.init)
        guard parts.count == 2, let collection = CloudCollection(rawValue: parts[0]) else { return }
        switch collection {
        case .people: if let row = try find(Person.self, parts[1], context) { context.delete(row) }
        case .interactions: if let row = try find(Interaction.self, parts[1], context) { context.delete(row) }
        case .memories: if let row = try find(Memory.self, parts[1], context) { context.delete(row) }
        case .followUps: if let row = try find(FollowUp.self, parts[1], context) { context.delete(row) }
        case .files: if let row = try find(PersonFile.self, parts[1], context) { context.delete(row) }
        case .notes: if let row = try find(ConversationNote.self, parts[1], context) { context.delete(row) }
        case .tags: if let row = try find(RelationshipTag.self, parts[1], context) { context.delete(row) }
        case .groups: if let row = try find(PersonGroup.self, parts[1], context) { context.delete(row) }
        case .links: if let row = try find(PersonLink.self, parts[1], context) { context.delete(row) }
        case .deletions, .preferences, .tokens: break
        }
    }

    static func apply(
        _ collection: CloudCollection,
        id: String,
        fields: [String: CloudField],
        blobs: [String: Data],
        in context: ModelContext
    ) throws {
        switch collection {
        case .tags: try applyTag(id: id, fields: fields, context: context)
        case .groups: try applyGroup(id: id, fields: fields, context: context)
        case .people: try applyPerson(id: id, fields: fields, blobs: blobs, context: context)
        case .interactions: try applyInteraction(id: id, fields: fields, context: context)
        case .memories: try applyMemory(id: id, fields: fields, context: context)
        case .followUps: try applyFollowUp(id: id, fields: fields, context: context)
        case .notes: try applyNote(id: id, fields: fields, context: context)
        case .files: try applyFile(id: id, fields: fields, blobs: blobs, context: context)
        case .links: try applyLink(id: id, fields: fields, context: context)
        case .deletions, .preferences, .tokens: break
        }
    }

    // MARK: - Export

    private static func finish(
        _ collection: CloudCollection,
        id: String,
        ownerIds: [String],
        fields: [String: CloudField],
        blobs: [CloudBlob] = []
    ) -> CloudRecord {
        var stored = fields
        stored["schemaVersion"] = .int(schemaVersion)
        let modified = stored.date("modifiedAt") ?? CloudTime.normalize(.distantPast)
        stored["modifiedAt"] = .date(modified)
        return CloudRecord(
            collection: collection,
            id: id,
            ownerIds: ownerIds,
            modifiedAt: modified,
            fields: stored,
            blobs: blobs,
            contentHash: CloudHash.contentHash(stored)
        )
    }

    private static func personRecord(_ person: Person) -> CloudRecord {
        var fields: [String: CloudField] = [
            "givenName": .string(person.givenName),
            "familyName": .string(person.familyName),
            "phoneNumbers": .strings(person.phoneNumbers),
            "emailAddresses": .strings(person.emailAddresses),
            "context": .string(person.contextRaw),
            "priority": .string(person.priorityRaw),
            "status": .string(person.statusRaw),
            "isFavorite": .bool(person.isFavorite),
            "createdAt": .date(CloudTime.normalize(person.createdAt)),
            "updatedAt": .date(CloudTime.normalize(person.updatedAt)),
            "modifiedAt": .date(CloudTime.normalize(person.modifiedAt)),
            "tagIds": .strings(person.tagList.map { $0.id.uuidString }),
            "groupIds": .strings(person.groupList.map { $0.id.uuidString })
        ]
        put(&fields, "contactIdentifier", person.contactIdentifier)
        put(&fields, "preferredName", person.preferredName)
        put(&fields, "company", person.company)
        put(&fields, "jobTitle", person.jobTitle)
        put(&fields, "workNote", person.workNote)
        put(&fields, "lastInteractionAt", person.lastInteractionAt)
        put(&fields, "howWeMet", person.howWeMet)
        put(&fields, "introducedBy", person.introducedBy)
        put(&fields, "notes", person.notes)
        put(&fields, "birthday", person.birthday)
        put(&fields, "postalAddress", person.postalAddress)
        put(&fields, "contactNote", person.contactNote)
        put(&fields, "cadenceDays", person.cadenceDays)
        put(&fields, "awaitingReplySince", person.awaitingReplySince)
        put(&fields, "reviewedAt", person.reviewedAt)
        var blobs: [CloudBlob] = []
        if let photo = person.photoData, !photo.isEmpty {
            let path = "people/\(person.id.uuidString)/photo"
            fields["photoPath"] = .string(path)
            fields["photoContentType"] = .string("image/jpeg")
            fields["photoSha256"] = .string(CloudHash.sha256Hex(photo))
            blobs.append(CloudBlob(path: path, data: photo, contentType: "image/jpeg"))
        }
        return finish(.people, id: person.id.uuidString, ownerIds: [], fields: fields, blobs: blobs)
    }

    private static func interactionRecord(_ interaction: Interaction) -> CloudRecord {
        var fields: [String: CloudField] = [
            "occurredAt": .date(CloudTime.normalize(interaction.occurredAt)),
            "createdAt": .date(CloudTime.normalize(interaction.createdAt)),
            "modifiedAt": .date(CloudTime.normalize(interaction.modifiedAt)),
            "kind": .string(interaction.kindRaw),
            "isQuickCapture": .bool(interaction.isQuickCapture),
            "awaitingReply": .bool(interaction.awaitingReply)
        ]
        put(&fields, "personId", interaction.person?.id.uuidString)
        put(&fields, "title", interaction.title)
        put(&fields, "rawNote", interaction.rawNote)
        put(&fields, "summary", interaction.summary)
        return finish(
            .interactions,
            id: interaction.id.uuidString,
            ownerIds: owner(interaction.person),
            fields: fields
        )
    }

    private static func memoryRecord(_ memory: Memory) -> CloudRecord {
        var fields: [String: CloudField] = [
            "content": .string(memory.content),
            "category": .string(memory.categoryRaw),
            "importance": .string(memory.importanceRaw),
            "createdAt": .date(CloudTime.normalize(memory.createdAt)),
            "updatedAt": .date(CloudTime.normalize(memory.updatedAt)),
            "modifiedAt": .date(CloudTime.normalize(memory.modifiedAt)),
            "isArchived": .bool(memory.isArchived)
        ]
        put(&fields, "personId", memory.person?.id.uuidString)
        put(&fields, "sourceInteractionId", memory.sourceInteraction?.id.uuidString)
        return finish(.memories, id: memory.id.uuidString, ownerIds: owner(memory.person), fields: fields)
    }

    private static func followUpRecord(_ followUp: FollowUp) -> CloudRecord {
        var fields: [String: CloudField] = [
            "title": .string(followUp.title),
            "dueDate": .date(CloudTime.normalize(followUp.dueDate)),
            "hasTime": .bool(followUp.hasTime),
            "priority": .string(followUp.priorityRaw),
            "isCompleted": .bool(followUp.isCompleted),
            "createdAt": .date(CloudTime.normalize(followUp.createdAt)),
            "modifiedAt": .date(CloudTime.normalize(followUp.modifiedAt))
        ]
        put(&fields, "personId", followUp.person?.id.uuidString)
        put(&fields, "sourceInteractionId", followUp.sourceInteraction?.id.uuidString)
        put(&fields, "note", followUp.note)
        put(&fields, "completedAt", followUp.completedAt)
        put(&fields, "lastSnoozedAt", followUp.lastSnoozedAt)
        put(&fields, "notificationIdentifier", followUp.notificationIdentifier)
        return finish(.followUps, id: followUp.id.uuidString, ownerIds: owner(followUp.person), fields: fields)
    }

    private static func noteRecord(_ note: ConversationNote) -> CloudRecord {
        var fields: [String: CloudField] = [
            "title": .string(note.title),
            "body": .string(note.body),
            "source": .string(note.sourceRaw),
            "occurredAt": .date(CloudTime.normalize(note.occurredAt)),
            "createdAt": .date(CloudTime.normalize(note.createdAt)),
            "updatedAt": .date(CloudTime.normalize(note.updatedAt)),
            "modifiedAt": .date(CloudTime.normalize(note.modifiedAt)),
            "isPinned": .bool(note.isPinned)
        ]
        put(&fields, "personId", note.person?.id.uuidString)
        put(&fields, "sourceInteractionId", note.sourceInteraction?.id.uuidString)
        return finish(.notes, id: note.id.uuidString, ownerIds: owner(note.person), fields: fields)
    }

    private static func fileRecord(_ file: PersonFile) -> CloudRecord {
        var fields: [String: CloudField] = [
            "title": .string(file.title),
            "kind": .string(file.kindRaw),
            "fileExtension": .string(file.fileExtension),
            "byteCount": .int(file.byteCount),
            "capturedAt": .date(CloudTime.normalize(file.capturedAt)),
            "createdAt": .date(CloudTime.normalize(file.createdAt)),
            "modifiedAt": .date(CloudTime.normalize(file.modifiedAt))
        ]
        put(&fields, "personId", file.person?.id.uuidString)
        put(&fields, "sourceInteractionId", file.sourceInteraction?.id.uuidString)
        put(&fields, "conversationNoteId", file.conversationNote?.id.uuidString)
        put(&fields, "note", file.note)
        var blobs: [CloudBlob] = []
        if let data = file.data, !data.isEmpty {
            let path = "files/\(file.id.uuidString)/original"
            fields["dataPath"] = .string(path)
            fields["dataContentType"] = .string(CloudContentType.forFileExtension(file.fileExtension))
            fields["dataSha256"] = .string(CloudHash.sha256Hex(data))
            blobs.append(CloudBlob(path: path, data: data, contentType: fields.string("dataContentType") ?? "application/octet-stream"))
        }
        if let thumb = file.thumbnailData, !thumb.isEmpty {
            let path = "files/\(file.id.uuidString)/thumbnail"
            fields["thumbnailPath"] = .string(path)
            fields["thumbnailSha256"] = .string(CloudHash.sha256Hex(thumb))
            blobs.append(CloudBlob(path: path, data: thumb, contentType: "image/jpeg"))
        }
        return finish(.files, id: file.id.uuidString, ownerIds: owner(file.person), fields: fields, blobs: blobs)
    }

    private static func tagRecord(_ tag: RelationshipTag) -> CloudRecord {
        var fields: [String: CloudField] = [
            "name": .string(tag.name),
            "kind": .string(tag.kindRaw),
            "isBuiltIn": .bool(tag.isBuiltIn),
            "sortOrder": .int(tag.sortOrder),
            "symbolName": .string(tag.symbolName),
            "createdAt": .date(CloudTime.normalize(tag.createdAt)),
            "modifiedAt": .date(CloudTime.normalize(tag.modifiedAt))
        ]
        put(&fields, "builtInKey", tag.builtInKey)
        put(&fields, "aliases", tag.aliases)
        put(&fields, "cadenceDays", tag.cadenceDays)
        return finish(.tags, id: tag.id.uuidString, ownerIds: [], fields: fields)
    }

    private static func groupRecord(_ group: PersonGroup) -> CloudRecord {
        var fields: [String: CloudField] = [
            "name": .string(group.name),
            "symbolName": .string(group.symbolName),
            "context": .string(group.contextRaw),
            "createdAt": .date(CloudTime.normalize(group.createdAt)),
            "modifiedAt": .date(CloudTime.normalize(group.modifiedAt)),
            "sortOrder": .int(group.sortOrder)
        ]
        put(&fields, "detail", group.detail)
        put(&fields, "aliases", group.aliases)
        return finish(.groups, id: group.id.uuidString, ownerIds: [], fields: fields)
    }

    private static func linkRecord(_ link: PersonLink) -> CloudRecord {
        var fields: [String: CloudField] = [
            "labelAToB": .string(link.labelAToB),
            "labelBToA": .string(link.labelBToA),
            "createdAt": .date(CloudTime.normalize(link.createdAt)),
            "modifiedAt": .date(CloudTime.normalize(link.modifiedAt))
        ]
        put(&fields, "personAId", link.personA?.id.uuidString)
        put(&fields, "personBId", link.personB?.id.uuidString)
        put(&fields, "note", link.note)
        let owners = [link.personA?.id.uuidString, link.personB?.id.uuidString].compactMap { $0 }
        return finish(.links, id: link.id.uuidString, ownerIds: owners, fields: fields)
    }

    private static func owner(_ person: Person?) -> [String] {
        person.map { [$0.id.uuidString] } ?? []
    }

    private static func put(_ fields: inout [String: CloudField], _ key: String, _ value: String?) {
        if let value { fields[key] = .string(value) }
    }

    private static func put(_ fields: inout [String: CloudField], _ key: String, _ value: Int?) {
        if let value { fields[key] = .int(value) }
    }

    private static func put(_ fields: inout [String: CloudField], _ key: String, _ value: Date?) {
        if let value { fields[key] = .date(CloudTime.normalize(value)) }
    }

    // MARK: - Apply

    private static func applyPerson(id: String, fields: [String: CloudField], blobs: [String: Data], context: ModelContext) throws {
        let person = try existing(Person.self, id, context) ?? inserted(Person(), id: id, context: context)
        person.givenName = fields.string("givenName") ?? ""
        person.familyName = fields.string("familyName") ?? ""
        person.preferredName = fields.string("preferredName")
        person.contactIdentifier = fields.string("contactIdentifier")
        person.company = fields.string("company")
        person.jobTitle = fields.string("jobTitle")
        person.workNote = fields.string("workNote")
        person.phoneNumbers = fields.strings("phoneNumbers")
        person.emailAddresses = fields.strings("emailAddresses")
        if let raw = fields.string("context") { person.contextRaw = raw }
        if let raw = fields.string("priority") { person.priorityRaw = raw }
        if let raw = fields.string("status") { person.statusRaw = raw }
        person.isFavorite = fields.bool("isFavorite")
        if let date = fields.date("createdAt") { person.createdAt = date }
        if let date = fields.date("updatedAt") { person.updatedAt = date }
        if let date = fields.date("modifiedAt") { person.modifiedAt = date }
        person.lastInteractionAt = fields.date("lastInteractionAt")
        person.howWeMet = fields.string("howWeMet")
        person.introducedBy = fields.string("introducedBy")
        person.notes = fields.string("notes")
        person.birthday = fields.date("birthday")
        person.postalAddress = fields.string("postalAddress")
        person.contactNote = fields.string("contactNote")
        person.cadenceDays = fields.int("cadenceDays")
        person.awaitingReplySince = fields.date("awaitingReplySince")
        person.reviewedAt = fields.date("reviewedAt")
        let tags = try fetch(RelationshipTag.self, context)
        let groups = try fetch(PersonGroup.self, context)
        person.tags = fields.strings("tagIds").compactMap { token in
            tags.first { $0.id.uuidString == token }
        }
        person.groups = fields.strings("groupIds").compactMap { token in
            groups.first { $0.id.uuidString == token }
        }
        if let path = fields.string("photoPath"), let data = blobs[path] {
            person.photoData = data
        } else if fields.string("photoSha256") == nil {
            person.photoData = nil
        }
    }

    private static func applyInteraction(id: String, fields: [String: CloudField], context: ModelContext) throws {
        let row = try existing(Interaction.self, id, context) ?? inserted(Interaction(), id: id, context: context)
        if let date = fields.date("occurredAt") { row.occurredAt = date }
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
        if let raw = fields.string("kind") { row.kindRaw = raw }
        row.title = fields.string("title")
        row.rawNote = fields.string("rawNote")
        row.summary = fields.string("summary")
        row.isQuickCapture = fields.bool("isQuickCapture")
        row.awaitingReply = fields.bool("awaitingReply")
        row.person = try person(fields.string("personId"), context)
    }

    private static func applyMemory(id: String, fields: [String: CloudField], context: ModelContext) throws {
        let row = try existing(Memory.self, id, context) ?? inserted(Memory(content: fields.string("content") ?? ""), id: id, context: context)
        row.content = fields.string("content") ?? ""
        if let raw = fields.string("category") { row.categoryRaw = raw }
        if let raw = fields.string("importance") { row.importanceRaw = raw }
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("updatedAt") { row.updatedAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
        row.isArchived = fields.bool("isArchived")
        row.person = try person(fields.string("personId"), context)
        row.sourceInteraction = try existing(Interaction.self, fields.string("sourceInteractionId"), context)
    }

    private static func applyFollowUp(id: String, fields: [String: CloudField], context: ModelContext) throws {
        let due = fields.date("dueDate") ?? Date()
        let row = try existing(FollowUp.self, id, context) ?? inserted(
            FollowUp(title: fields.string("title") ?? "", dueDate: due),
            id: id,
            context: context
        )
        row.title = fields.string("title") ?? ""
        row.note = fields.string("note")
        row.dueDate = due
        row.hasTime = fields.bool("hasTime")
        if let raw = fields.string("priority") { row.priorityRaw = raw }
        row.isCompleted = fields.bool("isCompleted")
        row.completedAt = fields.date("completedAt")
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
        row.lastSnoozedAt = fields.date("lastSnoozedAt")
        row.notificationIdentifier = fields.string("notificationIdentifier")
        row.person = try person(fields.string("personId"), context)
        row.sourceInteraction = try existing(Interaction.self, fields.string("sourceInteractionId"), context)
    }

    private static func applyNote(id: String, fields: [String: CloudField], context: ModelContext) throws {
        let row = try existing(ConversationNote.self, id, context) ?? inserted(
            ConversationNote(title: fields.string("title") ?? "", body: fields.string("body") ?? ""),
            id: id,
            context: context
        )
        row.title = fields.string("title") ?? ""
        row.body = fields.string("body") ?? ""
        if let raw = fields.string("source") { row.sourceRaw = raw }
        if let date = fields.date("occurredAt") { row.occurredAt = date }
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("updatedAt") { row.updatedAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
        row.isPinned = fields.bool("isPinned")
        row.person = try person(fields.string("personId"), context)
        row.sourceInteraction = try existing(Interaction.self, fields.string("sourceInteractionId"), context)
    }

    private static func applyFile(id: String, fields: [String: CloudField], blobs: [String: Data], context: ModelContext) throws {
        let row = try existing(PersonFile.self, id, context) ?? inserted(
            PersonFile(title: fields.string("title") ?? ""),
            id: id,
            context: context
        )
        row.title = fields.string("title") ?? ""
        if let raw = fields.string("kind") { row.kindRaw = raw }
        row.fileExtension = fields.string("fileExtension") ?? row.fileExtension
        row.byteCount = fields.int("byteCount") ?? row.byteCount
        if let date = fields.date("capturedAt") { row.capturedAt = date }
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
        row.note = fields.string("note")
        row.person = try person(fields.string("personId"), context)
        row.sourceInteraction = try existing(Interaction.self, fields.string("sourceInteractionId"), context)
        row.conversationNote = try existing(ConversationNote.self, fields.string("conversationNoteId"), context)
        if let path = fields.string("dataPath"), let data = blobs[path] {
            row.data = data
        }
        if let path = fields.string("thumbnailPath"), let data = blobs[path] {
            row.thumbnailData = data
        }
    }

    private static func applyTag(id: String, fields: [String: CloudField], context: ModelContext) throws {
        let kind = TagKind(rawValue: fields.string("kind") ?? "") ?? .business
        let row = try existing(RelationshipTag.self, id, context) ?? inserted(
            RelationshipTag(name: fields.string("name") ?? "", kind: kind),
            id: id,
            context: context
        )
        row.name = fields.string("name") ?? ""
        row.kindRaw = fields.string("kind") ?? row.kindRaw
        row.isBuiltIn = fields.bool("isBuiltIn")
        row.builtInKey = fields.string("builtInKey")
        row.aliases = fields.string("aliases")
        row.cadenceDays = fields.int("cadenceDays")
        row.sortOrder = fields.int("sortOrder") ?? row.sortOrder
        row.symbolName = fields.string("symbolName") ?? row.symbolName
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
    }

    private static func applyGroup(id: String, fields: [String: CloudField], context: ModelContext) throws {
        let row = try existing(PersonGroup.self, id, context) ?? inserted(
            PersonGroup(name: fields.string("name") ?? ""),
            id: id,
            context: context
        )
        row.name = fields.string("name") ?? ""
        row.symbolName = fields.string("symbolName") ?? "person.3"
        row.detail = fields.string("detail")
        row.aliases = fields.string("aliases")
        if let raw = fields.string("context") { row.contextRaw = raw }
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
        row.sortOrder = fields.int("sortOrder") ?? row.sortOrder
    }

    private static func applyLink(id: String, fields: [String: CloudField], context: ModelContext) throws {
        let row = try existing(PersonLink.self, id, context) ?? inserted(
            PersonLink(labelAToB: fields.string("labelAToB") ?? "", labelBToA: fields.string("labelBToA") ?? ""),
            id: id,
            context: context
        )
        row.labelAToB = fields.string("labelAToB") ?? ""
        row.labelBToA = fields.string("labelBToA") ?? ""
        row.note = fields.string("note")
        if let date = fields.date("createdAt") { row.createdAt = date }
        if let date = fields.date("modifiedAt") { row.modifiedAt = date }
        row.personA = try person(fields.string("personAId"), context)
        row.personB = try person(fields.string("personBId"), context)
    }

    private static func person(_ id: String?, _ context: ModelContext) throws -> Person? {
        try existing(Person.self, id, context)
    }

    private static func inserted<T: PersistentModel>(_ model: T, id: String, context: ModelContext) -> T {
        if let identifiable = model as? any CloudIdentifiable, let uuid = UUID(uuidString: id) {
            identifiable.replaceID(uuid)
        }
        context.insert(model)
        return model
    }

    private static func existing<T: PersistentModel>(_ type: T.Type, _ id: String?, _ context: ModelContext) throws -> T? {
        guard let id else { return nil }
        return try find(type, id, context)
    }

    private static func find<T: PersistentModel>(_ type: T.Type, _ id: String, _ context: ModelContext) throws -> T? {
        guard let uuid = UUID(uuidString: id) else { return nil }
        return try fetch(type, context).first { row in
            (row as? any CloudIdentifiable)?.cloudID == uuid
        }
    }

    private static func fetch<T: PersistentModel>(_ type: T.Type, _ context: ModelContext) throws -> [T] {
        try context.fetch(FetchDescriptor<T>())
    }
}

/// The models that carry their own UUID. Sync addresses them by that id.
protocol CloudIdentifiable: AnyObject {
    var cloudID: UUID { get }
    func replaceID(_ id: UUID)
}

extension Person: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension Interaction: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension Memory: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension FollowUp: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension PersonFile: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension ConversationNote: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension RelationshipTag: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension PersonGroup: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}
extension PersonLink: CloudIdentifiable {
    var cloudID: UUID { id }
    func replaceID(_ id: UUID) { self.id = id }
}

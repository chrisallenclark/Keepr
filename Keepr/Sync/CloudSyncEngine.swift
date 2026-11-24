import Foundation
import SwiftData

/// What has already been acknowledged for one account on this phone.
struct SyncBook: Codable, Equatable, Sendable {
    var backfillComplete: Bool = false
    var pendingWipe: Bool = false
    var ackedHashes: [String: String] = [:]
    var knownKeys: [String] = []
    var preferenceHash: String?
    var preferenceModifiedAt: Date?

    static func load(uid: String, defaults: UserDefaults = .standard) -> SyncBook {
        guard let data = defaults.data(forKey: key(uid)),
              let book = try? JSONDecoder().decode(SyncBook.self, from: data) else {
            return SyncBook()
        }
        return book
    }

    func save(uid: String, defaults: UserDefaults = .standard) {
        guard let data = try? JSONEncoder().encode(self) else { return }
        defaults.set(data, forKey: Self.key(uid))
    }

    static func remove(uid: String, defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: key(uid))
    }

    private static func key(_ uid: String) -> String {
        "keepr.sync.book.\(uid)"
    }
}

struct PreferenceSnapshot: Equatable, Sendable {
    var groupLabel: String
    var contextMode: String
    var remindersEnabled: Bool
    var peopleSort: String
}

/// Pushes this phone's store to the signed-in account and pulls what the
/// account has that this phone doesn't.
///
/// The first sync uploads every real record before it deletes anything, so
/// turning sync on cannot drop the data already on the phone.
@MainActor
enum CloudSyncEngine {

    struct Summary: Equatable, Sendable {
        var uploaded = 0
        var downloaded = 0
        var deletedLocal = 0
        var deletedRemote = 0
    }

    static func sync(
        in context: ModelContext,
        transport: CloudTransport,
        book: inout SyncBook,
        preferences: PreferenceSnapshot,
        persist: (SyncBook) -> Void = { _ in }
    ) async throws -> (Summary, PreferenceSnapshot?) {
        if book.pendingWipe {
            try await transport.deleteAll()
            book = SyncBook(backfillComplete: true)
            persist(book)
        }

        var local = try CloudRecords.export(from: context)
        // Built-in tags may have just been given their stable ids.
        try context.save()
        if book.backfillComplete {
            let dirtyIDs = Set(local.filter { book.ackedHashes[$0.key] != $0.contentHash }.map(\.key))
            if !dirtyIDs.isEmpty {
                let now = Date()
                for record in local where dirtyIDs.contains(record.key) {
                    try CloudRecords.setModifiedAt(record, now, in: context)
                }
                try context.save()
                local = try CloudRecords.export(from: context)
            }
        }

        var remote: [CloudRecord] = []
        var tombstones: [SyncPlanner.Tombstone] = []
        for collection in CloudCollection.synced {
            let rows = try await transport.list(collection: collection.rawValue)
            remote += rows.map { row in
                head(collection, id: row.id, fields: row.fields)
            }
        }
        let deletionRows = try await transport.list(collection: CloudCollection.deletions.rawValue)
        tombstones = deletionRows.compactMap { row in
            guard let type = row.fields.string("recordType"), let deleted = row.fields.date("deletedAt") else { return nil }
            return SyncPlanner.Tombstone(id: row.id, recordType: type, deletedAt: deleted)
        }

        let localItems = local.map { item(from: $0, dirty: book.ackedHashes[$0.key] != $0.contentHash) }
        let remoteItems = remote.map { item(from: $0, dirty: false) }
        let plan = SyncPlanner.plan(
            local: localItems,
            remote: remoteItems,
            knownKeys: Set(book.knownKeys),
            tombstones: tombstones,
            backfillComplete: book.backfillComplete
        )

        var summary = Summary()
        let localByKey = Dictionary(uniqueKeysWithValues: local.map { ($0.key, $0) })
        let remoteByKey = Dictionary(uniqueKeysWithValues: remote.map { ($0.key, $0) })
        let deleteOrder = Array(CloudCollection.synced.reversed())

        for key in ordered(plan.deleteRemote, deleteOrder) {
            guard let record = remoteByKey[key] else { continue }
            try await transport.delete(collection: record.collection.rawValue, id: record.id)
            try await deleteBlobs(for: record, transport: transport)
            try await writeTombstone(record, transport: transport)
            book.knownKeys.removeAll { $0 == key }
            book.ackedHashes.removeValue(forKey: key)
            summary.deletedRemote += 1
            persist(book)
        }

        for key in ordered(plan.deleteLocal, deleteOrder) {
            try CloudRecords.delete(key, in: context)
            book.knownKeys.removeAll { $0 == key }
            book.ackedHashes.removeValue(forKey: key)
            summary.deletedLocal += 1
            persist(book)
        }
        if summary.deletedLocal > 0 { try context.save() }

        for key in ordered(plan.upload, CloudCollection.synced) {
            guard let record = localByKey[key] else { continue }
            try await transport.upsert(collection: record.collection.rawValue, id: record.id, fields: record.fields)
            for blob in record.blobs {
                try await transport.putBlob(path: blob.path, data: blob.data, contentType: blob.contentType)
            }
            try await deleteMissingBlobs(record, transport: transport)
            try await transport.delete(collection: CloudCollection.deletions.rawValue, id: record.id)
            if !book.knownKeys.contains(key) { book.knownKeys.append(key) }
            book.ackedHashes[key] = record.contentHash
            summary.uploaded += 1
            persist(book)
        }

        for key in ordered(plan.download, CloudCollection.synced) {
            guard let record = remoteByKey[key] else { continue }
            let blobs = try await downloadBlobs(record, transport: transport)
            try CloudRecords.apply(record.collection, id: record.id, fields: record.fields, blobs: blobs, in: context)
            try context.save()
            if !book.knownKeys.contains(key) { book.knownKeys.append(key) }
            book.ackedHashes[key] = record.contentHash
            summary.downloaded += 1
            persist(book)
        }
        if summary.downloaded > 0 { try context.save() }

        let appliedPreferences = try await syncPreferences(
            preferences,
            book: &book,
            transport: transport
        )
        book.backfillComplete = true
        persist(book)
        return (summary, appliedPreferences)
    }

    // MARK: - Pieces

    private static func ordered(_ keys: [String], _ collections: [CloudCollection]) -> [String] {
        keys.sorted { lhs, rhs in
            let left = collections.firstIndex { lhs.hasPrefix("\($0.rawValue)/") } ?? collections.count
            let right = collections.firstIndex { rhs.hasPrefix("\($0.rawValue)/") } ?? collections.count
            if left != right { return left < right }
            return lhs < rhs
        }
    }

    private static func item(from record: CloudRecord, dirty: Bool) -> SyncPlanner.Item {
        SyncPlanner.Item(
            key: record.key,
            collection: record.collection.rawValue,
            id: record.id,
            ownerIds: record.ownerIds,
            modifiedAt: record.modifiedAt,
            contentHash: record.contentHash,
            dirty: dirty
        )
    }

    /// A remote document has no owner list. Recover it from the person fields
    /// so deleting a person still takes their interactions with them.
    private static func head(_ collection: CloudCollection, id: String, fields: [String: CloudField]) -> CloudRecord {
        var owners: [String] = []
        if let personId = fields.string("personId") { owners.append(personId) }
        if let personA = fields.string("personAId") { owners.append(personA) }
        if let personB = fields.string("personBId") { owners.append(personB) }
        let modified = fields.date("modifiedAt") ?? .distantPast
        return CloudRecord(
            collection: collection,
            id: id,
            ownerIds: owners,
            modifiedAt: modified,
            fields: fields,
            blobs: [],
            contentHash: CloudHash.contentHash(fields)
        )
    }

    private static func writeTombstone(_ record: CloudRecord, transport: CloudTransport) async throws {
        let now = CloudTime.normalize(Date())
        let fields: [String: CloudField] = [
            "recordType": .string(record.collection.rawValue),
            "deletedAt": .date(now),
            "modifiedAt": .date(now),
            "schemaVersion": .int(CloudRecords.schemaVersion)
        ]
        try await transport.upsert(collection: CloudCollection.deletions.rawValue, id: record.id, fields: fields)
        try await deleteBlobs(for: record, transport: transport)
    }

    private static func deleteBlobs(for record: CloudRecord, transport: CloudTransport) async throws {
        for path in blobPaths(record) {
            try await transport.deleteBlob(path: path)
        }
    }

    private static func deleteMissingBlobs(_ record: CloudRecord, transport: CloudTransport) async throws {
        let present = Set(record.blobs.map(\.path))
        for path in blobPaths(record) where !present.contains(path) {
            try await transport.deleteBlob(path: path)
        }
    }

    private static func downloadBlobs(_ record: CloudRecord, transport: CloudTransport) async throws -> [String: Data] {
        var blobs: [String: Data] = [:]
        for key in ["photoPath", "dataPath", "thumbnailPath"] {
            guard let path = record.fields.string(key) else { continue }
            if let data = try await transport.getBlob(path: path) {
                blobs[path] = data
            }
        }
        return blobs
    }

    private static func blobPaths(_ record: CloudRecord) -> [String] {
        switch record.collection {
        case .people: return ["people/\(record.id)/photo"]
        case .files: return ["files/\(record.id)/original", "files/\(record.id)/thumbnail"]
        default: return []
        }
    }

    private static func syncPreferences(
        _ preferences: PreferenceSnapshot,
        book: inout SyncBook,
        transport: CloudTransport
    ) async throws -> PreferenceSnapshot? {
        var fields: [String: CloudField] = [
            "schemaVersion": .int(CloudRecords.schemaVersion),
            "groupLabel": .string(preferences.groupLabel),
            "contextMode": .string(preferences.contextMode),
            "remindersEnabled": .bool(preferences.remindersEnabled),
            "peopleSort": .string(preferences.peopleSort)
        ]
        let hash = CloudHash.contentHash(fields)
        let dirty = book.preferenceHash != hash
        if dirty {
            book.preferenceModifiedAt = CloudTime.normalize(Date())
        }
        let localModified = book.preferenceModifiedAt ?? CloudTime.normalize(Date())
        fields["modifiedAt"] = .date(localModified)

        let remoteRows = try await transport.list(collection: CloudCollection.preferences.rawValue)
        let remote = remoteRows.first { $0.id == "current" }?.fields
        let remoteModified = remote?.date("modifiedAt") ?? .distantPast
        let remoteHash = remote.map { CloudHash.contentHash($0) }

        if remote == nil || dirty && localModified >= remoteModified {
            try await transport.upsert(collection: CloudCollection.preferences.rawValue, id: "current", fields: fields)
            book.preferenceHash = hash
            return nil
        }
        if let remote, remoteHash != hash, remoteModified > localModified {
            book.preferenceHash = remoteHash
            book.preferenceModifiedAt = remoteModified
            return PreferenceSnapshot(
                groupLabel: remote.string("groupLabel") ?? preferences.groupLabel,
                contextMode: remote.string("contextMode") ?? preferences.contextMode,
                remindersEnabled: remote.bool("remindersEnabled"),
                peopleSort: remote.string("peopleSort") ?? preferences.peopleSort
            )
        }
        book.preferenceHash = hash
        return nil
    }
}

enum SyncBinding {
    static let uidKey = "keepr.sync.boundUid"

    static func uid(defaults: UserDefaults = .standard) -> String? {
        defaults.string(forKey: uidKey)
    }

    static func bind(_ uid: String, defaults: UserDefaults = .standard) {
        defaults.set(uid, forKey: uidKey)
    }

    static func clear(defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: uidKey)
    }
}

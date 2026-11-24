import Foundation
import SwiftData
import Testing

@testable import Keepr

@Suite("Cloud records")
@MainActor
struct CloudRecordTests {

    @Test("A person's graph round-trips through the cloud shape")
    func roundTrip() throws {
        let source = try TestStore()
        let ada = Make.person(source.context, given: "Ada", family: "Lovelace", company: "Analytical")
        ada.phoneNumbers = ["555-0100"]
        ada.emailAddresses = ["ada@example.com"]
        ada.notes = "Prefers mornings"
        ada.birthday = TestDates.date(year: 1815, month: 12, day: 10)
        ada.workNote = "Writes engines"
        ada.photoData = Data([0x01, 0x02, 0x03])
        let tag = Make.tag(source.context, name: "Investor", kind: .business)
        Make.attach(tag, to: ada)
        let group = PersonGroup(name: "Analytical Club", detail: "London")
        source.context.insert(group)
        ada.groups = [group]
        let interaction = Make.interaction(
            source.context,
            kind: .meeting,
            occurredAt: TestDates.days(-3),
            title: "Difference engine",
            rawNote: "Talked through the design",
            person: ada
        )
        _ = Make.memory(
            source.context,
            content: "Daughter's wedding in spring",
            category: .importantDate,
            person: ada,
            sourceInteraction: interaction
        )
        _ = Make.followUp(source.context, title: "Send the notes", due: TestDates.dayStart(4), person: ada)
        let note = Make.note(source.context, title: "Consultation", body: "Full write-up", person: ada)
        _ = Make.file(source.context, title: "Scan", data: Data([0xFF, 0xD8]), byteCount: 2, person: ada, conversationNote: note)
        let bob = Make.person(source.context, given: "Charles", family: "Babbage")
        let link = PersonLink(personA: ada, personB: bob, labelAToB: "Colleague", labelBToA: "Colleague", note: "Met at the club")
        source.context.insert(link)
        try source.save()

        let exported = try CloudRecords.export(from: source.context)
        let destination = try TestStore()
        for collection in CloudCollection.synced {
            for record in exported where record.collection == collection {
                var blobs: [String: Data] = [:]
                for blob in record.blobs { blobs[blob.path] = blob.data }
                try CloudRecords.apply(collection, id: record.id, fields: record.fields, blobs: blobs, in: destination.context)
            }
            try destination.save()
        }

        let again = try CloudRecords.export(from: destination.context)
        let originalPeople = exported.filter { $0.collection == .people }.sorted { $0.id < $1.id }
        let copiedPeople = again.filter { $0.collection == .people }.sorted { $0.id < $1.id }
        #expect(originalPeople.map(\.contentHash) == copiedPeople.map(\.contentHash))
        #expect(again.contains { $0.collection == .files && $0.fields.string("dataSha256") != nil })
        #expect(again.contains { $0.collection == .notes && $0.fields.string("body") == "Full write-up" })
        #expect(again.contains { $0.collection == .links })
        #expect(again.contains { $0.collection == .memories && $0.fields.string("category") == "importantDate" })

        let copied = try #require(try destination.fetch(Person.self).first { $0.givenName == "Ada" })
        #expect(copied.photoData == Data([0x01, 0x02, 0x03]))
        #expect(copied.tagList.map(\.name) == ["Investor"])
        #expect(copied.groupList.map(\.name) == ["Analytical Club"])
        #expect(copied.birthday == CloudTime.normalize(TestDates.date(year: 1815, month: 12, day: 10)))
    }

    @Test("Sample people stay on the phone")
    func sampleDataIsNotExported() throws {
        let store = try TestStore()
        let sample = Make.person(store.context, given: "Sample", family: "Person")
        sample.isSampleData = true
        _ = Make.interaction(store.context, summary: "Not real", person: sample)
        let real = Make.person(store.context, given: "Real", family: "Person")
        try store.save()

        let exported = try CloudRecords.export(from: store.context)
        #expect(exported.contains { $0.collection == .people && $0.id == real.id.uuidString })
        #expect(!exported.contains { $0.id == sample.id.uuidString })
        #expect(!exported.contains { $0.collection == .interactions })
    }
}

@Suite("Cloud sync planner")
struct SyncPlannerTests {

    private func item(_ key: String, modifiedAt: Date, hash: String, dirty: Bool = false, owners: [String] = []) -> SyncPlanner.Item {
        let parts = key.split(separator: "/", maxSplits: 1).map(String.init)
        return SyncPlanner.Item(
            key: key,
            collection: parts[0],
            id: parts[1],
            ownerIds: owners,
            modifiedAt: modifiedAt,
            contentHash: hash,
            dirty: dirty
        )
    }

    @Test("The first sync uploads local data and downloads the account, and deletes nothing")
    func backfillDoesNotDelete() {
        let plan = SyncPlanner.plan(
            local: [item("people/local", modifiedAt: .distantPast, hash: "a")],
            remote: [item("people/remote", modifiedAt: .distantPast, hash: "b")],
            knownKeys: [],
            tombstones: [],
            backfillComplete: false
        )
        #expect(plan.upload == ["people/local"])
        #expect(plan.download == ["people/remote"])
        #expect(plan.deleteLocal.isEmpty)
        #expect(plan.deleteRemote.isEmpty)
    }

    @Test("A local edit is pushed and a newer remote edit is pulled")
    func lastWriteWins() {
        let older = Date(timeIntervalSince1970: 10)
        let newer = Date(timeIntervalSince1970: 20)
        let plan = SyncPlanner.plan(
            local: [
                item("people/edited", modifiedAt: newer, hash: "local", dirty: true),
                item("people/stale", modifiedAt: older, hash: "old", dirty: false)
            ],
            remote: [
                item("people/edited", modifiedAt: older, hash: "remote"),
                item("people/stale", modifiedAt: newer, hash: "new")
            ],
            knownKeys: ["people/edited", "people/stale"],
            tombstones: [],
            backfillComplete: true
        )
        #expect(plan.upload == ["people/edited"])
        #expect(plan.download == ["people/stale"])
    }

    @Test("A record removed on one side is deleted on the other, and a person takes their interactions")
    func deletesCascade() {
        let when = Date(timeIntervalSince1970: 10)
        let plan = SyncPlanner.plan(
            local: [
                item("interactions/i", modifiedAt: when, hash: "i", owners: ["p"])
            ],
            remote: [
                item("people/p", modifiedAt: when, hash: "p"),
                item("interactions/i", modifiedAt: when, hash: "i", owners: ["p"])
            ],
            knownKeys: ["people/p", "interactions/i"],
            tombstones: [],
            backfillComplete: true
        )
        #expect(plan.deleteRemote.contains("people/p"))
        #expect(plan.deleteRemote.contains("interactions/i"))
        #expect(plan.upload.isEmpty)
    }
}

@Suite("Cloud encoding")
struct CloudEncodingTests {

    @Test("Firestore values survive a round trip")
    func firestoreRoundTrip() throws {
        let fields: [String: CloudField] = [
            "birthday": .date(CloudTime.normalize(TestDates.now)),
            "cadenceDays": .int(30),
            "givenName": .string("Ada"),
            "isFavorite": .bool(true),
            "phoneNumbers": .strings(["555", "556"])
        ]
        let decoded = try FirestoreCodec.decodeFields(FirestoreCodec.encodeFields(fields))
        #expect(decoded == fields)
    }

    @Test("The content hash ignores key order and the sync clock")
    func hashIsStable() {
        let left: [String: CloudField] = ["b": .string("1"), "a": .int(2)]
        let right: [String: CloudField] = ["a": .int(2), "b": .string("1")]
        #expect(CloudHash.of(left) == CloudHash.of(right))
        var stamped = left
        stamped["modifiedAt"] = .date(Date())
        #expect(CloudHash.contentHash(stamped) == CloudHash.contentHash(left))
    }

    @Test("Built-in tags share one id across phones")
    func stableTagIDs() {
        let example = StableID.uuidV5(namespace: StableID.dnsNamespace, name: "www.example.com")
        #expect(example.uuidString.lowercased() == "2ed6657d-e927-568b-95e1-2665a8aea6a2")
        #expect(StableID.builtinTag("Family").uuidString.lowercased() == "7e8337bf-c4b2-5639-90f5-62eeda81db66")
        #expect(StableID.builtinTag("Current Client").uuidString.lowercased() == "af5810cc-55c0-5e25-881e-3ac99c291404")
    }

    @Test("Token hashes match the API")
    func tokenHashVector() {
        let hash = CloudHash.sha256Hex(Data("kpr_test".utf8))
        #expect(hash == "617ccba9509167b993a77ae820c51ab7ebc010d3e592e9c1514068d19d59f805")
    }

    @Test("Placeholder Firebase config is rejected")
    func rejectsPlaceholderConfig() {
        #expect(FirebasePublicConfig.isUsable("YOUR_PROJECT_ID") == false)
        #expect(FirebasePublicConfig.isUsable("") == false)
        #expect(FirebasePublicConfig.isUsable("keepr-prod") == true)
    }

    @Test("Extra Firestore fractional digits still parse")
    func parsesFirestoreTimestamps() {
        let date = CloudTime.date(from: "2025-06-15T12:00:00.123456Z")
        #expect(date == CloudTime.date(from: "2025-06-15T12:00:00.123Z"))
    }
}

@Suite("Cloud sync engine")
@MainActor
struct CloudSyncEngineTests {

    @Test("Existing local data is uploaded, and a second sync is quiet")
    func backfillThenIdle() async throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Ada", family: "Lovelace")
        person.notes = "Keep this"
        try store.save()
        let transport = MemoryCloudTransport()
        var book = SyncBook()

        _ = try await CloudSyncEngine.sync(
            in: store.context,
            transport: transport,
            book: &book,
            preferences: PreferenceSnapshot(groupLabel: "Group", contextMode: "business", remindersEnabled: true, peopleSort: "name")
        )
        let uploaded = transport.docs["people/\(person.id.uuidString)"]
        #expect(uploaded?.string("givenName") == "Ada")
        #expect(uploaded?.string("notes") == "Keep this")
        #expect(book.backfillComplete)
        let writes = transport.writes

        _ = try await CloudSyncEngine.sync(
            in: store.context,
            transport: transport,
            book: &book,
            preferences: PreferenceSnapshot(groupLabel: "Group", contextMode: "business", remindersEnabled: true, peopleSort: "name")
        )
        #expect(transport.writes == writes)
    }

    @Test("A person already in the account is pulled onto an empty phone")
    func downloadsRemotePeople() async throws {
        let source = try TestStore()
        let person = Make.person(source.context, given: "Grace", family: "Hopper")
        person.company = "Navy"
        let exported = try #require(try CloudRecords.export(from: source.context).first { $0.id == person.id.uuidString })

        let destination = try TestStore()
        let transport = MemoryCloudTransport()
        transport.docs["people/\(exported.id)"] = exported.fields
        var book = SyncBook()
        let summary = try await CloudSyncEngine.sync(
            in: destination.context,
            transport: transport,
            book: &book,
            preferences: PreferenceSnapshot(groupLabel: "Group", contextMode: "business", remindersEnabled: true, peopleSort: "name")
        ).0
        #expect(summary.downloaded >= 1)
        let people = try destination.fetch(Person.self)
        #expect(people.contains { $0.givenName == "Grace" && $0.company == "Navy" })
    }

    @Test("Deleting a person locally deletes them in the account and leaves a tombstone")
    func localDeletePropagates() async throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Ada", family: "Lovelace")
        try store.save()
        let transport = MemoryCloudTransport()
        var book = SyncBook()
        let preferences = PreferenceSnapshot(groupLabel: "Group", contextMode: "business", remindersEnabled: true, peopleSort: "name")
        _ = try await CloudSyncEngine.sync(in: store.context, transport: transport, book: &book, preferences: preferences)

        store.context.delete(person)
        try store.save()
        _ = try await CloudSyncEngine.sync(in: store.context, transport: transport, book: &book, preferences: preferences)

        #expect(transport.docs["people/\(person.id.uuidString)"] == nil)
        #expect(transport.docs["deletions/\(person.id.uuidString)"]?.string("recordType") == "people")
    }

    @Test("A wipe removes the account copy before anything is uploaded again")
    func pendingWipeClearsTheAccount() async throws {
        let transport = MemoryCloudTransport()
        transport.docs["people/stale"] = ["givenName": .string("Gone"), "modifiedAt": .date(Date())]
        var book = SyncBook(pendingWipe: true)
        let store = try TestStore()
        _ = try await CloudSyncEngine.sync(
            in: store.context,
            transport: transport,
            book: &book,
            preferences: PreferenceSnapshot(groupLabel: "Group", contextMode: "business", remindersEnabled: true, peopleSort: "name")
        )
        #expect(transport.docs["people/stale"] == nil)
        #expect(book.pendingWipe == false)
    }
}

final class MemoryCloudTransport: CloudTransport {
    var docs: [String: [String: CloudField]] = [:]
    var blobs: [String: Data] = [:]
    var writes = 0

    func upsert(collection: String, id: String, fields: [String: CloudField]) async throws {
        docs["\(collection)/\(id)"] = fields
        writes += 1
    }

    func merge(collection: String, id: String, fields: [String: CloudField]) async throws {
        var current = docs["\(collection)/\(id)"] ?? [:]
        for (key, value) in fields { current[key] = value }
        docs["\(collection)/\(id)"] = current
        writes += 1
    }

    func delete(collection: String, id: String) async throws {
        if docs.removeValue(forKey: "\(collection)/\(id)") != nil {
            writes += 1
        }
    }

    func list(collection: String) async throws -> [(id: String, fields: [String: CloudField])] {
        docs.compactMap { key, fields in
            let prefix = "\(collection)/"
            guard key.hasPrefix(prefix) else { return nil }
            return (String(key.dropFirst(prefix.count)), fields)
        }
    }

    func putBlob(path: String, data: Data, contentType: String) async throws {
        blobs[path] = data
        writes += 1
    }

    func getBlob(path: String) async throws -> Data? {
        blobs[path]
    }

    func deleteBlob(path: String) async throws {
        blobs.removeValue(forKey: path)
    }

    func deleteAll() async throws {
        docs.removeAll()
        blobs.removeAll()
        writes += 1
    }
}

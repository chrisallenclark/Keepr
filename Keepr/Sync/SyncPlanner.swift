import Foundation

/// Decides what a sync has to push, pull, or delete.
///
/// The first sync of an account on a phone uploads everything local and
/// downloads anything already in the account. It does not delete. Later syncs
/// treat a record that disappeared from one side, and wasn't edited on the
/// other, as a delete. If both sides changed, the later `modifiedAt` wins.
enum SyncPlanner {

    struct Item: Equatable, Sendable {
        var key: String
        var collection: String
        var id: String
        var ownerIds: [String]
        var modifiedAt: Date
        var contentHash: String
        /// True when the local content hash doesn't match the last acked hash.
        var dirty: Bool
    }

    struct Tombstone: Equatable, Sendable {
        var id: String
        var recordType: String
        var deletedAt: Date
    }

    struct Plan: Equatable, Sendable {
        var upload: [String] = []
        var download: [String] = []
        var deleteLocal: [String] = []
        var deleteRemote: [String] = []
    }

    static func plan(
        local: [Item],
        remote: [Item],
        knownKeys: Set<String>,
        tombstones: [Tombstone],
        backfillComplete: Bool
    ) -> Plan {
        let localMap = Dictionary(uniqueKeysWithValues: local.map { ($0.key, $0) })
        let remoteMap = Dictionary(uniqueKeysWithValues: remote.map { ($0.key, $0) })
        let tombstoneByID = Dictionary(tombstones.map { ($0.id, $0) }, uniquingKeysWith: { older, newer in
            newer.deletedAt > older.deletedAt ? newer : older
        })

        var plan = Plan()

        if !backfillComplete {
            plan.upload = local.map(\.key).sorted()
            plan.download = remote.map(\.key).filter { localMap[$0] == nil }.sorted()
            return plan
        }

        for item in local {
            let tombstone = tombstoneByID[item.id]
            let buried = tombstone.map { $0.deletedAt >= item.modifiedAt } ?? false
            if buried, !item.dirty {
                plan.deleteLocal.append(item.key)
                continue
            }
            if let remoteItem = remoteMap[item.key] {
                if item.dirty && item.modifiedAt >= remoteItem.modifiedAt {
                    plan.upload.append(item.key)
                } else if remoteItem.contentHash != item.contentHash && remoteItem.modifiedAt > item.modifiedAt {
                    plan.download.append(item.key)
                } else if item.dirty {
                    plan.upload.append(item.key)
                }
            } else if knownKeys.contains(item.key), !item.dirty {
                plan.deleteLocal.append(item.key)
            } else {
                plan.upload.append(item.key)
            }
        }

        for item in remote where localMap[item.key] == nil {
            if let tombstone = tombstoneByID[item.id], tombstone.deletedAt >= item.modifiedAt {
                plan.deleteRemote.append(item.key)
            } else if knownKeys.contains(item.key) {
                plan.deleteRemote.append(item.key)
            } else {
                plan.download.append(item.key)
            }
        }

        return cascade(plan, local: local, remote: remote)
    }

    /// Deleting a person deletes the records that belong to them on both sides.
    private static func cascade(_ plan: Plan, local: [Item], remote: [Item]) -> Plan {
        var plan = plan
        let removedPeople = (plan.deleteLocal + plan.deleteRemote)
            .filter { $0.hasPrefix("people/") }
            .map { String($0.dropFirst("people/".count)) }
        guard !removedPeople.isEmpty else { return sorted(plan) }

        let removed = Set(removedPeople)
        func children(_ items: [Item], deletedKeys: [String]) -> [String] {
            let peopleGoneLocally = Set(deletedKeys.filter { $0.hasPrefix("people/") }.map { String($0.dropFirst("people/".count)) })
            return items.compactMap { item in
                guard item.collection != "people" else { return nil }
                return item.ownerIds.contains(where: { peopleGoneLocally.contains($0) || removed.contains($0) }) ? item.key : nil
            }
        }

        plan.deleteLocal.append(contentsOf: children(local, deletedKeys: plan.deleteLocal))
        plan.deleteRemote.append(contentsOf: children(remote, deletedKeys: plan.deleteRemote))
        let doomed = Set(plan.deleteLocal + plan.deleteRemote)
        plan.upload.removeAll { doomed.contains($0) }
        plan.download.removeAll { doomed.contains($0) }
        return sorted(plan)
    }

    private static func sorted(_ plan: Plan) -> Plan {
        var plan = plan
        plan.upload = Array(Set(plan.upload)).sorted()
        plan.download = Array(Set(plan.download)).sorted()
        plan.deleteLocal = Array(Set(plan.deleteLocal)).sorted()
        plan.deleteRemote = Array(Set(plan.deleteRemote)).sorted()
        return plan
    }
}

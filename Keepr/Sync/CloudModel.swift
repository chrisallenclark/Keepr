import CryptoKit
import Foundation

/// Firestore collection names shared with the read API and, later, Android.
///
/// Names match the words the read API uses. Android's Room tables use the
/// same records; the mapping is in `docs/CLOUD_SYNC.md`.
enum CloudCollection: String, CaseIterable, Codable, Sendable {
    case people
    case interactions
    case memories
    case followUps
    case files
    case notes
    case tags
    case groups
    case links
    case deletions
    case preferences
    case tokens

    /// Records a signed-in user owns, in the order they must be applied.
    static let synced: [CloudCollection] = [
        .tags, .groups, .people, .interactions, .memories,
        .followUps, .notes, .files, .links
    ]
}

/// One stored value. Dates are normalized to millisecond UTC before they are
/// hashed or written, so a round trip through Firestore doesn't look like an edit.
enum CloudField: Equatable, Sendable {
    case string(String)
    case int(Int)
    case bool(Bool)
    case date(Date)
    case strings([String])
    case null

    var canonicalText: String {
        switch self {
        case .string(let value):
            return CloudHash.quoted(value)
        case .int(let value):
            return String(value)
        case .bool(let value):
            return value ? "true" : "false"
        case .date(let value):
            return CloudHash.quoted(CloudTime.string(from: value))
        case .strings(let values):
            return "[" + values.map(CloudHash.quoted).joined(separator: ",") + "]"
        case .null:
            return "null"
        }
    }
}

extension Dictionary where Key == String, Value == CloudField {
    func string(_ key: String) -> String? {
        if case .string(let value) = self[key] { return value }
        return nil
    }

    func int(_ key: String) -> Int? {
        if case .int(let value) = self[key] { return value }
        return nil
    }

    func bool(_ key: String) -> Bool {
        if case .bool(let value) = self[key] { return value }
        return false
    }

    func date(_ key: String) -> Date? {
        if case .date(let value) = self[key] { return value }
        return nil
    }

    func strings(_ key: String) -> [String] {
        if case .strings(let value) = self[key] { return value }
        return []
    }
}

enum CloudTime {
    /// Truncate to milliseconds so Firestore's microsecond timestamps hash
    /// the same way on the way back down.
    static func normalize(_ date: Date) -> Date {
        let milliseconds = (date.timeIntervalSince1970 * 1000).rounded(.toNearestOrAwayFromZero)
        return Date(timeIntervalSince1970: milliseconds / 1000)
    }

    static func string(from date: Date) -> String {
        formatter(fractional: true).string(from: normalize(date))
    }

    static func date(from raw: String) -> Date? {
        let fractional = trimFraction(raw)
        if let date = formatter(fractional: true).date(from: fractional) {
            return normalize(date)
        }
        if let date = formatter(fractional: false).date(from: raw) {
            return normalize(date)
        }
        return nil
    }

    /// Firestore sometimes returns six fractional digits. Keep three.
    private static func trimFraction(_ raw: String) -> String {
        guard let dot = raw.firstIndex(of: "."), let zed = raw.lastIndex(of: "Z"), dot < zed else {
            return raw
        }
        let fraction = raw[raw.index(after: dot)..<zed]
        let milliseconds = String(fraction.prefix(3)).padding(toLength: 3, withPad: "0", startingAt: 0)
        return "\(raw[..<dot]).\(milliseconds)Z"
    }

    private static func formatter(fractional: Bool) -> ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.formatOptions = fractional
            ? [.withInternetDateTime, .withFractionalSeconds]
            : [.withInternetDateTime]
        return formatter
    }
}

enum CloudHash {
    /// Hash of the record's content. `modifiedAt` is the sync clock, not
    /// content, so bumping it must not look like a new edit.
    static func contentHash(_ fields: [String: CloudField]) -> String {
        var copy = fields
        copy.removeValue(forKey: "modifiedAt")
        return of(copy)
    }

    static func of(_ fields: [String: CloudField]) -> String {
        let body = fields.keys.sorted().map { key in
            "\(quoted(key)):\(fields[key]?.canonicalText ?? "null")"
        }.joined(separator: ",")
        return sha256Hex(Data("{\(body)}".utf8))
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func quoted(_ string: String) -> String {
        guard let data = try? JSONEncoder().encode(string),
              let text = String(data: data, encoding: .utf8) else {
            return "\"\""
        }
        return text
    }
}

/// UUID v5 so both phones generate the same id for a built-in relationship
/// type. Without this, each install seeds "Family" with its own random id and
/// the first sync creates a second Family.
enum StableID {
    /// DNS namespace from RFC 4122. The name is `keepr.tag.v1/<builtInKey>`.
    static let dnsNamespace = UUID(uuidString: "6ba7b810-9dad-11d1-80b4-00c04fd430c8")!

    static func builtinTag(_ key: String) -> UUID {
        uuidV5(namespace: dnsNamespace, name: "keepr.tag.v1/\(key)")
    }

    static func uuidV5(namespace: UUID, name: String) -> UUID {
        let digest = Insecure.SHA1.hash(data: uuidBytes(namespace) + Data(name.utf8))
        var bytes = Array(digest.prefix(16))
        bytes[6] = (bytes[6] & 0x0F) | 0x50
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }

    private static func uuidBytes(_ uuid: UUID) -> Data {
        let u = uuid.uuid
        return Data([u.0, u.1, u.2, u.3, u.4, u.5, u.6, u.7, u.8, u.9, u.10, u.11, u.12, u.13, u.14, u.15])
    }
}

struct CloudBlob: Equatable, Sendable {
    /// Path under `users/{uid}/`, never an absolute Storage path.
    var path: String
    var data: Data
    var contentType: String
}

struct CloudRecord: Equatable, Identifiable, Sendable {
    var collection: CloudCollection
    var id: String
    /// Person ids whose deletion should take this record with them.
    var ownerIds: [String]
    var modifiedAt: Date
    var fields: [String: CloudField]
    var blobs: [CloudBlob]
    var contentHash: String

    var key: String { "\(collection.rawValue)/\(id)" }
    var idKey: String { id }
}

enum FirestoreCodec {
    static func encodeFields(_ fields: [String: CloudField]) throws -> Data {
        let payload: [String: Any] = ["fields": fields.mapValues(restValue)]
        return try JSONSerialization.data(withJSONObject: payload)
    }

    static func decodeFields(_ data: Data) throws -> [String: CloudField] {
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return decodeFieldMap(object?["fields"] as? [String: Any] ?? [:])
    }

    static func decodeFieldMap(_ raw: [String: Any]) -> [String: CloudField] {
        var fields: [String: CloudField] = [:]
        for (key, value) in raw {
            guard let dictionary = value as? [String: Any], let field = cloudField(from: dictionary) else {
                continue
            }
            fields[key] = field
        }
        return fields
    }

    static func documentID(from resourceName: String) -> String? {
        resourceName.split(separator: "/").last.map(String.init)
    }

    private static func restValue(_ field: CloudField) -> [String: Any] {
        switch field {
        case .string(let value):
            return ["stringValue": value]
        case .int(let value):
            return ["integerValue": String(value)]
        case .bool(let value):
            return ["booleanValue": value]
        case .date(let value):
            return ["timestampValue": CloudTime.string(from: value)]
        case .strings(let values):
            return ["arrayValue": ["values": values.map { ["stringValue": $0] }]]
        case .null:
            return ["nullValue": NSNull()]
        }
    }

    private static func cloudField(from value: [String: Any]) -> CloudField? {
        if value["nullValue"] != nil { return nil }
        if let string = value["stringValue"] as? String { return .string(string) }
        if let integer = value["integerValue"] as? String, let number = Int(integer) { return .int(number) }
        if let integer = value["integerValue"] as? Int { return .int(integer) }
        if let boolean = value["booleanValue"] as? Bool { return .bool(boolean) }
        if let boolean = value["booleanValue"] as? Int { return .bool(boolean != 0) }
        if let timestamp = value["timestampValue"] as? String, let date = CloudTime.date(from: timestamp) {
            return .date(date)
        }
        if let array = value["arrayValue"] as? [String: Any] {
            let values = array["values"] as? [[String: Any]] ?? []
            return .strings(values.compactMap { $0["stringValue"] as? String })
        }
        return nil
    }
}

enum CloudContentType {
    static func forFileExtension(_ ext: String) -> String {
        switch ext.lowercased() {
        case "jpg", "jpeg": "image/jpeg"
        case "png": "image/png"
        case "gif": "image/gif"
        case "heic", "heif": "image/heic"
        case "webp": "image/webp"
        case "pdf": "application/pdf"
        case "txt": "text/plain"
        case "json": "application/json"
        default: "application/octet-stream"
        }
    }
}

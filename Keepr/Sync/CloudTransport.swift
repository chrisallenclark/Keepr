import Foundation
import OSLog
import Security

enum CloudError: Error, Equatable {
    case notConfigured
    case invalidCredentials
    case emailInUse
    case weakPassword
    case tooManyAttempts
    case keychain
    case http(Int)
    case incompleteList
    case boundToAnotherAccount
}

struct FirebasePublicConfig: Equatable, Sendable {
    var projectId: String
    var apiKey: String
    var storageBucket: String

    static func load(bundle: Bundle = .main) -> FirebasePublicConfig? {
        guard let url = bundle.url(forResource: "FirebaseOptions", withExtension: "plist") else { return nil }
        return load(from: url)
    }

    static func load(from url: URL) -> FirebasePublicConfig? {
        guard let data = try? Data(contentsOf: url),
              let raw = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any],
              let projectId = raw["PROJECT_ID"] as? String,
              let apiKey = raw["API_KEY"] as? String,
              let bucket = raw["STORAGE_BUCKET"] as? String,
              isUsable(projectId), isUsable(apiKey), isUsable(bucket) else {
            return nil
        }
        return FirebasePublicConfig(projectId: projectId, apiKey: apiKey, storageBucket: bucket)
    }

    /// Rejects the checked-in placeholder so a forgotten example file can't
    /// point the app at a project that doesn't exist.
    static func isUsable(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && !trimmed.hasPrefix("YOUR_")
    }
}

struct CloudSession: Codable, Equatable, Sendable {
    var uid: String
    var email: String
    var idToken: String
    var refreshToken: String
    var expiresAt: Date

    var needsRefresh: Bool {
        expiresAt.timeIntervalSinceNow < 60
    }
}

enum CloudSessionStore {
    private static let account = "session"

    static func load() -> CloudSession? {
        guard let data = KeychainStore.load(account: account) else { return nil }
        return try? JSONDecoder().decode(CloudSession.self, from: data)
    }

    static func save(_ session: CloudSession) throws {
        let data = try JSONEncoder().encode(session)
        try KeychainStore.save(data, account: account)
    }

    static func clear() {
        KeychainStore.delete(account: account)
    }
}

enum KeychainStore {
    private static let service = "com.chrisallenclark.Keepr.cloud"

    static func save(_ data: Data, account: String) throws {
        delete(account: account)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw CloudError.keychain }
    }

    static func load(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }

    static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}

struct FirebaseAuthClient: Sendable {
    var config: FirebasePublicConfig

    func signIn(email: String, password: String) async throws -> CloudSession {
        try await passwordGrant(endpoint: "accounts:signInWithPassword", email: email, password: password)
    }

    func signUp(email: String, password: String) async throws -> CloudSession {
        try await passwordGrant(endpoint: "accounts:signUp", email: email, password: password)
    }

    func refresh(_ session: CloudSession) async throws -> CloudSession {
        let body = "grant_type=refresh_token&refresh_token=\(urlEncode(session.refreshToken))"
        var request = URLRequest(url: url("https://securetoken.googleapis.com/v1/token?key=\(urlEncode(config.apiKey))"))
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data(body.utf8)
        let data = try await send(request, mappingAuthErrors: true)
        let decoded = try JSONDecoder().decode(RefreshResponse.self, from: data)
        return CloudSession(
            uid: decoded.user_id,
            email: session.email,
            idToken: decoded.id_token,
            refreshToken: decoded.refresh_token,
            expiresAt: Date().addingTimeInterval(TimeInterval(decoded.expires_in) ?? 3600)
        )
    }

    private func passwordGrant(endpoint: String, email: String, password: String) async throws -> CloudSession {
        let payload: [String: Any] = ["email": email, "password": password, "returnSecureToken": true]
        var request = URLRequest(url: url("https://identitytoolkit.googleapis.com/v1/\(endpoint)?key=\(urlEncode(config.apiKey))"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let data = try await send(request, mappingAuthErrors: true)
        let decoded = try JSONDecoder().decode(PasswordResponse.self, from: data)
        return CloudSession(
            uid: decoded.localId,
            email: decoded.email ?? email,
            idToken: decoded.idToken,
            refreshToken: decoded.refreshToken,
            expiresAt: Date().addingTimeInterval(TimeInterval(decoded.expiresIn) ?? 3600)
        )
    }

    private func send(_ request: URLRequest, mappingAuthErrors: Bool) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            if mappingAuthErrors { throw mapAuth(data: data, status: status) }
            throw CloudError.http(status)
        }
        return data
    }

    private func mapAuth(data: Data, status: Int) -> CloudError {
        let message = (try? JSONDecoder().decode(AuthErrorBody.self, from: data))?.error.message ?? ""
        switch message {
        case "EMAIL_EXISTS": return .emailInUse
        case "WEAK_PASSWORD", "WEAK_PASSWORD : Password should be at least 6 characters": return .weakPassword
        case "TOO_MANY_ATTEMPTS_TRY_LATER": return .tooManyAttempts
        case "INVALID_PASSWORD", "EMAIL_NOT_FOUND", "INVALID_LOGIN_CREDENTIALS", "INVALID_EMAIL":
            return .invalidCredentials
        default:
            return status == 400 ? .invalidCredentials : .http(status)
        }
    }

    private func url(_ string: String) -> URL {
        URL(string: string) ?? URL(fileURLWithPath: "/")
    }

    private func urlEncode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
    }

    private struct PasswordResponse: Decodable {
        var idToken: String
        var refreshToken: String
        var expiresIn: String
        var localId: String
        var email: String?
    }

    private struct RefreshResponse: Decodable {
        var id_token: String
        var refresh_token: String
        var expires_in: String
        var user_id: String
    }

    private struct AuthErrorBody: Decodable {
        struct Inner: Decodable { var message: String }
        var error: Inner
    }
}

protocol CloudTransport: AnyObject {
    func upsert(collection: String, id: String, fields: [String: CloudField]) async throws
    /// Writes only the given fields and leaves the rest of the document alone.
    func merge(collection: String, id: String, fields: [String: CloudField]) async throws
    func delete(collection: String, id: String) async throws
    func list(collection: String) async throws -> [(id: String, fields: [String: CloudField])]
    func putBlob(path: String, data: Data, contentType: String) async throws
    func getBlob(path: String) async throws -> Data?
    func deleteBlob(path: String) async throws
    func deleteAll() async throws
}

/// Firestore and Storage over their public REST APIs, authorized as the
/// signed-in user. The read API the assistant calls is separate; this is the
/// phone writing its own account.
final class FirebaseCloudTransport: CloudTransport, @unchecked Sendable {
    let config: FirebasePublicConfig
    let uid: String
    var idToken: String

    init(config: FirebasePublicConfig, session: CloudSession) {
        self.config = config
        self.uid = session.uid
        self.idToken = session.idToken
    }

    func upsert(collection: String, id: String, fields: [String: CloudField]) async throws {
        try await write(collection: collection, id: id, fields: fields, merge: false)
    }

    func merge(collection: String, id: String, fields: [String: CloudField]) async throws {
        try await write(collection: collection, id: id, fields: fields, merge: true)
    }

    private func write(collection: String, id: String, fields: [String: CloudField], merge: Bool) async throws {
        var target = try documentURL(collection: collection, id: id)
        if merge {
            var components = URLComponents(url: target, resolvingAgainstBaseURL: false)
            components?.queryItems = fields.keys.sorted().map {
                URLQueryItem(name: "updateMask.fieldPaths", value: $0)
            }
            if let url = components?.url { target = url }
        }
        var request = authorized(.patch, url: target)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try FirestoreCodec.encodeFields(fields)
        _ = try await perform(request)
    }

    func delete(collection: String, id: String) async throws {
        let request = authorized(.delete, url: try documentURL(collection: collection, id: id))
        do {
            _ = try await perform(request)
        } catch CloudError.http(404) {
            return
        }
    }

    func list(collection: String) async throws -> [(id: String, fields: [String: CloudField])] {
        var pageToken: String?
        var rows: [(id: String, fields: [String: CloudField])] = []
        for _ in 0..<100 {
            var components = URLComponents(url: try collectionURL(collection), resolvingAgainstBaseURL: false)
            var items = [URLQueryItem(name: "pageSize", value: "300")]
            if let pageToken { items.append(URLQueryItem(name: "pageToken", value: pageToken)) }
            components?.queryItems = items
            guard let url = components?.url else { throw CloudError.http(0) }
            let request = authorized(.get, url: url)
            let payload = try await perform(request)
            let object = try JSONSerialization.jsonObject(with: payload) as? [String: Any]
            let documents = object?["documents"] as? [[String: Any]] ?? []
            for document in documents {
                guard let name = document["name"] as? String, let id = FirestoreCodec.documentID(from: name) else { continue }
                let fields = FirestoreCodec.decodeFieldMap(document["fields"] as? [String: Any] ?? [:])
                rows.append((id, fields))
            }
            pageToken = object?["nextPageToken"] as? String
            if pageToken == nil { return rows }
        }
        throw CloudError.incompleteList
    }

    func putBlob(path: String, data: Data, contentType: String) async throws {
        let name = objectName(path)
        let encoded = encodeObject(name)
        let url = try endpoint("https://firebasestorage.googleapis.com/v0/b/\(config.storageBucket)/o?uploadType=media&name=\(encoded)")
        var request = authorized(.post, url: url)
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        _ = try await perform(request)
    }

    func getBlob(path: String) async throws -> Data? {
        let url = try endpoint("https://firebasestorage.googleapis.com/v0/b/\(config.storageBucket)/o/\(encodeObject(objectName(path)))?alt=media")
        let request = authorized(.get, url: url)
        do {
            return try await perform(request)
        } catch CloudError.http(404) {
            return nil
        }
    }

    func deleteBlob(path: String) async throws {
        let url = try endpoint("https://firebasestorage.googleapis.com/v0/b/\(config.storageBucket)/o/\(encodeObject(objectName(path)))")
        let request = authorized(.delete, url: url)
        do {
            _ = try await perform(request)
        } catch CloudError.http(404) {
            return
        }
    }

    func deleteAll() async throws {
        for collection in CloudCollection.allCases.map(\.rawValue) {
            let rows = try await list(collection: collection)
            for row in rows {
                try await delete(collection: collection, id: row.id)
            }
        }
        var pageToken: String?
        for _ in 0..<100 {
            var items = [
                URLQueryItem(name: "prefix", value: "users/\(uid)/"),
                URLQueryItem(name: "maxResults", value: "1000")
            ]
            if let pageToken { items.append(URLQueryItem(name: "pageToken", value: pageToken)) }
            var components = URLComponents(string: "https://firebasestorage.googleapis.com/v0/b/\(config.storageBucket)/o")
            components?.queryItems = items
            guard let url = components?.url else { return }
            let request = authorized(.get, url: url)
            let payload: Data
            do {
                payload = try await perform(request)
            } catch CloudError.http(404) {
                return
            }
            let object = try JSONSerialization.jsonObject(with: payload) as? [String: Any]
            let listed = object?["items"] as? [[String: Any]] ?? []
            for item in listed {
                guard let name = item["name"] as? String else { continue }
                let relative = name.hasPrefix("users/\(uid)/")
                    ? String(name.dropFirst("users/\(uid)/".count))
                    : name
                try await deleteBlob(path: relative)
            }
            pageToken = object?["nextPageToken"] as? String
            if pageToken == nil { return }
        }
        throw CloudError.incompleteList
    }

    // MARK: - URLs

    private func documentURL(collection: String, id: String) throws -> URL {
        try collectionURL(collection).appendingPathComponent(encodePath(id))
    }

    private func collectionURL(_ collection: String) throws -> URL {
        try endpoint(
            "https://firestore.googleapis.com/v1/projects/\(config.projectId)/databases/%28default%29/documents/users/\(encodePath(uid))/\(encodePath(collection))"
        )
    }

    private func objectName(_ relative: String) -> String {
        "users/\(uid)/\(relative)"
    }

    private func endpoint(_ string: String) throws -> URL {
        guard let url = URL(string: string) else { throw CloudError.http(0) }
        return url
    }

    private func encodePath(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? value
    }

    private func encodeObject(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-_.~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private enum Method: String { case get = "GET", post = "POST", patch = "PATCH", delete = "DELETE" }

    private func authorized(_ method: Method, url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 60
        return request
    }

    private func perform(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            Logger.persistence.error("Cloud request failed with HTTP \(status, privacy: .public).")
            throw CloudError.http(status)
        }
        return data
    }
}

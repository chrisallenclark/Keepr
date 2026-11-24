import Foundation
import Observation
import Security
import SwiftData

struct AccessTokenSummary: Identifiable, Equatable, Sendable {
    var id: String
    var prefix: String
    var label: String
    var createdAt: Date
    var revokedAt: Date?
}

struct RevealedAccessToken: Identifiable, Equatable {
    var id = UUID()
    /// Shown once. Only the hash is written to the account.
    var value: String
}

/// The signed-in account, the sync book, and the assistant tokens.
///
/// Sync stays off until someone signs in, and it stays off in screenshot runs
/// so a demo launch never touches the network.
@MainActor
@Observable
final class CloudAccount {
    static let shared = CloudAccount()

    private(set) var email: String?
    private(set) var isConfigured = false
    private(set) var isWorking = false
    private(set) var lastSyncedAt: Date?
    private(set) var statusMessage: String?
    private(set) var tokens: [AccessTokenSummary] = []
    var revealedToken: RevealedAccessToken?

    private var config: FirebasePublicConfig?
    private var session: CloudSession?
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard, config: FirebasePublicConfig? = FirebasePublicConfig.load()) {
        self.defaults = defaults
        self.config = config
        self.isConfigured = config != nil
        self.session = CloudSessionStore.load()
        self.email = session?.email
    }

    var isSignedIn: Bool { session != nil }

    func signIn(email: String, password: String) async {
        await authenticate(email: email, password: password, create: false)
    }

    func signUp(email: String, password: String) async {
        await authenticate(email: email, password: password, create: true)
    }

    func signOut() {
        CloudSessionStore.clear()
        session = nil
        email = nil
        tokens = []
        revealedToken = nil
        statusMessage = "Signed out on this iPhone. Your relationships are still here, and still in the account."
    }

    func syncNow(in context: ModelContext) async {
        guard !LaunchOptions.isDemoMode else { return }
        guard !isWorking else { return }
        guard config != nil, session != nil else { return }
        isWorking = true
        defer { isWorking = false }
        do {
            let transport = try await makeTransport()
            var book = SyncBook.load(uid: transport.uid, defaults: defaults)
            let (summary, applied) = try await CloudSyncEngine.sync(
                in: context,
                transport: transport,
                book: &book,
                preferences: currentPreferences(),
                persist: { $0.save(uid: transport.uid, defaults: self.defaults) }
            )
            book.save(uid: transport.uid, defaults: defaults)
            if let applied { write(applied) }
            lastSyncedAt = Date()
            statusMessage = "Synced \(summary.uploaded) up, \(summary.downloaded) down."
            await refreshTokens()
        } catch {
            statusMessage = message(for: error)
        }
    }

    /// The next sync removes the cloud copy, then uploads whatever is left
    /// locally. Used by Delete All Data so the account doesn't restore the
    /// records the phone just erased.
    func prepareDeleteAll() {
        guard let uid = session?.uid else {
            if SyncBinding.uid(defaults: defaults) != nil, session == nil {
                // Nothing is signed in, so there is no cloud copy to reach.
            }
            return
        }
        var book = SyncBook.load(uid: uid, defaults: defaults)
        book.pendingWipe = true
        book.save(uid: uid, defaults: defaults)
    }

    func createToken(label: String) async {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            statusMessage = "Name the token so you can tell them apart."
            return
        }
        isWorking = true
        defer { isWorking = false }
        do {
            let transport = try await makeTransport()
            let raw = try Self.makeToken()
            let id = UUID().uuidString
            let now = CloudTime.normalize(Date())
            let fields: [String: CloudField] = [
                "schemaVersion": .int(1),
                "hash": .string(CloudHash.sha256Hex(Data(raw.utf8))),
                "prefix": .string(String(raw.prefix(8))),
                "label": .string(String(trimmed.prefix(80))),
                "createdAt": .date(now),
                "revokedAt": CloudField.null
            ]
            try await transport.upsert(collection: CloudCollection.tokens.rawValue, id: id, fields: fields)
            revealedToken = RevealedAccessToken(value: raw)
            await refreshTokens()
        } catch {
            statusMessage = message(for: error)
        }
    }

    func revokeToken(id: String) async {
        isWorking = true
        defer { isWorking = false }
        do {
            let transport = try await makeTransport()
            let rows = try await transport.list(collection: CloudCollection.tokens.rawValue)
            guard rows.contains(where: { $0.id == id }) else { return }
            try await transport.merge(
                collection: CloudCollection.tokens.rawValue,
                id: id,
                fields: ["revokedAt": .date(CloudTime.normalize(Date()))]
            )
            await refreshTokens()
            statusMessage = "Token revoked."
        } catch {
            statusMessage = message(for: error)
        }
    }

    func refreshTokens() async {
        guard session != nil else {
            tokens = []
            return
        }
        do {
            let transport = try await makeTransport()
            let rows = try await transport.list(collection: CloudCollection.tokens.rawValue)
            tokens = rows.map { row in
                AccessTokenSummary(
                    id: row.id,
                    prefix: row.fields.string("prefix") ?? "kpr_",
                    label: row.fields.string("label") ?? "Token",
                    createdAt: row.fields.date("createdAt") ?? .distantPast,
                    revokedAt: row.fields.date("revokedAt")
                )
            }.sorted { $0.createdAt > $1.createdAt }
        } catch {
            statusMessage = message(for: error)
        }
    }

    // MARK: - Account

    private func authenticate(email: String, password: String, create: Bool) async {
        guard let config else {
            statusMessage = "This build has no Firebase project yet. See Cloud sync in the docs."
            return
        }
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.contains("@"), password.count >= 6 else {
            statusMessage = "Use an email address and a password of at least 6 characters."
            return
        }
        isWorking = true
        defer { isWorking = false }
        do {
            let client = FirebaseAuthClient(config: config)
            let fresh = try await (create ? client.signUp(email: trimmed, password: password) : client.signIn(email: trimmed, password: password))
            if let bound = SyncBinding.uid(defaults: defaults), bound != fresh.uid {
                statusMessage = "This iPhone is already synced with another account. Delete all data before switching, so the two accounts don't get mixed."
                return
            }
            try CloudSessionStore.save(fresh)
            session = fresh
            self.email = fresh.email
            if SyncBinding.uid(defaults: defaults) == nil {
                SyncBinding.bind(fresh.uid, defaults: defaults)
            }
            statusMessage = create ? "Account created." : "Signed in."
        } catch {
            statusMessage = message(for: error)
        }
    }

    private func makeTransport() async throws -> FirebaseCloudTransport {
        guard let config else { throw CloudError.notConfigured }
        guard var current = session else { throw CloudError.invalidCredentials }
        if current.needsRefresh {
            current = try await FirebaseAuthClient(config: config).refresh(current)
            try CloudSessionStore.save(current)
            session = current
            email = current.email
        }
        return FirebaseCloudTransport(config: config, session: current)
    }

    private func currentPreferences() -> PreferenceSnapshot {
        PreferenceSnapshot(
            groupLabel: defaults.string(forKey: PreferenceKey.groupLabel) ?? GroupVocabulary.default.singular,
            contextMode: defaults.string(forKey: PreferenceKey.contextMode) ?? ContextMode.business.rawValue,
            remindersEnabled: (defaults.object(forKey: PreferenceKey.remindersEnabled) as? Bool) ?? true,
            peopleSort: defaults.string(forKey: PreferenceKey.peopleSort) ?? PeopleSort.name.rawValue
        )
    }

    private func write(_ preferences: PreferenceSnapshot) {
        defaults.set(preferences.groupLabel, forKey: PreferenceKey.groupLabel)
        defaults.set(preferences.contextMode, forKey: PreferenceKey.contextMode)
        defaults.set(preferences.remindersEnabled, forKey: PreferenceKey.remindersEnabled)
        defaults.set(preferences.peopleSort, forKey: PreferenceKey.peopleSort)
    }

    static func makeToken() throws -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else { throw CloudError.keychain }
        let encoded = Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return "kpr_\(encoded)"
    }

    private func message(for error: Error) -> String {
        guard let cloud = error as? CloudError else {
            return "Couldn't reach your account. Your data on this iPhone is unchanged."
        }
        switch cloud {
        case .notConfigured:
            return "This build has no Firebase project yet."
        case .invalidCredentials:
            return "That email or password doesn't match."
        case .emailInUse:
            return "There's already an account for that email. Sign in instead."
        case .weakPassword:
            return "Use a password of at least 6 characters."
        case .tooManyAttempts:
            return "Too many attempts. Wait a few minutes and try again."
        case .keychain:
            return "The iPhone couldn't store the sign-in securely."
        case .http(let status):
            return "The account returned HTTP \(status). Nothing on this iPhone was deleted."
        case .incompleteList:
            return "The account didn't finish listing your data, so sync stopped before deleting anything."
        case .boundToAnotherAccount:
            return "This iPhone is already synced with another account."
        }
    }
}

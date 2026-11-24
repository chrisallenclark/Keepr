import SwiftUI
import UIKit

/// Sign in, sync, and the assistant tokens. The token itself is shown once.
struct CloudAccountView: View {
    @Environment(\.modelContext) private var context

    @State private var account = CloudAccount.shared
    @State private var email = ""
    @State private var password = ""
    @State private var tokenLabel = "Assistant"
    @State private var tokenPendingRevoke: AccessTokenSummary?

    var body: some View {
        Form {
            if !account.isConfigured {
                Section {
                    Text("Cloud sync isn't turned on in this build. The app keeps working on this iPhone only.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Not configured")
                } footer: {
                    Text("Add Keepr/FirebaseOptions.plist from the Firebase project, then rebuild. The steps are in docs/CLOUD_SYNC.md. Nothing is uploaded until you sign in.")
                }
            } else if !account.isSignedIn {
                Section {
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                    Button("Sign In") { Task { await account.signIn(email: email, password: password) } }
                        .disabled(account.isWorking)
                    Button("Create Account") { Task { await account.signUp(email: email, password: password) } }
                        .disabled(account.isWorking)
                } header: {
                    Text("Account")
                } footer: {
                    Text("Signing in copies the relationships on this iPhone into your account, and pulls anything already there. Sample data stays on the phone.")
                }
            } else {
                Section {
                    LabeledContent("Signed in", value: account.email ?? "")
                    if let last = account.lastSyncedAt {
                        LabeledContent("Last sync", value: last.formatted(date: .abbreviated, time: .shortened))
                    }
                    Button(account.isWorking ? "Syncing…" : "Sync Now") {
                        Task { await account.syncNow(in: context) }
                    }
                    .disabled(account.isWorking)
                    Button("Sign Out", role: .destructive) { account.signOut() }
                } footer: {
                    Text("Signing out leaves your relationships on this iPhone and in the account. It doesn't revoke assistant tokens.")
                }

                Section {
                    TextField("Name", text: $tokenLabel)
                    Button("Create Token") {
                        Task { await account.createToken(label: tokenLabel) }
                    }
                    .disabled(account.isWorking)
                    if account.tokens.isEmpty {
                        Text("No tokens yet.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(account.tokens) { token in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(token.label)
                            Text(token.revokedAt == nil ? token.prefix : "\(token.prefix) · revoked")
                                .font(.caption.monospaced())
                                .foregroundStyle(.secondary)
                        }
                        .swipeActions {
                            if token.revokedAt == nil {
                                Button("Revoke", role: .destructive) {
                                    tokenPendingRevoke = token
                                }
                            }
                        }
                    }
                } header: {
                    Text("Assistant access")
                } footer: {
                    Text("A token lets something read your account. It can't change anything. Keepr stores only a hash of the token. Revoke it here and the next request fails.")
                }
            }

            if let status = account.statusMessage {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Cloud & Assistant")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard account.isSignedIn else { return }
            await account.refreshTokens()
        }
        .sheet(item: Binding(
            get: { account.revealedToken },
            set: { account.revealedToken = $0 }
        )) { revealed in
            NavigationStack {
                VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                    Text("Copy this token now. It won't be shown again.")
                        .font(.body)
                    Text(revealed.value)
                        .font(.system(.footnote, design: .monospaced))
                        .textSelection(.enabled)
                    Button("Copy") {
                        UIPasteboard.general.string = revealed.value
                    }
                    .buttonStyle(.borderedProminent)
                    Spacer()
                }
                .padding(Theme.Spacing.large)
                .navigationTitle("New Token")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { account.revealedToken = nil }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .confirmationDialog(
            "Revoke this token?",
            isPresented: Binding(
                get: { tokenPendingRevoke != nil },
                set: { if !$0 { tokenPendingRevoke = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Revoke", role: .destructive) {
                guard let token = tokenPendingRevoke else { return }
                tokenPendingRevoke = nil
                Task { await account.revokeToken(id: token.id) }
            }
        } message: {
            Text("Anything using it will stop being able to read your account.")
        }
    }
}

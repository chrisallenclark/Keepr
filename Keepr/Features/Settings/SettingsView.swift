import SwiftData
import SwiftUI
import UIKit
import UserNotifications

/// Preferences, privacy, and the honest version of what this app does and
/// doesn't do.
struct SettingsView: View {

    @Environment(\.modelContext) private var context
    @Environment(\.contactStore) private var contactStore
    @Environment(\.notificationService) private var notificationService
    @Environment(\.dismiss) private var dismiss

    @AppStorage(PreferenceKey.remindersEnabled) private var remindersEnabled = true
    @AppStorage(PreferenceKey.groupLabel) private var groupLabel = GroupVocabulary.default.singular
    @AppStorage(PreferenceKey.hasAskedForNotifications) private var hasAskedForNotifications = false

    @Query private var people: [Person]

    @State private var contactAccess: ContactAccess = .notDetermined
    @State private var notificationStatus: UNAuthorizationStatus = .notDetermined
    @State private var isSampleDataLoaded = false
    @State private var isConfirmingDelete = false

    /// Everyone organizes people by *something* — which of my businesses, which
    /// gym, which dating app, which chapter of my life. The app can't pick the
    /// noun for all of them, so it asks once and then uses their word everywhere.
    private var vocabularySection: some View {
        Section {
            Picker("Call them", selection: $groupLabel) {
                ForEach(GroupVocabulary.presets) { option in
                    Text(option.plural).tag(option.singular)
                }
            }
        } header: {
            Text("Wording")
        } footer: {
            Text("The second way Keepr sorts people, after relationship type. Life Time and your training company are \(GroupVocabulary.plural(for: groupLabel).lowercased()); so are Hinge and Bumble, or the bar you met someone in. Someone can be in more than one.")
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Reminders") {
                    Toggle("Follow-up reminders", isOn: $remindersEnabled)
                        .onChange(of: remindersEnabled) { _, enabled in
                            Task { await handleRemindersToggle(enabled) }
                        }

                    if remindersEnabled, notificationStatus == .denied {
                        settingsLink(
                            "Notifications are off in iOS Settings",
                            symbolName: "exclamationmark.triangle"
                        )
                    }
                }

                vocabularySection

                Section {
                    LabeledContent("Contacts", value: contactAccessLabel)
                    if contactAccess != .authorized {
                        settingsLink("Manage in Settings", symbolName: "gearshape")
                    }
                } header: {
                    Text("Permissions")
                } footer: {
                    Text("Keepr reads your contacts only to link them to relationships. Nothing is uploaded, and Keepr never writes to your contacts.")
                }

                Section {
                    LabeledContent("People", value: "\(people.count)")
                    LabeledContent("Storage", value: "This device")
                    if KeeprStore.isUsingFallbackStore {
                        Label(
                            "Your database couldn't be opened, so this session isn't being saved.",
                            systemImage: "exclamationmark.triangle"
                        )
                        .font(.footnote)
                        .foregroundStyle(.orange)
                    }
                } header: {
                    Text("Data")
                } footer: {
                    Text("Everything you record stays on this device. There's no account, no server, and no analytics.")
                }

                Section {
                    Toggle("Sample data", isOn: sampleDataBinding)
                } header: {
                    Text("Development")
                } footer: {
                    Text("Loads a set of example relationships for evaluating the app. Turning it off removes only those, never anything you added.")
                }

                Section {
                    Button("Delete All Data", role: .destructive) {
                        isConfirmingDelete = true
                    }
                } footer: {
                    Text("Removes every relationship, memory, interaction and follow-up from this device. Your contacts are not affected.")
                }

                Section("About") {
                    LabeledContent("Version", value: appVersion)
                    NavigationLink("What Keepr can't do") {
                        LimitationsView()
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .confirmationDialog(
                "Delete all Keepr data?",
                isPresented: $isConfirmingDelete,
                titleVisibility: .visible
            ) {
                Button("Delete Everything", role: .destructive, action: deleteAll)
            } message: {
                Text("This can't be undone.")
            }
            .task { await refreshStatus() }
        }
    }

    // MARK: - Pieces

    private var contactAccessLabel: String {
        switch contactAccess {
        case .authorized: "Full access"
        case .limited: "Selected contacts"
        case .denied: "Off"
        case .restricted: "Unavailable"
        case .notDetermined: "Not asked"
        }
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }

    private var sampleDataBinding: Binding<Bool> {
        Binding(
            get: { isSampleDataLoaded },
            set: { wantsSample in
                if wantsSample {
                    SampleData.load(into: context)
                } else {
                    SampleData.remove(from: context)
                }
                isSampleDataLoaded = SampleData.isLoaded(in: context)
                Haptics.light()
            }
        )
    }

    @ViewBuilder
    private func settingsLink(_ title: String, symbolName: String) -> some View {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            Link(destination: url) {
                Label(title, systemImage: symbolName)
                    .font(.subheadline)
            }
        }
    }

    // MARK: - Actions

    private func refreshStatus() async {
        contactAccess = contactStore.authorizationStatus()
        notificationStatus = await notificationService.authorizationStatus()
        isSampleDataLoaded = SampleData.isLoaded(in: context)
    }

    /// Asks for notification permission the first time reminders are switched
    /// on — never at launch, and never for a feature the user hasn't chosen.
    private func handleRemindersToggle(_ enabled: Bool) async {
        guard enabled else {
            await notificationService.cancelAll()
            return
        }
        if !hasAskedForNotifications, notificationStatus == .notDetermined {
            hasAskedForNotifications = true
            _ = await notificationService.requestAuthorization()
        }
        notificationStatus = await notificationService.authorizationStatus()
    }

    private func deleteAll() {
        Task { await notificationService.cancelAll() }
        KeeprStore.deleteAllData(in: context)
        // The seen-contacts list is app data too. Leaving it behind would mean a
        // wiped app quietly treats every existing contact as already handled.
        ContactChangeTracker.shared.reset()
        isSampleDataLoaded = false
        Haptics.success()
    }
}

/// Said plainly, in the app, rather than discovered as a disappointment.
struct LimitationsView: View {
    var body: some View {
        List {
            Section {
                Text("Keepr can't read your iMessage, Mail or call history. Apple doesn't allow apps to do that, and any app claiming otherwise isn't on the App Store.")
            } header: {
                Text("Messages")
            }

            Section {
                Text("Your interaction timeline is what you record — by hand or through Quick Capture. That's a feature: it's the interactions that mattered, not every notification you ever got.")
            } header: {
                Text("What's in the timeline")
            }

            Section {
                Text("Quick Capture reads your note on this device using plain text rules. Nothing is sent anywhere, and every suggestion it makes waits for you to approve it.")
            } header: {
                Text("Suggestions")
            }

            Section {
                Text("Keepr doesn't sync between devices yet. Everything lives on this iPhone, so keep an iCloud backup if it matters to you.")
            } header: {
                Text("Sync")
            }
        }
        .navigationTitle("What Keepr can't do")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    SettingsView()
        .modelContainer(.preview)
}

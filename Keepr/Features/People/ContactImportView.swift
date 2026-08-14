import UIKit
import SwiftData
import SwiftUI

/// Pick people out of Contacts and bring them in as relationships.
///
/// Handles every permission state as a normal state, not an error: denied and
/// restricted both offer the manual path instead of a dead end, and iOS 18's
/// limited access says plainly that you're only seeing what you chose to share.
struct ContactImportView: View {

    let mode: ContextMode
    /// Called with how many people were imported, for onboarding to react to.
    var onFinish: ((Int) -> Void)?

    @Environment(\.contactStore) private var contactStore
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query private var existingPeople: [Person]

    @State private var access: ContactAccess = .notDetermined
    @State private var contacts: [ContactSummary] = []
    @State private var selected: Set<String> = []
    @State private var query = ""
    @State private var isLoading = false

    private var linkedIdentifiers: Set<String> {
        Set(existingPeople.compactMap(\.contactIdentifier))
    }

    private var results: [ContactSummary] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return contacts }
        return contacts.filter {
            $0.fullName.localizedStandardContains(trimmed)
                || ($0.organizationName ?? "").localizedStandardContains(trimmed)
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView().controlSize(.large)
                } else if access.canRead {
                    contactList
                } else {
                    permissionState
                }
            }
            .navigationTitle("Add from Contacts")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                if access.canRead {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add \(selected.count)", action: importSelected)
                            .fontWeight(.semibold)
                            .disabled(selected.isEmpty)
                    }
                }
            }
            .task { await load() }
        }
    }

    // MARK: - List

    private var contactList: some View {
        List {
            if access == .limited {
                Section {
                    Label(
                        "You're sharing some contacts with Keepr. Tap to share more in Settings.",
                        systemImage: "info.circle"
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
            }

            ForEach(results) { contact in
                let isLinked = linkedIdentifiers.contains(contact.identifier)
                Button {
                    toggle(contact)
                } label: {
                    HStack(spacing: Theme.Spacing.medium) {
                        Avatar(contact: contact, size: .small)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(contact.fullName)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(isLinked ? .secondary : .primary)
                            if let subtitle = contact.subtitle {
                                Text(subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }

                        Spacer(minLength: Theme.Spacing.small)

                        if isLinked {
                            Text("Added")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        } else if selected.contains(contact.identifier) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Color.accentColor)
                        } else {
                            Image(systemName: "circle")
                                .foregroundStyle(.quaternary)
                        }
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .disabled(isLinked)
            }
        }
        .listStyle(.plain)
        .searchable(text: $query, prompt: "Search contacts")
        .overlay {
            if results.isEmpty {
                ContentUnavailableView(
                    query.isEmpty ? "No contacts" : "No matches",
                    systemImage: "person.crop.circle",
                    description: Text(
                        query.isEmpty
                            ? "There's nothing in your contacts to import."
                            : "Nothing matches \"\(query)\"."
                    )
                )
            }
        }
    }

    // MARK: - Permission

    private var permissionState: some View {
        ContentUnavailableView {
            Label("Contacts are off", systemImage: "person.crop.circle.badge.exclamationmark")
        } description: {
            Text(access.explanation ?? "Keepr needs contact access to import people.")
        } actions: {
            if access == .notDetermined {
                Button("Allow Contacts") {
                    Task { access = await contactStore.requestAccess(); await load() }
                }
                .buttonStyle(.borderedProminent)
            } else if let url = URL(string: UIApplication.openSettingsURLString) {
                Link("Open Settings", destination: url)
                    .buttonStyle(.bordered)
            }
        }
    }

    // MARK: - Actions

    private func load() async {
        access = contactStore.authorizationStatus()
        guard access.canRead, contacts.isEmpty else { return }

        isLoading = true
        contacts = await contactStore.fetchContacts()
        isLoading = false
    }

    private func toggle(_ contact: ContactSummary) {
        if selected.contains(contact.identifier) {
            selected.remove(contact.identifier)
        } else {
            selected.insert(contact.identifier)
        }
        Haptics.selection()
    }

    private func importSelected() {
        let chosen = contacts.filter { selected.contains($0.identifier) }
        for contact in chosen {
            let person = Person(
                givenName: contact.givenName,
                familyName: contact.familyName,
                context: RelationshipContext(mode: mode),
                contactIdentifier: contact.identifier,
                company: contact.organizationName,
                jobTitle: contact.jobTitle,
                phoneNumbers: contact.phoneNumbers,
                emailAddresses: contact.emailAddresses,
                photoData: contact.thumbnailData
            )
            context.insert(person)
        }
        try? context.save()

        Haptics.success()
        onFinish?(chosen.count)
        dismiss()
    }
}

#Preview {
    ContactImportView(mode: .business)
        .modelContainer(.preview)
}

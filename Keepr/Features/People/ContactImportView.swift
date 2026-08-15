import SwiftData
import SwiftUI
import UIKit

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
    @Query(sort: \PersonGroup.sortOrder) private var groups: [PersonGroup]
    @Query(sort: \RelationshipTag.sortOrder) private var tags: [RelationshipTag]

    @AppStorage(PreferenceKey.groupLabel) private var groupLabel = GroupVocabulary.default.singular

    @State private var access: ContactAccess = .notDetermined
    @State private var contacts: [ContactSummary] = []
    @State private var selected: Set<String> = []
    @State private var suggestions: [String: CategorySuggestion] = [:]
    @State private var discovered: [ContactMarkerParser.Candidate] = []
    @State private var newGroupToken: DiscoveredToken?
    @State private var applySuggestions = true
    @State private var query = ""
    @State private var isLoading = false

    private var vocabulary: MarkerVocabulary {
        MarkerVocabulary.build(groups: groups, tags: tags)
    }

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

    private var importableWithSuggestions: [ContactSummary] {
        results.filter {
            suggestions[$0.identifier] != nil && !linkedIdentifiers.contains($0.identifier)
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView("Reading contacts").controlSize(.large)
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
                    ToolbarItem(placement: .topBarTrailing) { optionsMenu }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add \(selected.count)", action: importSelected)
                            .fontWeight(.semibold)
                            .disabled(selected.isEmpty)
                    }
                }
            }
            .task { await load() }
            .sheet(item: $newGroupToken) { pending in
                // Prefilled with the shorthand as both the name and the alias,
                // so "LT" keeps working even after it's renamed to "Life Time".
                GroupEditor(
                    group: nil,
                    mode: mode,
                    prefilledName: pending.token,
                    prefilledAlias: pending.token
                ) { _ in
                    rebuildSuggestions()
                }
            }
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

            discoveredSection

            if !suggestions.isEmpty {
                Section {
                    Toggle("Apply suggested categories", isOn: $applySuggestions)
                        .font(.subheadline)
                } footer: {
                    Text("Read off each contact card — your own shorthand first, then an employer, a job title, a family relation. Everything is editable afterwards, and anything Keepr can't place is left for you.")
                }
            }

            Section {
                ForEach(results) { contact in
                    contactRow(contact)
                }
            }
        }
        .listStyle(.insetGrouped)
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

    /// Shorthand that's all over the address book but means nothing to Keepr
    /// yet — "LT" on eight cards, "HYP" on five.
    ///
    /// Without this, the whole marker feature only works for someone who set up
    /// their groups first, which is nobody. One tap here turns a habit the user
    /// already has into something the importer can act on.
    @ViewBuilder
    private var discoveredSection: some View {
        if !discovered.isEmpty {
            Section {
                ScrollView(.horizontal) {
                    HStack(spacing: Theme.Spacing.small) {
                        ForEach(discovered) { candidate in
                            Button {
                                newGroupToken = DiscoveredToken(token: candidate.token)
                            } label: {
                                HStack(spacing: Theme.Spacing.tight) {
                                    Image(systemName: "plus.circle")
                                    Text(candidate.token)
                                        .fontWeight(.medium)
                                    Text("\(candidate.count)")
                                        .foregroundStyle(.secondary)
                                }
                                .font(.subheadline)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(.vertical, Theme.Spacing.tight)
                }
                .scrollIndicators(.hidden)
                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 0))
            } header: {
                Text("Shorthand On Your Contacts")
            } footer: {
                Text("Keepr keeps seeing these on your contact cards. Make one a \(groupLabel.lowercased()) and every contact carrying it gets sorted automatically — now and every time you import.")
            }
        }
    }

    private func displayName(for contact: ContactSummary, suggestion: CategorySuggestion?) -> String {
        guard applySuggestions,
              let suggestion,
              suggestion.originalName != nil
        else { return contact.fullName }

        let cleaned = [suggestion.cleanedGivenName, suggestion.cleanedFamilyName]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return cleaned.isEmpty ? contact.fullName : cleaned
    }

    private func contactRow(_ contact: ContactSummary) -> some View {
        let isLinked = linkedIdentifiers.contains(contact.identifier)
        let suggestion = suggestions[contact.identifier]

        return Button {
            toggle(contact)
        } label: {
            HStack(spacing: Theme.Spacing.medium) {
                Avatar(contact: contact, size: .small)

                VStack(alignment: .leading, spacing: 2) {
                    Text(displayName(for: contact, suggestion: suggestion))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(isLinked ? .secondary : .primary)

                    if let subtitle = contact.subtitle {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    if applySuggestions, let suggestion, !isLinked {
                        HStack(spacing: Theme.Spacing.tight) {
                            Image(systemName: suggestion.context.symbolName)
                            Text(
                                ([suggestion.tagName].compactMap { $0 }
                                    + suggestion.extraTagNames
                                    + suggestion.groupNames)
                                    .joined(separator: " · ")
                            )
                            .lineLimit(1)
                        }
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(Color.accentColor)

                        Text(suggestion.reason)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
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

    private var optionsMenu: some View {
        Menu {
            Button {
                selected = Set(
                    results
                        .filter { !linkedIdentifiers.contains($0.identifier) }
                        .map(\.identifier)
                )
                Haptics.selection()
            } label: {
                Label("Select All", systemImage: "checkmark.circle")
            }

            if !importableWithSuggestions.isEmpty {
                Button {
                    selected = Set(importableWithSuggestions.map(\.identifier))
                    Haptics.selection()
                } label: {
                    Label(
                        "Select \(importableWithSuggestions.count) with Suggestions",
                        systemImage: "wand.and.stars"
                    )
                }
            }

            if !selected.isEmpty {
                Button {
                    selected = []
                    Haptics.selection()
                } label: {
                    Label("Deselect All", systemImage: "circle")
                }
            }
        } label: {
            Label("Options", systemImage: "ellipsis.circle")
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
        let fetched = await contactStore.fetchContacts()
        contacts = fetched
        rebuildSuggestions()
        isLoading = false
    }

    /// Re-read every card against the current vocabulary. Called again whenever
    /// a group is created from discovered shorthand, so the list visibly
    /// reorganizes itself the moment "LT" means something.
    private func rebuildSuggestions() {
        let vocabulary = self.vocabulary
        var result: [String: CategorySuggestion] = [:]
        for contact in contacts {
            if let suggestion = ContactCategorizer.suggestion(for: contact, vocabulary: vocabulary) {
                result[contact.identifier] = suggestion
            }
        }
        suggestions = result
        discovered = ContactMarkerParser.candidates(in: contacts, vocabulary: vocabulary)
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
            let suggestion = applySuggestions ? suggestions[contact.identifier] : nil

            let person = PersonImporter.makePerson(
                from: contact,
                context: suggestion?.context ?? RelationshipContext(mode: mode),
                in: context,
                suggestion: suggestion
            )
            if let suggestion {
                PersonImporter.apply(suggestion, to: person, in: context, groups: groups)
                PersonImporter.noteOriginalName(suggestion, for: person, in: context)
            }
            PersonImporter.addMemories(from: contact, to: person, in: context)
        }
        try? context.save()

        // Anyone imported here has been dealt with, so the review queue doesn't
        // raise them again as a new contact tomorrow.
        ContactChangeTracker.shared.acknowledge(chosen)

        Haptics.success()
        onFinish?(chosen.count)
        dismiss()
    }
}

/// A shorthand token on its way to becoming a group. `sheet(item:)` needs
/// identity, and a bare String has none.
private struct DiscoveredToken: Identifiable {
    let token: String

    var id: String { token }
}

#Preview {
    ContactImportView(mode: .business)
        .modelContainer(.preview)
}

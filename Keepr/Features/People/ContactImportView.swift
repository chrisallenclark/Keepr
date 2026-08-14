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

    @State private var access: ContactAccess = .notDetermined
    @State private var contacts: [ContactSummary] = []
    @State private var selected: Set<String> = []
    @State private var suggestions: [String: CategorySuggestion] = [:]
    @State private var applySuggestions = true
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

            if !suggestions.isEmpty {
                Section {
                    Toggle("Apply suggested categories", isOn: $applySuggestions)
                        .font(.subheadline)
                } footer: {
                    Text("Guessed from what's already on each contact card — an employer, a job title, a family relation. Everything is editable afterwards, and anything Keepr can't place is left for you.")
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

    private func contactRow(_ contact: ContactSummary) -> some View {
        let isLinked = linkedIdentifiers.contains(contact.identifier)
        let suggestion = suggestions[contact.identifier]

        return Button {
            toggle(contact)
        } label: {
            HStack(spacing: Theme.Spacing.medium) {
                Avatar(contact: contact, size: .small)

                VStack(alignment: .leading, spacing: 2) {
                    Text(contact.fullName)
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
                            Text(suggestion.tagName ?? suggestion.context.title)
                            Text("·")
                            Text(suggestion.reason).lineLimit(1)
                        }
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
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
        suggestions = ContactCategorizer.suggestions(for: fetched)
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
            let suggestion = applySuggestions ? suggestions[contact.identifier] : nil

            let person = Person(
                givenName: contact.givenName,
                familyName: contact.familyName,
                context: suggestion?.context ?? RelationshipContext(mode: mode),
                contactIdentifier: contact.identifier,
                company: contact.organizationName,
                jobTitle: contact.jobTitle,
                phoneNumbers: contact.phoneNumbers,
                emailAddresses: contact.emailAddresses,
                photoData: contact.thumbnailData
            )
            context.insert(person)

            person.birthday = contact.birthday
            person.postalAddress = contact.postalAddress
            person.contactNote = contact.note

            if let tagName = suggestion?.tagName {
                let kind: TagKind = suggestion?.context == .personal ? .personal : .business
                person.tags = [KeeprStore.tag(named: tagName, kind: kind, in: context)]
            }

            createMemories(from: contact, for: person)
        }
        try? context.save()

        Haptics.success()
        onFinish?(chosen.count)
        dismiss()
    }

    /// Facts already sitting on the contact card become memories, so the profile
    /// isn't empty the moment someone is imported. Only things the user typed
    /// themselves — nothing inferred.
    private func createMemories(from contact: ContactSummary, for person: Person) {
        if let birthday = contact.birthday {
            let formatted = birthday.formatted(.dateTime.month(.wide).day())
            context.insert(
                Memory(
                    content: "Birthday: \(formatted)",
                    category: .importantDate,
                    importance: .high,
                    person: person
                )
            )
        }

        for relation in contact.relations {
            context.insert(
                Memory(
                    content: "\(relation.label.capitalized): \(relation.name)",
                    category: .family,
                    person: person
                )
            )
        }

        if let note = contact.note, !note.isEmpty {
            context.insert(
                Memory(
                    content: note,
                    category: .other,
                    person: person
                )
            )
        }
    }
}

#Preview {
    ContactImportView(mode: .business)
        .modelContainer(.preview)
}

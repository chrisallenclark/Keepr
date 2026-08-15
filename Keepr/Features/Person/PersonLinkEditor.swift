import SwiftData
import SwiftUI

/// Connect two people — "Linda is Alex's parent", "Priya reports to Dana".
///
/// Opens with whatever it already knows: from a profile it knows one end, from
/// picking two people in the People list it knows both. Anything the contact
/// card already states is offered as a one-tap suggestion, because retyping a
/// relationship the phone already knows about is busywork.
struct PersonLinkEditor: View {

    let person: Person
    /// Pre-filled when the editor is opened with both ends already chosen.
    var other: Person?
    /// Called after a link is saved, so a selection can be cleared.
    var onSave: (() -> Void)?

    @Environment(\.modelContext) private var context
    @Environment(\.contactStore) private var contactStore
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \Person.familyName) private var people: [Person]

    @State private var selected: Person?
    @State private var role: LinkRole = LinkRole.named("Friend")
    @State private var customRole = ""
    @State private var isUsingCustomRole = false
    @State private var note = ""
    @State private var suggestions: [LinkSuggestion] = []
    @State private var hasLoaded = false

    private var resolvedRole: LinkRole {
        isUsingCustomRole ? .custom(trimmedCustomRole) : role
    }

    private var trimmedCustomRole: String {
        customRole.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        selected != nil && !resolvedRole.name.isEmpty
    }

    /// Everyone who could be the other end: not this person, not already linked.
    private var candidates: [Person] {
        people.filter { $0.id != person.id && !person.isLinked(to: $0) }
    }

    var body: some View {
        NavigationStack {
            Form {
                if !suggestions.isEmpty, other == nil {
                    suggestionsSection
                }

                Section("Who") {
                    NavigationLink {
                        LinkPersonPicker(candidates: candidates, selection: $selected)
                    } label: {
                        personRow
                    }
                }

                Section {
                    if isUsingCustomRole {
                        TextField("Relationship", text: $customRole)
                            .textInputAutocapitalization(.words)
                        Button("Choose from the list") {
                            withAnimation { isUsingCustomRole = false }
                        }
                        .font(.subheadline)
                    } else {
                        rolePicker
                        Button("Type my own") {
                            withAnimation { isUsingCustomRole = true }
                        }
                        .font(.subheadline)
                    }
                } header: {
                    Text("Relationship")
                } footer: {
                    Text(explanation)
                }

                Section {
                    TextField("Note (optional)", text: $note, axis: .vertical)
                        .font(.subheadline)
                } footer: {
                    Text("Anything worth remembering about the connection itself.")
                }
            }
            .navigationTitle("Link People")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(!canSave)
                }
            }
            .task { await load() }
        }
        .presentationDragIndicator(.visible)
    }

    // MARK: - Sections

    private var suggestionsSection: some View {
        Section {
            ForEach(suggestions) { suggestion in
                Button {
                    apply(suggestion)
                } label: {
                    HStack(spacing: Theme.Spacing.medium) {
                        Avatar(person: suggestion.candidate, size: .small)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(suggestion.candidate.displayName)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.primary)
                            Text(suggestion.reason)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer(minLength: Theme.Spacing.small)
                        Label(suggestion.role.name, systemImage: suggestion.role.symbolName)
                            .font(.caption)
                            .labelStyle(.titleAndIcon)
                            .foregroundStyle(Color.accentColor)
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        } header: {
            Text("From Their Contact Card")
        }
    }

    private var personRow: some View {
        HStack {
            Text("Person")
            Spacer()
            if let selected {
                Text(selected.displayName).foregroundStyle(.secondary)
            } else {
                Text("Choose").foregroundStyle(.tertiary)
            }
        }
    }

    private var rolePicker: some View {
        Picker("They are their…", selection: $role) {
            ForEach(LinkRole.sections) { section in
                Section(section.title) {
                    ForEach(section.roles) { option in
                        Label(option.name, systemImage: option.symbolName).tag(option)
                    }
                }
            }
        }
    }

    /// Spells out both directions, because a link that reads correctly from one
    /// profile and backwards from the other is the whole risk in this feature.
    private var explanation: String {
        let them = selected?.displayName ?? "They"
        let role = resolvedRole
        guard !role.name.isEmpty else { return " " }
        return "\(them) shows up as \(role.name) on \(person.displayName)'s profile, "
            + "and \(person.displayName) shows up as \(role.inverse) on theirs."
    }

    // MARK: - Actions

    private func load() async {
        guard !hasLoaded else { return }
        hasLoaded = true

        if let other {
            selected = other
        }

        guard other == nil,
              let identifier = person.contactIdentifier,
              contactStore.authorizationStatus().canRead,
              let contact = await contactStore.contact(withIdentifier: identifier)
        else { return }

        suggestions = LinkSuggester.suggestions(
            from: contact.relations,
            for: person,
            among: candidates
        )
    }

    private func apply(_ suggestion: LinkSuggestion) {
        withAnimation {
            selected = suggestion.candidate
            isUsingCustomRole = !LinkRole.catalog.contains(suggestion.role)
            if isUsingCustomRole {
                customRole = suggestion.role.name
            } else {
                role = suggestion.role
            }
        }
        Haptics.selection()
    }

    private func save() {
        guard let selected else { return }
        let role = resolvedRole

        let link = PersonLink(
            personA: person,
            personB: selected,
            labelAToB: role.name,
            labelBToA: role.inverse,
            note: note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? nil
                : note.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        context.insert(link)
        person.touch()
        selected.touch()
        try? context.save()

        Haptics.success()
        onSave?()
        dismiss()
    }
}

// MARK: - Picker

/// Choose the other end of a link. Separate from `PersonPicker` because this one
/// can't offer to create someone new — a link needs two records that exist.
private struct LinkPersonPicker: View {

    let candidates: [Person]
    @Binding var selection: Person?

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var results: [Person] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let matching = trimmed.isEmpty
            ? candidates
            : candidates.filter { PeopleEngine.matches($0, query: trimmed) }
        return PeopleEngine.sort(matching, by: .name)
    }

    var body: some View {
        List {
            ForEach(PeopleEngine.alphabeticalSections(results)) { section in
                Section(section.key) {
                    ForEach(section.people) { person in
                        Button {
                            selection = person
                            Haptics.selection()
                            dismiss()
                        } label: {
                            HStack {
                                PersonCompactRow(
                                    person: person,
                                    detail: person.subtitle ?? person.context.title
                                )
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .listStyle(.plain)
        .searchable(text: $query, prompt: "Search everyone")
        .navigationTitle("Link To")
        .navigationBarTitleDisplayMode(.inline)
        .overlay {
            if results.isEmpty {
                ContentUnavailableView(
                    query.isEmpty ? "Nobody left to link" : "No matches",
                    systemImage: "person.2.slash",
                    description: Text(
                        query.isEmpty
                            ? "Everyone else is already linked to this person."
                            : "Nothing matches \"\(query)\"."
                    )
                )
            }
        }
    }
}

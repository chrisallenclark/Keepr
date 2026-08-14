import SwiftData
import SwiftUI

/// Create or edit a group.
///
/// Suggestions come first when creating, because the honest starting state of
/// this feature is "I have no idea what a group is for" — one tap on "Gym"
/// answers that better than any explanatory paragraph.
struct GroupEditor: View {

    /// nil creates a new group.
    var group: PersonGroup?
    /// The mode the user is in, used as the default context for a new group.
    var mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \PersonGroup.sortOrder) private var allGroups: [PersonGroup]

    @State private var name = ""
    @State private var detail = ""
    @State private var symbolName = "person.3"
    @State private var relationshipContext: RelationshipContext = .both
    @State private var isShowingSymbolPicker = false
    @State private var isConfirmingDelete = false
    @State private var hasLoaded = false

    @FocusState private var isNameFocused: Bool

    private var isEditing: Bool { group != nil }

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Suggestions the user has already acted on are dropped, so the row shrinks
    /// as the list fills in rather than offering duplicates.
    private var openSuggestions: [PersonGroup.Suggestion] {
        PersonGroup.suggestions.filter { suggestion in
            !allGroups.contains {
                $0.name.localizedCaseInsensitiveCompare(suggestion.name) == .orderedSame
            }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                if !isEditing, !openSuggestions.isEmpty {
                    suggestionsSection
                }

                Section {
                    TextField("Name", text: $name)
                        .focused($isNameFocused)
                    TextField("Detail (optional)", text: $detail)
                        .font(.subheadline)
                    symbolRow
                } footer: {
                    Text("A detail tells two similar groups apart — \"Life Time, Delray\" under \"Gym\".")
                }

                Section {
                    Picker("Context", selection: $relationshipContext) {
                        ForEach(RelationshipContext.allCases) { option in
                            Text(option.title).tag(option)
                        }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("Context")
                } footer: {
                    Text("Both keeps the group visible in Business and Personal. A gym is usually both.")
                }

                if isEditing {
                    deleteSection
                }
            }
            .navigationTitle(isEditing ? "Edit Group" : "New Group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(trimmedName.isEmpty)
                }
            }
            .sheet(isPresented: $isShowingSymbolPicker) {
                SymbolPicker(selection: $symbolName)
            }
            .confirmationDialog(
                "Delete \(group?.name ?? "Group")?",
                isPresented: $isConfirmingDelete,
                titleVisibility: .visible
            ) {
                Button("Delete Group", role: .destructive, action: deleteGroup)
            } message: {
                Text(deleteMessage)
            }
            .onAppear(perform: load)
        }
        .presentationDragIndicator(.visible)
    }

    // MARK: - Sections

    private var suggestionsSection: some View {
        Section {
            ScrollView(.horizontal) {
                HStack(spacing: Theme.Spacing.small) {
                    ForEach(openSuggestions, id: \.name) { suggestion in
                        Button {
                            apply(suggestion)
                        } label: {
                            Label(suggestion.name, systemImage: suggestion.symbolName)
                        }
                        .buttonStyle(.bordered)
                        .tint(trimmedName == suggestion.name ? .accentColor : .secondary)
                        .font(.subheadline)
                    }
                }
                .padding(.vertical, Theme.Spacing.tight)
            }
            .scrollIndicators(.hidden)
            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 0))
        } header: {
            Text("Start From")
        }
    }

    private var symbolRow: some View {
        Button {
            isShowingSymbolPicker = true
        } label: {
            HStack {
                Text("Symbol")
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: symbolName)
                    .font(.title3)
                    .foregroundStyle(Color.accentColor)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Symbol")
        .accessibilityValue(SymbolPicker.readableName(for: symbolName))
        .accessibilityHint("Opens the symbol picker")
    }

    private var deleteSection: some View {
        Section {
            Button("Delete Group", role: .destructive) {
                isConfirmingDelete = true
            }
        } footer: {
            Text("Deleting a group removes only the grouping. Everyone in it stays in Keepr.")
        }
    }

    /// Says the number out loud, because "are the people deleted too?" is the
    /// only question this dialog exists to answer.
    private var deleteMessage: String {
        switch group?.memberCount ?? 0 {
        case 0: "Nobody is in this group, so nothing else changes."
        case 1: "The person in it stays in Keepr — only the grouping is removed."
        case let count: "The \(count) people in it stay in Keepr — only the grouping is removed."
        }
    }

    // MARK: - Actions

    private func apply(_ suggestion: PersonGroup.Suggestion) {
        name = suggestion.name
        symbolName = suggestion.symbolName
        relationshipContext = suggestion.context
        isNameFocused = false
        Haptics.selection()
    }

    private func load() {
        guard !hasLoaded else { return }
        hasLoaded = true

        guard let group else {
            relationshipContext = RelationshipContext(mode: mode)
            isNameFocused = true
            return
        }
        name = group.name
        detail = group.detail ?? ""
        symbolName = group.symbolName
        relationshipContext = group.context
    }

    private func save() {
        let trimmedDetail = detail.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedDetail = trimmedDetail.isEmpty ? nil : trimmedDetail

        if let group {
            group.name = trimmedName
            group.detail = resolvedDetail
            group.symbolName = symbolName
            group.context = relationshipContext
        } else {
            let created = PersonGroup(
                name: trimmedName,
                symbolName: symbolName,
                detail: resolvedDetail,
                context: relationshipContext,
                sortOrder: (allGroups.map(\.sortOrder).max() ?? 0) + 10
            )
            context.insert(created)
        }

        try? context.save()
        Haptics.success()
        dismiss()
    }

    private func deleteGroup() {
        guard let group else { return }
        // Membership is a nullify relationship, so this drops the grouping and
        // leaves every Person record intact.
        context.delete(group)
        try? context.save()
        Haptics.success()
        dismiss()
    }
}

#Preview {
    GroupEditor(group: nil, mode: .business)
        .modelContainer(.preview)
}

import SwiftData
import SwiftUI

/// Manage relationship types.
///
/// The built-in list is a starting point, not a taxonomy: a realtor thinks in
/// "Past Buyer" and a coach in "Nutrition Client". Anything here can be renamed,
/// re-symbolled, or deleted — built-ins included. A starter list you can't clear
/// out isn't a starting point, it's a permanent argument with the app.
struct RelationshipTypeEditor: View {

    @Binding var mode: ContextMode
    /// Opens straight into the new-type row, for callers who arrived by tapping
    /// "New Type" somewhere else.
    var startsCreating = false

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \RelationshipTag.sortOrder) private var allTags: [RelationshipTag]

    @State private var isCreating = false
    @State private var draftName = ""
    @State private var draftSymbolName = "tag"
    @State private var draftKind: TagKind = .business
    @State private var duplicateWarning: String?
    @State private var isShowingSymbolPicker = false
    @State private var editingTag: RelationshipTag?
    @State private var pendingDeletion: RelationshipTag?

    @FocusState private var isDraftNameFocused: Bool

    private var kind: TagKind { TagKind(mode: mode) }

    private var tags: [RelationshipTag] {
        allTags.filter { $0.kind == kind }
    }

    private var trimmedDraftName: String {
        draftName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isConfirmingDelete: Binding<Bool> {
        Binding(
            get: { pendingDeletion != nil },
            set: { if !$0 { pendingDeletion = nil } }
        )
    }

    /// The presenting screen supplies the `NavigationStack`, so this view only
    /// contributes its own bar contents.
    var body: some View {
        List {
            if isCreating {
                creatorSection
            }

            Section {
                ForEach(tags) { tag in
                    row(for: tag)
                }
            } header: {
                Text("\(kind.title) Types")
            } footer: {
                Text("Tap a type to rename it or change its symbol. Swipe to delete — including the ones Keepr started you with. Deleting a type never deletes anyone.")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Relationship Types")
        .navigationBarTitleDisplayMode(.inline)
        .contextSwitcher($mode)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    startCreating()
                } label: {
                    Label("New Type", systemImage: "plus")
                }
                .disabled(isCreating)

                Button("Done") { dismiss() }
                    .fontWeight(.semibold)
            }
        }
        .sheet(isPresented: $isShowingSymbolPicker) {
            SymbolPicker(selection: $draftSymbolName)
        }
        .sheet(item: $editingTag) { tag in
            RelationshipTypeDetailEditor(tag: tag, existingTags: allTags)
        }
        .confirmationDialog(
            "Delete \(pendingDeletion?.name ?? "Type")?",
            isPresented: isConfirmingDelete,
            titleVisibility: .visible
        ) {
            Button("Delete Type", role: .destructive) {
                if let pendingDeletion {
                    delete(pendingDeletion)
                }
            }
        } message: {
            Text(deleteMessage(for: pendingDeletion))
        }
        .onChange(of: draftName) { _, _ in duplicateWarning = nil }
        .onChange(of: draftKind) { _, _ in duplicateWarning = nil }
        .onAppear {
            if startsCreating, !isCreating { startCreating() }
        }
    }

    // MARK: - Creator

    private var creatorSection: some View {
        Section {
            TextField("Name", text: $draftName)
                .focused($isDraftNameFocused)
                .onAppear { isDraftNameFocused = true }

            Button {
                isShowingSymbolPicker = true
            } label: {
                HStack {
                    Text("Symbol")
                        .foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: draftSymbolName)
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
            .accessibilityValue(SymbolPicker.readableName(for: draftSymbolName))
            .accessibilityHint("Opens the symbol picker")

            Picker("Kind", selection: $draftKind) {
                ForEach(TagKind.allCases) { option in
                    Text(option.title).tag(option)
                }
            }
            .pickerStyle(.segmented)

            if let duplicateWarning {
                Label(duplicateWarning, systemImage: "exclamationmark.circle")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }

            HStack {
                Button("Cancel") { cancelCreating() }
                Spacer()
                Button("Add", action: create)
                    .fontWeight(.semibold)
                    .disabled(trimmedDraftName.isEmpty)
            }
            .buttonStyle(.borderless)
        } header: {
            Text("New Type")
        }
    }

    // MARK: - Rows

    private func row(for tag: RelationshipTag) -> some View {
        Button {
            editingTag = tag
        } label: {
            HStack(spacing: Theme.Spacing.medium) {
                Image(systemName: tag.symbolName)
                    .foregroundStyle(Color.accentColor)
                    .frame(width: 26)

                Text(tag.name)
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer(minLength: Theme.Spacing.small)

                if tag.isBuiltIn {
                    Text("Built-in")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, Theme.Spacing.small)
                        .padding(.vertical, 3)
                        .background(.quaternary, in: .rect(cornerRadius: Theme.Radius.chip))
                }
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        // Full swipe is off: deletion asks first, and a gesture that ends in a
        // dialog shouldn't feel like it already happened.
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                pendingDeletion = tag
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    private func deleteMessage(for tag: RelationshipTag?) -> String {
        switch tag?.peopleList.count ?? 0 {
        case 0: "No one is marked with this type."
        case 1: "One person is marked with this type. They stay in Keepr — only the type goes."
        case let count: "\(count) people are marked with this type. They stay in Keepr — only the type goes."
        }
    }

    // MARK: - Actions

    private func startCreating() {
        draftName = ""
        draftSymbolName = "tag"
        draftKind = kind
        duplicateWarning = nil
        withAnimation { isCreating = true }
    }

    private func cancelCreating() {
        isDraftNameFocused = false
        duplicateWarning = nil
        withAnimation { isCreating = false }
    }

    private func create() {
        let name = trimmedDraftName
        guard !name.isEmpty else { return }

        guard !relationshipTagNameIsTaken(name, kind: draftKind, among: allTags) else {
            duplicateWarning = "\(draftKind.title) already has a type called \"\(name)\"."
            return
        }

        context.insert(
            RelationshipTag(
                name: name,
                kind: draftKind,
                sortOrder: (allTags.map(\.sortOrder).max() ?? 0) + 10,
                symbolName: draftSymbolName
            )
        )
        try? context.save()
        Haptics.success()

        // Follow the new type into its list, so it isn't created into a screen
        // the user isn't looking at.
        if draftKind != kind {
            mode = draftKind == .business ? .business : .personal
        }

        isDraftNameFocused = false
        withAnimation { isCreating = false }
    }

    private func delete(_ tag: RelationshipTag) {
        // Person.tags is a nullify relationship, so this unmarks people rather
        // than removing them. Seeding remembers the built-in was offered, so a
        // deleted one doesn't reappear on the next launch.
        withAnimation { context.delete(tag) }
        try? context.save()
        pendingDeletion = nil
        Haptics.success()
    }
}

// MARK: - Rename

/// Rename and re-symbol one type.
///
/// Built-ins are editable here too — someone who calls a Lead a "Prospect"
/// should be able to say so. The cost is that Keepr's quick filters look tags up
/// by their seeded names, so a renamed built-in drops out of them; that's a
/// fair trade for a taxonomy the user actually recognizes.
private struct RelationshipTypeDetailEditor: View {

    let tag: RelationshipTag
    /// Passed in rather than re-queried so the uniqueness check sees exactly the
    /// same set the list did.
    let existingTags: [RelationshipTag]

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var symbolName = "tag"
    @State private var isShowingSymbolPicker = false
    @State private var duplicateWarning: String?
    @State private var hasLoaded = false

    @FocusState private var isNameFocused: Bool

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name)
                        .focused($isNameFocused)

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

                    if let duplicateWarning {
                        Label(duplicateWarning, systemImage: "exclamationmark.circle")
                            .font(.footnote)
                            .foregroundStyle(.orange)
                    }
                } footer: {
                    if tag.isBuiltIn {
                        Text("One of the types Keepr started you with. Rename it, re-symbol it, or delete it from the list — it's yours.")
                    }
                }
            }
            .navigationTitle(tag.kind.title + " Type")
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
            .onChange(of: name) { _, _ in duplicateWarning = nil }
            .onAppear(perform: load)
        }
        .presentationDragIndicator(.visible)
    }

    private func load() {
        guard !hasLoaded else { return }
        hasLoaded = true
        name = tag.name
        symbolName = tag.symbolName
    }

    private func save() {
        guard !trimmedName.isEmpty else { return }

        guard !relationshipTagNameIsTaken(
            trimmedName,
            kind: tag.kind,
            among: existingTags,
            excluding: tag
        ) else {
            duplicateWarning = "\(tag.kind.title) already has a type called \"\(trimmedName)\"."
            return
        }

        tag.name = trimmedName
        tag.symbolName = symbolName
        try? context.save()
        Haptics.success()
        dismiss()
    }
}

// MARK: - Uniqueness

/// Trimmed and case-insensitive within a kind: two "Prospect"s would be
/// indistinguishable in every picker in the app, and impossible to tell apart
/// once they're on someone's profile.
private func relationshipTagNameIsTaken(
    _ name: String,
    kind: TagKind,
    among tags: [RelationshipTag],
    excluding excluded: RelationshipTag? = nil
) -> Bool {
    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
    return tags.contains { tag in
        tag.kind == kind
            && tag.id != excluded?.id
            && tag.name
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .localizedCaseInsensitiveCompare(trimmed) == .orderedSame
    }
}

#Preview {
    NavigationStack {
        RelationshipTypeEditor(mode: .constant(.business))
    }
    .modelContainer(.preview)
}

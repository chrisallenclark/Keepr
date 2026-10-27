import SwiftData
import SwiftUI

/// A write-up as a card that opens where it sits.
///
/// Closed, it's a title, a date and two lines — enough to recognise without
/// reading. Open, it's the whole thing. Expanding in place rather than pushing
/// a new screen is deliberate: these get skimmed in sequence ("which one was
/// the pricing conversation?"), and a push loses your place every time.
struct NoteCard: View {

    let note: ConversationNote
    /// Hidden on the profile, where space is tight and the full list is a tap
    /// away; shown in the notes list itself.
    var showsActions: Bool = true

    @Environment(\.modelContext) private var context

    @State private var isExpanded = false
    @State private var isEditing = false
    @State private var isPullingFacts = false
    @State private var isConfirmingDelete = false

    var body: some View {
        FloatingCard(isHighlighted: note.isPinned) {
            VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                header

                if isExpanded {
                    expandedBody
                } else if !note.preview.isEmpty {
                    Text(note.preview)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
        .contentShape(.rect)
        .onTapGesture {
            withAnimation(.snappy(duration: 0.26)) { isExpanded.toggle() }
            Haptics.light()
        }
        .accessibilityElement(children: .contain)
        .accessibilityHint(isExpanded ? "Collapses this note" : "Expands this note")
        .sheet(isPresented: $isEditing) {
            if let person = note.person {
                NoteComposerView(person: person, existing: note)
            }
        }
        .sheet(isPresented: $isPullingFacts) {
            if let person = note.person {
                BrainDumpView(person: person, initialText: note.body)
            }
        }
        .confirmationDialog(
            "Delete this note?",
            isPresented: $isConfirmingDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive, action: delete)
        } message: {
            Text("The write-up and its screenshots are removed. Anything you've already pulled into What You Know stays.")
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.small) {
            Image(systemName: note.isPinned ? "pin.fill" : note.source.symbolName)
                .font(.caption)
                .foregroundStyle(note.isPinned ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                .frame(width: 16)

            VStack(alignment: .leading, spacing: 2) {
                Text(note.title)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.leading)

                Text("\(note.occurredAt.formatted(date: .abbreviated, time: .omitted)) · \(note.lengthLabel)")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

            Spacer(minLength: Theme.Spacing.tight)

            DisclosureChevron(isExpanded: isExpanded)
        }
    }

    // MARK: - Expanded

    private var expandedBody: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            Divider()

            if note.hasBody {
                // Selectable so a line can be copied straight back out — these
                // came from somewhere else and often need to go somewhere else.
                Text(note.body)
                    .font(.subheadline)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if !note.fileList.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: Theme.Spacing.small) {
                        ForEach(note.fileList) { file in
                            PersonFileThumbnail(file: file, size: 72)
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }

            if showsActions {
                actions
            }
        }
    }

    private var actions: some View {
        HStack(spacing: Theme.Spacing.medium) {
            if note.hasBody {
                cardButton("Pull Facts", systemImage: "brain") {
                    isPullingFacts = true
                }
            }
            cardButton(
                note.isPinned ? "Unpin" : "Pin",
                systemImage: note.isPinned ? "pin.slash" : "pin"
            ) {
                togglePin()
            }
            cardButton("Edit", systemImage: "pencil") { isEditing = true }

            Spacer(minLength: 0)

            Button {
                isConfirmingDelete = true
            } label: {
                Image(systemName: "trash")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Delete note")
        }
    }

    private func cardButton(
        _ title: String,
        systemImage: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.caption.weight(.medium))
                .labelStyle(.titleAndIcon)
        }
        .buttonStyle(.plain)
        .foregroundStyle(.tint)
    }

    // MARK: - Actions

    private func togglePin() {
        withAnimation {
            note.isPinned.toggle()
            note.touch()
        }
        try? context.save()
        Haptics.selection()
    }

    private func delete() {
        withAnimation { context.delete(note) }
        try? context.save()
    }
}

/// Every write-up on one person, newest first with pinned ones leading.
struct NotesListView: View {

    let person: Person

    @State private var query = ""
    @State private var isAdding = false

    private var notes: [ConversationNote] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return person.noteList }
        return person.noteList.filter {
            $0.searchableText.localizedStandardContains(trimmed)
        }
    }

    var body: some View {
        List {
            ForEach(notes) { note in
                NoteCard(note: note)
                    .floatingCardRow()
            }
        }
        .listStyle(.insetGrouped)
        .scrollDismissesKeyboard(.immediately)
        .searchable(text: $query, prompt: "Search these notes")
        .navigationTitle("Notes")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    isAdding = true
                } label: {
                    Label("Add Note", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $isAdding) {
            NoteComposerView(person: person)
        }
        .overlay {
            if notes.isEmpty {
                if query.isEmpty {
                    ContentUnavailableView {
                        Label("No notes yet", systemImage: "doc.text")
                    } description: {
                        Text("Paste a consultation summary or meeting notes and they'll live here.")
                    } actions: {
                        Button("Add Note") { isAdding = true }
                            .buttonStyle(.borderedProminent)
                    }
                } else {
                    ContentUnavailableView.search(text: query)
                }
            }
        }
    }
}

import PhotosUI
import SwiftData
import SwiftUI
import UIKit

/// Put a conversation write-up on someone's profile without retyping it.
///
/// Built for text that already exists somewhere else — a recorder's summary, a
/// consultation sheet, meeting notes from another app. One tap pastes the
/// clipboard, or screenshots come in as images for summaries that only exist as
/// pictures. The title is guessed from the text and shown in a field, so the
/// common case is paste, glance, save.
struct NoteComposerView: View {

    let person: Person
    /// Passed when editing an existing note; nil creates a new one.
    var existing: ConversationNote?

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var body_ = ""
    @State private var occurredAt = Date()
    @State private var source: NoteSource = .pasted
    @State private var alsoLogInteraction = false
    @State private var interactionKind: InteractionKind = .meeting

    /// Images chosen for this note, held until save so cancelling leaves
    /// nothing behind.
    @State private var pendingImages: [PersonFilePayload] = []
    @State private var pickedPhotos: [PhotosPickerItem] = []
    @State private var isPickingPhotos = false
    @State private var isImporting = false

    /// Once the user edits the title themselves, the suggestion stops
    /// overwriting it — the app's guess never beats a decision.
    @State private var titleIsMine = false

    @FocusState private var isWritingBody: Bool

    private var isEditing: Bool { existing != nil }

    private var trimmedBody: String {
        body_.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        !trimmedBody.isEmpty || !pendingImages.isEmpty || !(existing?.fileList.isEmpty ?? true)
    }

    var body: some View {
        NavigationStack {
            Form {
                bodySection
                imagesSection
                detailsSection
                if !isEditing {
                    logSection
                }
            }
            .navigationTitle(isEditing ? "Edit Note" : "Add Note")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(!canSave || isImporting)
                }
            }
            .photosPicker(
                isPresented: $isPickingPhotos,
                selection: $pickedPhotos,
                maxSelectionCount: 10,
                matching: .images
            )
            .onChange(of: pickedPhotos) { _, items in
                guard !items.isEmpty else { return }
                loadPhotos(items)
            }
            .onAppear(perform: loadExisting)
        }
    }

    // MARK: - The write-up

    private var bodySection: some View {
        Section {
            if body_.isEmpty, UIPasteboard.general.hasStrings {
                Button(action: pasteFromClipboard) {
                    Label("Paste from Clipboard", systemImage: "doc.on.clipboard")
                        .font(.subheadline.weight(.medium))
                }
            }

            TextEditor(text: $body_)
                .focused($isWritingBody)
                .font(.subheadline)
                .frame(minHeight: 200)
                .scrollContentBackground(.hidden)
                .overlay(alignment: .topLeading) {
                    if body_.isEmpty {
                        Text("Paste the summary, or type what was said.")
                            .font(.subheadline)
                            .foregroundStyle(.tertiary)
                            .padding(.top, 8)
                            .allowsHitTesting(false)
                    }
                }
                .onChange(of: body_) { _, _ in refreshSuggestedTitle() }
        } header: {
            Text("Notes")
        } footer: {
            Text("Kept word for word. Nothing is rewritten or uploaded.")
        }
    }

    // MARK: - Screenshots

    @ViewBuilder
    private var imagesSection: some View {
        Section {
            if let existing, !existing.fileList.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: Theme.Spacing.small) {
                        ForEach(existing.fileList) { file in
                            PersonFileThumbnail(file: file)
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }

            if !pendingImages.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: Theme.Spacing.small) {
                        ForEach(Array(pendingImages.enumerated()), id: \.offset) { index, payload in
                            PendingImageThumbnail(payload: payload) {
                                pendingImages.remove(at: index)
                            }
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }

            Button {
                pickedPhotos = []
                isPickingPhotos = true
            } label: {
                Label(
                    pendingImages.isEmpty ? "Add Screenshots" : "Add More",
                    systemImage: "photo.on.rectangle.angled"
                )
                .font(.subheadline)
            }
            .disabled(isImporting)
        } header: {
            Text("Screenshots")
        } footer: {
            Text("For summaries that only exist as a picture. They live inside this note, not loose in Files.")
        }
    }

    // MARK: - Title, date, source

    private var detailsSection: some View {
        Section {
            TextField("Title", text: $title)
                .onChange(of: title) { _, _ in
                    // Only a real edit counts; the suggestion writes to `title`
                    // too, and that must not look like the user typing.
                    if !isApplyingSuggestion { titleIsMine = true }
                }

            DatePicker("When", selection: $occurredAt, displayedComponents: .date)

            Picker("Where from", selection: $source) {
                ForEach(NoteSource.allCases) { option in
                    Label(option.title, systemImage: option.symbolName).tag(option)
                }
            }
        } header: {
            Text("Details")
        } footer: {
            Text(titleIsMine
                 ? "Your title."
                 : "Suggested from the text — type over it any time.")
        }
    }

    // MARK: - Also log it

    private var logSection: some View {
        Section {
            Toggle("Also log this as an interaction", isOn: $alsoLogInteraction)
            if alsoLogInteraction {
                Picker("Type", selection: $interactionKind) {
                    ForEach(InteractionKind.allCases) { kind in
                        Label(kind.title, systemImage: kind.symbolName).tag(kind)
                    }
                }
            }
        } footer: {
            Text("Puts it on their timeline and resets their rhythm, so adding notes counts as having talked.")
        }
    }

    // MARK: - Suggestion

    /// Guards the title's `onChange` while the suggestion is being written.
    @State private var isApplyingSuggestion = false

    private func refreshSuggestedTitle() {
        guard !titleIsMine else { return }

        let suggestion = trimmedBody.isEmpty
            ? (pendingImages.isEmpty ? "" : NoteTitler.imageOnlyTitle(on: occurredAt))
            : NoteTitler.suggest(from: trimmedBody, on: occurredAt)

        guard suggestion != title else { return }
        isApplyingSuggestion = true
        title = suggestion
        isApplyingSuggestion = false
    }

    // MARK: - Loading

    private func loadExisting() {
        guard let existing, title.isEmpty, body_.isEmpty else { return }
        isApplyingSuggestion = true
        title = existing.title
        isApplyingSuggestion = false
        titleIsMine = true
        body_ = existing.body
        occurredAt = existing.occurredAt
        source = existing.source
    }

    private func pasteFromClipboard() {
        guard let pasted = UIPasteboard.general.string, !pasted.isEmpty else { return }
        body_ = pasted
        source = .pasted
        refreshSuggestedTitle()
        Haptics.light()
    }

    private func loadPhotos(_ items: [PhotosPickerItem]) {
        isImporting = true
        Task { @MainActor in
            for item in items {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      let payload = PersonFileImporter.photo(from: data) else { continue }
                pendingImages.append(payload)
            }
            pickedPhotos = []
            isImporting = false
            if trimmedBody.isEmpty { source = .screenshot }
            refreshSuggestedTitle()
        }
    }

    // MARK: - Saving

    private func save() {
        let finalTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedTitle = finalTitle.isEmpty
            ? NoteTitler.imageOnlyTitle(on: occurredAt)
            : finalTitle

        let note: ConversationNote
        if let existing {
            existing.title = resolvedTitle
            existing.body = trimmedBody
            existing.occurredAt = occurredAt
            existing.source = source
            existing.touch()
            note = existing
        } else {
            note = ConversationNote(
                title: resolvedTitle,
                body: trimmedBody,
                source: source,
                occurredAt: occurredAt,
                person: person
            )
            context.insert(note)
        }

        for payload in pendingImages {
            let file = PersonFile(
                title: resolvedTitle,
                kind: payload.kind,
                fileExtension: payload.fileExtension,
                data: payload.data,
                thumbnailData: payload.thumbnail,
                byteCount: payload.byteCount,
                capturedAt: occurredAt,
                person: person,
                conversationNote: note
            )
            context.insert(file)
        }

        if alsoLogInteraction, existing == nil {
            let interaction = Interaction(
                kind: interactionKind,
                occurredAt: occurredAt,
                title: resolvedTitle,
                person: person
            )
            context.insert(interaction)
            note.sourceInteraction = interaction

            if (person.lastInteractionAt ?? .distantPast) < occurredAt {
                person.lastInteractionAt = occurredAt
            }
            Outreach.markReplied(person, at: occurredAt)
        }

        person.touch()
        try? context.save()
        Haptics.success()
        dismiss()
    }
}

/// A screenshot chosen but not yet saved, with a way to take it back out.
private struct PendingImageThumbnail: View {
    let payload: PersonFilePayload
    let onRemove: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            if let data = payload.thumbnail, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 64, height: 64)
                    .clipShape(.rect(cornerRadius: Theme.Radius.chip))
            } else {
                Image(systemName: "photo")
                    .frame(width: 64, height: 64)
                    .background(.quaternary, in: .rect(cornerRadius: Theme.Radius.chip))
            }

            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.body)
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, .black.opacity(0.55))
            }
            .buttonStyle(.plain)
            .padding(2)
            .accessibilityLabel("Remove screenshot")
        }
    }
}

#Preview {
    NoteComposerView(person: Person(givenName: "Jake", familyName: "Mathers"))
        .modelContainer(.preview)
}

import PhotosUI
import QuickLook
import SwiftData
import SwiftUI
import UniformTypeIdentifiers

/// Everything on file for one person.
///
/// Built around the consultation case: photograph their results, name it, and
/// have the stack read chronologically a year later. Newest first, because the
/// question is almost always "what did the last one say".
struct AttachmentsView: View {

    let person: Person

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var pickedPhotos: [PhotosPickerItem] = []
    @State private var isPickingPhotos = false
    @State private var isImportingFile = false
    @State private var isTakingPhoto = false
    @State private var isImporting = false
    @State private var importError: String?
    @State private var quickLookURL: URL?
    @State private var renaming: Attachment?

    var body: some View {
        List {
            if person.attachmentList.isEmpty {
                Section {
                    ContentUnavailableView {
                        Label("No files yet", systemImage: "folder")
                    } description: {
                        Text("Photograph a consultation sheet, keep a signed form, or save their plan. Stays on your device.")
                    }
                    .listRowBackground(Color.clear)
                }
            }

            ForEach(person.attachmentList) { attachment in
                AttachmentRow(attachment: attachment)
                    .contentShape(.rect)
                    .onTapGesture { open(attachment) }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            delete(attachment)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        Button {
                            renaming = attachment
                        } label: {
                            Label("Rename", systemImage: "pencil")
                        }
                        .tint(.accentColor)
                    }
                    .contextMenu {
                        Button {
                            renaming = attachment
                        } label: {
                            Label("Rename & Date", systemImage: "pencil")
                        }
                        if let url = exportURL(for: attachment) {
                            ShareLink(item: url) {
                                Label("Share", systemImage: "square.and.arrow.up")
                            }
                        }
                        Button(role: .destructive) {
                            delete(attachment)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
            }

            if !person.attachmentList.isEmpty {
                Section {
                    Text("Files are stored on this device only, inside Keepr. Nothing is uploaded.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Files")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                addMenu
            }
        }
        .photosPicker(
            isPresented: $isPickingPhotos,
            selection: $pickedPhotos,
            maxSelectionCount: 10,
            matching: .images
        )
        .fileImporter(
            isPresented: $isImportingFile,
            allowedContentTypes: [.item],
            allowsMultipleSelection: true,
            onCompletion: handleFileImport
        )
        .fullScreenCover(isPresented: $isTakingPhoto) {
            CameraCaptureView { image in
                addPhoto(image)
            }
            .ignoresSafeArea()
        }
        .onChange(of: pickedPhotos) { _, items in
            guard !items.isEmpty else { return }
            addPickedPhotos(items)
        }
        .quickLookPreview($quickLookURL)
        .sheet(item: $renaming) { attachment in
            AttachmentDetailsEditor(attachment: attachment)
        }
        .overlay {
            if isImporting {
                ProgressView("Adding…")
                    .padding(Theme.Spacing.large)
                    .background(.regularMaterial, in: .rect(cornerRadius: Theme.Radius.card))
            }
        }
        .alert("Couldn't add that", isPresented: errorBinding) {
            Button("OK", role: .cancel) { importError = nil }
        } message: {
            Text(importError ?? "")
        }
    }

    private var addMenu: some View {
        Menu {
            Button {
                isTakingPhoto = true
            } label: {
                Label("Take Photo", systemImage: "camera")
            }
            Button {
                pickedPhotos = []
                isPickingPhotos = true
            } label: {
                Label("Choose Photos", systemImage: "photo.on.rectangle")
            }
            Button {
                isImportingFile = true
            } label: {
                Label("Choose File", systemImage: "folder")
            }
        } label: {
            Label("Add", systemImage: "plus")
        }
    }

    // MARK: - Bindings

    private var errorBinding: Binding<Bool> {
        Binding(get: { importError != nil }, set: { if !$0 { importError = nil } })
    }

    // MARK: - Adding

    private func addPickedPhotos(_ items: [PhotosPickerItem]) {
        isImporting = true
        Task { @MainActor in
            var failures = 0
            for item in items {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      let payload = AttachmentImporter.photo(from: data) else {
                    failures += 1
                    continue
                }
                insert(payload)
            }
            pickedPhotos = []
            isImporting = false
            if failures > 0 {
                importError = failures == 1
                    ? "One photo couldn't be read."
                    : "\(failures) photos couldn't be read."
            } else {
                Haptics.success()
            }
        }
    }

    private func addPhoto(_ image: UIImage) {
        guard let payload = AttachmentImporter.photo(from: image) else {
            importError = "That photo couldn't be saved."
            return
        }
        insert(payload)
        Haptics.success()
    }

    private func handleFileImport(_ result: Result<[URL], Error>) {
        guard case let .success(urls) = result else {
            importError = "That file couldn't be opened."
            return
        }

        var failures = 0
        for url in urls {
            // Files picked outside the app arrive security-scoped; without this
            // the read fails silently on a file the user clearly chose.
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }

            guard let data = try? Data(contentsOf: url) else {
                failures += 1
                continue
            }
            var payload = AttachmentImporter.file(
                from: data,
                fileExtension: url.pathExtension
            )
            if payload.fileExtension == "dat", !url.pathExtension.isEmpty {
                payload.fileExtension = url.pathExtension.lowercased()
            }
            insert(payload, titledAfter: url.deletingPathExtension().lastPathComponent)
        }

        if failures > 0 {
            importError = "Some files couldn't be read."
        } else {
            Haptics.success()
        }
    }

    /// Inserts one prepared file. Titles default to kind and date; a file picked
    /// out of Files keeps its own name, which is usually already meaningful.
    private func insert(_ payload: AttachmentPayload, titledAfter name: String? = nil) {
        let existing = person.attachmentList.map(\.title)
        let title: String = {
            if let name, !name.trimmingCharacters(in: .whitespaces).isEmpty {
                return name
            }
            return AttachmentImporter.defaultTitle(
                for: payload.kind,
                existingTitles: existing
            )
        }()

        let attachment = Attachment(
            title: title,
            kind: payload.kind,
            fileExtension: payload.fileExtension,
            data: payload.data,
            thumbnailData: payload.thumbnail,
            byteCount: payload.byteCount,
            person: person
        )
        context.insert(attachment)
        person.touch()
        try? context.save()
    }

    // MARK: - Opening & removing

    /// Writes the bytes to a temporary file so Quick Look can show it. Nothing
    /// readable is left behind beyond the app's own sandbox.
    private func exportURL(for attachment: Attachment) -> URL? {
        guard let data = attachment.data else { return nil }

        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("KeeprPreview", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        let url = directory.appendingPathComponent(attachment.suggestedFileName)
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    private func open(_ attachment: Attachment) {
        guard let url = exportURL(for: attachment) else {
            importError = "That file is missing its contents."
            return
        }
        quickLookURL = url
    }

    private func delete(_ attachment: Attachment) {
        context.delete(attachment)
        person.touch()
        try? context.save()
        Haptics.light()
    }
}

// MARK: - Row

/// One file: its preview, what it's called, and when it's from.
struct AttachmentRow: View {
    let attachment: Attachment

    var body: some View {
        HStack(spacing: Theme.Spacing.medium) {
            preview

            VStack(alignment: .leading, spacing: 2) {
                Text(attachment.title)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)

                Text(attachment.capturedAt.formatted(date: .abbreviated, time: .omitted))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let note = attachment.note, !note.isEmpty {
                    Text(note)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }

            Spacer(minLength: Theme.Spacing.small)

            Text(attachment.sizeLabel)
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
    }

    @ViewBuilder
    private var preview: some View {
        if let data = attachment.thumbnailData, let image = UIImage(data: data) {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: 44, height: 44)
                .clipShape(.rect(cornerRadius: Theme.Radius.chip))
        } else {
            Image(systemName: attachment.kind.symbolName)
                .font(.title3)
                .foregroundStyle(.secondary)
                .frame(width: 44, height: 44)
                .background(.quaternary, in: .rect(cornerRadius: Theme.Radius.chip))
        }
    }
}

// MARK: - Rename & date

/// Naming and dating a file is the whole difference between a folder of photos
/// and a record you can read. Both are editable, and the date defaults to today
/// rather than pretending to know when the photo was taken.
struct AttachmentDetailsEditor: View {

    @Bindable var attachment: Attachment

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("What this is", text: $attachment.title)
                }
                Section {
                    DatePicker(
                        "Date",
                        selection: $attachment.capturedAt,
                        displayedComponents: .date
                    )
                } footer: {
                    Text("When this is from — the day of the consultation, not the day you added it.")
                }
                Section("Note") {
                    TextField(
                        "Anything worth remembering about it",
                        text: Binding(
                            get: { attachment.note ?? "" },
                            set: { attachment.note = $0.isEmpty ? nil : $0 }
                        ),
                        axis: .vertical
                    )
                }
            }
            .navigationTitle("File Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        try? context.save()
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }
}

// MARK: - Camera

/// The system camera, just enough wrapped to hand back one image.
///
/// Deliberately not a custom capture UI: people already know this one, and a
/// photo of a printed sheet needs no controls beyond the shutter.
struct CameraCaptureView: UIViewControllerRepresentable {

    let onCapture: (UIImage) -> Void

    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera)
            ? .camera
            : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture, onFinish: { dismiss() })
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let onCapture: (UIImage) -> Void
        private let onFinish: () -> Void

        init(onCapture: @escaping (UIImage) -> Void, onFinish: @escaping () -> Void) {
            self.onCapture = onCapture
            self.onFinish = onFinish
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage {
                onCapture(image)
            }
            onFinish()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onFinish()
        }
    }
}

// MARK: - Thumbnail

/// The square preview used on the profile strip. Falls back to the file-kind
/// icon rather than an empty box, so a PDF without a renderable first page
/// still reads as a document.
struct AttachmentThumbnail: View {
    let attachment: Attachment

    var body: some View {
        Group {
            if let data = attachment.thumbnailData, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                Image(systemName: attachment.kind.symbolName)
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.quaternary)
            }
        }
        .frame(width: 56, height: 56)
        .clipShape(.rect(cornerRadius: Theme.Radius.chip))
    }
}

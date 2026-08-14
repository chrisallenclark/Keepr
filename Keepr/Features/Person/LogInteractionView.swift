import SwiftData
import SwiftUI

/// Recording what just happened, in as few taps as possible.
///
/// Kind and date are prefilled, the note field is focused on appear, and Save is
/// reachable without scrolling. Everything else is optional.
struct LogInteractionView: View {

    let person: Person
    /// Prefills from a quick capture that has already been parsed.
    var draft: CaptureDraft?

    @Environment(\.modelContext) private var context
    @Environment(\.notificationService) private var notificationService
    @Environment(\.dismiss) private var dismiss
    @AppStorage(PreferenceKey.remindersEnabled) private var remindersEnabled = true

    @State private var kind: InteractionKind = .inPerson
    @State private var occurredAt = Date()
    @State private var note = ""
    @State private var summary = ""
    @State private var memoryDrafts: [MemoryDraft] = []
    @State private var newMemory = ""
    @State private var wantsFollowUp = false
    @State private var followUpTitle = ""
    @State private var followUpDate = FollowUpEngine.DuePreset.nextWeek.date()
    @State private var hasLoadedDraft = false

    @FocusState private var isNoteFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Type", selection: $kind) {
                        ForEach(InteractionKind.allCases) { kind in
                            Label(kind.title, systemImage: kind.symbolName).tag(kind)
                        }
                    }
                    DatePicker("When", selection: $occurredAt, in: ...Date())
                }

                Section("What happened") {
                    TextField(
                        "Talked about…",
                        text: $note,
                        axis: .vertical
                    )
                    .lineLimit(3...8)
                    .focused($isNoteFocused)
                }

                Section {
                    ForEach($memoryDrafts) { $draft in
                        Toggle(isOn: $draft.isSelected) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(draft.content)
                                    .font(.subheadline)
                                Text(draft.category.title)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }

                    HStack {
                        TextField("Something to remember", text: $newMemory)
                            .font(.subheadline)
                            .onSubmit(addMemory)
                        if !newMemory.trimmingCharacters(in: .whitespaces).isEmpty {
                            Button("Add", action: addMemory)
                                .font(.subheadline.weight(.semibold))
                        }
                    }
                } header: {
                    Text("Remember")
                } footer: {
                    Text("Facts saved here show up at the top of \(person.displayName)'s profile.")
                }

                Section {
                    Toggle("Add a follow-up", isOn: $wantsFollowUp.animation())

                    if wantsFollowUp {
                        TextField("What do you need to do?", text: $followUpTitle)
                        DatePicker("Due", selection: $followUpDate, displayedComponents: .date)
                    }
                }
            }
            .navigationTitle("Log Interaction")
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
            .onAppear(perform: loadDraft)
        }
        .presentationDragIndicator(.visible)
    }

    private var canSave: Bool {
        !note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !memoryDrafts.filter(\.isSelected).isEmpty
    }

    // MARK: - Actions

    private func loadDraft() {
        guard !hasLoadedDraft else { return }
        hasLoadedDraft = true

        if let draft {
            kind = draft.kind == .other ? .inPerson : draft.kind
            occurredAt = draft.occurredAt
            summary = draft.summary
            memoryDrafts = draft.memories
            if let followUp = draft.followUp {
                wantsFollowUp = true
                followUpTitle = followUp.title
                followUpDate = followUp.dueDate
            }
        } else {
            isNoteFocused = true
        }
    }

    private func addMemory() {
        let content = newMemory.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty else { return }
        memoryDrafts.append(MemoryDraft(content: content))
        newMemory = ""
        Haptics.light()
    }

    private func save() {
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)

        let interaction = Interaction(
            kind: kind,
            occurredAt: occurredAt,
            rawNote: trimmedNote.isEmpty ? nil : trimmedNote,
            summary: summary.isEmpty ? nil : summary,
            person: person
        )
        context.insert(interaction)

        for draft in memoryDrafts where draft.isSelected {
            context.insert(
                Memory(
                    content: draft.content,
                    category: draft.category,
                    importance: draft.importance,
                    person: person,
                    sourceInteraction: interaction
                )
            )
        }

        let trimmedFollowUp = followUpTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        if wantsFollowUp, !trimmedFollowUp.isEmpty {
            let followUp = FollowUp(
                title: trimmedFollowUp,
                dueDate: Calendar.current.startOfDay(for: followUpDate),
                person: person,
                sourceInteraction: interaction
            )
            context.insert(followUp)
            FollowUpScheduler.sync(
                followUp,
                using: notificationService,
                remindersEnabled: remindersEnabled
            )
        }

        if (person.lastInteractionAt ?? .distantPast) < occurredAt {
            person.lastInteractionAt = occurredAt
        }
        person.touch()
        try? context.save()

        Haptics.success()
        dismiss()
    }
}

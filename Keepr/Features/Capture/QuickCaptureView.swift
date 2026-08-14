import SwiftData
import SwiftUI

/// Get it out of your head in seconds; sort it out second.
///
/// Step one is a single text field and nothing else. Step two shows what the
/// on-device extractor made of it — every suggestion is a toggle, and nothing is
/// saved until you confirm.
struct QuickCaptureView: View {

    let mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.captureExtractor) private var extractor
    @Environment(\.notificationService) private var notificationService
    @Environment(\.dismiss) private var dismiss
    @AppStorage(PreferenceKey.remindersEnabled) private var remindersEnabled = true

    @Query private var people: [Person]

    private enum Step { case capture, review }

    @State private var step: Step = .capture
    @State private var text = ""
    @State private var draft = CaptureDraft()
    @State private var selectedPerson: Person?
    @State private var newPersonName = ""
    @State private var isPickingPerson = false
    @State private var isExtracting = false

    @FocusState private var isEditorFocused: Bool

    var body: some View {
        NavigationStack {
            Group {
                switch step {
                case .capture: captureStep
                case .review: reviewStep
                }
            }
            .navigationTitle(step == .capture ? "Quick Capture" : "Review")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(step == .capture ? "Cancel" : "Back") {
                        if step == .capture {
                            dismiss()
                        } else {
                            withAnimation { step = .capture }
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    switch step {
                    case .capture:
                        Button("Next", action: runExtraction)
                            .fontWeight(.semibold)
                            .disabled(trimmedText.isEmpty || isExtracting)
                    case .review:
                        Button("Save", action: save)
                            .fontWeight(.semibold)
                            .disabled(!canSave)
                    }
                }
            }
            .sheet(isPresented: $isPickingPerson) {
                PersonPicker(
                    mode: mode,
                    suggestedName: draft.suggestedName,
                    selection: $selectedPerson,
                    newPersonName: $newPersonName
                )
            }
        }
    }

    private var trimmedText: String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Step 1

    private var captureStep: some View {
        VStack(alignment: .leading, spacing: 0) {
            TextEditor(text: $text)
                .font(.body)
                .focused($isEditorFocused)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, Theme.Spacing.medium)
                .padding(.top, Theme.Spacing.small)
                .overlay(alignment: .topLeading) {
                    if text.isEmpty {
                        Text("Talked to Jake at the gym. He owns a roofing company, wants to lose 20 lb, and said to text him next week.")
                            .font(.body)
                            .foregroundStyle(.tertiary)
                            .padding(.horizontal, Theme.Spacing.medium + 5)
                            .padding(.top, Theme.Spacing.small + 8)
                            .allowsHitTesting(false)
                    }
                }

            Text("Stays on your device.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.horizontal, Theme.Spacing.large)
                .padding(.bottom, Theme.Spacing.medium)
        }
        .onAppear { isEditorFocused = true }
    }

    // MARK: - Step 2

    private var reviewStep: some View {
        Form {
            Section("Who") {
                Button {
                    isPickingPerson = true
                } label: {
                    HStack {
                        if let selectedPerson {
                            Avatar(person: selectedPerson, size: .small)
                            Text(selectedPerson.displayName)
                                .foregroundStyle(.primary)
                        } else if !newPersonName.isEmpty {
                            Image(systemName: "person.crop.circle.badge.plus")
                                .foregroundStyle(.secondary)
                            Text("New: \(newPersonName)")
                                .foregroundStyle(.primary)
                        } else {
                            Image(systemName: "person.crop.circle.badge.questionmark")
                                .foregroundStyle(.secondary)
                            Text("Choose someone")
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }

            Section("Interaction") {
                Picker("Type", selection: $draft.kind) {
                    ForEach(InteractionKind.allCases) { kind in
                        Label(kind.title, systemImage: kind.symbolName).tag(kind)
                    }
                }
                DatePicker("When", selection: $draft.occurredAt, in: ...Date())
            }

            if !draft.memories.isEmpty {
                Section {
                    ForEach($draft.memories) { $memory in
                        Toggle(isOn: $memory.isSelected) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(memory.content)
                                    .font(.subheadline)
                                Text(memory.category.title)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                } header: {
                    Text("Remember")
                } footer: {
                    Text("Turn off anything that isn't worth keeping.")
                }
            }

            if draft.followUp != nil {
                Section("Follow Up") {
                    Toggle("Add this follow-up", isOn: followUpSelection)
                    if let followUp = draft.followUp, followUp.isSelected {
                        TextField("What to do", text: followUpTitle)
                        DatePicker("Due", selection: followUpDate, displayedComponents: .date)
                    }
                }
            }

            Section("Note") {
                Text(trimmedText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Follow-up bindings

    private var followUpSelection: Binding<Bool> {
        Binding(
            get: { draft.followUp?.isSelected ?? false },
            set: { draft.followUp?.isSelected = $0 }
        )
    }

    private var followUpTitle: Binding<String> {
        Binding(
            get: { draft.followUp?.title ?? "" },
            set: { draft.followUp?.title = $0 }
        )
    }

    private var followUpDate: Binding<Date> {
        Binding(
            get: { draft.followUp?.dueDate ?? Date() },
            set: { draft.followUp?.dueDate = $0 }
        )
    }

    // MARK: - Actions

    private var canSave: Bool {
        selectedPerson != nil || !newPersonName.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func runExtraction() {
        isExtracting = true
        let input = trimmedText
        let service = extractor

        Task { @MainActor in
            let result = await service.extract(from: input, now: Date())
            draft = result
            matchSuggestedPerson(result.suggestedName)
            isExtracting = false
            withAnimation { step = .review }
        }
    }

    /// Preselects an existing person when the name in the note matches one,
    /// otherwise offers to create them. Never creates anything on its own.
    private func matchSuggestedPerson(_ name: String?) {
        guard let name, !name.isEmpty else { return }

        let candidates = PeopleEngine.visible(people, in: mode)
        if let match = candidates.first(where: {
            $0.displayName.localizedCaseInsensitiveCompare(name) == .orderedSame
                || $0.fullName.localizedCaseInsensitiveCompare(name) == .orderedSame
                || $0.givenName.localizedCaseInsensitiveCompare(name) == .orderedSame
        }) {
            selectedPerson = match
        } else {
            newPersonName = name
        }
    }

    private func save() {
        guard let person = selectedPerson ?? createPerson() else { return }

        let interaction = Interaction(
            kind: draft.kind,
            occurredAt: draft.occurredAt,
            rawNote: trimmedText,
            summary: draft.summary.isEmpty ? nil : draft.summary,
            isQuickCapture: true,
            person: person
        )
        context.insert(interaction)

        for memory in draft.memories where memory.isSelected {
            context.insert(
                Memory(
                    content: memory.content,
                    category: memory.category,
                    importance: memory.importance,
                    person: person,
                    sourceInteraction: interaction
                )
            )
        }

        if let followUpDraft = draft.followUp, followUpDraft.isSelected,
           !followUpDraft.title.trimmingCharacters(in: .whitespaces).isEmpty {
            let followUp = FollowUp(
                title: followUpDraft.title,
                dueDate: Calendar.current.startOfDay(for: followUpDraft.dueDate),
                priority: followUpDraft.priority,
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

        if (person.lastInteractionAt ?? .distantPast) < draft.occurredAt {
            person.lastInteractionAt = draft.occurredAt
        }
        person.touch()
        try? context.save()

        Haptics.success()
        dismiss()
    }

    private func createPerson() -> Person? {
        let name = newPersonName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return nil }

        let parts = name.split(separator: " ").map(String.init)
        let person = Person(
            givenName: parts.first ?? name,
            familyName: parts.count > 1 ? parts.dropFirst().joined(separator: " ") : "",
            context: RelationshipContext(mode: mode)
        )
        context.insert(person)
        return person
    }
}

#Preview {
    QuickCaptureView(mode: .business)
        .modelContainer(.preview)
}

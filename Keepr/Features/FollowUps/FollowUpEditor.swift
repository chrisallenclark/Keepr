import SwiftData
import SwiftUI

/// Create or edit a follow-up. Presets first, because "next week" is what people
/// actually mean; the date picker is there when it isn't.
struct FollowUpEditor: View {

    let person: Person
    /// nil creates a new follow-up.
    var followUp: FollowUp?

    @Environment(\.modelContext) private var context
    @Environment(\.notificationService) private var notificationService
    @Environment(\.dismiss) private var dismiss
    @AppStorage(PreferenceKey.remindersEnabled) private var remindersEnabled = true

    @State private var title = ""
    @State private var note = ""
    @State private var dueDate = FollowUpEngine.DuePreset.nextWeek.date()
    @State private var hasTime = false
    @State private var priority: Priority = .normal
    @State private var hasLoaded = false

    @FocusState private var isTitleFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("What do you need to do?", text: $title, axis: .vertical)
                        .lineLimit(1...3)
                        .focused($isTitleFocused)
                    TextField("Note (optional)", text: $note, axis: .vertical)
                        .lineLimit(1...4)
                        .font(.subheadline)
                }

                Section("When") {
                    if followUp == nil {
                        presetRow
                    }
                    DatePicker(
                        "Due",
                        selection: $dueDate,
                        displayedComponents: hasTime ? [.date, .hourAndMinute] : [.date]
                    )
                    Toggle("Set a time", isOn: $hasTime.animation())
                }

                Section {
                    Picker("Priority", selection: $priority) {
                        ForEach(Priority.allCases) { priority in
                            Text(priority.title).tag(priority)
                        }
                    }
                    .pickerStyle(.segmented)
                } footer: {
                    if remindersEnabled {
                        Text("You'll get one reminder — nothing repeats.")
                    } else {
                        Text("Reminders are off, so this won't notify you. Turn them on in Settings.")
                    }
                }
            }
            .navigationTitle(followUp == nil ? "New Follow-Up" : "Edit Follow-Up")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear(perform: load)
        }
        .presentationDragIndicator(.visible)
    }

    private var presetRow: some View {
        ScrollView(.horizontal) {
            HStack(spacing: Theme.Spacing.small) {
                ForEach(FollowUpEngine.DuePreset.allCases) { preset in
                    let date = preset.date()
                    Button(preset.title) {
                        withAnimation {
                            dueDate = date
                            hasTime = false
                        }
                        Haptics.selection()
                    }
                    .buttonStyle(.bordered)
                    .tint(Calendar.current.isDate(dueDate, inSameDayAs: date) ? .accentColor : .secondary)
                    .font(.subheadline)
                }
            }
            .padding(.vertical, Theme.Spacing.tight)
        }
        .scrollIndicators(.hidden)
        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 0))
    }

    // MARK: - Actions

    private func load() {
        guard !hasLoaded else { return }
        hasLoaded = true

        guard let followUp else {
            isTitleFocused = true
            return
        }
        title = followUp.title
        note = followUp.note ?? ""
        dueDate = followUp.dueDate
        hasTime = followUp.hasTime
        priority = followUp.priority
    }

    private func save() {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedDate = hasTime ? dueDate : Calendar.current.startOfDay(for: dueDate)

        let target: FollowUp
        if let followUp {
            followUp.title = trimmedTitle
            followUp.note = trimmedNote.isEmpty ? nil : trimmedNote
            followUp.dueDate = resolvedDate
            followUp.hasTime = hasTime
            followUp.priority = priority
            target = followUp
        } else {
            let created = FollowUp(
                title: trimmedTitle,
                note: trimmedNote.isEmpty ? nil : trimmedNote,
                dueDate: resolvedDate,
                hasTime: hasTime,
                priority: priority,
                person: person
            )
            context.insert(created)
            target = created
        }

        person.touch()
        try? context.save()

        FollowUpScheduler.sync(
            target,
            using: notificationService,
            remindersEnabled: remindersEnabled
        )

        Haptics.success()
        dismiss()
    }
}

import SwiftData
import SwiftUI

/// Every promise you've made, bucketed by when it's due.
struct FollowUpsView: View {

    @Binding var mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.notificationService) private var notificationService
    @AppStorage(PreferenceKey.remindersEnabled) private var remindersEnabled = true

    @Query(sort: \FollowUp.dueDate) private var allFollowUps: [FollowUp]

    @State private var showsCompleted = false
    @State private var selectedPerson: Person?
    @State private var editingFollowUp: FollowUp?

    private var sections: [FollowUpSection] {
        let visible = FollowUpEngine.visible(allFollowUps, in: mode)
        return FollowUpEngine.grouped(visible)
            .filter { showsCompleted || $0.bucket != .completed }
    }

    var body: some View {
        NavigationStack {
            Group {
                if sections.isEmpty {
                    ContentUnavailableView {
                        Label("Nothing to follow up on", systemImage: "bell.slash")
                    } description: {
                        Text("Add a follow-up from anyone's profile and it'll show up here.")
                    }
                } else {
                    list
                }
            }
            .navigationTitle("Follow Up")
            .contextSwitcher($mode)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Toggle("Show Completed", isOn: $showsCompleted)
                    } label: {
                        Label("Options", systemImage: "ellipsis.circle")
                    }
                }
            }
            .navigationDestination(item: $selectedPerson) { person in
                PersonProfileView(person: person, mode: $mode)
            }
            .sheet(item: $editingFollowUp) { followUp in
                if let person = followUp.person {
                    FollowUpEditor(person: person, followUp: followUp)
                }
            }
        }
    }

    private var list: some View {
        List {
            ForEach(sections) { section in
                Section(section.title) {
                    ForEach(section.items) { followUp in
                        FollowUpRow(followUp: followUp) {
                            toggle(followUp)
                        } onOpenPerson: {
                            selectedPerson = followUp.person
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button {
                                toggle(followUp)
                            } label: {
                                Label(
                                    followUp.isCompleted ? "Reopen" : "Done",
                                    systemImage: followUp.isCompleted ? "arrow.uturn.backward" : "checkmark"
                                )
                            }
                            .tint(followUp.isCompleted ? .gray : .green)

                            Button(role: .destructive) {
                                delete(followUp)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                        .swipeActions(edge: .leading) {
                            if !followUp.isCompleted {
                                Button {
                                    snooze(followUp, days: 1)
                                } label: {
                                    Label("Tomorrow", systemImage: "clock")
                                }
                                .tint(.orange)

                                Button {
                                    snooze(followUp, days: 7)
                                } label: {
                                    Label("Next Week", systemImage: "calendar")
                                }
                                .tint(.blue)
                            }
                        }
                        .contextMenu {
                            Button {
                                editingFollowUp = followUp
                            } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            if let person = followUp.person {
                                Button {
                                    selectedPerson = person
                                } label: {
                                    Label("Open \(person.displayName)", systemImage: "person")
                                }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - Actions

    private func toggle(_ followUp: FollowUp) {
        withAnimation {
            if followUp.isCompleted {
                followUp.reopen()
                FollowUpScheduler.sync(
                    followUp,
                    using: notificationService,
                    remindersEnabled: remindersEnabled
                )
            } else {
                followUp.complete()
                FollowUpScheduler.cancel(followUp, using: notificationService)
            }
        }
        try? context.save()
        Haptics.success()
    }

    private func snooze(_ followUp: FollowUp, days: Int) {
        withAnimation { followUp.snooze(byDays: days) }
        FollowUpScheduler.sync(
            followUp,
            using: notificationService,
            remindersEnabled: remindersEnabled
        )
        try? context.save()
        Haptics.light()
    }

    private func delete(_ followUp: FollowUp) {
        FollowUpScheduler.cancel(followUp, using: notificationService)
        withAnimation { context.delete(followUp) }
        try? context.save()
    }
}

#Preview {
    FollowUpsView(mode: .constant(.business))
        .modelContainer(.preview)
}

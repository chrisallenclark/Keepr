import SwiftData
import SwiftUI

/// What needs attention, right now, in the current context.
///
/// The screen is allowed to be nearly empty. When there's nothing to do it says
/// so and stops — no streaks, no badges, no invented urgency.
struct TodayView: View {

    @Binding var mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.notificationService) private var notificationService
    @AppStorage(PreferenceKey.remindersEnabled) private var remindersEnabled = true

    @Query(sort: \Person.updatedAt, order: .reverse) private var people: [Person]

    @State private var isShowingCapture = false
    @State private var isShowingSettings = false
    @State private var selectedPerson: Person?

    var body: some View {
        NavigationStack {
            let digest = TodayEngine.digest(people: people, mode: mode)

            Group {
                if digest.isEmpty {
                    caughtUp
                } else {
                    List {
                        followUpSection("Needs Attention", digest.needsAttention)
                        followUpSection("Upcoming", digest.upcoming)
                        goingQuietSection(digest.goingQuiet)
                        recentSection(digest.recent)
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle(greeting)
            .navigationBarTitleDisplayMode(.inline)
            .contextSwitcher($mode)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        isShowingSettings = true
                    } label: {
                        Label("Settings", systemImage: "gearshape")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isShowingCapture = true
                    } label: {
                        Label("Quick Capture", systemImage: "square.and.pencil")
                    }
                }
            }
            .navigationDestination(item: $selectedPerson) { person in
                PersonProfileView(person: person, mode: $mode)
            }
            .sheet(isPresented: $isShowingCapture) {
                QuickCaptureView(mode: mode)
            }
            .sheet(isPresented: $isShowingSettings) {
                SettingsView()
            }
        }
    }

    // MARK: - Sections

    private var caughtUp: some View {
        ContentUnavailableView {
            Label("You're caught up.", systemImage: "checkmark.circle")
        } description: {
            Text(
                mode == .business
                    ? "Nothing needs your attention in Business right now."
                    : "Nothing needs your attention in Personal right now."
            )
        } actions: {
            Button("Quick Capture") { isShowingCapture = true }
                .buttonStyle(.bordered)
        }
    }

    @ViewBuilder
    private func followUpSection(_ title: String, _ followUps: [FollowUp]) -> some View {
        if !followUps.isEmpty {
            Section(title) {
                ForEach(followUps) { followUp in
                    FollowUpRow(followUp: followUp) {
                        complete(followUp)
                    } onOpenPerson: {
                        selectedPerson = followUp.person
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        Button {
                            complete(followUp)
                        } label: {
                            Label("Done", systemImage: "checkmark")
                        }
                        .tint(.green)
                    }
                    .swipeActions(edge: .leading) {
                        Button {
                            snooze(followUp, days: 1)
                        } label: {
                            Label("Tomorrow", systemImage: "clock")
                        }
                        .tint(.orange)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func goingQuietSection(_ quiet: [Person]) -> some View {
        if !quiet.isEmpty {
            Section {
                ForEach(quiet) { person in
                    Button {
                        selectedPerson = person
                    } label: {
                        PersonCompactRow(
                            person: person,
                            detail: RelativeDate.lastContact(person.lastInteractionAt)
                        )
                    }
                    .buttonStyle(.plain)
                }
            } header: {
                Text("Going Quiet")
            } footer: {
                Text("No interaction logged in a while, and nothing planned.")
            }
        }
    }

    @ViewBuilder
    private func recentSection(_ recent: [Person]) -> some View {
        if !recent.isEmpty {
            Section("Recent") {
                ForEach(recent) { person in
                    Button {
                        selectedPerson = person
                    } label: {
                        PersonCompactRow(
                            person: person,
                            detail: RelativeDate.lastContact(person.lastInteractionAt)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Actions

    private var greeting: String {
        switch Calendar.current.component(.hour, from: Date()) {
        case 0..<12: "Good Morning"
        case 12..<17: "Good Afternoon"
        default: "Good Evening"
        }
    }

    private func complete(_ followUp: FollowUp) {
        withAnimation {
            followUp.complete()
        }
        FollowUpScheduler.cancel(followUp, using: notificationService)
        try? context.save()
        Haptics.success()
    }

    private func snooze(_ followUp: FollowUp, days: Int) {
        withAnimation {
            followUp.snooze(byDays: days)
        }
        FollowUpScheduler.sync(
            followUp,
            using: notificationService,
            remindersEnabled: remindersEnabled
        )
        try? context.save()
        Haptics.light()
    }
}

// MARK: - Rows

/// A follow-up row: who it's about, what was promised, when it's due.
struct FollowUpRow: View {
    let followUp: FollowUp
    var showsPerson = true
    let onComplete: () -> Void
    var onOpenPerson: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.medium) {
            Button(action: onComplete) {
                Image(systemName: followUp.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(followUp.isCompleted ? Color.accentColor : Color.secondary)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(followUp.isCompleted ? "Mark not done" : "Mark done")

            Button {
                onOpenPerson?()
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    if showsPerson, let person = followUp.person {
                        Text(person.displayName)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                    }

                    Text(followUp.title)
                        .font(.subheadline)
                        .foregroundStyle(followUp.isCompleted ? .tertiary : .secondary)
                        .strikethrough(followUp.isCompleted, color: .secondary)
                        .multilineTextAlignment(.leading)

                    HStack(spacing: Theme.Spacing.tight) {
                        if followUp.priority == .high, !followUp.isCompleted {
                            Image(systemName: "exclamationmark")
                                .foregroundStyle(.orange)
                        }
                        Text(dueText)
                            .foregroundStyle(followUp.isOverdue() ? Color.orange : Color.secondary)
                    }
                    .font(.caption)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .disabled(onOpenPerson == nil)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    private var dueText: String {
        if followUp.isCompleted {
            return "Completed"
        }
        let due = RelativeDate.dueWithTime(followUp)
        return followUp.isOverdue() ? "Overdue · \(due)" : due
    }
}

/// A single line for a person inside a list.
struct PersonCompactRow: View {
    let person: Person
    var detail: String?

    var body: some View {
        HStack(spacing: Theme.Spacing.medium) {
            Avatar(person: person, size: .small)

            VStack(alignment: .leading, spacing: 1) {
                Text(person.displayName)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)
                if let detail {
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer(minLength: Theme.Spacing.small)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    TodayView(mode: .constant(.business))
        .modelContainer(.preview)
}

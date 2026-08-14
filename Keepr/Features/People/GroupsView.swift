import SwiftData
import SwiftUI

/// The groups in the current context, and who's in them.
struct GroupsView: View {

    @Binding var mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \PersonGroup.sortOrder) private var allGroups: [PersonGroup]

    @State private var isCreating = false
    @State private var editingGroup: PersonGroup?
    @State private var openedGroup: PersonGroup?
    @State private var pendingDeletion: PersonGroup?

    private var groups: [PersonGroup] {
        allGroups.filter { $0.matches(mode) }
    }

    /// `confirmationDialog` wants a Bool; the group being deleted is the real
    /// state, so the flag is derived from it.
    private var isConfirmingDelete: Binding<Bool> {
        Binding(
            get: { pendingDeletion != nil },
            set: { if !$0 { pendingDeletion = nil } }
        )
    }

    /// The presenting screen supplies the `NavigationStack`, so this view only
    /// contributes its own bar contents.
    var body: some View {
        Group {
            if groups.isEmpty {
                emptyState
            } else {
                list
            }
        }
        .navigationTitle("Groups")
        .navigationBarTitleDisplayMode(.inline)
        .contextSwitcher($mode)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    isCreating = true
                } label: {
                    Label("New Group", systemImage: "plus")
                }

                Button("Done") { dismiss() }
                    .fontWeight(.semibold)
            }
        }
        .navigationDestination(item: $openedGroup) { group in
            GroupMembersView(group: group)
        }
        .sheet(isPresented: $isCreating) {
            GroupEditor(group: nil, mode: mode)
        }
        .sheet(item: $editingGroup) { group in
            GroupEditor(group: group, mode: mode)
        }
        .confirmationDialog(
            "Delete \(pendingDeletion?.name ?? "Group")?",
            isPresented: isConfirmingDelete,
            titleVisibility: .visible
        ) {
            Button("Delete Group", role: .destructive) {
                if let pendingDeletion {
                    delete(pendingDeletion)
                }
            }
        } message: {
            Text(deleteMessage(for: pendingDeletion))
        }
    }

    // MARK: - List

    private var list: some View {
        List {
            ForEach(groups) { group in
                Button {
                    openedGroup = group
                } label: {
                    GroupRow(group: group)
                }
                .buttonStyle(.plain)
                .swipeActions(edge: .leading) {
                    Button {
                        editingGroup = group
                    } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                    .tint(.blue)
                }
                // Full swipe is off: deletion asks first, and a gesture that
                // ends in a dialog shouldn't feel like it already happened.
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        pendingDeletion = group
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    @ViewBuilder
    private var emptyState: some View {
        if allGroups.isEmpty {
            ContentUnavailableView {
                Label("No groups yet", systemImage: "person.3")
            } description: {
                Text("A group is where people came from — the gym you train at, people you met at a conference, everyone from a dating app.")
            } actions: {
                Button("Create a Group") { isCreating = true }
                    .buttonStyle(.borderedProminent)
            }
        } else {
            ContentUnavailableView {
                Label("No \(mode.title.lowercased()) groups", systemImage: mode.symbolName)
            } description: {
                Text("Your other groups are in \(mode.other.title).")
            } actions: {
                Button("Create a Group") { isCreating = true }
                    .buttonStyle(.bordered)
            }
        }
    }

    private func deleteMessage(for group: PersonGroup?) -> String {
        switch group?.memberCount ?? 0 {
        case 0: "Nothing else changes — no one is in this group."
        case 1: "The person in it stays in Keepr — only the grouping is removed."
        case let count: "The \(count) people in it stay in Keepr — only the grouping is removed."
        }
    }

    // MARK: - Actions

    private func delete(_ group: PersonGroup) {
        withAnimation { context.delete(group) }
        try? context.save()
        pendingDeletion = nil
        Haptics.success()
    }
}

// MARK: - Row

private struct GroupRow: View {
    let group: PersonGroup

    var body: some View {
        HStack(spacing: Theme.Spacing.medium) {
            Image(systemName: group.symbolName)
                .font(.title3)
                .foregroundStyle(Color.accentColor)
                .frame(width: 30)

            VStack(alignment: .leading, spacing: 3) {
                Text(group.name)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                if let detail = group.detail, !detail.isEmpty {
                    Text(detail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: Theme.Spacing.small)

            Text(memberCountLabel(group.memberCount))
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, Theme.Spacing.tight)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Members

/// Who's in one group. Rows are inert until member navigation is wired up, so
/// they're plain content rather than buttons that look tappable and aren't.
private struct GroupMembersView: View {
    let group: PersonGroup

    private var members: [Person] {
        group.memberList.sorted { $0.sortKey < $1.sortKey }
    }

    var body: some View {
        Group {
            if members.isEmpty {
                ContentUnavailableView {
                    Label("No one in \(group.name) yet", systemImage: group.symbolName)
                } description: {
                    Text("Add someone to this group from their profile.")
                }
            } else {
                List(members) { person in
                    HStack(spacing: Theme.Spacing.medium) {
                        Avatar(person: person, size: .small)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(person.displayName)
                                .font(.body)
                                .lineLimit(1)

                            if let subtitle = person.subtitle {
                                Text(subtitle)
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                    .padding(.vertical, Theme.Spacing.tight)
                    .accessibilityElement(children: .combine)
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle(group.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Counting

/// "12 people" / "1 person" / "No one yet".
private func memberCountLabel(_ count: Int) -> String {
    switch count {
    case 0: "No one yet"
    case 1: "1 person"
    default: "\(count) people"
    }
}

#Preview {
    NavigationStack {
        GroupsView(mode: .constant(.business))
    }
    .modelContainer(.preview)
}

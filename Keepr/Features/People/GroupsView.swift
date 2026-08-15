import SwiftData
import SwiftUI

/// The groups in the current context, and who's in them.
struct GroupsView: View {

    @Binding var mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \PersonGroup.sortOrder) private var allGroups: [PersonGroup]
    @AppStorage(PreferenceKey.groupLabel) private var groupLabel = GroupVocabulary.default.singular

    /// The user's word for the second axis, in the shapes the copy needs.
    private var groupPlural: String { GroupVocabulary.plural(for: groupLabel) }
    private var article: String { GroupVocabulary.article(for: groupLabel) }

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
        .navigationTitle(groupPlural)
        .navigationBarTitleDisplayMode(.inline)
        .contextSwitcher($mode)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    isCreating = true
                } label: {
                    Label("New \(groupLabel)", systemImage: "plus")
                }

                Button("Done") { dismiss() }
                    .fontWeight(.semibold)
            }
        }
        .navigationDestination(item: $openedGroup) { group in
            GroupMembersView(group: group, mode: $mode)
        }
        .sheet(isPresented: $isCreating) {
            GroupEditor(group: nil, mode: mode)
        }
        .sheet(item: $editingGroup) { group in
            GroupEditor(group: group, mode: mode)
        }
        .confirmationDialog(
            "Delete \(pendingDeletion?.name ?? groupLabel)?",
            isPresented: isConfirmingDelete,
            titleVisibility: .visible
        ) {
            Button("Delete \(groupLabel)", role: .destructive) {
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
                Label("No \(groupPlural.lowercased()) yet", systemImage: "mappin.and.ellipse")
            } description: {
                Text("Life Time, your training company, a dating app, a bar — whatever a relationship comes through. One of these can hold clients and colleagues at the same time, and someone can be in several.")
            } actions: {
                Button("Create \(article) \(groupLabel)") { isCreating = true }
                    .buttonStyle(.borderedProminent)
            }
        } else {
            ContentUnavailableView {
                Label("No \(mode.title.lowercased()) \(groupPlural.lowercased())", systemImage: mode.symbolName)
            } description: {
                Text("Your other \(groupPlural.lowercased()) are in \(mode.other.title).")
            } actions: {
                Button("Create \(article) \(groupLabel)") { isCreating = true }
                    .buttonStyle(.bordered)
            }
        }
    }

    private func deleteMessage(for group: PersonGroup?) -> String {
        switch group?.memberCount ?? 0 {
        case 0: "Nothing else changes — no one is here."
        case 1: "The person there stays in Keepr — only the place is removed."
        case let count: "The \(count) people there stay in Keepr — only the place is removed."
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

/// Everyone at one place, split by what they are to you.
///
/// This is the payoff for keeping type and place apart: one gym holds the
/// clients you train there *and* the trainer you work alongside, and you can see
/// both at once without them being filed as the same thing.
private struct GroupMembersView: View {
    let group: PersonGroup
    @Binding var mode: ContextMode

    private var sections: [PlaceSection] {
        PeopleEngine.byType(group.memberList)
    }

    var body: some View {
        Group {
            if group.memberList.isEmpty {
                ContentUnavailableView {
                    Label("No one at \(group.name) yet", systemImage: group.symbolName)
                } description: {
                    Text("Add someone here from their profile, or select several people at once in People.")
                }
            } else {
                List {
                    ForEach(sections) { section in
                        Section {
                            ForEach(section.people) { person in
                                NavigationLink {
                                    PersonProfileView(person: person, mode: $mode)
                                } label: {
                                    PersonCompactRow(
                                        person: person,
                                        detail: person.subtitle ?? person.context.title
                                    )
                                }
                            }
                        } header: {
                            HStack {
                                Text(section.title)
                                Spacer()
                                Text("\(section.people.count)")
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
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

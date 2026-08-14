import SwiftData
import SwiftUI

/// Every relationship in the current context.
struct PeopleView: View {

    @Binding var mode: ContextMode

    @Environment(\.modelContext) private var context
    @AppStorage(PreferenceKey.peopleSort) private var sortRaw = PeopleSort.recent.rawValue

    @Query(sort: \Person.familyName) private var people: [Person]
    @Query(sort: \RelationshipTag.sortOrder) private var tags: [RelationshipTag]
    @Query(sort: \PersonGroup.sortOrder) private var groups: [PersonGroup]

    @State private var searchText = ""
    @State private var selectedTag: String?
    @State private var selectedGroupID: UUID?
    @State private var isShowingAddPerson = false
    @State private var isShowingContactImport = false
    @State private var isShowingGroups = false
    @State private var isShowingTypes = false
    @State private var isShowingReview = false
    @State private var selectedPerson: Person?

    // Multi-select
    @State private var editMode: EditMode = .inactive
    @State private var selection: Set<UUID> = []
    @State private var isConfirmingBulkDelete = false
    @State private var linkPair: LinkPair?

    private var sort: PeopleSort {
        get { PeopleSort(rawValue: sortRaw) ?? .recent }
        nonmutating set { sortRaw = newValue.rawValue }
    }

    private var activeGroup: PersonGroup? {
        guard let selectedGroupID else { return nil }
        return groups.first { $0.id == selectedGroupID }
    }

    private var filtered: [Person] {
        var matching = PeopleEngine.filter(
            people,
            mode: mode,
            tagName: selectedTag,
            query: searchText
        )
        if let activeGroup {
            matching = matching.filter { $0.isMember(of: activeGroup) }
        }
        return PeopleEngine.sort(matching, by: sort)
    }

    /// The people behind the current selection, resolved once per action.
    private var selectedPeople: [Person] {
        people.filter { selection.contains($0.id) }
    }

    /// People already in Keepr with no relationship type on them.
    private var uncategorizedCount: Int {
        people.filter(ReviewQueue.needsCategorizing).count
    }

    /// Exactly two selected, in list order, and not already linked.
    private var selectedPair: LinkPair? {
        let chosen = selectedPeople
        guard chosen.count == 2, !chosen[0].isLinked(to: chosen[1]) else { return nil }
        return LinkPair(first: chosen[0], second: chosen[1])
    }

    var body: some View {
        NavigationStack {
            Group {
                if filtered.isEmpty {
                    emptyState
                } else if sort == .name {
                    sectionedList
                } else {
                    flatList
                }
            }
            .environment(\.editMode, $editMode)
            .navigationTitle(editMode.isEditing ? selectionTitle : "People")
            .contextSwitcher($mode)
            .searchable(text: $searchText, prompt: "Search \(mode.title.lowercased()) contacts")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { filterMenu }
                ToolbarItem(placement: .topBarTrailing) {
                    if editMode.isEditing {
                        Button("Done") { endSelecting() }
                            .fontWeight(.semibold)
                    } else {
                        addMenu
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if editMode.isEditing {
                    bulkActionBar
                }
            }
            .navigationDestination(item: $selectedPerson) { person in
                PersonProfileView(person: person, mode: $mode)
            }
            .onAppear {
                if LaunchOptions.screen == .profile, selectedPerson == nil {
                    selectedPerson = filtered.first
                }
            }
            .sheet(isPresented: $isShowingAddPerson) {
                EditPersonView(person: nil, mode: mode)
            }
            .sheet(isPresented: $isShowingContactImport) {
                ContactImportView(mode: mode)
            }
            .sheet(isPresented: $isShowingGroups) {
                NavigationStack { GroupsView(mode: $mode) }
            }
            .sheet(isPresented: $isShowingTypes) {
                NavigationStack { RelationshipTypeEditor(mode: $mode) }
            }
            .sheet(isPresented: $isShowingReview) {
                ReviewView(mode: mode)
            }
            .sheet(item: $linkPair) { pair in
                PersonLinkEditor(person: pair.first, other: pair.second) {
                    endSelecting()
                }
            }
            .confirmationDialog(
                "Delete \(selection.count) \(selection.count == 1 ? "person" : "people")?",
                isPresented: $isConfirmingBulkDelete,
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) { bulkDelete() }
            } message: {
                Text("This removes everything recorded about them. Their contact cards are not affected.")
            }
        }
    }

    private var selectionTitle: String {
        selection.isEmpty ? "Select People" : "\(selection.count) Selected"
    }

    // MARK: - Lists

    private var flatList: some View {
        List(selection: $selection) {
            if selectedTag != nil || activeGroup != nil {
                activeFilterRow
            }
            ForEach(filtered) { person in
                row(for: person)
            }
        }
        .listStyle(.plain)
    }

    private var sectionedList: some View {
        List(selection: $selection) {
            if selectedTag != nil || activeGroup != nil {
                activeFilterRow
            }
            ForEach(PeopleEngine.alphabeticalSections(filtered)) { section in
                Section(section.key) {
                    ForEach(section.people) { person in
                        row(for: person)
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    /// Rows stay tappable-to-open normally; in edit mode the list takes over and
    /// the same row becomes a checkbox, which is the standard iOS behaviour.
    private func row(for person: Person) -> some View {
        Button {
            selectedPerson = person
        } label: {
            PersonRow(person: person, mode: mode)
        }
        .buttonStyle(.plain)
        .tag(person.id)
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                toggleFavorite(person)
            } label: {
                Label(
                    person.isFavorite ? "Unfavorite" : "Favorite",
                    systemImage: person.isFavorite ? "star.slash" : "star"
                )
            }
            .tint(.yellow)
        }
        .contextMenu {
            personContextMenu(person)
        }
    }

    @ViewBuilder
    private var activeFilterRow: some View {
        HStack {
            if let selectedTag {
                Label(selectedTag, systemImage: "line.3.horizontal.decrease")
                    .font(.subheadline)
            }
            if let activeGroup {
                Label(activeGroup.name, systemImage: activeGroup.symbolName)
                    .font(.subheadline)
            }
            Spacer()
            Button("Clear") {
                selectedTag = nil
                selectedGroupID = nil
            }
            .font(.subheadline)
        }
        .listRowSeparator(.hidden)
    }

    private var emptyState: some View {
        Group {
            if !searchText.isEmpty {
                ContentUnavailableView.search(text: searchText)
            } else if selectedTag != nil || activeGroup != nil {
                ContentUnavailableView {
                    Label("No one here yet", systemImage: "person.crop.circle.badge.questionmark")
                } description: {
                    Text("Add people to this group as relationships develop.")
                } actions: {
                    Button("Show Everyone") {
                        selectedTag = nil
                        selectedGroupID = nil
                    }
                    .buttonStyle(.bordered)
                }
            } else {
                ContentUnavailableView {
                    Label("No \(mode.title.lowercased()) relationships", systemImage: mode.symbolName)
                } description: {
                    Text("Import from your contacts, or add someone by hand.")
                } actions: {
                    VStack(spacing: Theme.Spacing.small) {
                        Button("Import from Contacts") { isShowingContactImport = true }
                            .buttonStyle(.borderedProminent)
                        Button("Add Someone") { isShowingAddPerson = true }
                    }
                }
            }
        }
    }

    // MARK: - Bulk actions

    /// Sits above the tab bar while selecting. One menu rather than a row of
    /// icons: the actions are all "apply X to these people", and a menu names
    /// them in words instead of asking anyone to decode glyphs.
    private var bulkActionBar: some View {
        HStack {
            Button("Select All") { selection = Set(filtered.map(\.id)) }
                .font(.subheadline)
                .disabled(selection.count == filtered.count)

            Spacer()

            Menu {
                bulkMenuContents
            } label: {
                Label("Categorize", systemImage: "square.and.pencil")
                    .font(.subheadline.weight(.semibold))
            }
            .disabled(selection.isEmpty)
        }
        .padding(.horizontal)
        .padding(.vertical, Theme.Spacing.small)
        .background(.bar)
    }

    @ViewBuilder
    private var bulkMenuContents: some View {
        // Two people selected is the one case that isn't "apply X to all of
        // them" — it's the only way to say these two are related to each other.
        if let pair = selectedPair {
            Button {
                linkPair = pair
            } label: {
                Label("Link These Two", systemImage: "link")
            }
            Divider()
        }

        let relevantTags = tags.filter { $0.kind == TagKind(mode: mode) }
        if !relevantTags.isEmpty {
            Menu("Add Relationship Type") {
                ForEach(relevantTags) { tag in
                    Button {
                        bulkAddTag(tag)
                    } label: {
                        Label(tag.name, systemImage: tag.symbolName)
                    }
                }
            }
            Menu("Remove Relationship Type") {
                ForEach(relevantTags) { tag in
                    Button {
                        bulkRemoveTag(tag)
                    } label: {
                        Label(tag.name, systemImage: tag.symbolName)
                    }
                }
            }
        }

        let relevantGroups = groups.filter { $0.matches(mode) }
        if !relevantGroups.isEmpty {
            Menu("Add to Group") {
                ForEach(relevantGroups) { group in
                    Button {
                        bulkAddToGroup(group)
                    } label: {
                        Label(group.name, systemImage: group.symbolName)
                    }
                }
            }
        }

        Divider()

        Menu("Move to") {
            ForEach(RelationshipContext.allCases) { option in
                Button {
                    bulkSetContext(option)
                } label: {
                    Label(option.title, systemImage: option.symbolName)
                }
            }
        }

        Menu("Priority") {
            ForEach(Priority.allCases) { option in
                Button(option.title) { bulkSetPriority(option) }
            }
        }

        Button {
            bulkSetFavorite(true)
        } label: {
            Label("Favorite", systemImage: "star")
        }
        Button {
            bulkSetFavorite(false)
        } label: {
            Label("Remove Favorite", systemImage: "star.slash")
        }

        Divider()

        Button {
            bulkArchive()
        } label: {
            Label("Archive", systemImage: "archivebox")
        }
        Button(role: .destructive) {
            isConfirmingBulkDelete = true
        } label: {
            Label("Delete", systemImage: "trash")
        }
    }

    // MARK: - Toolbar

    private var filterMenu: some View {
        Menu {
            Button {
                beginSelecting()
            } label: {
                Label("Select", systemImage: "checkmark.circle")
            }

            Divider()

            Picker("Sort", selection: Binding(get: { sort }, set: { sort = $0 })) {
                ForEach(PeopleSort.allCases) { option in
                    Label(option.title, systemImage: option.symbolName).tag(option)
                }
            }

            Divider()

            Picker(
                "Type",
                selection: Binding(
                    get: { selectedTag ?? "" },
                    set: { selectedTag = $0.isEmpty ? nil : $0 }
                )
            ) {
                Text("All Types").tag("")
                ForEach(tags.filter { $0.kind == TagKind(mode: mode) }) { tag in
                    Text(tag.name).tag(tag.name)
                }
            }

            let relevantGroups = groups.filter { $0.matches(mode) }
            if !relevantGroups.isEmpty {
                Menu("Group") {
                    Button("All Groups") { selectedGroupID = nil }
                    ForEach(relevantGroups) { group in
                        Button {
                            selectedGroupID = group.id
                        } label: {
                            Label(group.name, systemImage: group.symbolName)
                        }
                    }
                }
            }

            Divider()

            Button {
                isShowingGroups = true
            } label: {
                Label("Manage Groups", systemImage: "person.3")
            }
            Button {
                isShowingTypes = true
            } label: {
                Label("Manage Types", systemImage: "tag")
            }
        } label: {
            Label("Sort and Filter", systemImage: "line.3.horizontal.decrease.circle")
                .symbolVariant(selectedTag == nil && activeGroup == nil ? .none : .fill)
        }
    }

    private var addMenu: some View {
        Menu {
            Button {
                isShowingContactImport = true
            } label: {
                Label("Import from Contacts", systemImage: "person.crop.circle.badge.plus")
            }
            Button {
                isShowingReview = true
            } label: {
                Label(
                    uncategorizedCount > 0 ? "Catch Up (\(uncategorizedCount))" : "Catch Up",
                    systemImage: "person.crop.circle.badge.questionmark"
                )
            }
            Button {
                isShowingAddPerson = true
            } label: {
                Label("Add Manually", systemImage: "square.and.pencil")
            }
        } label: {
            Label("Add Person", systemImage: "plus")
        }
    }

    @ViewBuilder
    private func personContextMenu(_ person: Person) -> some View {
        ForEach(ContactMethod.allCases) { method in
            if let url = CommunicationLauncher.url(for: method, person: person) {
                Link(destination: url) {
                    Label(method.title, systemImage: method.symbolName)
                }
            }
        }
        Divider()
        Button {
            toggleFavorite(person)
        } label: {
            Label(
                person.isFavorite ? "Remove Favorite" : "Favorite",
                systemImage: person.isFavorite ? "star.slash" : "star"
            )
        }
        Button {
            beginSelecting(with: person)
        } label: {
            Label("Select…", systemImage: "checkmark.circle")
        }
    }

    // MARK: - Actions

    private func beginSelecting(with person: Person? = nil) {
        withAnimation {
            editMode = .active
            selection = person.map { [$0.id] } ?? []
        }
    }

    private func endSelecting() {
        withAnimation {
            editMode = .inactive
            selection = []
        }
    }

    private func toggleFavorite(_ person: Person) {
        withAnimation {
            person.isFavorite.toggle()
            person.touch()
        }
        try? context.save()
        Haptics.light()
    }

    /// Every bulk action funnels through here so saving, haptics and the
    /// updated-at bump can't be forgotten in one of eight near-identical methods.
    private func applyToSelection(
        endsSelection: Bool = false,
        _ change: (Person) -> Void
    ) {
        let targets = selectedPeople
        guard !targets.isEmpty else { return }

        withAnimation {
            for person in targets {
                change(person)
                person.touch()
            }
            if endsSelection {
                editMode = .inactive
                selection = []
            }
        }
        try? context.save()
        Haptics.success()
    }

    private func bulkAddTag(_ tag: RelationshipTag) {
        applyToSelection { person in
            guard !person.hasTag(named: tag.name) else { return }
            person.tags = person.tagList + [tag]
        }
    }

    private func bulkRemoveTag(_ tag: RelationshipTag) {
        applyToSelection { person in
            person.tags = person.tagList.filter { $0.id != tag.id }
        }
    }

    private func bulkAddToGroup(_ group: PersonGroup) {
        applyToSelection { person in
            guard !person.isMember(of: group) else { return }
            person.groups = person.groupList + [group]
        }
    }

    private func bulkSetContext(_ value: RelationshipContext) {
        applyToSelection { $0.context = value }
    }

    private func bulkSetPriority(_ value: Priority) {
        applyToSelection { $0.priority = value }
    }

    private func bulkSetFavorite(_ value: Bool) {
        applyToSelection { $0.isFavorite = value }
    }

    private func bulkArchive() {
        applyToSelection(endsSelection: true) { $0.status = .archived }
    }

    private func bulkDelete() {
        let targets = selectedPeople
        withAnimation {
            for person in targets {
                context.delete(person)
            }
            editMode = .inactive
            selection = []
        }
        try? context.save()
        Haptics.success()
    }
}

/// Two selected people on their way to the link editor. A struct rather than a
/// tuple because `sheet(item:)` needs identity.
struct LinkPair: Identifiable {
    let first: Person
    let second: Person

    var id: String { "\(first.id)-\(second.id)" }
}

// MARK: - Row

/// One person in the People list: who they are, how they're classified, and
/// when you last spoke.
struct PersonRow: View {
    let person: Person
    let mode: ContextMode

    var body: some View {
        HStack(spacing: Theme.Spacing.medium) {
            Avatar(person: person, size: .medium)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: Theme.Spacing.tight) {
                    Text(person.displayName)
                        .font(.body.weight(.medium))
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    if person.isFavorite {
                        Image(systemName: "star.fill")
                            .font(.caption2)
                            .foregroundStyle(.yellow)
                            .accessibilityLabel("Favorite")
                    }
                }

                if let subtitle = person.subtitle {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                TagRow(tags: person.headlineTags(for: mode))
            }

            Spacer(minLength: Theme.Spacing.small)

            VStack(alignment: .trailing, spacing: 3) {
                if let last = person.lastInteractionAt {
                    Text(RelativeDate.past(last))
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
                if let next = person.nextFollowUp {
                    Label(
                        RelativeDate.due(next.dueDate),
                        systemImage: "bell"
                    )
                    .font(.caption2)
                    .foregroundStyle(next.isOverdue() ? Color.orange : Color.secondary)
                    .labelStyle(.titleAndIcon)
                }
            }
        }
        .padding(.vertical, Theme.Spacing.tight)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    PeopleView(mode: .constant(.business))
        .modelContainer(.preview)
}

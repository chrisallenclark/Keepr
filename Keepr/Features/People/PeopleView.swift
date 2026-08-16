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
    @AppStorage(PreferenceKey.groupLabel) private var groupLabel = GroupVocabulary.default.singular

    /// The user's word for the second axis, in the shapes the copy needs.
    private var groupPlural: String { GroupVocabulary.plural(for: groupLabel) }
    private var article: String { GroupVocabulary.article(for: groupLabel) }

    @State private var searchText = ""
    @State private var selectedTag: String?
    /// A `FilterFacet` id: a group's UUID string, the unplaced sentinel, or nil.
    @State private var selectedPlaceID: String?
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

    private var place: PlaceFilter {
        PlaceFilter(facetID: selectedPlaceID)
    }

    private var filtered: [Person] {
        PeopleEngine.sort(
            PeopleEngine.filter(
                people,
                mode: mode,
                tagName: selectedTag,
                query: searchText,
                place: place
            ),
            by: sort
        )
    }

    /// Counts on each row reflect the *other* row's selection, so "Client 5"
    /// under Life Time means five clients at Life Time — not five clients total.
    private var typeFacets: [FilterFacet] {
        PeopleEngine.typeFacets(
            for: PeopleEngine.filter(people, mode: mode, query: searchText, place: place),
            tags: tags,
            mode: mode,
            keeping: selectedTag
        )
    }

    private var placeFacets: [FilterFacet] {
        PeopleEngine.placeFacets(
            for: PeopleEngine.filter(people, mode: mode, tagName: selectedTag, query: searchText),
            groups: groups,
            mode: mode,
            keeping: selectedPlaceID
        )
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
                // The filter chips live inside the list, so an empty result
                // still shows them — otherwise the one control that could undo
                // the filter disappears exactly when it's needed.
                if filtered.isEmpty, !hasActiveFilters {
                    emptyState
                } else if sort == .name {
                    sectionedList
                } else {
                    flatList
                }
            }
            .environment(\.editMode, $editMode)
            .navigationTitle(editMode.isEditing ? selectionTitle : "People")
            // Inline, like Today. A large title scrolls away under the pinned
            // context switcher, so the screen loses its name the moment you
            // scroll and only gets it back by pulling down — which reads as a
            // bug even though it's stock behaviour.
            .navigationBarTitleDisplayMode(.inline)
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
            filterBar
            noMatchesRow
            ForEach(filtered) { person in
                row(for: person)
            }
        }
        .listStyle(.plain)
    }

    private var sectionedList: some View {
        List(selection: $selection) {
            filterBar
            noMatchesRow
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

    /// A row opens the profile normally, and ticks the box while selecting.
    ///
    /// The row can't be a `Button` in edit mode: a button swallows the tap, so
    /// the list never sees it as selection and the only thing that works is the
    /// little circle. Handing the plain row to the list — no button, no swipes,
    /// no context menu — is what makes tapping anywhere on someone select them.
    @ViewBuilder
    private func row(for person: Person) -> some View {
        if editMode.isEditing {
            PersonRow(person: person, mode: mode)
                .contentShape(.rect)
                .tag(person.id)
        } else {
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
    }

    private var hasActiveFilters: Bool {
        selectedTag != nil || selectedPlaceID != nil
    }

    /// Two rows of chips — what someone is to you, and where the relationship
    /// lives. They cross: "Client" and "Life Time" together is the answer to
    /// "who do I train there", and "Life Time" alone still shows the trainers.
    ///
    /// Scrolls away with the list rather than pinning: it's a starting point,
    /// not a permanent control panel.
    @ViewBuilder
    private var filterBar: some View {
        // Hidden only while selecting, where every row is a checkbox and a chip
        // row would just be one more thing to accidentally tick.
        if !editMode.isEditing {
            VStack(alignment: .leading, spacing: 0) {
                if !typeFacets.isEmpty {
                    FilterChipRow(title: "Type", facets: typeFacets, selection: $selectedTag)
                }
                if !placeFacets.isEmpty {
                    FilterChipRow(title: groupLabel, facets: placeFacets, selection: $selectedPlaceID)
                } else {
                    addAPlaceRow
                }
            }
            .listRowInsets(EdgeInsets(top: 2, leading: 0, bottom: 2, trailing: 0))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    /// Shown until the first place exists, because "Place" is the half of this
    /// screen nobody discovers on their own.
    private var addAPlaceRow: some View {
        Button {
            isShowingGroups = true
        } label: {
            Label("Add \(article) \(groupLabel.lowercased()) — a gym, one of your businesses, an app", systemImage: "mappin.and.ellipse")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Theme.Spacing.medium)
        .padding(.vertical, Theme.Spacing.tight)
    }

    @ViewBuilder
    private var noMatchesRow: some View {
        if filtered.isEmpty {
            VStack(spacing: Theme.Spacing.small) {
                Text("Nobody matches that combination")
                    .font(.subheadline.weight(.medium))
                Button("Clear Filters") {
                    withAnimation {
                        selectedTag = nil
                        selectedPlaceID = nil
                    }
                }
                .font(.subheadline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Theme.Spacing.large)
            .listRowSeparator(.hidden)
        }
    }

    private var emptyState: some View {
        Group {
            if !searchText.isEmpty {
                ContentUnavailableView.search(text: searchText)
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
            Menu("Add to \(groupLabel)") {
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

            if hasActiveFilters {
                Divider()

                Button {
                    withAnimation {
                        selectedTag = nil
                        selectedPlaceID = nil
                    }
                } label: {
                    Label("Clear Filters", systemImage: "xmark.circle")
                }
            }

            Divider()

            // Filtering itself is on the chips now; this is the editing door.
            Button {
                isShowingGroups = true
            } label: {
                Label("Manage \(groupPlural)", systemImage: "mappin.and.ellipse")
            }
            Button {
                isShowingTypes = true
            } label: {
                Label("Manage Types", systemImage: "tag")
            }
        } label: {
            Label("Sort and Filter", systemImage: "line.3.horizontal.decrease.circle")
                .symbolVariant(hasActiveFilters ? .fill : .none)
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

import SwiftData
import SwiftUI

/// Every relationship in the current context.
struct PeopleView: View {

    @Binding var mode: ContextMode

    @Environment(\.modelContext) private var context
    @AppStorage(PreferenceKey.peopleSort) private var sortRaw = PeopleSort.recent.rawValue

    @Query(sort: \Person.familyName) private var people: [Person]

    @State private var searchText = ""
    @State private var selectedTag: String?
    @State private var isShowingAddPerson = false
    @State private var isShowingContactImport = false
    @State private var selectedPerson: Person?

    private var sort: PeopleSort {
        get { PeopleSort(rawValue: sortRaw) ?? .recent }
        nonmutating set { sortRaw = newValue.rawValue }
    }

    private var filtered: [Person] {
        let matching = PeopleEngine.filter(
            people,
            mode: mode,
            tagName: selectedTag,
            query: searchText
        )
        return PeopleEngine.sort(matching, by: sort)
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
            .navigationTitle("People")
            .contextSwitcher($mode)
            .searchable(text: $searchText, prompt: "Search \(mode.title.lowercased()) contacts")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { filterMenu }
                ToolbarItem(placement: .topBarTrailing) { addMenu }
            }
            .navigationDestination(item: $selectedPerson) { person in
                PersonProfileView(person: person, mode: $mode)
            }
            .sheet(isPresented: $isShowingAddPerson) {
                EditPersonView(person: nil, mode: mode)
            }
            .sheet(isPresented: $isShowingContactImport) {
                ContactImportView(mode: mode)
            }
        }
    }

    // MARK: - Lists

    private var flatList: some View {
        List {
            if selectedTag != nil {
                activeFilterRow
            }
            ForEach(filtered) { person in
                row(for: person)
            }
        }
        .listStyle(.plain)
    }

    private var sectionedList: some View {
        List {
            if selectedTag != nil {
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

    private func row(for person: Person) -> some View {
        Button {
            selectedPerson = person
        } label: {
            PersonRow(person: person, mode: mode)
        }
        .buttonStyle(.plain)
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
        if let selectedTag {
            HStack {
                Label(selectedTag, systemImage: "line.3.horizontal.decrease")
                    .font(.subheadline)
                Spacer()
                Button("Clear") { self.selectedTag = nil }
                    .font(.subheadline)
            }
            .listRowSeparator(.hidden)
        }
    }

    private var emptyState: some View {
        Group {
            if !searchText.isEmpty {
                ContentUnavailableView.search(text: searchText)
            } else if selectedTag != nil {
                ContentUnavailableView {
                    Label("No one here yet", systemImage: "person.crop.circle.badge.questionmark")
                } description: {
                    Text("Add people to this group as relationships develop.")
                } actions: {
                    Button("Show Everyone") { selectedTag = nil }
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

    // MARK: - Toolbar

    private var filterMenu: some View {
        Menu {
            Picker("Sort", selection: Binding(get: { sort }, set: { sort = $0 })) {
                ForEach(PeopleSort.allCases) { option in
                    Label(option.title, systemImage: option.symbolName).tag(option)
                }
            }

            Divider()

            Picker(
                "Filter",
                selection: Binding(
                    get: { selectedTag ?? "" },
                    set: { selectedTag = $0.isEmpty ? nil : $0 }
                )
            ) {
                Text("All").tag("")
                ForEach(RelationshipTag.quickFilterNames(for: mode), id: \.self) { name in
                    Text(name).tag(name)
                }
            }
        } label: {
            Label("Sort and Filter", systemImage: "line.3.horizontal.decrease.circle")
                .symbolVariant(selectedTag == nil ? .none : .fill)
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
    }

    // MARK: - Actions

    private func toggleFavorite(_ person: Person) {
        withAnimation {
            person.isFavorite.toggle()
            person.touch()
        }
        try? context.save()
        Haptics.light()
    }
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

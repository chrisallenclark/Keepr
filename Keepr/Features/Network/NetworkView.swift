import SwiftData
import SwiftUI

/// The way into the relationship map: who's worth looking at first.
///
/// Deliberately not "here is your whole network at once". A single graph of
/// everyone is a lovely poster and an unreadable phone screen — past about
/// twenty linked people the edges cross more than they connect, and no amount
/// of pinching fixes it. So the tab opens on the people with the most going on
/// around them, and the map is always about one person at a time.
struct NetworkView: View {

    @Binding var mode: ContextMode

    @Query private var people: [Person]

    @State private var searchText = ""
    @State private var mappedPerson: Person?

    private var visible: [Person] { PeopleEngine.visible(people, in: mode) }

    /// Everyone with at least one link, most-connected first.
    ///
    /// Sorted by connection count rather than alphabetically because this is a
    /// starting point, not an address book — the person at the top should be
    /// the one whose map has the most to show.
    private var connected: [Person] {
        visible
            .filter { !$0.connections.isEmpty }
            .filter(matchesSearch)
            .sorted { lhs, rhs in
                lhs.connections.count == rhs.connections.count
                    ? lhs.sortKey.localizedStandardCompare(rhs.sortKey) == .orderedAscending
                    : lhs.connections.count > rhs.connections.count
            }
    }

    /// People in this context who could be linked but aren't yet.
    private var unlinkedCount: Int {
        visible.filter { $0.connections.isEmpty }.count
    }

    private func matchesSearch(_ person: Person) -> Bool {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return true }
        return person.displayName.localizedCaseInsensitiveContains(query)
            || person.fullName.localizedCaseInsensitiveContains(query)
            || person.connections.contains {
                $0.person.displayName.localizedCaseInsensitiveContains(query)
                    || $0.label.localizedCaseInsensitiveContains(query)
            }
    }

    var body: some View {
        NavigationStack {
            Group {
                if connected.isEmpty {
                    emptyState
                } else {
                    list
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .contextSwitcher($mode)
            .searchable(text: $searchText, prompt: "Search people and connections")
            .navigationDestination(item: $mappedPerson) { person in
                RelationshipMapView(person: person, mode: $mode)
            }
            .onAppear {
                if LaunchOptions.screen == .map, mappedPerson == nil {
                    mappedPerson = connected.first
                }
            }
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("Network")
                        .font(.keeprTitleInline)
                        .foregroundStyle(.primary)
                        .accessibilityAddTraits(.isHeader)
                }
            }
        }
    }

    private var list: some View {
        List {
            Section {
                ForEach(connected) { person in
                    Button {
                        mappedPerson = person
                    } label: {
                        NetworkRow(person: person, mode: mode)
                    }
                    .buttonStyle(.plain)
                }
            } header: {
                SectionHeading("Most Connected", count: connected.count)
            } footer: {
                if searchText.isEmpty, unlinkedCount > 0 {
                    Text("Not linked yet: ^[\(unlinkedCount) other person](inflect: true) in \(mode.title.lowercased()). Link people from a profile, or by selecting two of them in People.")
                }
            }
        }
        .keeprList()
    }

    private var emptyState: some View {
        Group {
            if !searchText.isEmpty {
                ContentUnavailableView.search(text: searchText)
            } else {
                ContentUnavailableView {
                    Label("Nothing linked yet", systemImage: "point.3.connected.trianglepath.dotted")
                } description: {
                    Text("Say who introduced you to whom, who works with whom, who referred whom — and this becomes a map you can walk. Link people from a profile, or by selecting two of them in People.")
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Palette.ground)
    }
}

// MARK: - Row

/// One person in the picker: who they are, and a hint of what their map holds.
private struct NetworkRow: View {

    let person: Person
    let mode: ContextMode

    /// The first couple of link labels — "Introduced by, Referred" — which is
    /// what actually tells you whether this map is worth opening.
    private var summary: String {
        let labels = person.connections.map(\.label).uniqued().prefix(3)
        return labels.joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: Theme.Spacing.medium) {
            Avatar(person: person, size: .medium)

            VStack(alignment: .leading, spacing: 3) {
                Text(person.displayName)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                if !summary.isEmpty {
                    Text(summary)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                TypeBadgeRow(tags: person.headlineTags(for: mode), limit: 1)
            }

            Spacer(minLength: Theme.Spacing.small)

            Text("^[\(person.connections.count) connection](inflect: true)")
                .font(.caption)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.trailing)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .contentShape(.rect)
        .padding(.vertical, Theme.Spacing.tight)
        .accessibilityElement(children: .combine)
    }
}

private extension Array where Element: Hashable {
    /// Order-preserving dedupe. Two people linked as "Colleague" shouldn't make
    /// the summary read "Colleague · Colleague".
    func uniqued() -> [Element] {
        var seen: Set<Element> = []
        return filter { seen.insert($0).inserted }
    }
}

#Preview {
    NetworkView(mode: .constant(.business))
        .modelContainer(.preview)
}

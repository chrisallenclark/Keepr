import SwiftData
import SwiftUI

/// Search across everything you've recorded — names, companies, memories,
/// interactions, notes, follow-ups.
///
/// Results are people, grouped by *why* they matched, because "which client
/// owns a roofing company" is a question about a person, not about a note.
struct SearchView: View {

    @Query private var people: [Person]

    @State private var query = ""
    @State private var mode = ContextMode.business
    @State private var selectedPerson: Person?

    private var sections: [SearchSection] {
        SearchEngine.grouped(SearchEngine.search(query, in: people))
    }

    var body: some View {
        NavigationStack {
            Group {
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    startState
                } else if sections.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    results
                }
            }
            .navigationTitle("Search")
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Names, companies, anything you noted"
            )
            .navigationDestination(item: $selectedPerson) { person in
                PersonProfileView(person: person, mode: $mode)
            }
        }
    }

    private var results: some View {
        List {
            ForEach(sections) { section in
                Section(section.title) {
                    ForEach(section.results) { result in
                        Button {
                            mode = ContextMode(context: result.person.context)
                            selectedPerson = result.person
                        } label: {
                            SearchResultRow(result: result)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var startState: some View {
        ContentUnavailableView {
            Label("Search everyone", systemImage: "magnifyingglass")
        } description: {
            Text("Business and personal together — try a company, a place you met, or something you noted.")
        }
    }
}

/// A search hit: the person, and the line that matched.
struct SearchResultRow: View {
    let result: SearchResult

    var body: some View {
        HStack(spacing: Theme.Spacing.medium) {
            Avatar(person: result.person, size: .small)

            VStack(alignment: .leading, spacing: 2) {
                Text(result.person.displayName)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)

                if let snippet = result.snippet, !snippet.isEmpty {
                    Label {
                        Text(snippet).lineLimit(2)
                    } icon: {
                        Image(systemName: result.field.symbolName)
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .labelStyle(.titleAndIcon)
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

private extension ContextMode {
    /// Opening a search result should land you in that person's own context.
    init(context: RelationshipContext) {
        self = context == .personal ? .personal : .business
    }
}

#Preview {
    SearchView()
        .modelContainer(.preview)
}

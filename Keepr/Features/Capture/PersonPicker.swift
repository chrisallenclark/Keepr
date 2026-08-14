import SwiftData
import SwiftUI

/// Choose who a capture is about — an existing relationship, or a new one.
struct PersonPicker: View {

    let mode: ContextMode
    var suggestedName: String?
    @Binding var selection: Person?
    @Binding var newPersonName: String

    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Person.familyName) private var people: [Person]

    @State private var query = ""

    /// The whole address book, not just the current mode — a capture about a
    /// friend shouldn't be impossible to file while you're in Business.
    private var results: [Person] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let matching = trimmed.isEmpty
            ? people
            : people.filter { PeopleEngine.matches($0, query: trimmed) }
        return PeopleEngine.sort(matching, by: trimmed.isEmpty ? .recent : .name)
    }

    private var proposedNewName: String {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? (suggestedName ?? "") : trimmed
    }

    var body: some View {
        NavigationStack {
            List {
                if !proposedNewName.isEmpty,
                   !results.contains(where: {
                       $0.displayName.localizedCaseInsensitiveCompare(proposedNewName) == .orderedSame
                   }) {
                    Section {
                        Button {
                            selection = nil
                            newPersonName = proposedNewName
                            dismiss()
                        } label: {
                            Label {
                                Text("Add \"\(proposedNewName)\"")
                            } icon: {
                                Image(systemName: "person.crop.circle.badge.plus")
                            }
                        }
                    }
                }

                Section {
                    ForEach(results) { person in
                        Button {
                            selection = person
                            newPersonName = ""
                            Haptics.selection()
                            dismiss()
                        } label: {
                            HStack {
                                PersonCompactRow(
                                    person: person,
                                    detail: person.subtitle ?? person.context.title
                                )
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .listStyle(.plain)
            .searchable(text: $query, prompt: "Search everyone")
            .navigationTitle("Who?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .overlay {
                if results.isEmpty, proposedNewName.isEmpty {
                    ContentUnavailableView(
                        "No one yet",
                        systemImage: "person.2",
                        description: Text("Type a name to add someone.")
                    )
                }
            }
        }
    }
}

import SwiftData
import SwiftUI

/// Place one person: what they are to you, where they came from, and how you
/// met — asked while the answer is still in your head.
///
/// "How did you meet?" sits on this screen and nowhere else in the flow because
/// it is the fact with the shortest half-life. Everything else about a person
/// can be reconstructed later; that one is gone by Wednesday.
struct CategorizeSheet: View {

    let item: ReviewItem
    var mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \RelationshipTag.sortOrder) private var tags: [RelationshipTag]
    @Query(sort: \PersonGroup.sortOrder) private var groups: [PersonGroup]

    @State private var relationshipContext: RelationshipContext = .business
    @State private var selectedTagID: UUID?
    @State private var selectedGroupIDs: Set<UUID> = []
    @State private var howWeMet = ""
    @State private var hasLoaded = false

    private var kind: TagKind {
        relationshipContext == .personal ? .personal : .business
    }

    private var relevantTags: [RelationshipTag] {
        tags.filter { $0.kind == kind }
    }

    private var relevantGroups: [PersonGroup] {
        switch relationshipContext {
        case .both: groups
        case .business: groups.filter { $0.matches(.business) }
        case .personal: groups.filter { $0.matches(.personal) }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Context", selection: $relationshipContext) {
                        ForEach(RelationshipContext.allCases) { option in
                            Text(option.title).tag(option)
                        }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text(item.name)
                        .font(.headline)
                        .textCase(nil)
                        .foregroundStyle(.primary)
                } footer: {
                    if let detail = item.detail {
                        Text(detail)
                    }
                }

                if let suggestion = item.suggestion {
                    Section {
                        Button {
                            applySuggestion(suggestion)
                        } label: {
                            Label {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Use \(suggestion.tagName ?? suggestion.context.title)")
                                    Text(suggestion.reason)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            } icon: {
                                Image(systemName: "wand.and.stars")
                            }
                        }
                    }
                }

                Section("Relationship Type") {
                    Picker("Type", selection: $selectedTagID) {
                        Text("None").tag(UUID?.none)
                        ForEach(relevantTags) { tag in
                            Label(tag.name, systemImage: tag.symbolName).tag(UUID?.some(tag.id))
                        }
                    }
                    .labelsHidden()
                    .pickerStyle(.inline)
                }

                if !relevantGroups.isEmpty {
                    Section {
                        ForEach(relevantGroups) { group in
                            Button {
                                toggle(group)
                            } label: {
                                HStack {
                                    Label(group.name, systemImage: group.symbolName)
                                        .foregroundStyle(.primary)
                                    Spacer()
                                    if selectedGroupIDs.contains(group.id) {
                                        Image(systemName: "checkmark")
                                            .foregroundStyle(Color.accentColor)
                                    }
                                }
                                .contentShape(.rect)
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        Text("Places")
                    } footer: {
                        Text("Where the relationship lives — the gym you train at, home, a night out.")
                    }
                }

                Section {
                    TextField("Met at the gym, introduced by Michael…", text: $howWeMet, axis: .vertical)
                } header: {
                    Text("How You Met")
                } footer: {
                    Text("The first thing to fade. Worth ten seconds now.")
                }
            }
            .navigationTitle(item.isNew ? "New Contact" : "Categorize")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                }
            }
            .onAppear(perform: load)
        }
        .presentationDragIndicator(.visible)
    }

    // MARK: - Actions

    private func load() {
        guard !hasLoaded else { return }
        hasLoaded = true

        relationshipContext = item.suggestion?.context ?? RelationshipContext(mode: mode)

        if case let .existing(person) = item.subject {
            relationshipContext = person.context
            howWeMet = person.howWeMet ?? ""
            selectedGroupIDs = Set(person.groupList.map(\.id))
            selectedTagID = person.tagList.first?.id
        }

        if selectedTagID == nil, let name = item.suggestion?.tagName {
            selectedTagID = relevantTags.first { $0.builtInKey == name || $0.name == name }?.id
        }
    }

    private func applySuggestion(_ suggestion: CategorySuggestion) {
        withAnimation {
            relationshipContext = suggestion.context
            if let name = suggestion.tagName {
                selectedTagID = tags.first {
                    ($0.builtInKey == name || $0.name == name) && $0.kind == kind
                }?.id
            }
        }
        Haptics.selection()
    }

    private func toggle(_ group: PersonGroup) {
        if selectedGroupIDs.contains(group.id) {
            selectedGroupIDs.remove(group.id)
            // Only take back the sentence this screen wrote itself.
            if howWeMet == Self.metAt(group) { howWeMet = "" }
        } else {
            selectedGroupIDs.insert(group.id)
            // A first draft, in an editable field, on the one screen that's
            // actually asking the question. Never overwrites what's there.
            if howWeMet.isEmpty { howWeMet = Self.metAt(group) }
        }
        Haptics.selection()
    }

    private static func metAt(_ group: PersonGroup) -> String {
        "Met at \(group.name)"
    }

    private func save() {
        let person: Person

        switch item.subject {
        case let .newContact(contact):
            person = PersonImporter.makePerson(
                from: contact,
                context: relationshipContext,
                in: context
            )
            PersonImporter.addMemories(from: contact, to: person, in: context)
            ContactChangeTracker.shared.acknowledge([contact])
        case let .existing(existing):
            person = existing
            person.context = relationshipContext
        }

        if let selectedTagID, let tag = tags.first(where: { $0.id == selectedTagID }) {
            if !person.tagList.contains(where: { $0.id == tag.id }) {
                person.tags = person.tagList + [tag]
            }
        }

        person.groups = groups.filter { selectedGroupIDs.contains($0.id) }

        let trimmed = howWeMet.trimmingCharacters(in: .whitespacesAndNewlines)
        person.howWeMet = trimmed.isEmpty ? nil : trimmed
        // Answering counts as reviewing them, even if no type was chosen.
        person.reviewedAt = Date()
        person.touch()

        try? context.save()
        Haptics.success()
        dismiss()
    }
}

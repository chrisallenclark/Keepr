import SwiftData
import SwiftUI
import UIKit

/// The catch-up queue: people who haven't been placed yet.
///
/// Two groups, one screen. New contacts the phone picked up since Keepr last
/// looked come first, because that's the answer with a shelf life — you know
/// today which bar it was. Underneath sits everyone already in Keepr who never
/// got a relationship type, which is the part that actually accumulates.
struct ReviewView: View {

    var mode: ContextMode

    @Environment(\.contactStore) private var contactStore
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query private var people: [Person]
    @Query(sort: \PersonGroup.sortOrder) private var groups: [PersonGroup]
    @Query(sort: \RelationshipTag.sortOrder) private var tags: [RelationshipTag]

    @State private var newContacts: [ContactSummary] = []
    @State private var isLoading = true
    @State private var editing: ReviewItem?

    private var items: [ReviewItem] {
        ReviewQueue.items(
            newContacts: newContacts,
            people: people,
            vocabulary: MarkerVocabulary.build(groups: groups, tags: tags)
        )
    }

    private var fresh: [ReviewItem] { items.filter(\.isNew) }
    private var backlog: [ReviewItem] { items.filter { !$0.isNew } }

    private var withSuggestions: [ReviewItem] {
        items.filter { $0.suggestion != nil }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView("Checking your contacts").controlSize(.large)
                } else if items.isEmpty {
                    caughtUp
                } else {
                    list
                }
            }
            .navigationTitle("Catch Up")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                if !withSuggestions.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Button {
                                applyAllSuggestions()
                            } label: {
                                Label(
                                    "Apply \(withSuggestions.count) Suggestions",
                                    systemImage: "wand.and.stars"
                                )
                            }
                        } label: {
                            Label("Options", systemImage: "ellipsis.circle")
                        }
                    }
                }
            }
            .sheet(item: $editing) { item in
                CategorizeSheet(item: item, mode: mode)
            }
            .task { await load() }
        }
    }

    // MARK: - List

    private var list: some View {
        List {
            if !fresh.isEmpty {
                Section {
                    ForEach(fresh) { item in
                        row(item)
                    }
                } header: {
                    Text("New in Contacts")
                } footer: {
                    Text("Added to your phone since Keepr last looked. Categorize them now, while you still remember how you met.")
                }
            }

            if !backlog.isEmpty {
                Section {
                    ForEach(backlog) { item in
                        row(item)
                    }
                } header: {
                    Text("No Type Yet")
                } footer: {
                    Text("Already in Keepr, never given a relationship type.")
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private func row(_ item: ReviewItem) -> some View {
        Button {
            editing = item
        } label: {
            HStack(spacing: Theme.Spacing.medium) {
                avatar(for: item)

                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)

                    if let detail = item.detail {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    if let suggestion = item.suggestion {
                        HStack(spacing: Theme.Spacing.tight) {
                            Image(systemName: suggestion.context.symbolName)
                            Text(suggestion.tagName ?? suggestion.context.title)
                            Text("·")
                            Text(suggestion.reason).lineLimit(1)
                        }
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                    }
                }

                Spacer(minLength: Theme.Spacing.small)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button {
                skip(item)
            } label: {
                Label("Not Now", systemImage: "clock")
            }
            .tint(.gray)
        }
        .swipeActions(edge: .leading) {
            if let suggestion = item.suggestion {
                Button {
                    apply(suggestion, to: item)
                } label: {
                    Label("Accept", systemImage: "checkmark")
                }
                .tint(.green)
            }
        }
    }

    @ViewBuilder
    private func avatar(for item: ReviewItem) -> some View {
        switch item.subject {
        case let .newContact(contact): Avatar(contact: contact, size: .small)
        case let .existing(person): Avatar(person: person, size: .small)
        }
    }

    private var caughtUp: some View {
        ContentUnavailableView {
            Label("Everyone's placed", systemImage: "checkmark.circle")
        } description: {
            Text(
                contactStore.authorizationStatus().canRead
                    ? "Nobody is waiting to be categorized. New contacts will show up here as you add them."
                    : "Turn on contact access and Keepr will notice new contacts as you add them."
            )
        } actions: {
            if !contactStore.authorizationStatus().canRead,
               let url = URL(string: UIApplication.openSettingsURLString) {
                Link("Open Settings", destination: url)
                    .buttonStyle(.bordered)
            }
        }
    }

    // MARK: - Actions

    private func load() async {
        guard contactStore.authorizationStatus().canRead else {
            isLoading = false
            return
        }
        let fetched = await contactStore.fetchContacts()
        newContacts = ContactChangeTracker.shared.newContacts(in: fetched)
        isLoading = false
    }

    /// Waving someone off has to stick, or the queue becomes a nag. For a
    /// contact that means remembering the identifier; for someone already in
    /// Keepr it's a date on their record.
    private func skip(_ item: ReviewItem) {
        withAnimation {
            switch item.subject {
            case let .newContact(contact):
                ContactChangeTracker.shared.acknowledge([contact])
                newContacts.removeAll { $0.identifier == contact.identifier }
            case let .existing(person):
                person.reviewedAt = Date()
                try? context.save()
            }
        }
        Haptics.light()
    }

    private func apply(_ suggestion: CategorySuggestion, to item: ReviewItem) {
        withAnimation {
            switch item.subject {
            case let .newContact(contact):
                let person = PersonImporter.makePerson(
                    from: contact,
                    context: suggestion.context,
                    in: context,
                    suggestion: suggestion
                )
                PersonImporter.apply(suggestion, to: person, in: context, groups: groups)
                PersonImporter.noteOriginalName(suggestion, for: person, in: context)
                PersonImporter.addMemories(from: contact, to: person, in: context)
                ContactChangeTracker.shared.acknowledge([contact])
                newContacts.removeAll { $0.identifier == contact.identifier }
            case let .existing(person):
                PersonImporter.apply(suggestion, to: person, in: context, groups: groups)
                person.touch()
            }
            try? context.save()
        }
        Haptics.success()
    }

    private func applyAllSuggestions() {
        for item in withSuggestions {
            guard let suggestion = item.suggestion else { continue }
            apply(suggestion, to: item)
        }
    }
}

#Preview {
    ReviewView(mode: .business)
        .modelContainer(.preview)
}

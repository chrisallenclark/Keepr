import SwiftData
import SwiftUI

/// Everything worth knowing about one person, in the order you need it:
/// who they are → what to do next → what to remember → what's happened.
struct PersonProfileView: View {

    @Bindable var person: Person
    @Binding var mode: ContextMode

    @Environment(\.modelContext) private var context
    @Environment(\.notificationService) private var notificationService
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var isShowingLogInteraction = false
    @State private var isShowingEdit = false
    @State private var isShowingNewFollowUp = false
    @State private var isShowingAllMemories = false
    @State private var isShowingAllInteractions = false
    @State private var newMemoryText = ""
    @State private var isAddingMemory = false
    @State private var isConfirmingDelete = false
    @State private var isShowingLinkEditor = false

    var body: some View {
        List {
            Section {
                PersonHeader(person: person, mode: mode)
                    .listRowInsets(EdgeInsets(top: Theme.Spacing.large, leading: 0,
                                              bottom: Theme.Spacing.medium, trailing: 0))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)

                QuickActions(person: person) { method in
                    open(method)
                } onLog: {
                    isShowingLogInteraction = true
                }
                .listRowInsets(EdgeInsets(top: 0, leading: 0,
                                          bottom: Theme.Spacing.medium, trailing: 0))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }

            nextActionSection
            memoriesSection
            connectionsSection
            timelineSection
            detailsSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle(person.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button {
                        isShowingEdit = true
                    } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                    Button {
                        toggleFavorite()
                    } label: {
                        Label(
                            person.isFavorite ? "Remove Favorite" : "Favorite",
                            systemImage: person.isFavorite ? "star.slash" : "star"
                        )
                    }
                    Divider()
                    Button(role: .destructive) {
                        isConfirmingDelete = true
                    } label: {
                        Label("Delete Person", systemImage: "trash")
                    }
                } label: {
                    Label("More", systemImage: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $isShowingLogInteraction) {
            LogInteractionView(person: person)
        }
        .sheet(isPresented: $isShowingEdit) {
            EditPersonView(person: person, mode: mode)
        }
        .sheet(isPresented: $isShowingNewFollowUp) {
            FollowUpEditor(person: person, followUp: nil)
        }
        .sheet(isPresented: $isShowingLinkEditor) {
            PersonLinkEditor(person: person)
        }
        .confirmationDialog(
            "Delete \(person.displayName)?",
            isPresented: $isConfirmingDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive, action: deletePerson)
        } message: {
            Text("This removes everything you've recorded about them. Their contact card is not affected.")
        }
    }

    // MARK: - Next action

    @ViewBuilder
    private var nextActionSection: some View {
        Section {
            if person.openFollowUps.isEmpty {
                Button {
                    isShowingNewFollowUp = true
                } label: {
                    Label("Add a follow-up", systemImage: "plus.circle")
                        .font(.subheadline)
                }
            } else {
                ForEach(person.openFollowUps) { followUp in
                    FollowUpRow(followUp: followUp, showsPerson: false) {
                        complete(followUp)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        Button {
                            complete(followUp)
                        } label: {
                            Label("Done", systemImage: "checkmark")
                        }
                        .tint(.green)
                    }
                }
            }
        } header: {
            HStack {
                Text("Next")
                Spacer()
                if !person.openFollowUps.isEmpty {
                    Button("Add") { isShowingNewFollowUp = true }
                        .font(.caption.weight(.semibold))
                        .textCase(nil)
                }
            }
        }
    }

    // MARK: - Memories

    private var visibleMemories: [Memory] {
        let all = person.visibleMemories
        return isShowingAllMemories ? all : Array(all.prefix(4))
    }

    @ViewBuilder
    private var memoriesSection: some View {
        Section {
            if person.visibleMemories.isEmpty, !isAddingMemory {
                Button {
                    startAddingMemory()
                } label: {
                    Label("Remember something about them", systemImage: "plus.circle")
                        .font(.subheadline)
                }
            }

            ForEach(visibleMemories) { memory in
                MemoryRow(memory: memory)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            delete(memory)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        Button {
                            archive(memory)
                        } label: {
                            Label("Archive", systemImage: "archivebox")
                        }
                        .tint(.gray)
                    }
            }

            if isAddingMemory {
                HStack {
                    TextField("Something worth remembering", text: $newMemoryText, axis: .vertical)
                        .font(.subheadline)
                        .onSubmit(saveMemory)
                    Button("Save", action: saveMemory)
                        .font(.subheadline.weight(.semibold))
                        .disabled(newMemoryText.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }

            if person.visibleMemories.count > 4 {
                Button(isShowingAllMemories ? "Show Less" : "Show All \(person.visibleMemories.count)") {
                    withAnimation { isShowingAllMemories.toggle() }
                }
                .font(.subheadline)
            }
        } header: {
            HStack {
                Text("Important Context")
                Spacer()
                if !person.visibleMemories.isEmpty || isAddingMemory {
                    Button("Add") { startAddingMemory() }
                        .font(.caption.weight(.semibold))
                        .textCase(nil)
                }
            }
        }
    }

    // MARK: - Connections

    /// Who this person is connected to, and how. Empty until there's something
    /// to show — an empty "Connections" header on every profile would be noise
    /// on the majority of them.
    @ViewBuilder
    private var connectionsSection: some View {
        let connections = person.connections

        if !connections.isEmpty {
            Section {
                ForEach(connections) { connection in
                    NavigationLink {
                        PersonProfileView(person: connection.person, mode: $mode)
                    } label: {
                        ConnectionRow(connection: connection)
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            unlink(connection)
                        } label: {
                            Label("Unlink", systemImage: "minus.circle")
                        }
                    }
                }
            } header: {
                HStack {
                    Text("Connections")
                    Spacer()
                    Button("Link") { isShowingLinkEditor = true }
                        .font(.caption.weight(.semibold))
                        .textCase(nil)
                }
            }
        } else {
            Section {
                Button {
                    isShowingLinkEditor = true
                } label: {
                    Label("Link to someone else", systemImage: "link")
                        .font(.subheadline)
                }
            } header: {
                Text("Connections")
            } footer: {
                Text("Their partner, their assistant, whoever introduced you.")
            }
        }
    }

    // MARK: - Timeline

    private var visibleInteractions: [Interaction] {
        let all = person.timeline
        return isShowingAllInteractions ? all : Array(all.prefix(5))
    }

    @ViewBuilder
    private var timelineSection: some View {
        Section {
            if person.timeline.isEmpty {
                Button {
                    isShowingLogInteraction = true
                } label: {
                    Label("Log your first interaction", systemImage: "plus.circle")
                        .font(.subheadline)
                }
            }

            ForEach(visibleInteractions) { interaction in
                InteractionRow(interaction: interaction)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            delete(interaction)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
            }

            if person.timeline.count > 5 {
                Button(isShowingAllInteractions ? "Show Less" : "Show All \(person.timeline.count)") {
                    withAnimation { isShowingAllInteractions.toggle() }
                }
                .font(.subheadline)
            }
        } header: {
            HStack {
                Text("Interactions")
                Spacer()
                if !person.timeline.isEmpty {
                    Button("Log") { isShowingLogInteraction = true }
                        .font(.caption.weight(.semibold))
                        .textCase(nil)
                }
            }
        }
    }

    // MARK: - Details

    @ViewBuilder
    private var detailsSection: some View {
        Section("Details") {
            LabeledContent("Context", value: person.context.title)

            if !person.tagList.isEmpty {
                LabeledContent("Type") {
                    Text(person.tagList.map(\.name).joined(separator: ", "))
                        .multilineTextAlignment(.trailing)
                }
            }
            if let howWeMet = person.howWeMet, !howWeMet.isEmpty {
                LabeledContent("How we met") {
                    Text(howWeMet).multilineTextAlignment(.trailing)
                }
            }
            if let introducedBy = person.introducedBy, !introducedBy.isEmpty {
                LabeledContent("Introduced by", value: introducedBy)
            }
            LabeledContent("Last interaction", value: RelativeDate.lastContact(person.lastInteractionAt))
            LabeledContent("Added", value: RelativeDate.absolute(person.createdAt))

            if let notes = person.notes, !notes.isEmpty {
                VStack(alignment: .leading, spacing: Theme.Spacing.tight) {
                    Text("Notes")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(notes)
                        .font(.subheadline)
                }
                .padding(.vertical, 2)
            }

            if !person.isLinkedToContact {
                Label("Not linked to a contact", systemImage: "person.crop.circle.badge.questionmark")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Actions

    private func open(_ method: ContactMethod) {
        guard let url = CommunicationLauncher.url(for: method, person: person) else { return }
        openURL(url)
        Haptics.light()
    }

    private func startAddingMemory() {
        withAnimation {
            isAddingMemory = true
            isShowingAllMemories = true
        }
    }

    private func saveMemory() {
        let content = newMemoryText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty else { return }

        let memory = Memory(content: content, person: person)
        context.insert(memory)
        person.touch()
        try? context.save()

        newMemoryText = ""
        isAddingMemory = false
        Haptics.success()
    }

    private func complete(_ followUp: FollowUp) {
        withAnimation { followUp.complete() }
        FollowUpScheduler.cancel(followUp, using: notificationService)
        try? context.save()
        Haptics.success()
    }

    private func archive(_ memory: Memory) {
        withAnimation {
            memory.isArchived = true
            memory.updatedAt = Date()
        }
        try? context.save()
    }

    private func delete(_ memory: Memory) {
        withAnimation { context.delete(memory) }
        try? context.save()
    }

    private func delete(_ interaction: Interaction) {
        withAnimation { context.delete(interaction) }
        // The person's "last interaction" is a cache; recompute it.
        person.lastInteractionAt = person.timeline
            .filter { $0.id != interaction.id }
            .first?
            .occurredAt
        try? context.save()
    }

    /// Removing a link deletes the one record that served both ends, so it
    /// disappears from the other person's profile too.
    private func unlink(_ connection: PersonConnection) {
        withAnimation { context.delete(connection.link) }
        try? context.save()
        Haptics.light()
    }

    private func toggleFavorite() {
        person.isFavorite.toggle()
        person.touch()
        try? context.save()
        Haptics.light()
    }

    private func deletePerson() {
        for followUp in person.followUpList {
            FollowUpScheduler.cancel(followUp, using: notificationService)
        }
        context.delete(person)
        try? context.save()
        dismiss()
    }
}

#Preview {
    NavigationStack {
        PersonProfilePreview()
    }
    .modelContainer(.preview)
}

/// Pulls the first sample person out of the preview container.
private struct PersonProfilePreview: View {
    @Query(sort: \Person.createdAt) private var people: [Person]
    @State private var mode = ContextMode.business

    var body: some View {
        if let person = people.first {
            PersonProfileView(person: person, mode: $mode)
        } else {
            Text("No sample data")
        }
    }
}

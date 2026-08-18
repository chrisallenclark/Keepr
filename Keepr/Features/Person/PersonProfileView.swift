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
    @State private var isShowingNewPlace = false
    @State private var isEditingWorkNote = false
    @State private var workNoteDraft = ""

    @Query(sort: \PersonGroup.sortOrder) private var allPlaces: [PersonGroup]
    @AppStorage(PreferenceKey.groupLabel) private var groupLabel = GroupVocabulary.default.singular

    /// The user's word for the second axis, in the shapes the copy needs.
    private var groupPlural: String { GroupVocabulary.plural(for: groupLabel) }
    private var article: String { GroupVocabulary.article(for: groupLabel) }

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

            waitingRow
            nextActionSection
            workSection
            placesSection
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
                    if person.awaitingReplySince == nil {
                        Button {
                            markReachedOut()
                        } label: {
                            Label("Reached Out — No Reply", systemImage: "paperplane")
                        }
                    } else {
                        Button {
                            markReplied()
                        } label: {
                            Label("They Replied", systemImage: "checkmark.bubble")
                        }
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
        .sheet(isPresented: $isShowingNewPlace) {
            // Creating a place from a profile means "put them here", so the
            // person is added the moment it's saved.
            GroupEditor(group: nil, mode: mode) { created in
                add(created)
            }
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

    // MARK: - Waiting

    /// A quiet line, not an alarm: you reached out, nothing has come back yet.
    @ViewBuilder
    private var waitingRow: some View {
        if let since = person.awaitingReplySince {
            Section {
                HStack(spacing: Theme.Spacing.medium) {
                    Image(systemName: "hourglass")
                        .foregroundStyle(.secondary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Waiting on a reply")
                            .font(.subheadline.weight(.medium))
                        Text("You reached out \(RelativeDate.past(since).lowercased()).")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Button("Replied") { markReplied() }
                        .font(.caption.weight(.semibold))
                        .buttonStyle(.borderless)
                }
            }
        }
    }

    // MARK: - Rhythm

    /// How often this person is worth contacting, and where that came from.
    ///
    /// Shown on the profile rather than buried in settings because the override
    /// is the whole point: one client you speak to weekly shouldn't force you to
    /// re-time every other client.
    @ViewBuilder
    private var cadenceRow: some View {
        let status = CadenceEngine.status(for: person, now: Date())

        Menu {
            Button("Use my type's rhythm") { setCadence(nil) }
            Button("Never remind me") { setCadence(0) }
            Divider()
            ForEach(CadenceEngine.presets, id: \.self) { days in
                Button(CadenceEngine.label(forDays: days)) { setCadence(days) }
            }
        } label: {
            HStack {
                Text("Reach out")
                    .foregroundStyle(.primary)
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text(cadenceValueLabel(status))
                        .foregroundStyle(.secondary)
                    if let status, let source = status.source {
                        Text("from \(source)")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }
            }
            .contentShape(.rect)
        }
    }

    private func cadenceValueLabel(_ status: CadenceStatus?) -> String {
        if let status { return CadenceEngine.label(forDays: status.days) }
        return person.cadenceDays == 0 ? "Never" : "No rhythm"
    }

    // MARK: - Work

    /// Who they work for and what they actually do.
    ///
    /// The reason this is a section and not a line in Details: the client you
    /// train at 6am might run a business you'd learn a lot from, and that fact is
    /// worth nothing if it's buried under an employer field you never read.
    @ViewBuilder
    private var workSection: some View {
        let employer = [person.jobTitle, person.company]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        let hasWorkNote = !(person.workNote ?? "").isEmpty

        if !employer.isEmpty || hasWorkNote || isEditingWorkNote {
            Section {
                if !employer.isEmpty {
                    Label(employer, systemImage: "building.2")
                        .font(.subheadline)
                }

                if let note = person.workNote, !note.isEmpty, !isEditingWorkNote {
                    Button {
                        startEditingWorkNote()
                    } label: {
                        Text(note)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                } else if isEditingWorkNote {
                    HStack {
                        TextField(
                            "Owns a Shopify brand — good person to learn ads from",
                            text: $workNoteDraft,
                            axis: .vertical
                        )
                        .font(.subheadline)
                        Button("Save", action: saveWorkNote)
                            .font(.subheadline.weight(.semibold))
                    }
                } else {
                    Button {
                        startEditingWorkNote()
                    } label: {
                        Label("What do they do?", systemImage: "plus.circle")
                            .font(.subheadline)
                    }
                }
            } header: {
                Text("What They Do")
            }
        } else {
            Section {
                Button {
                    startEditingWorkNote()
                } label: {
                    Label("What do they do?", systemImage: "plus.circle")
                        .font(.subheadline)
                }
            } header: {
                Text("What They Do")
            } footer: {
                Text("Who they work for, what they run, what they're good at. The thing you'd want in front of you before the next conversation.")
            }
        }
    }

    // MARK: - Places

    /// Where this relationship lives. Someone can be in more than one — a client
    /// you train at Life Time and also see at the beach volleyball league.
    ///
    /// This is one tap from the profile rather than buried behind a manager
    /// screen, because a place you have to go and administer is a place nobody
    /// fills in.
    @ViewBuilder
    private var placesSection: some View {
        let places = person.groupList.sorted { $0.sortOrder < $1.sortOrder }

        Section {
            if places.isEmpty {
                placeMenu {
                    Label("Add \(article) \(groupLabel.lowercased())", systemImage: "mappin.and.ellipse")
                        .font(.subheadline)
                }
            } else {
                ForEach(places) { place in
                    HStack {
                        Label(place.name, systemImage: place.symbolName)
                            .font(.subheadline)
                        if let detail = place.detail, !detail.isEmpty {
                            Text(detail)
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                        Spacer(minLength: 0)
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            remove(place)
                        } label: {
                            Label("Remove", systemImage: "minus.circle")
                        }
                    }
                }
            }
        } header: {
            HStack {
                Text(groupPlural)
                Spacer()
                if !places.isEmpty {
                    placeMenu {
                        Text("Add")
                            .font(.caption.weight(.semibold))
                            .textCase(nil)
                    }
                }
            }
        } footer: {
            if places.isEmpty {
                Text("Life Time, your training company, the bar you met in. One of these shows you everyone from it at once — clients and colleagues together.")
            }
        }
    }

    /// The add-a-place control: every place they aren't in yet, then a way to
    /// make a new one without leaving the profile.
    private func placeMenu<Label: View>(@ViewBuilder label: () -> Label) -> some View {
        Menu {
            ForEach(availablePlaces) { place in
                Button {
                    add(place)
                } label: {
                    SwiftUI.Label(place.name, systemImage: place.symbolName)
                }
            }
            if !availablePlaces.isEmpty {
                Divider()
            }
            Button {
                isShowingNewPlace = true
            } label: {
                SwiftUI.Label("New \(groupLabel)…", systemImage: "plus")
            }
        } label: {
            label()
        }
    }

    private var availablePlaces: [PersonGroup] {
        allPlaces.filter { place in
            !person.isMember(of: place) && place.matches(mode)
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
            cadenceRow

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

    /// Sent something, heard nothing. Their clock resets — you did your part —
    /// and they join the waiting list until they come back.
    private func markReachedOut() {
        withAnimation { Outreach.markReachedOut(person, in: context) }
        try? context.save()
        Haptics.success()
    }

    private func markReplied() {
        withAnimation { Outreach.markReplied(person) }
        try? context.save()
        Haptics.light()
    }

    /// nil means "inherit from my types", 0 means "never chase me about them".
    private func setCadence(_ days: Int?) {
        person.cadenceDays = days
        person.touch()
        try? context.save()
        Haptics.selection()
    }

    private func startEditingWorkNote() {
        workNoteDraft = person.workNote ?? ""
        withAnimation { isEditingWorkNote = true }
    }

    private func saveWorkNote() {
        let trimmed = workNoteDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        withAnimation {
            person.workNote = trimmed.isEmpty ? nil : trimmed
            person.touch()
            isEditingWorkNote = false
        }
        try? context.save()
        Haptics.success()
    }

    private func add(_ place: PersonGroup) {
        guard !person.isMember(of: place) else { return }
        // Deliberately doesn't touch "how we met": where a relationship lives
        // now and where it started are different facts, and quietly writing a
        // guess into a field the user trusts is worse than leaving it blank.
        withAnimation {
            person.groups = person.groupList + [place]
            person.touch()
        }
        try? context.save()
        Haptics.success()
    }

    private func remove(_ place: PersonGroup) {
        withAnimation {
            person.groups = person.groupList.filter { $0.id != place.id }
            person.touch()
        }
        try? context.save()
        Haptics.light()
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

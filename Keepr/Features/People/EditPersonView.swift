import SwiftData
import SwiftUI

/// Add someone by hand, or edit who they are to you.
///
/// Identity fields stay editable even when a contact is linked, because what
/// Keepr shows should be what *you* call them — but Contacts is never written to.
struct EditPersonView: View {

    /// nil creates a new person.
    var person: Person?
    /// The mode the user is currently in, used as the default context.
    let mode: ContextMode
    /// Prefills a new person from a contact the user picked.
    var contact: ContactSummary?

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \RelationshipTag.sortOrder) private var allTags: [RelationshipTag]

    @State private var givenName = ""
    @State private var familyName = ""
    @State private var preferredName = ""
    @State private var company = ""
    @State private var jobTitle = ""
    @State private var workNote = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var relationshipContext: RelationshipContext = .business
    @State private var priority: Priority = .normal
    @State private var isFavorite = false
    @State private var howWeMet = ""
    @State private var introducedBy = ""
    @State private var notes = ""
    @State private var selectedTagIDs: Set<UUID> = []
    @State private var isShowingTypeEditor = false
    @State private var hasLoaded = false

    @FocusState private var isNameFocused: Bool

    private var isEditing: Bool { person != nil }

    private var canSave: Bool {
        !givenName.trimmingCharacters(in: .whitespaces).isEmpty
            || !familyName.trimmingCharacters(in: .whitespaces).isEmpty
            || !preferredName.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("First name", text: $givenName)
                        .textContentType(.givenName)
                        .focused($isNameFocused)
                    TextField("Last name", text: $familyName)
                        .textContentType(.familyName)
                    TextField("Goes by (optional)", text: $preferredName)
                }

                Section("Context") {
                    Picker("Context", selection: $relationshipContext) {
                        ForEach(RelationshipContext.allCases) { option in
                            Text(option.title).tag(option)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                tagsSection

                Section {
                    TextField("Company", text: $company)
                        .textContentType(.organizationName)
                    TextField("Title", text: $jobTitle)
                        .textContentType(.jobTitle)
                    TextField("What they do", text: $workNote, axis: .vertical)
                        .lineLimit(1...4)
                } header: {
                    Text("Work")
                } footer: {
                    Text("\"What they do\" is for your words, not the contact card's — what they run, what they're good at, what you could learn from them.")
                }

                Section("Reach them") {
                    TextField("Phone", text: $phone)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .textContentType(.emailAddress)
                }

                Section("Relationship") {
                    Toggle("Favorite", isOn: $isFavorite)
                    Picker("Priority", selection: $priority) {
                        ForEach(Priority.allCases) { option in
                            Text(option.title).tag(option)
                        }
                    }
                    TextField("How we met", text: $howWeMet, axis: .vertical)
                        .lineLimit(1...3)
                    TextField("Introduced by", text: $introducedBy)
                }

                Section("Notes") {
                    TextField("Anything else", text: $notes, axis: .vertical)
                        .lineLimit(3...10)
                }
            }
            .navigationTitle(isEditing ? "Edit Person" : "Add Person")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(!canSave)
                }
            }
            .onAppear(perform: load)
            .sheet(isPresented: $isShowingTypeEditor) {
                NavigationStack {
                    RelationshipTypeEditor(mode: .constant(mode), startsCreating: true)
                }
            }
        }
    }

    // MARK: - Tags

    /// Tags for the chosen context. Choosing "Both" shows everything, which is
    /// the point of "Both".
    private var relevantTags: [RelationshipTag] {
        switch relationshipContext {
        case .business: allTags.filter { $0.kind == .business }
        case .personal: allTags.filter { $0.kind == .personal }
        case .both: allTags
        }
    }

    private var tagsSection: some View {
        Section {
            ForEach(relevantTags) { tag in
                Button {
                    toggle(tag)
                } label: {
                    HStack {
                        Label(tag.name, systemImage: tag.symbolName)
                            .foregroundStyle(.primary)
                        Spacer()
                        if selectedTagIDs.contains(tag.id) {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.accentColor)
                                .fontWeight(.semibold)
                        }
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
            Button {
                isShowingTypeEditor = true
            } label: {
                Label("New Type…", systemImage: "plus.circle")
                    .font(.subheadline)
            }
        } header: {
            Text("Relationship Type")
        } footer: {
            Text("Pick as many as fit. Someone can be a friend and a referral source. Make your own if none of these are how you'd put it.")
        }
    }

    private func toggle(_ tag: RelationshipTag) {
        if selectedTagIDs.contains(tag.id) {
            selectedTagIDs.remove(tag.id)
        } else {
            selectedTagIDs.insert(tag.id)
        }
        Haptics.selection()
    }

    // MARK: - Actions

    private func load() {
        guard !hasLoaded else { return }
        hasLoaded = true

        if let person {
            givenName = person.givenName
            familyName = person.familyName
            preferredName = person.preferredName ?? ""
            company = person.company ?? ""
            jobTitle = person.jobTitle ?? ""
            workNote = person.workNote ?? ""
            phone = person.primaryPhone ?? ""
            email = person.primaryEmail ?? ""
            relationshipContext = person.context
            priority = person.priority
            isFavorite = person.isFavorite
            howWeMet = person.howWeMet ?? ""
            introducedBy = person.introducedBy ?? ""
            notes = person.notes ?? ""
            selectedTagIDs = Set(person.tagList.map(\.id))
        } else {
            relationshipContext = RelationshipContext(mode: mode)
            if let contact {
                givenName = contact.givenName
                familyName = contact.familyName
                company = contact.organizationName ?? ""
                jobTitle = contact.jobTitle ?? ""
                phone = contact.phoneNumbers.first ?? ""
                email = contact.emailAddresses.first ?? ""
            } else {
                isNameFocused = true
            }
        }
    }

    private func save() {
        let target: Person
        if let person {
            target = person
        } else {
            let created = Person(context: relationshipContext, contactIdentifier: contact?.identifier)
            created.photoData = contact?.thumbnailData
            context.insert(created)
            target = created
        }

        target.givenName = givenName.trimmingCharacters(in: .whitespaces)
        target.familyName = familyName.trimmingCharacters(in: .whitespaces)
        target.preferredName = nonEmpty(preferredName)
        target.company = nonEmpty(company)
        target.jobTitle = nonEmpty(jobTitle)
        target.workNote = nonEmpty(workNote)
        target.phoneNumbers = nonEmpty(phone).map { [$0] } ?? []
        target.emailAddresses = nonEmpty(email).map { [$0] } ?? []
        target.context = relationshipContext
        target.priority = priority
        target.isFavorite = isFavorite
        target.howWeMet = nonEmpty(howWeMet)
        target.introducedBy = nonEmpty(introducedBy)
        target.notes = nonEmpty(notes)
        target.tags = allTags.filter { selectedTagIDs.contains($0.id) }
        target.touch()

        try? context.save()
        Haptics.success()
        dismiss()
    }

    private func nonEmpty(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

#Preview {
    EditPersonView(person: nil, mode: .business)
        .modelContainer(.preview)
}

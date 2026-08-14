import Foundation
import SwiftData

/// An app-owned relationship profile.
///
/// Named `Person` rather than `Relationship` so it doesn't shadow SwiftData's
/// `@Relationship` macro — and because it reads better at every call site.
///
/// Apple Contacts remains the source of truth for identity data; we cache a
/// minimal snapshot so the app stays fast, searchable and useful offline or when
/// contact access is denied or limited.
///
/// Every stored property has a default and every relationship is optional, which
/// are the CloudKit requirements — adopted up front so sync can be switched on
/// later without a migration.
@Model
final class Person {

    // MARK: Identity

    var id: UUID = UUID()

    /// `CNContact.identifier` when this profile is linked to an Apple contact.
    var contactIdentifier: String?

    var givenName: String = ""
    var familyName: String = ""
    /// What the user actually calls them, if different ("Mike", "Mom").
    var preferredName: String?

    var company: String?
    var jobTitle: String?

    var phoneNumbers: [String] = []
    var emailAddresses: [String] = []

    /// Small thumbnail copied from Contacts, if the user granted access.
    @Attribute(.externalStorage) var photoData: Data?

    // MARK: Context & classification

    var contextRaw: String = RelationshipContext.personal.rawValue
    var priorityRaw: String = Priority.normal.rawValue
    var statusRaw: String = RelationshipStatus.active.rawValue
    var isFavorite: Bool = false

    // MARK: Relationship metadata

    var createdAt: Date = Date()
    var updatedAt: Date = Date()
    /// Mirrors the most recent logged interaction, so lists can sort without a join.
    var lastInteractionAt: Date?
    var howWeMet: String?
    var introducedBy: String?
    var notes: String?

    /// Copied from the linked contact card when available.
    var birthday: Date?
    var postalAddress: String?
    /// The contact's own Notes field. Only ever populated when Apple has
    /// granted the contacts-notes entitlement; nil otherwise.
    var contactNote: String?

    /// Marks records created by the developer sample data set, so they can be
    /// removed in one action without touching anything real.
    var isSampleData: Bool = false

    // MARK: Graph

    @Relationship(deleteRule: .cascade, inverse: \Interaction.person)
    var interactions: [Interaction]?

    @Relationship(deleteRule: .cascade, inverse: \Memory.person)
    var memories: [Memory]?

    @Relationship(deleteRule: .cascade, inverse: \FollowUp.person)
    var followUps: [FollowUp]?

    @Relationship(deleteRule: .nullify, inverse: \RelationshipTag.people)
    var tags: [RelationshipTag]?

    @Relationship(deleteRule: .nullify, inverse: \PersonGroup.members)
    var groups: [PersonGroup]?

    init(
        givenName: String = "",
        familyName: String = "",
        preferredName: String? = nil,
        context: RelationshipContext = .personal,
        contactIdentifier: String? = nil,
        company: String? = nil,
        jobTitle: String? = nil,
        phoneNumbers: [String] = [],
        emailAddresses: [String] = [],
        photoData: Data? = nil,
        priority: Priority = .normal,
        isFavorite: Bool = false,
        howWeMet: String? = nil,
        introducedBy: String? = nil,
        notes: String? = nil,
        createdAt: Date = Date(),
        isSampleData: Bool = false
    ) {
        self.id = UUID()
        self.givenName = givenName
        self.familyName = familyName
        self.preferredName = preferredName
        self.contextRaw = context.rawValue
        self.contactIdentifier = contactIdentifier
        self.company = company
        self.jobTitle = jobTitle
        self.phoneNumbers = phoneNumbers
        self.emailAddresses = emailAddresses
        self.photoData = photoData
        self.priorityRaw = priority.rawValue
        self.statusRaw = RelationshipStatus.active.rawValue
        self.isFavorite = isFavorite
        self.howWeMet = howWeMet
        self.introducedBy = introducedBy
        self.notes = notes
        self.createdAt = createdAt
        self.updatedAt = createdAt
        self.isSampleData = isSampleData
        self.interactions = []
        self.memories = []
        self.followUps = []
        self.tags = []
        self.groups = []
    }
}

// MARK: - Typed accessors

extension Person {

    var context: RelationshipContext {
        get { RelationshipContext(rawValue: contextRaw) ?? .personal }
        set { contextRaw = newValue.rawValue }
    }

    var priority: Priority {
        get { Priority(rawValue: priorityRaw) ?? .normal }
        set { priorityRaw = newValue.rawValue }
    }

    var status: RelationshipStatus {
        get { RelationshipStatus(rawValue: statusRaw) ?? .active }
        set { statusRaw = newValue.rawValue }
    }

    var interactionList: [Interaction] { interactions ?? [] }
    var memoryList: [Memory] { memories ?? [] }
    var followUpList: [FollowUp] { followUps ?? [] }
    var tagList: [RelationshipTag] { tags ?? [] }
    var groupList: [PersonGroup] { groups ?? [] }

    var isLinkedToContact: Bool { contactIdentifier != nil }

    /// Memories worth showing on the profile, most important first.
    var visibleMemories: [Memory] {
        memoryList
            .filter { !$0.isArchived }
            .sorted {
                $0.importance.weight == $1.importance.weight
                    ? $0.createdAt > $1.createdAt
                    : $0.importance.weight > $1.importance.weight
            }
    }

    var timeline: [Interaction] {
        interactionList.sorted { $0.occurredAt > $1.occurredAt }
    }

    var openFollowUps: [FollowUp] {
        followUpList
            .filter { !$0.isCompleted }
            .sorted { $0.dueDate < $1.dueDate }
    }

    var nextFollowUp: FollowUp? { openFollowUps.first }
}

// MARK: - Display

extension Person {

    /// Full name as stored in Contacts.
    var fullName: String {
        let name = [givenName, familyName]
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return name.isEmpty ? "New Person" : name
    }

    /// What the user calls them. Used everywhere in the UI.
    var displayName: String {
        if let preferredName, !preferredName.trimmingCharacters(in: .whitespaces).isEmpty {
            return preferredName
        }
        return fullName
    }

    /// Shown under the name on rows and the profile header.
    var subtitle: String? {
        let role = [jobTitle, company]
            .compactMap { $0?.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        return role.isEmpty ? nil : role
    }

    var initials: String {
        let parts = displayName
            .split(separator: " ")
            .prefix(2)
            .compactMap { $0.first.map(String.init) }
        let joined = parts.joined().uppercased()
        return joined.isEmpty ? "?" : joined
    }

    /// Key used for alphabetical sorting.
    var sortKey: String {
        let key = familyName.isEmpty ? displayName : familyName
        return key.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
    }

    var primaryPhone: String? { phoneNumbers.first }
    var primaryEmail: String? { emailAddresses.first }

    /// The tags worth showing on a row or header, context-relevant ones first.
    func headlineTags(for mode: ContextMode) -> [RelationshipTag] {
        let preferred = tagList.filter { $0.kind == TagKind(mode: mode) }
        let source = preferred.isEmpty ? tagList : preferred
        return source.sorted { $0.sortOrder < $1.sortOrder }
    }

    func hasTag(named name: String) -> Bool {
        tagList.contains { $0.name == name }
    }

    func isMember(of group: PersonGroup) -> Bool {
        groupList.contains { $0.id == group.id }
    }

    func touch(_ date: Date = Date()) {
        updatedAt = date
    }
}

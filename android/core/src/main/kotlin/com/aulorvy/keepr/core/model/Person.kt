package com.aulorvy.keepr.core.model

import java.time.Instant
import java.util.UUID

/**
 * The app-owned relationship profile. Translated from Keepr/Models/Person.swift.
 *
 * Caches a minimal snapshot of the linked phone contact so the app is fast,
 * searchable and useful offline and when contact access is denied;
 * [contactIdentifier] is the link back, and the contact stays the source of
 * truth.
 */
class Person(
    givenName: String = "",
    familyName: String = "",
    preferredName: String? = null,
    context: RelationshipContext = RelationshipContext.PERSONAL,
    contactIdentifier: String? = null,
    company: String? = null,
    jobTitle: String? = null,
    phoneNumbers: List<String> = emptyList(),
    emailAddresses: List<String> = emptyList(),
    photoData: ByteArray? = null,
    priority: Priority = Priority.NORMAL,
    isFavorite: Boolean = false,
    howWeMet: String? = null,
    introducedBy: String? = null,
    notes: String? = null,
    createdAt: Instant = Instant.now(),
    isSampleData: Boolean = false,
    override val id: UUID = UUID.randomUUID(),
) : Model() {

    // Identity
    var contactIdentifier: String? = contactIdentifier
    var givenName: String = givenName
    var familyName: String = familyName
    var preferredName: String? = preferredName
    var company: String? = company
    var jobTitle: String? = jobTitle
    /** What they actually do, in the user's words, separate from the card's title. */
    var workNote: String? = null
    var phoneNumbers: List<String> = phoneNumbers
    var emailAddresses: List<String> = emailAddresses
    var photoData: ByteArray? = photoData

    // Context & classification (stored as raw strings, like iOS)
    var contextRaw: String = context.rawValue
    var priorityRaw: String = priority.rawValue
    var statusRaw: String = RelationshipStatus.ACTIVE.rawValue
    var isFavorite: Boolean = isFavorite

    // Relationship metadata
    var createdAt: Instant = createdAt
    var updatedAt: Instant = createdAt
    var lastInteractionAt: Instant? = null
    var howWeMet: String? = howWeMet
    var introducedBy: String? = introducedBy
    var notes: String? = notes

    var birthday: Instant? = null
    var postalAddress: String? = null
    /** The contact card's own Notes field, when the platform allows reading it. */
    var contactNote: String? = null

    /** Days between contacts worth keeping; nil inherits from types, 0 = never chase. */
    var cadenceDays: Int? = null

    /** Set when the user reached out and is waiting to hear back. */
    var awaitingReplySince: Instant? = null

    /** When the user last confirmed this person's categorization. */
    var reviewedAt: Instant? = null

    var isSampleData: Boolean = isSampleData

    // Graph
    internal val interactionStore = mutableListOf<Interaction>()
    internal val memoryStore = mutableListOf<Memory>()
    internal val followUpStore = mutableListOf<FollowUp>()
    internal val fileStore = mutableListOf<PersonFile>()
    internal val noteStore = mutableListOf<ConversationNote>()
    internal val tagStore = mutableListOf<RelationshipTag>()
    internal val groupStore = mutableListOf<PersonGroup>()
    internal val linkAStore = mutableListOf<PersonLink>()
    internal val linkBStore = mutableListOf<PersonLink>()

    var interactions: List<Interaction>
        get() = interactionStore.toList()
        set(value) = replaceChildren(interactions, value, { it.person = null }, { it.person = this })

    var memories: List<Memory>
        get() = memoryStore.toList()
        set(value) = replaceChildren(memories, value, { it.person = null }, { it.person = this })

    var followUps: List<FollowUp>
        get() = followUpStore.toList()
        set(value) = replaceChildren(followUps, value, { it.person = null }, { it.person = this })

    var files: List<PersonFile>
        get() = fileStore.toList()
        set(value) = replaceChildren(files, value, { it.person = null }, { it.person = this })

    var conversationNotes: List<ConversationNote>
        get() = noteStore.toList()
        set(value) = replaceChildren(conversationNotes, value, { it.person = null }, { it.person = this })

    var tags: List<RelationshipTag>
        get() = tagStore.toList()
        set(value) = replaceMany(tagStore, value) { it.peopleStore }

    var groups: List<PersonGroup>
        get() = groupStore.toList()
        set(value) = replaceMany(groupStore, value) { it.memberStore }

    val linksAsA: List<PersonLink> get() = linkAStore.toList()
    val linksAsB: List<PersonLink> get() = linkBStore.toList()

    override fun related(): List<Model> =
        interactionStore + memoryStore + followUpStore + fileStore + noteStore + tagStore + groupStore + linkAStore + linkBStore

    override fun applyDeleteRules(context: ModelContext) {
        // Cascade
        (interactionStore + memoryStore + followUpStore + fileStore + noteStore + linkAStore + linkBStore)
            .forEach { context.delete(it) }
        // Nullify
        tags = emptyList()
        groups = emptyList()
    }

    // MARK: Typed accessors

    var context: RelationshipContext
        get() = RelationshipContext.from(contextRaw) ?: RelationshipContext.PERSONAL
        set(value) { contextRaw = value.rawValue }

    var priority: Priority
        get() = Priority.from(priorityRaw) ?: Priority.NORMAL
        set(value) { priorityRaw = value.rawValue }

    var status: RelationshipStatus
        get() = RelationshipStatus.from(statusRaw) ?: RelationshipStatus.ACTIVE
        set(value) { statusRaw = value.rawValue }

    val interactionList: List<Interaction> get() = interactions
    val memoryList: List<Memory> get() = memories
    val followUpList: List<FollowUp> get() = followUps

    /** Files on the profile itself; images inside a conversation note live with the note. */
    val fileList: List<PersonFile>
        get() = files.filter { !it.belongsToNote }.sortedByDescending { it.capturedAt }

    /** Pinned first, then newest. */
    val noteList: List<ConversationNote>
        get() = conversationNotes.sortedWith { lhs, rhs ->
            if (lhs.isPinned == rhs.isPinned) rhs.occurredAt.compareTo(lhs.occurredAt)
            else if (lhs.isPinned) -1 else 1
        }

    val tagList: List<RelationshipTag> get() = tags
    val groupList: List<PersonGroup> get() = groups

    /** Every link touching this person, from either end. */
    val linkList: List<PersonLink> get() = linksAsA + linksAsB

    /** Who this person is connected to, labelled from this person's side. */
    val connections: List<PersonConnection>
        get() = linkList
            .mapNotNull { link ->
                val other = link.other(this) ?: return@mapNotNull null
                PersonConnection(link, other, link.label(from = this))
            }
            .sortedWith { a, b -> localizedStandardCompare(a.person.sortKey, b.person.sortKey) }

    fun isLinked(to: Person): Boolean = linkList.any { it.joins(this, to) }

    val isLinkedToContact: Boolean get() = contactIdentifier != null

    /** Unarchived memories, most important first, then newest. */
    val visibleMemories: List<Memory>
        get() = memoryList
            .filter { !it.isArchived }
            .sortedWith { a, b ->
                if (a.importance.weight == b.importance.weight) b.createdAt.compareTo(a.createdAt)
                else b.importance.weight.compareTo(a.importance.weight)
            }

    val timeline: List<Interaction> get() = interactionList.sortedByDescending { it.occurredAt }

    val openFollowUps: List<FollowUp>
        get() = followUpList.filter { !it.isCompleted }.sortedBy { it.dueDate }

    val nextFollowUp: FollowUp? get() = openFollowUps.firstOrNull()

    // MARK: Display

    val fullName: String
        get() {
            val name = listOf(givenName, familyName)
                .map { it.trimmedSpaces() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            return name.ifEmpty { "New Person" }
        }

    val displayName: String
        get() {
            val preferred = preferredName
            if (preferred != null && preferred.trimmedSpaces().isNotEmpty()) return preferred
            return fullName
        }

    /** "Job Title · Company", or nil when neither is set. */
    val subtitle: String?
        get() {
            val role = listOf(jobTitle, company)
                .mapNotNull { it?.trimmedSpaces() }
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            return role.ifEmpty { null }
        }

    val initials: String
        get() {
            val joined = displayName
                .split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .map { it.first().toString() }
                .joinToString("")
                .uppercase()
            return joined.ifEmpty { "?" }
        }

    /** Family name, falling back to the display name; case- and accent-folded. */
    val sortKey: String
        get() = (familyName.ifEmpty { displayName }).folded()

    val primaryPhone: String? get() = phoneNumbers.firstOrNull()
    val primaryEmail: String? get() = emailAddresses.firstOrNull()

    /** The types to show under a name: the current mode's first, else all. */
    fun headlineTags(mode: ContextMode): List<RelationshipTag> {
        val preferred = tagList.filter { it.kind == TagKind.of(mode) }
        val source = preferred.ifEmpty { tagList }
        return source.sortedBy { it.sortOrder }
    }

    fun hasTag(named: String): Boolean = tagList.any { it.name == named }

    fun isMember(of: PersonGroup): Boolean = groupList.any { it.id == of.id }

    fun touch(date: Instant = Instant.now()) {
        updatedAt = date
    }
}

/** Someone this person is linked to, with the label read from this side. */
data class PersonConnection(val link: PersonLink, val person: Person, val label: String) {
    val id: UUID get() = link.id
}

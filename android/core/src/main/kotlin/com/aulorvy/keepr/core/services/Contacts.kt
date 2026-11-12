package com.aulorvy.keepr.core.services

import com.aulorvy.keepr.core.model.folded
import com.aulorvy.keepr.core.model.trimmedSpaces
import java.time.Instant

// The value types of Keepr/Services/ContactsService.swift. The live contact
// reader is platform code (Android's ContactsContract); these are what it
// produces and what the importer, categorizer and markers consume.

/** The app's view of contacts permission. */
enum class ContactAccess {
    NOT_DETERMINED,
    DENIED,
    RESTRICTED,
    /** iOS 18's "selected contacts". Android has no equivalent, so it's never produced there. */
    LIMITED,
    AUTHORIZED;

    val canRead: Boolean get() = this == AUTHORIZED || this == LIMITED

    /** Shown when access is off. Never scolds — the app works without it. */
    val explanation: String?
        get() = when (this) {
            AUTHORIZED, LIMITED, NOT_DETERMINED -> null
            DENIED -> "Contact access is off. You can still add people by hand, or turn it on in Settings."
            RESTRICTED -> "Contact access isn't available on this device. You can still add people by hand."
        }
}

/** A labelled relation from a contact card, e.g. "mother: Jane Smith". */
data class ContactRelation(val label: String, val name: String)

/** A minimal snapshot of a phone contact: only the fields Keepr shows or acts on. */
data class ContactSummary(
    val identifier: String,
    val givenName: String,
    val familyName: String,
    val organizationName: String?,
    val jobTitle: String?,
    val phoneNumbers: List<String>,
    val emailAddresses: List<String>,
    val thumbnailData: ByteArray? = null,
    val birthday: Instant? = null,
    val postalAddress: String? = null,
    val relations: List<ContactRelation> = emptyList(),
    /** The card's own Notes field, when the platform lets it be read. */
    val note: String? = null,
) {
    val id: String get() = identifier

    val fullName: String
        get() {
            val name = listOf(givenName, familyName)
                .map { it.trimmedSpaces() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            return name.ifEmpty { organizationName ?: "No Name" }
        }

    val subtitle: String?
        get() {
            val value = listOfNotNull(jobTitle, organizationName)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            return value.ifEmpty { phoneNumbers.firstOrNull() }
        }

    val initials: String
        get() {
            val joined = fullName.split(" ").filter { it.isNotEmpty() }.take(2).map { it.first().toString() }
                .joinToString("").uppercase()
            return joined.ifEmpty { "?" }
        }

    val sortKey: String get() = (familyName.ifEmpty { fullName }).folded()

    // ByteArray breaks data-class equality; compare by identifier like iOS's Identifiable.
    override fun equals(other: Any?): Boolean = other is ContactSummary && other.identifier == identifier &&
        other.givenName == givenName && other.familyName == familyName && other.note == note &&
        other.organizationName == organizationName && other.jobTitle == jobTitle &&
        other.phoneNumbers == phoneNumbers && other.emailAddresses == emailAddresses &&
        other.relations == relations && other.birthday == birthday && other.postalAddress == postalAddress

    override fun hashCode(): Int = identifier.hashCode()
}

/** Everything the app needs from contacts. Injected so tests never touch a real store. */
interface ContactStoreProviding {
    fun authorizationStatus(): ContactAccess
    suspend fun requestAccess(): ContactAccess
    suspend fun fetchContacts(): List<ContactSummary>
    suspend fun contact(identifier: String): ContactSummary?
}

/** Deterministic fake used by tests and previews. Never touches contacts. */
class StubContactStore(
    var access: ContactAccess = ContactAccess.AUTHORIZED,
    var contacts: List<ContactSummary> = defaultContacts,
) : ContactStoreProviding {
    override fun authorizationStatus(): ContactAccess = access
    override suspend fun requestAccess(): ContactAccess = access
    override suspend fun fetchContacts(): List<ContactSummary> = if (access.canRead) contacts else emptyList()
    override suspend fun contact(identifier: String): ContactSummary? = contacts.firstOrNull { it.identifier == identifier }

    companion object {
        val defaultContacts = listOf(
            ContactSummary("stub-1", "Jake", "Martinez", "Martinez Roofing", "Owner", listOf("(561) 555-0142"), listOf("jake@martinezroofing.com")),
            ContactSummary("stub-2", "Sarah", "Miller", "Coastal Design Co.", "Creative Director", listOf("(561) 555-0198"), emptyList()),
            ContactSummary("stub-3", "Alex", "Nguyen", null, null, listOf("(561) 555-0155"), emptyList()),
            ContactSummary("stub-4", "Linda", "Clark", null, null, listOf("(407) 555-0121"), emptyList()),
        )
    }
}

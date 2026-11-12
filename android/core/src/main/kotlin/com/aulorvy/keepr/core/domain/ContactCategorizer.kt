package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.TagKind
import com.aulorvy.keepr.core.services.ContactSummary

// Translated from Keepr/Domain/ContactCategorizer.swift.

/** What the importer proposes for one contact, before the user confirms it. */
data class CategorySuggestion(
    var context: RelationshipContext,
    /** Name of a built-in `RelationshipTag`, or null when nothing fits well. */
    var tagName: String?,
    /** Why the guess was made, shown so it can be judged rather than trusted. */
    var reason: String,
    var confidence: Confidence,
    /** Groups read off the card — ids, since two groups can share a name across contexts. */
    var groupIDs: List<String> = emptyList(),
    /** Those groups' names, for showing the suggestion without a lookup. */
    var groupNames: List<String> = emptyList(),
    /** Types beyond [tagName], when the card named more than one. */
    var extraTagNames: List<String> = emptyList(),
    /** The name with recognized shorthand removed. Null when nothing needed removing. */
    var cleanedGivenName: String? = null,
    var cleanedFamilyName: String? = null,
    /** What the card actually said, kept so nothing is silently lost. */
    var originalName: String? = null,
) {
    /** Ordered low < medium < high, as the Swift `Comparable` raw values are. */
    enum class Confidence(val rawValue: Int) {
        LOW(0),
        MEDIUM(1),
        HIGH(2),
    }
}

/**
 * Guesses whether a contact is a business or personal relationship.
 *
 * The bar for guessing is deliberately high: a wrong suggestion that arrives
 * pre-selected gets accepted in bulk and quietly poisons the data. So every
 * rule keys off something the user actually typed into the card — an
 * employer, a job title, a work email domain, a family relation — and
 * anything else comes back null for the user to decide.
 */
object ContactCategorizer {

    /** Consumer mail providers. An address at one says nothing about work. */
    val personalEmailDomains: Set<String> = setOf(
        "gmail.com", "googlemail.com", "yahoo.com", "ymail.com", "hotmail.com",
        "outlook.com", "live.com", "msn.com", "icloud.com", "me.com", "mac.com",
        "aol.com", "proton.me", "protonmail.com", "gmx.com", "zoho.com",
        "comcast.net", "att.net", "verizon.net", "sbcglobal.net", "cox.net",
    )

    /** Relation labels on a contact card that mean family. */
    val familyRelationLabels: Set<String> = setOf(
        "mother", "father", "parent", "brother", "sister", "sibling", "child",
        "son", "daughter", "spouse", "husband", "wife", "partner",
        "grandmother", "grandfather", "grandparent", "grandchild",
    )

    /** Job titles that read as someone running a business — a lead, not a generic contact. */
    val ownerTitles: List<String> = listOf(
        "owner", "founder", "co-founder", "ceo", "president", "principal",
        "partner", "proprietor", "director", "managing",
    )

    /**
     * The full read on one contact: what the user's own shorthand says, and
     * then — only where that's silent — what the employer and family fields imply.
     * Shorthand wins because it's a decision the user already made.
     */
    fun suggestion(contact: ContactSummary, vocabulary: MarkerVocabulary): CategorySuggestion? {
        val markers = ContactMarkerParser.parse(contact, vocabulary)
        // A type the user deleted is a decision: the inference keeps its
        // context and drops the type it can't offer. An empty vocabulary means
        // nobody said what exists, not that everything was deleted.
        val inferred = suggestion(contact)?.let { suggestion ->
            val name = suggestion.tagName
            if (vocabulary.types.isEmpty() || name == null || vocabulary.types.any { it.display == name }) {
                suggestion
            } else {
                suggestion.copy(tagName = null)
            }
        }

        if (markers.isEmpty) return inferred

        // A type named on the card decides the context; otherwise whatever the
        // rest of the card implied, and finally business — a group with no type
        // is most often a work arrangement.
        val namedType = markers.typeNames.firstOrNull()
        val context = namedType
            ?.let { kindOfTag(it, vocabulary) }
            ?.let { RelationshipContext.of(it) }
            ?: inferred?.context
            ?: RelationshipContext.BUSINESS

        val suggestion = CategorySuggestion(
            context = context,
            tagName = namedType ?: inferred?.tagName,
            reason = markers.reasons.joinToString(" · "),
            confidence = CategorySuggestion.Confidence.HIGH,
        )
        suggestion.groupIDs = markers.groupIDs
        suggestion.groupNames = markers.groupNames
        suggestion.extraTagNames = markers.typeNames.drop(1)

        if (markers.changedName) {
            suggestion.cleanedGivenName = markers.givenName
            suggestion.cleanedFamilyName = markers.familyName
            suggestion.originalName = contact.fullName
        }
        return suggestion
    }

    /** Which side a named type sits on. Custom types carry their own kind. */
    private fun kindOfTag(name: String, vocabulary: MarkerVocabulary): TagKind? =
        vocabulary.types.firstOrNull { it.display == name }?.kind

    fun suggestion(contact: ContactSummary): CategorySuggestion? {
        // Family stated on the card is the strongest personal signal there is.
        val relation = contact.relations.firstOrNull { it.label.lowercase() in familyRelationLabels }
        if (relation != null) {
            return CategorySuggestion(
                context = RelationshipContext.PERSONAL,
                tagName = "Family",
                reason = "Listed as ${relation.label.lowercase()} on the contact card",
                confidence = CategorySuggestion.Confidence.HIGH,
            )
        }

        return suggestion(contact.jobTitle, contact.organizationName, contact.emailAddresses)
    }

    /**
     * The same rules applied to someone already in Keepr — people imported
     * before auto-categorization, or never classified, are who the review
     * queue is for.
     */
    fun suggestion(person: Person): CategorySuggestion? =
        suggestion(person.jobTitle, person.company, person.emailAddresses)

    /** The employer/title/email rules, shared by both entry points. */
    fun suggestion(jobTitle: String?, company: String?, emails: List<String>): CategorySuggestion? {
        val title = jobTitle?.trimmingWhitespaces() ?: ""
        val employer = company?.trimmingWhitespaces() ?: ""

        if (employer.isNotEmpty() || title.isNotEmpty()) {
            val lowerTitle = title.lowercase()
            val isOwner = ownerTitles.any { lowerTitle.contains(it) }

            val reason = when {
                employer.isNotEmpty() && title.isNotEmpty() -> "$title at $employer"
                employer.isNotEmpty() -> "Works at $employer"
                else -> title
            }

            return CategorySuggestion(
                context = RelationshipContext.BUSINESS,
                tagName = if (isOwner) "Potential Client" else "Professional Contact",
                reason = reason,
                confidence = CategorySuggestion.Confidence.HIGH,
            )
        }

        // A work email domain implies an employer the card didn't spell out.
        val domain = workEmailDomain(emails)
        if (domain != null) {
            return CategorySuggestion(
                context = RelationshipContext.BUSINESS,
                tagName = "Professional Contact",
                reason = "Work email at $domain",
                confidence = CategorySuggestion.Confidence.MEDIUM,
            )
        }

        // Nothing on the card distinguishes a friend from a plumber.
        return null
    }

    /** The first email domain that isn't a consumer mail provider. */
    fun workEmailDomain(addresses: List<String>): String? {
        for (address in addresses) {
            // Swift's split drops empty pieces: "@acme.co" is one part, not two.
            val parts = address.lowercase().splitCharacters { it == "@" }
            if (parts.size != 2) continue
            val domain = parts[1]
            if (domain !in personalEmailDomains) return domain
        }
        return null
    }

    /** Suggestions for a whole import, keyed by contact identifier; unplaceable contacts are absent. */
    fun suggestions(contacts: List<ContactSummary>): Map<String, CategorySuggestion> {
        val result = mutableMapOf<String, CategorySuggestion>()
        for (contact in contacts) {
            suggestion(contact)?.let { result[contact.identifier] = it }
        }
        return result
    }
}

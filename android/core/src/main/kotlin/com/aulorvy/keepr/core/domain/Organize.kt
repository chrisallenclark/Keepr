package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.LinkRole
import com.aulorvy.keepr.core.model.ModelContext
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.PersonLink
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.model.TagKind
import com.aulorvy.keepr.core.model.localizedCaseInsensitiveCompare
import java.time.Instant

// The decisions behind the Organize screens — groups, relationship types and
// links — translated from GroupsView.swift, GroupEditor.swift,
// RelationshipTypeEditor.swift and PersonLinkEditor.swift. Kept out of the
// Compose files so they can be unit-tested on the JVM.

/** Groups: what a relationship comes through. */
object GroupRules {

    /** "12 people" / "1 person" / "No one yet". */
    fun memberCountLabel(count: Int): String = when (count) {
        0 -> "No one yet"
        1 -> "1 person"
        else -> "$count people"
    }

    /** The confirmation in the groups list. */
    fun listDeleteMessage(memberCount: Int): String = when (memberCount) {
        0 -> "Nothing else changes — no one is here."
        1 -> "The person there stays in Keepr — only the place is removed."
        else -> "The $memberCount people there stay in Keepr — only the place is removed."
    }

    /** The confirmation inside the editor. */
    fun editorDeleteMessage(memberCount: Int): String = when (memberCount) {
        0 -> "Nobody is here, so nothing else changes."
        1 -> "The person there stays in Keepr — only the place is removed."
        else -> "The $memberCount people there stay in Keepr — only the place is removed."
    }

    /**
     * Suggestions the user has already acted on are dropped, so the row
     * shrinks as the list fills in rather than offering duplicates.
     */
    fun openSuggestions(existing: List<PersonGroup>): List<PersonGroup.Suggestion> =
        PersonGroup.suggestions.filter { suggestion ->
            existing.none { localizedCaseInsensitiveCompare(it.name, suggestion.name) == 0 }
        }

    fun nextSortOrder(existing: List<PersonGroup>): Int = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 10

    /**
     * Saves the editor: updates [group], or creates a new one at the end of
     * the list. A new group gets [addingTo] as its first member — the profile
     * opened the editor to place that person, so they shouldn't have to go and
     * find the group again. Returns the saved group; the caller saves the context.
     */
    fun save(
        context: ModelContext,
        group: PersonGroup?,
        name: String,
        detail: String,
        aliases: String,
        symbolName: String,
        relationshipContext: RelationshipContext,
        addingTo: Person? = null,
        now: Instant = Instant.now(),
    ): PersonGroup {
        val resolvedName = name.trimmingWhitespacesAndNewlines()
        val resolvedDetail = detail.trimmingWhitespacesAndNewlines().ifEmpty { null }
        val resolvedAliases = aliases.trimmingWhitespacesAndNewlines().ifEmpty { null }
        val saved = if (group != null) {
            group.name = resolvedName
            group.detail = resolvedDetail
            group.aliases = resolvedAliases
            group.symbolName = symbolName
            group.context = relationshipContext
            group
        } else {
            PersonGroup(
                name = resolvedName,
                symbolName = symbolName,
                detail = resolvedDetail,
                aliases = resolvedAliases,
                context = relationshipContext,
                sortOrder = nextSortOrder(context.fetch()),
            ).also {
                it.createdAt = now
                context.insert(it)
            }
        }
        if (group == null && addingTo != null && !addingTo.isMember(saved)) {
            addingTo.groups = addingTo.groupList + saved
            addingTo.touch(now)
        }
        return saved
    }
}

/** Relationship types: built-in and the user's own, per Business/Personal. */
object RelationshipTypeRules {

    /**
     * Trimmed and case-insensitive within a kind: two "Prospect"s would be
     * indistinguishable in every picker in the app.
     */
    fun nameIsTaken(
        name: String,
        kind: TagKind,
        among: List<RelationshipTag>,
        excluding: RelationshipTag? = null,
    ): Boolean {
        val trimmed = name.trimmingWhitespacesAndNewlines()
        return among.any { tag ->
            tag.kind == kind &&
                tag.id != excluding?.id &&
                localizedCaseInsensitiveCompare(tag.name.trimmingWhitespacesAndNewlines(), trimmed) == 0
        }
    }

    fun duplicateWarning(name: String, kind: TagKind): String =
        "${kind.title} already has a type called \"${name.trimmingWhitespacesAndNewlines()}\"."

    fun deleteMessage(peopleCount: Int): String = when (peopleCount) {
        0 -> "No one is marked with this type."
        1 -> "One person is marked with this type. They stay in Keepr — only the type goes."
        else -> "$peopleCount people are marked with this type. They stay in Keepr — only the type goes."
    }

    /** The types shown for one side of the switcher, in their order. */
    fun types(of: TagKind, among: List<RelationshipTag>): List<RelationshipTag> =
        among.filter { it.kind == of }.sortedBy { it.sortOrder }

    /**
     * Creates a type at the end of the list, or returns null when the name is
     * empty or already taken in that kind (the caller shows [duplicateWarning]).
     */
    fun create(
        context: ModelContext,
        name: String,
        kind: TagKind,
        symbolName: String,
        now: Instant = Instant.now(),
    ): RelationshipTag? {
        val trimmed = name.trimmingWhitespacesAndNewlines()
        if (trimmed.isEmpty()) return null
        val all = context.fetch<RelationshipTag>()
        if (nameIsTaken(trimmed, kind, all)) return null
        val tag = RelationshipTag(
            name = trimmed,
            kind = kind,
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0) + 10,
            symbolName = symbolName,
        )
        tag.createdAt = now
        context.insert(tag)
        return tag
    }

    /**
     * Renames, re-symbols and sets the rhythm of one type. Returns false (and
     * changes nothing) when the new name is empty or taken.
     */
    fun update(
        tag: RelationshipTag,
        name: String,
        symbolName: String,
        cadenceDays: Int,
        among: List<RelationshipTag>,
    ): Boolean {
        val trimmed = name.trimmingWhitespacesAndNewlines()
        if (trimmed.isEmpty()) return false
        if (nameIsTaken(trimmed, tag.kind, among, excluding = tag)) return false
        tag.name = trimmed
        tag.symbolName = symbolName
        tag.cadenceDays = if (cadenceDays > 0) cadenceDays else null
        return true
    }
}

/** Links between two people. */
object LinkRules {

    /** Everyone who could be the other end: not this person, not already linked. */
    fun candidates(person: Person, people: List<Person>): List<Person> =
        people.filter { it.id != person.id && !it.isDeleted && !person.isLinked(it) }

    /** A role typed by hand; empty when nothing usable was typed. */
    fun customRole(text: String): LinkRole = LinkRole.custom(text.trimmingWhitespacesAndNewlines())

    /**
     * Spells out both directions, because a link that reads correctly from
     * one profile and backwards from the other is the whole risk.
     */
    fun explanation(person: Person, selected: Person?, role: LinkRole): String {
        if (role.name.isEmpty()) return " "
        // iOS reads "They shows up…" before anyone is picked; say it properly.
        val them = selected?.displayName?.let { "$it shows" } ?: "They show"
        return "$them up as ${role.name} on ${person.displayName}'s profile, " +
            "and ${person.displayName} shows up as ${role.inverse} on theirs."
    }

    /** Inserts the link; the caller saves. */
    fun link(
        context: ModelContext,
        person: Person,
        other: Person,
        role: LinkRole,
        note: String,
        now: Instant = Instant.now(),
    ): PersonLink {
        val trimmedNote = note.trimmingWhitespacesAndNewlines()
        val link = PersonLink(
            personA = person,
            personB = other,
            labelAToB = role.name,
            labelBToA = role.inverse,
            note = trimmedNote.ifEmpty { null },
            createdAt = now,
        )
        context.insert(link)
        person.touch(now)
        other.touch(now)
        return link
    }
}

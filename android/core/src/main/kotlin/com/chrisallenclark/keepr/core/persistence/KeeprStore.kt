package com.chrisallenclark.keepr.core.persistence

import com.chrisallenclark.keepr.core.model.ConversationNote
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.ModelContext
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonFile
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.PersonLink
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind

/**
 * First-run seeding, catalog repair and delete-all, translated from
 * Keepr/Persistence/KeeprStore.swift. Storage itself is the platform's
 * (Room on Android); this is the store-independent behavior on top.
 */
object KeeprStore {

    /** Key holding the built-ins this install has already been offered. */
    const val SEEDED_KEYS_DEFAULT = "keepr.seededBuiltInKeys"

    /**
     * Seeds built-in relationship types, including ones added in later
     * versions.
     *
     * Each built-in is offered exactly once *ever*, tracked by key rather than
     * by "is the table empty": someone who installed before "Mentor" existed
     * still receives it on update, and someone who deleted "Vendor" doesn't
     * have it reappear every launch.
     */
    fun seedIfNeeded(context: ModelContext, defaults: Preferences) {
        repairTagCatalog(context)

        val existing = context.fetch<RelationshipTag>()

        // Identity by key *and* by visible name: rows seeded before keys
        // existed carry none.
        val existingKeys = existing.mapNotNull { it.builtInKey }.toSet() + existing.map { it.name }
        var offered = (defaults.stringList(SEEDED_KEYS_DEFAULT) ?: emptyList()).toMutableSet()

        // A store with tags but no record of what was seeded predates this
        // bookkeeping. Treat what it holds as already offered.
        if (offered.isEmpty() && existing.isNotEmpty()) {
            offered = existingKeys.toMutableSet()
        }

        var didInsert = false
        RelationshipTag.builtInCatalog.forEachIndexed { index, builtIn ->
            if (builtIn.name in offered || builtIn.name in existingKeys) return@forEachIndexed
            context.insert(
                RelationshipTag(
                    name = builtIn.name,
                    kind = builtIn.kind,
                    isBuiltIn = true,
                    sortOrder = index * 10,
                    symbolName = builtIn.symbolName,
                    builtInKey = builtIn.name,
                ),
            )
            offered.add(builtIn.name)
            didInsert = true
        }

        // Always write the record, even when nothing was inserted, or the
        // first type the user deletes comes back on the next launch.
        offered.addAll(existingKeys)
        defaults.setStringList(SEEDED_KEYS_DEFAULT, offered.sorted())

        if (!didInsert) return
        context.save()
    }

    /**
     * Merges duplicate relationship types and gives old built-ins their key.
     * People marked with a discarded copy are moved to the survivor first.
     */
    fun repairTagCatalog(context: ModelContext) {
        val all = context.fetch<RelationshipTag>()
        if (all.isEmpty()) return
        var didChange = false

        // Backfill keys first, so a merge keeps the row that knows what it is.
        val catalog = RelationshipTag.builtInCatalog.associateBy { "${it.kind.rawValue}|${it.name}" }
        for (tag in all.filter { it.builtInKey == null }) {
            if (catalog["${tag.kind.rawValue}|${tag.name}"] == null) continue
            tag.builtInKey = tag.name
            tag.isBuiltIn = true
            didChange = true
        }

        val groups = all.groupBy { "${it.kind.rawValue}|${it.name.trim().lowercase()}" }
        for (duplicates in groups.values.filter { it.size > 1 }) {
            // Keep the one people are actually marked with; ties go to the oldest.
            val ordered = duplicates.sortedWith { lhs, rhs ->
                if (lhs.peopleList.size != rhs.peopleList.size) rhs.peopleList.size.compareTo(lhs.peopleList.size)
                else lhs.createdAt.compareTo(rhs.createdAt)
            }
            val survivor = ordered.first()
            for (extra in ordered.drop(1)) {
                for (person in extra.peopleList.filter { p -> p.tagList.none { it.id == survivor.id } }) {
                    person.tags = person.tagList.filter { it.id != extra.id } + survivor
                }
                context.delete(extra)
                didChange = true
            }
        }

        if (!didChange) return
        context.save()
    }

    /**
     * Fetches a tag, creating it if the user has deleted or never had it.
     * [name] doubles as the built-in key, so a renamed built-in is still found.
     */
    fun tag(named: String, kind: TagKind, context: ModelContext): RelationshipTag {
        val all = context.fetch<RelationshipTag>()
        all.firstOrNull { it.builtInKey == named && it.kind == kind }?.let { return it }
        all.firstOrNull { it.name == named && it.kind == kind }?.let { return it }
        val created = RelationshipTag(
            name = named,
            kind = kind,
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0) + 10,
        )
        context.insert(created)
        return created
    }

    /** Wipes every record the app owns; phone contacts are untouched. */
    fun deleteAllData(context: ModelContext, defaults: Preferences) {
        context.deleteAll<Person>()
        context.deleteAll<Interaction>()
        context.deleteAll<Memory>()
        context.deleteAll<FollowUp>()
        context.deleteAll<PersonFile>()
        context.deleteAll<ConversationNote>()
        context.deleteAll<RelationshipTag>()
        context.deleteAll<PersonGroup>()
        context.deleteAll<PersonLink>()
        context.save()
        // Erasing means starting over, so the built-ins come back.
        defaults.remove(SEEDED_KEYS_DEFAULT)
        seedIfNeeded(context, defaults)
    }
}

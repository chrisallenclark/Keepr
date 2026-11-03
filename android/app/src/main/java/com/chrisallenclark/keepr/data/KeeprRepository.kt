package com.chrisallenclark.keepr.data

import com.chrisallenclark.keepr.core.model.ModelContext
import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's data, the way SwiftData's container works on iOS: the whole graph
 * lives in memory as a [ModelContext] the screens read and change directly,
 * and [ModelContext.save] persists it.
 *
 * Each save diffs the graph against what's on disk and writes only changed
 * rows, in one transaction, on a single background writer so saves land in
 * order. Every screen observes [version] and recomputes when it moves.
 */
class KeeprRepository(
    private val database: KeeprDatabase,
    val preferences: Preferences,
    /** Outlives any screen; saves and reminder scheduling run here. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    val context = ModelContext()

    private val _version = MutableStateFlow(0L)
    /** Bumps on every save; screens recompute from [context] when it changes. */
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private var lastWrite: Job? = null
    private var snapshot = RowSnapshot.EMPTY

    init {
        context.onSave = { persist() }
    }

    /** Reads everything from disk into the graph, then seeds built-ins. Call once at launch. */
    suspend fun load() {
        val rows = withContext(writer) {
            val dao = database.dao()
            Rows(
                people = dao.people(), interactions = dao.interactions(), memories = dao.memories(),
                followUps = dao.followUps(), notes = dao.notes(), files = dao.files(), tags = dao.tags(),
                groups = dao.groups(), links = dao.links(), personTags = dao.personTags(), groupMembers = dao.groupMembers(),
            )
        }
        rows.buildGraph(context)
        snapshot = RowSnapshot.of(rows)
        KeeprStore.seedIfNeeded(context, preferences) // saves if it inserted anything
        _isLoaded.value = true
        _version.value++
    }

    /** Waits until every save so far is on disk (tests, and before the app is backgrounded). */
    suspend fun flush() {
        lastWrite?.join()
    }

    private fun persist() {
        val current = context.toRows()
        val changes = snapshot.diff(current, System.currentTimeMillis())
        snapshot = RowSnapshot.of(current)
        _version.value++
        if (changes.isEmpty) return
        val previous = lastWrite
        lastWrite = scope.launch(writer) {
            previous?.join()
            database.dao().apply(changes)
        }
    }
}

/** The rows last written, by table and id, to diff the next save against. */
internal class RowSnapshot private constructor(
    private val people: Map<String, PersonRow>,
    private val interactions: Map<String, InteractionRow>,
    private val memories: Map<String, MemoryRow>,
    private val followUps: Map<String, FollowUpRow>,
    private val notes: Map<String, ConversationNoteRow>,
    private val files: Map<String, PersonFileRow>,
    private val tags: Map<String, RelationshipTagRow>,
    private val groups: Map<String, PersonGroupRow>,
    private val links: Map<String, PersonLinkRow>,
    private val personTags: Map<String, List<String>>,
    private val groupMembers: Map<String, List<String>>,
) {
    fun diff(current: Rows, now: Long): ChangeSet {
        fun <R> changed(old: Map<String, R>, new: List<R>, id: (R) -> String): List<R> =
            new.filter { old[id(it)] != it }
        fun <R> removed(old: Map<String, R>, new: List<R>, id: (R) -> String): List<String> {
            val ids = new.mapTo(HashSet(), id)
            return old.keys.filter { it !in ids }
        }

        val upserted = Rows(
            people = changed(people, current.people) { it.id }.map { it.copy(modifiedAt = now) },
            interactions = changed(interactions, current.interactions) { it.id }.map { it.copy(modifiedAt = now) },
            memories = changed(memories, current.memories) { it.id }.map { it.copy(modifiedAt = now) },
            followUps = changed(followUps, current.followUps) { it.id }.map { it.copy(modifiedAt = now) },
            notes = changed(notes, current.notes) { it.id }.map { it.copy(modifiedAt = now) },
            files = changed(files, current.files) { it.id }.map { it.copy(modifiedAt = now) },
            tags = changed(tags, current.tags) { it.id }.map { it.copy(modifiedAt = now) },
            groups = changed(groups, current.groups) { it.id }.map { it.copy(modifiedAt = now) },
            links = changed(links, current.links) { it.id }.map { it.copy(modifiedAt = now) },
        )
        val deleted = DeletedIds(
            people = removed(people, current.people) { it.id },
            interactions = removed(interactions, current.interactions) { it.id },
            memories = removed(memories, current.memories) { it.id },
            followUps = removed(followUps, current.followUps) { it.id },
            notes = removed(notes, current.notes) { it.id },
            files = removed(files, current.files) { it.id },
            tags = removed(tags, current.tags) { it.id },
            groups = removed(groups, current.groups) { it.id },
            links = removed(links, current.links) { it.id },
        )

        val newTags = membership(current.personTags.map { it.personId to it.tagId })
        val newMembers = membership(current.groupMembers.map { it.personId to it.groupId })
        val livePeople = current.people.mapTo(HashSet()) { it.id }
        val membershipPeople = (newTags.keys + newMembers.keys + personTags.keys + groupMembers.keys)
            .filter { it in livePeople && (personTags[it].orEmpty() != newTags[it].orEmpty() || groupMembers[it].orEmpty() != newMembers[it].orEmpty()) }
            .distinct()
        val selected = membershipPeople.toHashSet()

        val tombstones = listOf(
            "people" to deleted.people, "interactions" to deleted.interactions, "memories" to deleted.memories,
            "follow_ups" to deleted.followUps, "conversation_notes" to deleted.notes, "person_files" to deleted.files,
            "relationship_tags" to deleted.tags, "person_groups" to deleted.groups, "person_links" to deleted.links,
        ).flatMap { (table, ids) -> ids.map { DeletionRow(it, table, now) } }

        return ChangeSet(
            upserted = upserted,
            deleted = deleted,
            membershipPeople = membershipPeople,
            personTags = current.personTags.filter { it.personId in selected },
            groupMembers = current.groupMembers.filter { it.personId in selected },
            tombstones = tombstones,
        )
    }

    companion object {
        val EMPTY = of(Rows())

        fun of(rows: Rows) = RowSnapshot(
            people = rows.people.associateBy { it.id },
            interactions = rows.interactions.associateBy { it.id },
            memories = rows.memories.associateBy { it.id },
            followUps = rows.followUps.associateBy { it.id },
            notes = rows.notes.associateBy { it.id },
            files = rows.files.associateBy { it.id },
            tags = rows.tags.associateBy { it.id },
            groups = rows.groups.associateBy { it.id },
            links = rows.links.associateBy { it.id },
            personTags = membership(rows.personTags.map { it.personId to it.tagId }),
            groupMembers = membership(rows.groupMembers.map { it.personId to it.groupId }),
        ).normalized()

        private fun membership(pairs: List<Pair<String, String>>): Map<String, List<String>> =
            pairs.groupBy({ it.first }, { it.second })
    }

    /** Loaded rows carry their stored modifiedAt; compare with it zeroed, as built rows are. */
    private fun normalized() = RowSnapshot(
        people.mapValues { it.value.copy(modifiedAt = 0) },
        interactions.mapValues { it.value.copy(modifiedAt = 0) },
        memories.mapValues { it.value.copy(modifiedAt = 0) },
        followUps.mapValues { it.value.copy(modifiedAt = 0) },
        notes.mapValues { it.value.copy(modifiedAt = 0) },
        files.mapValues { it.value.copy(modifiedAt = 0) },
        tags.mapValues { it.value.copy(modifiedAt = 0) },
        groups.mapValues { it.value.copy(modifiedAt = 0) },
        links.mapValues { it.value.copy(modifiedAt = 0) },
        personTags,
        groupMembers,
    )
}

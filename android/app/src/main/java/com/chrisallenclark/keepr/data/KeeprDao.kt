package com.chrisallenclark.keepr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * Reads the whole graph at launch and writes changes in one transaction.
 * The app works on an in-memory graph (as SwiftData does); this is only the
 * load and the save underneath it.
 */
@Dao
interface KeeprDao {
    @Query("SELECT * FROM people") suspend fun people(): List<PersonRow>
    @Query("SELECT * FROM interactions") suspend fun interactions(): List<InteractionRow>
    @Query("SELECT * FROM memories") suspend fun memories(): List<MemoryRow>
    @Query("SELECT * FROM follow_ups") suspend fun followUps(): List<FollowUpRow>
    @Query("SELECT * FROM conversation_notes") suspend fun notes(): List<ConversationNoteRow>
    @Query("SELECT * FROM person_files") suspend fun files(): List<PersonFileRow>
    @Query("SELECT * FROM relationship_tags") suspend fun tags(): List<RelationshipTagRow>
    @Query("SELECT * FROM person_groups") suspend fun groups(): List<PersonGroupRow>
    @Query("SELECT * FROM person_links") suspend fun links(): List<PersonLinkRow>
    @Query("SELECT * FROM person_tags ORDER BY position") suspend fun personTags(): List<PersonTagRow>
    @Query("SELECT * FROM person_group_members ORDER BY position") suspend fun groupMembers(): List<GroupMemberRow>
    @Query("SELECT * FROM deletions") suspend fun deletions(): List<DeletionRow>

    @Upsert suspend fun upsertPeople(rows: List<PersonRow>)
    @Upsert suspend fun upsertInteractions(rows: List<InteractionRow>)
    @Upsert suspend fun upsertMemories(rows: List<MemoryRow>)
    @Upsert suspend fun upsertFollowUps(rows: List<FollowUpRow>)
    @Upsert suspend fun upsertNotes(rows: List<ConversationNoteRow>)
    @Upsert suspend fun upsertFiles(rows: List<PersonFileRow>)
    @Upsert suspend fun upsertTags(rows: List<RelationshipTagRow>)
    @Upsert suspend fun upsertGroups(rows: List<PersonGroupRow>)
    @Upsert suspend fun upsertLinks(rows: List<PersonLinkRow>)

    @Query("DELETE FROM people WHERE id IN (:ids)") suspend fun deletePeople(ids: List<String>)
    @Query("DELETE FROM interactions WHERE id IN (:ids)") suspend fun deleteInteractions(ids: List<String>)
    @Query("DELETE FROM memories WHERE id IN (:ids)") suspend fun deleteMemories(ids: List<String>)
    @Query("DELETE FROM follow_ups WHERE id IN (:ids)") suspend fun deleteFollowUps(ids: List<String>)
    @Query("DELETE FROM conversation_notes WHERE id IN (:ids)") suspend fun deleteNotes(ids: List<String>)
    @Query("DELETE FROM person_files WHERE id IN (:ids)") suspend fun deleteFiles(ids: List<String>)
    @Query("DELETE FROM relationship_tags WHERE id IN (:ids)") suspend fun deleteTags(ids: List<String>)
    @Query("DELETE FROM person_groups WHERE id IN (:ids)") suspend fun deleteGroups(ids: List<String>)
    @Query("DELETE FROM person_links WHERE id IN (:ids)") suspend fun deleteLinks(ids: List<String>)

    @Query("DELETE FROM person_tags WHERE personId IN (:personIds)") suspend fun clearPersonTags(personIds: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPersonTags(rows: List<PersonTagRow>)
    @Query("DELETE FROM person_group_members WHERE personId IN (:personIds)") suspend fun clearGroupMembers(personIds: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroupMembers(rows: List<GroupMemberRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDeletions(rows: List<DeletionRow>)

    /** Applies one save: deletes children before parents, writes parents before children. */
    @Transaction
    suspend fun apply(changes: ChangeSet) {
        with(changes.deleted) {
            if (links.isNotEmpty()) deleteLinks(links)
            if (files.isNotEmpty()) deleteFiles(files)
            if (memories.isNotEmpty()) deleteMemories(memories)
            if (followUps.isNotEmpty()) deleteFollowUps(followUps)
            if (notes.isNotEmpty()) deleteNotes(notes)
            if (interactions.isNotEmpty()) deleteInteractions(interactions)
            if (people.isNotEmpty()) deletePeople(people)
            if (tags.isNotEmpty()) deleteTags(tags)
            if (groups.isNotEmpty()) deleteGroups(groups)
        }
        with(changes.upserted) {
            if (people.isNotEmpty()) upsertPeople(people)
            if (tags.isNotEmpty()) upsertTags(tags)
            if (groups.isNotEmpty()) upsertGroups(groups)
            if (interactions.isNotEmpty()) upsertInteractions(interactions)
            if (notes.isNotEmpty()) upsertNotes(notes)
            if (memories.isNotEmpty()) upsertMemories(memories)
            if (followUps.isNotEmpty()) upsertFollowUps(followUps)
            if (files.isNotEmpty()) upsertFiles(files)
            if (links.isNotEmpty()) upsertLinks(links)
        }
        if (changes.membershipPeople.isNotEmpty()) {
            clearPersonTags(changes.membershipPeople)
            clearGroupMembers(changes.membershipPeople)
            if (changes.personTags.isNotEmpty()) insertPersonTags(changes.personTags)
            if (changes.groupMembers.isNotEmpty()) insertGroupMembers(changes.groupMembers)
        }
        if (changes.tombstones.isNotEmpty()) insertDeletions(changes.tombstones)
    }
}

/** Rows of every table, for one load or one batch of writes. */
data class Rows(
    val people: List<PersonRow> = emptyList(),
    val interactions: List<InteractionRow> = emptyList(),
    val memories: List<MemoryRow> = emptyList(),
    val followUps: List<FollowUpRow> = emptyList(),
    val notes: List<ConversationNoteRow> = emptyList(),
    val files: List<PersonFileRow> = emptyList(),
    val tags: List<RelationshipTagRow> = emptyList(),
    val groups: List<PersonGroupRow> = emptyList(),
    val links: List<PersonLinkRow> = emptyList(),
    val personTags: List<PersonTagRow> = emptyList(),
    val groupMembers: List<GroupMemberRow> = emptyList(),
)

data class DeletedIds(
    val people: List<String> = emptyList(),
    val interactions: List<String> = emptyList(),
    val memories: List<String> = emptyList(),
    val followUps: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val links: List<String> = emptyList(),
)

/** What one save writes. [membershipPeople] are people whose type/group lists are rewritten. */
data class ChangeSet(
    val upserted: Rows,
    val deleted: DeletedIds,
    val membershipPeople: List<String>,
    val personTags: List<PersonTagRow>,
    val groupMembers: List<GroupMemberRow>,
    val tombstones: List<DeletionRow>,
) {
    val isEmpty: Boolean
        get() = upserted == Rows() && deleted == DeletedIds() && membershipPeople.isEmpty() && tombstones.isEmpty()
}

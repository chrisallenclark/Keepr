package com.aulorvy.keepr.core.backup

import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.FollowUp
import com.aulorvy.keepr.core.model.Interaction
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.ModelContext
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.PersonLink

/**
 * What a backup (or this phone) holds, in the terms people think in, for
 * the preview before a restore and the confirmation after a backup.
 * Relationship types aren't counted: every phone starts with the built-ins.
 */
data class BackupSummary(
    val people: Int,
    val interactions: Int,
    val memories: Int,
    val followUps: Int,
    val notes: Int,
    val files: Int,
    val groups: Int,
    val links: Int,
) {
    /** Nothing a person made: restoring over this loses nothing. */
    val isEmpty: Boolean
        get() = people == 0 && interactions == 0 && memories == 0 && followUps == 0 && notes == 0 &&
            files == 0 && groups == 0 && links == 0

    /** Rows for a list: ("People", 12), … — empty kinds included, so two lists line up. */
    fun rows(groupPlural: String = "Groups"): List<Pair<String, Int>> = listOf(
        "People" to people,
        "Interactions" to interactions,
        "Memories" to memories,
        "Follow-ups" to followUps,
        "Notes" to notes,
        "Files" to files,
        groupPlural to groups,
        "Connections" to links,
    )

    companion object {
        fun of(manifest: BackupManifest) = BackupSummary(
            people = manifest.count("people"),
            interactions = manifest.count("interactions"),
            memories = manifest.count("memories"),
            followUps = manifest.count("followUps"),
            notes = manifest.count("notes"),
            files = manifest.count("files"),
            groups = manifest.count("groups"),
            links = manifest.count("links"),
        )

        fun of(context: ModelContext) = BackupSummary(
            people = context.fetch<Person>().count { !it.isDeleted },
            interactions = context.fetch<Interaction>().count { !it.isDeleted },
            memories = context.fetch<Memory>().count { !it.isDeleted },
            followUps = context.fetch<FollowUp>().count { !it.isDeleted },
            notes = context.fetch<ConversationNote>().count { !it.isDeleted },
            files = context.fetch<PersonFile>().count { !it.isDeleted },
            groups = context.fetch<PersonGroup>().count { !it.isDeleted },
            links = context.fetch<PersonLink>().count { !it.isDeleted },
        )

        private fun people(count: Int) = if (count == 1) "1 person" else "$count people"

        /** "Replace 3 people with 12 people from the backup?" */
        fun replaceQuestion(onPhone: BackupSummary, inBackup: BackupSummary): String =
            "Replace ${people(onPhone.people)} with ${people(inBackup.people)} from the backup?"
    }
}

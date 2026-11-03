package com.chrisallenclark.keepr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PersonRow::class,
        InteractionRow::class,
        MemoryRow::class,
        FollowUpRow::class,
        ConversationNoteRow::class,
        PersonFileRow::class,
        RelationshipTagRow::class,
        PersonGroupRow::class,
        PersonLinkRow::class,
        PersonTagRow::class,
        GroupMemberRow::class,
        DeletionRow::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KeeprDatabase : RoomDatabase() {
    abstract fun dao(): KeeprDao

    /**
     * Where profile files' bytes are kept (see [FileBlobStore]). Null keeps
     * them inline in `person_files.data`, as a database opened directly
     * (tests) does.
     */
    @Volatile
    var fileBlobs: FileBlobStore? = null

    companion object {
        const val FILE_NAME = "keepr.db"

        /** The user's own data, on disk. */
        fun open(context: Context): KeeprDatabase =
            Room.databaseBuilder(context, KeeprDatabase::class.java, FILE_NAME).build().also {
                it.fileBlobs = DiskFileBlobStore(java.io.File(context.filesDir, DiskFileBlobStore.DIRECTORY))
            }

        /**
         * A throwaway database that lives only in memory — what screenshot
         * (demo) runs and tests use, so sample data never shares a store with
         * the user's own.
         */
        fun inMemory(context: Context): KeeprDatabase =
            Room.inMemoryDatabaseBuilder(context, KeeprDatabase::class.java).build().also {
                it.fileBlobs = MemoryFileBlobStore()
            }
    }
}

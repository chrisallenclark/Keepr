package com.chrisallenclark.keepr.backup

import androidx.room.withTransaction
import com.chrisallenclark.keepr.BuildConfig
import com.chrisallenclark.keepr.core.backup.BackupAppInfo
import com.chrisallenclark.keepr.core.backup.BackupBlobSource
import com.chrisallenclark.keepr.core.backup.BackupFormat
import com.chrisallenclark.keepr.core.backup.BackupManifest
import com.chrisallenclark.keepr.core.backup.BackupReader
import com.chrisallenclark.keepr.core.backup.BackupSettings
import com.chrisallenclark.keepr.core.backup.BackupSummary
import com.chrisallenclark.keepr.core.backup.BackupWriter
import com.chrisallenclark.keepr.core.backup.StagedBackup
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.data.Rows
import com.chrisallenclark.keepr.notifications.rescheduleAllReminders
import com.chrisallenclark.keepr.ui.screens.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** What a finished restore brought back, and the Business/Personal side it was left on. */
data class RestoreResult(val summary: BackupSummary, val mode: ContextMode?)

/**
 * Backing up to, and restoring from, a password-protected .keeprbackup
 * file (the format is in docs/BACKUP_FORMAT.md; its encryption and checks
 * are in core/.../backup). The UI and the device tests both come through
 * here, with streams, so the system file picker is the only part a test
 * can't drive.
 *
 * Backing up reads a consistent snapshot of the database on the store's
 * writer and streams it, encrypted, straight into the destination: no
 * plaintext copy is ever written.
 *
 * Restoring is all-or-nothing:
 * 1. [stage] decrypts and checks the entire file into a private staging
 *    folder — every chunk's authentication, every blob's SHA-256, the
 *    counts, the JSON, every reference — before anything is touched.
 * 2. [restore] then, with saves paused, swaps the file contents in (the
 *    current set kept aside) and replaces every table in ONE database
 *    transaction. If anything throws, the transaction rolls back and the
 *    kept-aside files come back; the database, the files and the in-memory
 *    graph are exactly as they were.
 * 3. Only after the commit: settings, the seen-contacts reset, rebuilding
 *    the in-memory graph from disk, and rescheduling every reminder.
 */
class BackupService(
    private val state: AppState,
    cacheDirectory: File,
) {
    private val repository = state.repository
    private val stagingDirectory = File(cacheDirectory, STAGING_DIRECTORY)

    /**
     * Writes an encrypted backup of everything to [out] (which the caller
     * closes). Call from the main thread; the work runs on the store's
     * writer, so no save lands halfway through. [onProgress] gets 0…1 from
     * a background thread.
     */
    suspend fun backUp(
        out: OutputStream,
        password: CharArray,
        onProgress: (Float) -> Unit = {},
        iterations: Int = BackupFormat.DEFAULT_ITERATIONS,
    ): BackupManifest {
        val settings = BackupSettings.capture(repository.preferences)
        val createdAt = Instant.now()
        return repository.exclusive { database ->
            val dao = database.dao()
            val snapshot = database.withTransaction {
                Rows(
                    people = dao.people(), interactions = dao.interactions(), memories = dao.memories(),
                    followUps = dao.followUps(), notes = dao.notes(), files = dao.files(), tags = dao.tags(),
                    groups = dao.groups(), links = dao.links(), personTags = dao.personTags(),
                    groupMembers = dao.groupMembers(),
                ) to dao.deletions()
            }
            val rows = snapshot.first
            val blobs = database.fileBlobs
            val stored = blobs?.ids().orEmpty()
            val records = rows.toBackupRecords(snapshot.second, settings) { row -> row.data != null || row.id in stored }

            val sources = ArrayList<BackupBlobSource>()
            rows.people.forEach { row ->
                val photo = row.photoData ?: return@forEach
                sources += BackupBlobSource(BackupFormat.personPhotoBlob(row.id)) { ByteArrayInputStream(photo) }
            }
            rows.files.forEach { row ->
                val inline = row.data
                if (inline != null) {
                    sources += BackupBlobSource(BackupFormat.fileDataBlob(row.id)) { ByteArrayInputStream(inline) }
                } else if (blobs != null && row.id in stored) {
                    sources += BackupBlobSource(BackupFormat.fileDataBlob(row.id)) {
                        blobs.open(row.id) ?: throw IOException("A file's contents went missing during the backup")
                    }
                }
                val thumbnail = row.thumbnailData
                if (thumbnail != null) {
                    sources += BackupBlobSource(BackupFormat.fileThumbnailBlob(row.id)) { ByteArrayInputStream(thumbnail) }
                }
            }
            val app = BackupAppInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                databaseSchemaVersion = database.openHelper.readableDatabase.version,
            )
            val progress = Throttle(onProgress)
            BackupWriter.write(
                out = out,
                password = password,
                records = records,
                blobs = sources,
                app = app,
                createdAt = createdAt,
                iterations = iterations,
                onProgress = { done, total -> progress.report(if (total > 0) done.toFloat() / total else 1f) },
            )
        }
    }

    /**
     * Decrypts and checks the whole backup in [input] into the staging
     * folder, without touching anything else. Throws a
     * [com.chrisallenclark.keepr.core.backup.BackupException] (or the
     * input's IOException) if any of it doesn't check out. [totalBytes],
     * when known, turns the bytes read into 0…1 for [onProgress].
     */
    suspend fun stage(
        input: InputStream,
        password: CharArray,
        totalBytes: Long? = null,
        onProgress: (Float) -> Unit = {},
    ): StagedBackup = withContext(Dispatchers.IO) {
        val progress = Throttle(onProgress)
        BackupReader.stage(input.buffered(), password, stagingDirectory) { read ->
            if (totalBytes != null && totalBytes > 0) progress.report(read.toFloat() / totalBytes)
        }
    }

    /** What's on this phone now, for the preview. Main thread. */
    fun currentSummary(): BackupSummary = BackupSummary.of(repository.context)

    /**
     * Replaces everything on this phone with [staged] — see the class
     * comment for how it stays all-or-nothing. Call from the main thread.
     * The staging folder is removed whether it succeeds or not.
     */
    suspend fun restore(staged: StagedBackup): RestoreResult {
        var committed = false
        repository.suspendSaves()
        try {
            repository.exclusive { database ->
                val blobs = database.fileBlobs
                val rows = staged.records.toRows(inlineFileData = blobs == null) { name -> staged.readBlob(name) }
                val deletions = staged.records.deletionRows()
                val incoming: Map<String, File> = if (blobs == null) {
                    emptyMap()
                } else {
                    staged.records.files.mapNotNull { file -> file.dataBlob?.let { file.id to staged.blobFile(it) } }.toMap()
                }
                // The previous contents stay aside until the rows commit.
                val replacement = blobs?.replaceAll(incoming)
                try {
                    database.withTransaction {
                        database.dao().replaceEverything(rows, deletions)
                        afterRowsWrittenForTesting?.invoke()
                    }
                } catch (error: Throwable) {
                    replacement?.rollback()
                    throw error
                }
                replacement?.commit()
            }
            committed = true
        } finally {
            // Nothing changed: carry on with the graph as it was.
            if (!committed) repository.resumeSaves()
            staged.discard()
        }

        val preferences = repository.preferences
        BackupSettings.apply(staged.records.settings, preferences)
        // Contact ids are per device; the next look at the address book re-baselines.
        state.contactTracker.reset()
        repository.reloadAfterRestore()

        // Every reminder comes from the restored data now, through the same
        // path a launch uses (it honours the restored reminders switch and
        // the notification permission this phone has).
        state.notifications.cancelAll()
        state.rescheduleAllReminders()
        return RestoreResult(BackupSummary.of(staged.manifest), ContextMode.from(preferences.string(PreferenceKey.CONTEXT_MODE)))
    }

    /** Throws away a staged backup that won't be restored. */
    fun discard(staged: StagedBackup) {
        staged.discard()
    }

    /** At most one progress report per percent. */
    private class Throttle(private val onProgress: (Float) -> Unit) {
        private var last = -1

        fun report(fraction: Float) {
            val percent = (fraction.coerceIn(0f, 1f) * 100).toInt()
            if (percent == last) return
            last = percent
            onProgress(percent / 100f)
        }
    }

    companion object {
        /** Under the app's cache folder; only ever holds one staged restore. */
        const val STAGING_DIRECTORY = "backup-restore-staging"

        /**
         * Tests only: runs inside the restore's database transaction, after
         * every row is written. Throwing from it must leave everything as it
         * was before the restore started.
         */
        @Volatile
        var afterRowsWrittenForTesting: (() -> Unit)? = null

        private val clearedLeftovers = AtomicBoolean(false)

        /**
         * Once per process: removes a staged restore a previous process left
         * behind (it was killed mid-restore). Nothing can be staging yet.
         */
        fun clearLeftoverStaging(cacheDirectory: File) {
            if (clearedLeftovers.compareAndSet(false, true)) File(cacheDirectory, STAGING_DIRECTORY).deleteRecursively()
        }
    }
}

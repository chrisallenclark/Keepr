package com.aulorvy.keepr.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the contents of profile files live: beside the database, not in it.
 *
 * A PDF or a signed form is stored byte-for-byte and can be many megabytes.
 * In a Room row that would break reading it back — Android's cursor window
 * is 2 MB, and a larger row fails to load at all — and it would make every
 * launch read every file on every profile into memory. So `person_files`
 * keeps the record and the small thumbnail; the bytes are one app-private
 * file per record, named by its id, read only when someone opens it.
 *
 * Everything here runs on the repository's single writer thread or on a
 * background reader; never on the main thread.
 */
interface FileBlobStore {
    fun write(id: String, bytes: ByteArray)
    fun read(id: String): ByteArray?
    /** The bytes as a stream, for copying a large file without holding it in memory (a backup). */
    fun open(id: String): java.io.InputStream? = read(id)?.let { java.io.ByteArrayInputStream(it) }
    fun delete(id: String)
    /** Ids that have bytes stored, for clearing out files whose record is gone. */
    fun ids(): Set<String>

    /**
     * Restoring a backup: puts [incoming] (record id → a staged file with
     * its bytes) in place of everything stored, keeping the previous set
     * aside until [BlobReplacement.commit] (after the database transaction
     * commits) or bringing it back on [BlobReplacement.rollback]. The
     * staged files are moved, not copied, where possible.
     */
    fun replaceAll(incoming: Map<String, File>): BlobReplacement

    /**
     * At launch, after a restore the process didn't live to finish: makes
     * sure every record in [liveIds] has its bytes. File contents never
     * change under an id, so taking the missing ones from the set kept aside
     * is right whichever side of the database commit the restore stopped on.
     */
    fun recoverInterruptedReplace(liveIds: Set<String>) {}
}

/** The other half of [FileBlobStore.replaceAll]. */
interface BlobReplacement {
    /** The database now matches the new set: drop the old one. */
    fun commit()
    /** The database didn't change: put the old set back. */
    fun rollback()
}

/** Moves [source] to [target], falling back to a copy across volumes. */
internal fun moveFile(source: File, target: File) {
    target.parentFile?.mkdirs()
    if (source.renameTo(target)) return
    source.copyTo(target, overwrite = true)
    source.delete()
}

/** The real store: `files/person_files/<id>` in the app's private storage. */
class DiskFileBlobStore(private val directory: File) : FileBlobStore {

    private fun file(id: String) = File(directory, sanitize(id))

    override fun write(id: String, bytes: ByteArray) {
        directory.mkdirs()
        val target = file(id)
        // Write then rename, so a crash mid-write never leaves half a file
        // under the record's name.
        val partial = File(directory, "${sanitize(id)}.partial")
        partial.writeBytes(bytes)
        if (!partial.renameTo(target)) {
            target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
        }
    }

    override fun read(id: String): ByteArray? {
        val target = file(id)
        return if (target.isFile) runCatching { target.readBytes() }.getOrNull() else null
    }

    override fun open(id: String): java.io.InputStream? {
        val target = file(id)
        return if (target.isFile) runCatching { target.inputStream().buffered() }.getOrNull() else null
    }

    override fun delete(id: String) {
        file(id).delete()
    }

    override fun ids(): Set<String> =
        directory.listFiles()?.map { it.name }?.filterNot { it.endsWith(".partial") }?.toSet().orEmpty()

    private val parent: File get() = directory.absoluteFile.parentFile ?: directory
    private val previousDirectory: File get() = File(parent, directory.name + PREVIOUS_SUFFIX)
    private val incomingDirectory: File get() = File(parent, directory.name + INCOMING_SUFFIX)

    override fun replaceAll(incoming: Map<String, File>): BlobReplacement {
        val next = incomingDirectory
        val previous = previousDirectory
        next.deleteRecursively()
        previous.deleteRecursively()
        try {
            if (!next.mkdirs() && !next.isDirectory) throw java.io.IOException("Couldn't make ${next.name}")
            incoming.forEach { (id, file) -> moveFile(file, File(next, sanitize(id))) }
        } catch (error: Throwable) {
            next.deleteRecursively()
            throw error
        }
        // Swap: the current set steps aside, the new one takes its name.
        if (directory.exists() && !directory.renameTo(previous)) {
            next.deleteRecursively()
            throw java.io.IOException("Couldn't set aside the current files")
        }
        if (!next.renameTo(directory)) {
            if (previous.exists()) previous.renameTo(directory)
            next.deleteRecursively()
            throw java.io.IOException("Couldn't put the restored files in place")
        }
        return object : BlobReplacement {
            override fun commit() {
                previous.deleteRecursively()
            }

            override fun rollback() {
                directory.deleteRecursively()
                if (previous.exists()) previous.renameTo(directory)
            }
        }
    }

    override fun recoverInterruptedReplace(liveIds: Set<String>) {
        incomingDirectory.deleteRecursively()
        val previous = previousDirectory
        if (!previous.isDirectory) return
        directory.mkdirs()
        liveIds.forEach { id ->
            val name = sanitize(id)
            val current = File(directory, name)
            val kept = File(previous, name)
            if (!current.isFile && kept.isFile) moveFile(kept, current)
        }
        previous.deleteRecursively()
    }

    /** Ids are UUIDs; anything else is reduced to safe filename characters. */
    private fun sanitize(id: String) = id.filter { it.isLetterOrDigit() || it == '-' }

    companion object {
        const val DIRECTORY = "person_files"
        private const val PREVIOUS_SUFFIX = ".previous"
        private const val INCOMING_SUFFIX = ".incoming"
    }
}

/** For demo (screenshot) runs, whose database lives only in memory too. */
class MemoryFileBlobStore : FileBlobStore {
    private val blobs = ConcurrentHashMap<String, ByteArray>()
    override fun write(id: String, bytes: ByteArray) { blobs[id] = bytes }
    override fun read(id: String): ByteArray? = blobs[id]
    override fun delete(id: String) { blobs.remove(id) }
    override fun ids(): Set<String> = blobs.keys.toSet()

    override fun replaceAll(incoming: Map<String, File>): BlobReplacement {
        val before = HashMap(blobs)
        val next = incoming.mapValues { (_, file) -> file.readBytes() }
        blobs.clear()
        blobs.putAll(next)
        incoming.values.forEach { it.delete() }
        return object : BlobReplacement {
            override fun commit() = Unit

            override fun rollback() {
                blobs.clear()
                blobs.putAll(before)
            }
        }
    }
}

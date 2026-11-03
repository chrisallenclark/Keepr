package com.chrisallenclark.keepr.data

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
    fun delete(id: String)
    /** Ids that have bytes stored, for clearing out files whose record is gone. */
    fun ids(): Set<String>
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

    override fun delete(id: String) {
        file(id).delete()
    }

    override fun ids(): Set<String> =
        directory.listFiles()?.map { it.name }?.filterNot { it.endsWith(".partial") }?.toSet().orEmpty()

    /** Ids are UUIDs; anything else is reduced to safe filename characters. */
    private fun sanitize(id: String) = id.filter { it.isLetterOrDigit() || it == '-' }

    companion object {
        const val DIRECTORY = "person_files"
    }
}

/** For demo (screenshot) runs, whose database lives only in memory too. */
class MemoryFileBlobStore : FileBlobStore {
    private val blobs = ConcurrentHashMap<String, ByteArray>()
    override fun write(id: String, bytes: ByteArray) { blobs[id] = bytes }
    override fun read(id: String): ByteArray? = blobs[id]
    override fun delete(id: String) { blobs.remove(id) }
    override fun ids(): Set<String> = blobs.keys.toSet()
}

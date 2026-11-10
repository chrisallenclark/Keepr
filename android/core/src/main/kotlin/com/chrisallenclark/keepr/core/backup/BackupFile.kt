package com.chrisallenclark.keepr.core.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

/*
 * The plaintext inside the encryption: a sequence of named entries, each
 *
 *   u8 1 ‖ u16 name length ‖ name (UTF-8) ‖ u64 size ‖ size bytes
 *
 * ended by a single u8 0. manifest.json comes first, then records.json, then
 * one entry per blob. All integers are big-endian.
 */

/** A blob to include: its entry name and how to read its bytes (opened twice: once to hash, once to write). */
class BackupBlobSource(val name: String, val open: () -> InputStream)

object BackupWriter {
    private const val ENTRY = 1
    private const val END = 0

    /**
     * Encrypts a complete backup into [out] (not closed). Nothing is
     * written to disk on the way: the records are serialized in memory and
     * each blob streams from its source through the cipher.
     *
     * [onProgress] gets (bytes written, total bytes) of plaintext.
     */
    fun write(
        out: OutputStream,
        password: CharArray,
        records: BackupRecords,
        blobs: List<BackupBlobSource>,
        app: BackupAppInfo,
        createdAt: Instant,
        iterations: Int = BackupFormat.DEFAULT_ITERATIONS,
        chunkSize: Int = BackupFormat.DEFAULT_CHUNK_SIZE,
        random: SecureRandom = SecureRandom(),
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): BackupManifest = write(out, password, records, blobs, app, createdAt, iterations, chunkSize, random, onProgress, Tamper())

    /** Test seams: rewrite the manifest or the records text after they're built. */
    internal class Tamper(
        val manifest: (String) -> String = { it },
        val records: (String) -> String = { it },
        val manifestValue: (BackupManifest) -> BackupManifest = { it },
    )

    internal fun write(
        out: OutputStream,
        password: CharArray,
        records: BackupRecords,
        blobs: List<BackupBlobSource>,
        app: BackupAppInfo,
        createdAt: Instant,
        iterations: Int,
        chunkSize: Int,
        random: SecureRandom,
        onProgress: (Long, Long) -> Unit,
        tamper: Tamper,
    ): BackupManifest {
        require(password.size >= 1) { "A backup needs a password" }
        records.validate()
        val sources = LinkedHashMap<String, BackupBlobSource>()
        blobs.forEach { source -> require(sources.put(source.name, source) == null) { "Two blobs named ${source.name}" } }
        val referenced = records.referencedBlobs
        val missing = referenced - sources.keys
        require(missing.isEmpty()) { "No bytes for ${missing.first()}" }
        val included = sources.values.filter { it.name in referenced }

        val recordsBytes = tamper.records(records.toJson()).toByteArray(Charsets.UTF_8)
        // First pass: hash every blob, so the manifest can lead the file.
        val infos = included.map { source -> source.open().use { hash(source.name, it) } }
        val manifest = tamper.manifestValue(
            BackupManifest(
                formatVersion = BackupFormat.FORMAT_MAJOR,
                formatMinorVersion = BackupFormat.FORMAT_MINOR,
                createdAt = createdAt,
                app = app,
                counts = records.counts,
                records = BackupBlobInfo(BackupFormat.RECORDS_ENTRY, recordsBytes.size.toLong(), sha256Hex(recordsBytes)),
                blobs = infos,
            ),
        )
        val manifestBytes = tamper.manifest(manifest.toJson()).toByteArray(Charsets.UTF_8)
        val total = manifestBytes.size.toLong() + recordsBytes.size + infos.sumOf { it.size }
        var written = 0L

        val (header, keys) = BackupCrypto.newHeader(password, iterations, chunkSize, random)
        val encrypted = BackupEncryptingOutputStream(out, header, keys)
        val data = DataOutputStream(encrypted)

        fun entryHeader(name: String, size: Long) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            require(nameBytes.size <= 0xFFFF)
            data.writeByte(ENTRY)
            data.writeShort(nameBytes.size)
            data.write(nameBytes)
            data.writeLong(size)
        }

        entryHeader(BackupFormat.MANIFEST_ENTRY, manifestBytes.size.toLong())
        data.write(manifestBytes)
        written += manifestBytes.size
        onProgress(written, total)

        entryHeader(BackupFormat.RECORDS_ENTRY, recordsBytes.size.toLong())
        data.write(recordsBytes)
        written += recordsBytes.size
        onProgress(written, total)

        // Second pass: the bytes themselves, checked against the first.
        val buffer = ByteArray(64 * 1024)
        included.zip(infos).forEach { (source, info) ->
            entryHeader(info.name, info.size)
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            source.open().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (count + read > info.size) throw IOException("${info.name} changed while it was being backed up")
                    digest.update(buffer, 0, read)
                    data.write(buffer, 0, read)
                    count += read
                    written += read
                    onProgress(written, total)
                }
            }
            if (count != info.size || hex(digest.digest()) != info.sha256) {
                throw IOException("${info.name} changed while it was being backed up")
            }
        }
        data.writeByte(END)
        data.flush()
        encrypted.finish()
        onProgress(total, total)
        return manifest
    }

    private fun hash(name: String, input: InputStream): BackupBlobInfo {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            size += read
        }
        return BackupBlobInfo(name, size, hex(digest.digest()))
    }
}

/**
 * A backup that has been decrypted, authenticated and checked in full, its
 * blobs sitting in [directory]. Nothing in the app has changed yet. Call
 * [discard] when done with it, whether it was restored or not.
 */
class StagedBackup internal constructor(
    val manifest: BackupManifest,
    val records: BackupRecords,
    val directory: File,
    private val blobs: Map<String, File>,
) {
    /** The staged file holding [name]'s bytes. */
    fun blobFile(name: String): File = blobs[name] ?: throw BackupException.Invalid("No blob $name")

    fun readBlob(name: String): ByteArray = blobFile(name).readBytes()

    fun discard() {
        directory.deleteRecursively()
    }
}

object BackupReader {
    private const val ENTRY = 1
    private const val END = 0
    private const val MAX_JSON_BYTES = 256L * 1024 * 1024

    /** Reads only the header, to tell quickly whether a file is a backup this app can open. */
    fun readHeader(input: InputStream): BackupHeader = BackupHeader.read(input)

    /**
     * Decrypts and checks everything in [input] — every chunk's
     * authentication, every blob's length and SHA-256 against the manifest,
     * the record counts, and that every reference in the records resolves —
     * staging blobs into [stagingDirectory] (emptied first). Returns only
     * when all of it checks out; on any failure the staging directory is
     * removed and a [BackupException] (or the input's own IOException) is
     * thrown. [onProgress] gets the number of file bytes read so far.
     */
    fun stage(
        input: InputStream,
        password: CharArray,
        stagingDirectory: File,
        onProgress: (Long) -> Unit = {},
    ): StagedBackup {
        stagingDirectory.deleteRecursively()
        if (!stagingDirectory.mkdirs() && !stagingDirectory.isDirectory) throw IOException("Couldn't make $stagingDirectory")
        try {
            return stageInto(CountingInputStream(input, onProgress), password, stagingDirectory)
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            if (error is EOFException) throw BackupException.Damaged("ends early", error)
            throw error
        }
    }

    private class Entry(val name: String, val size: Long)

    private fun stageInto(input: InputStream, password: CharArray, directory: File): StagedBackup {
        val header = BackupHeader.read(input)
        val keys = BackupCrypto.open(password, header)
        val plain = BackupDecryptingInputStream(input, header, keys)
        val data = DataInputStream(plain)

        fun nextEntry(): Entry? {
            val type = data.read()
            if (type < 0) throw BackupException.Invalid("no end marker")
            if (type == END) return null
            if (type != ENTRY) throw BackupException.Invalid("unknown entry type $type")
            val nameBytes = ByteArray(data.readUnsignedShort())
            data.readFully(nameBytes)
            val size = data.readLong()
            if (size < 0) throw BackupException.Invalid("negative entry size")
            return Entry(String(nameBytes, Charsets.UTF_8), size)
        }

        fun readSmall(entry: Entry): ByteArray {
            if (entry.size > MAX_JSON_BYTES) throw BackupException.Invalid("${entry.name} is too large")
            return ByteArray(entry.size.toInt()).also { data.readFully(it) }
        }

        val first = nextEntry() ?: throw BackupException.Invalid("empty backup")
        if (first.name != BackupFormat.MANIFEST_ENTRY) throw BackupException.Invalid("manifest.json isn't first")
        val manifest = BackupManifest.fromJson(String(readSmall(first), Charsets.UTF_8))
        val expected = LinkedHashMap<String, BackupBlobInfo>()
        manifest.blobs.forEach { info ->
            if (!info.name.startsWith("blobs/")) throw BackupException.Invalid("blob name ${info.name}")
            if (expected.put(info.name, info) != null) throw BackupException.Invalid("${info.name} listed twice")
        }

        var recordsBytes: ByteArray? = null
        val staged = LinkedHashMap<String, File>()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val entry = nextEntry() ?: break
            when {
                entry.name == BackupFormat.RECORDS_ENTRY -> {
                    if (recordsBytes != null) throw BackupException.Invalid("records.json twice")
                    val bytes = readSmall(entry)
                    if (bytes.size.toLong() != manifest.records.size || sha256Hex(bytes) != manifest.records.sha256) {
                        throw BackupException.Damaged("records.json doesn't match the manifest")
                    }
                    recordsBytes = bytes
                }
                entry.name.startsWith("blobs/") -> {
                    val info = expected[entry.name] ?: throw BackupException.Invalid("${entry.name} isn't in the manifest")
                    if (entry.name in staged) throw BackupException.Invalid("${entry.name} twice")
                    if (entry.size != info.size) throw BackupException.Damaged("${entry.name} has the wrong length")
                    val file = File(directory, "blob-${staged.size}")
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.outputStream().buffered().use { out ->
                        var remaining = entry.size
                        while (remaining > 0) {
                            val read = data.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (read < 0) throw EOFException()
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                            remaining -= read
                        }
                    }
                    if (hex(digest.digest()) != info.sha256) throw BackupException.Damaged("${entry.name} doesn't match its hash")
                    staged[entry.name] = file
                }
                // An entry a later minor version added: skipped, as unknown fields are.
                else -> {
                    var remaining = entry.size
                    while (remaining > 0) {
                        val read = data.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw EOFException()
                        remaining -= read
                    }
                }
            }
        }
        // The end marker must be the very end of the authenticated stream;
        // reading on is what verifies the final chunk.
        if (data.read() != -1 || !plain.isComplete) throw BackupException.Damaged("data after the end marker")

        val recordsData = recordsBytes ?: throw BackupException.Invalid("no records.json")
        val absent = expected.keys - staged.keys
        if (absent.isNotEmpty()) throw BackupException.Damaged("${absent.first()} is missing")

        val records = BackupRecords.fromJson(String(recordsData, Charsets.UTF_8))
        records.counts.forEach { (kind, count) ->
            val listed = manifest.counts[kind] ?: throw BackupException.Invalid("manifest has no count for $kind")
            if (listed != count) throw BackupException.Damaged("$kind: manifest says $listed, records hold $count")
        }
        records.validate()
        val unbacked = records.referencedBlobs - staged.keys
        if (unbacked.isNotEmpty()) throw BackupException.Invalid("${unbacked.first()} is referenced but not stored")
        return StagedBackup(manifest, records, directory, staged)
    }
}

/** Counts what's read, for progress. */
private class CountingInputStream(input: InputStream, private val onProgress: (Long) -> Unit) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

    private fun advance(by: Long) {
        count += by
        onProgress(count)
    }
}

internal fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

internal fun hex(bytes: ByteArray): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(bytes.size * 2)
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        out.append(digits[value ushr 4]).append(digits[value and 0x0F])
    }
    return out.toString()
}

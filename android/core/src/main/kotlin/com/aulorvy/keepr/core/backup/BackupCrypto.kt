package com.aulorvy.keepr.core.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/*
 * The encrypted envelope of a .keeprbackup file (see docs/BACKUP_FORMAT.md):
 *
 *   header ‖ chunk 0 ‖ chunk 1 ‖ … ‖ chunk n (final)
 *
 * The header says how the key was derived from the password. Everything
 * after it is the plaintext cut into fixed-size chunks, each sealed on its
 * own with AES-256-GCM so a backup of any size streams through a small
 * buffer. Each chunk's nonce carries its position, and its associated data
 * carries the whole header, its position and whether it's the last one, so
 * changing any header byte, swapping, dropping, repeating or appending
 * chunks, or cutting the file short all fail authentication (the "STREAM"
 * construction of Hoang, Reyhanitabar, Rogaway and Vizár).
 */

/** The authenticated header at the start of every backup. [bytes] is exactly what's on disk. */
class BackupHeader private constructor(
    val formatMajor: Int,
    val formatMinor: Int,
    val kdfId: Int,
    val iterations: Int,
    val salt: ByteArray,
    val cipherId: Int,
    val noncePrefix: ByteArray,
    val chunkSize: Int,
    val passwordCheck: ByteArray,
    val bytes: ByteArray,
) {
    companion object {
        val MAGIC: ByteArray = "KEEPRBK1".toByteArray(Charsets.US_ASCII)
        const val KDF_PBKDF2_HMAC_SHA256 = 1
        const val CIPHER_AES_256_GCM_CHUNKED = 1
        const val SALT_LENGTH = 16
        const val NONCE_PREFIX_LENGTH = 8
        const val PASSWORD_CHECK_LENGTH = 16

        /** The fixed part every version shares: magic, major, minor, header length. */
        private const val PREAMBLE_LENGTH = 14
        private const val MAX_HEADER_LENGTH = 4096
        /** Accepted from a file; anything outside is a damaged (or hostile) header. */
        private const val MAX_ITERATIONS = 10_000_000
        private const val MIN_CHUNK = 1024
        private const val MAX_CHUNK = 16 * 1024 * 1024

        fun create(iterations: Int, salt: ByteArray, noncePrefix: ByteArray, chunkSize: Int, passwordCheck: ByteArray): BackupHeader {
            require(salt.size == SALT_LENGTH && noncePrefix.size == NONCE_PREFIX_LENGTH && passwordCheck.size == PASSWORD_CHECK_LENGTH)
            require(iterations in 1..MAX_ITERATIONS && chunkSize in MIN_CHUNK..MAX_CHUNK)
            val body = ByteArrayOutputStream()
            DataOutputStream(body).apply {
                writeByte(KDF_PBKDF2_HMAC_SHA256)
                writeInt(iterations)
                writeByte(salt.size)
                write(salt)
                writeByte(CIPHER_AES_256_GCM_CHUNKED)
                writeByte(noncePrefix.size)
                write(noncePrefix)
                writeInt(chunkSize)
                writeByte(passwordCheck.size)
                write(passwordCheck)
                flush()
            }
            val bodyBytes = body.toByteArray()
            val all = ByteArrayOutputStream()
            DataOutputStream(all).apply {
                write(MAGIC)
                writeShort(BackupFormat.FORMAT_MAJOR)
                writeShort(BackupFormat.FORMAT_MINOR)
                writeShort(PREAMBLE_LENGTH + bodyBytes.size)
                write(bodyBytes)
                flush()
            }
            return BackupHeader(
                BackupFormat.FORMAT_MAJOR, BackupFormat.FORMAT_MINOR, KDF_PBKDF2_HMAC_SHA256, iterations, salt.copyOf(),
                CIPHER_AES_256_GCM_CHUNKED, noncePrefix.copyOf(), chunkSize, passwordCheck.copyOf(), all.toByteArray(),
            )
        }

        /** Reads and checks the header; leaves [input] at the first chunk. */
        fun read(input: InputStream): BackupHeader {
            val preamble = ByteArray(PREAMBLE_LENGTH)
            if (readFully(input, preamble) < PREAMBLE_LENGTH) throw BackupException.NotABackup()
            if (!preamble.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw BackupException.NotABackup()
            val buffer = ByteBuffer.wrap(preamble, MAGIC.size, PREAMBLE_LENGTH - MAGIC.size)
            val major = buffer.short.toInt() and 0xFFFF
            val minor = buffer.short.toInt() and 0xFFFF
            val length = buffer.short.toInt() and 0xFFFF
            // A newer major version may lay out everything after this point
            // differently, so it's refused before reading any further.
            if (major > BackupFormat.FORMAT_MAJOR) throw BackupException.NewerVersion()
            if (major < 1 || length < PREAMBLE_LENGTH || length > MAX_HEADER_LENGTH) throw BackupException.Damaged("header length $length")
            val rest = ByteArray(length - PREAMBLE_LENGTH)
            if (readFully(input, rest) < rest.size) throw BackupException.Damaged("header cut short")
            try {
                val data = DataInputStream(rest.inputStream())
                val kdf = data.readUnsignedByte()
                val iterations = data.readInt()
                val salt = ByteArray(data.readUnsignedByte()).also { data.readFully(it) }
                val cipher = data.readUnsignedByte()
                val prefix = ByteArray(data.readUnsignedByte()).also { data.readFully(it) }
                val chunkSize = data.readInt()
                val check = ByteArray(data.readUnsignedByte()).also { data.readFully(it) }
                // Anything after this is a later minor version's addition; it's
                // still covered by authentication because the whole header is.
                if (kdf != KDF_PBKDF2_HMAC_SHA256 || cipher != CIPHER_AES_256_GCM_CHUNKED) throw BackupException.Damaged("unknown algorithm $kdf/$cipher")
                if (iterations !in 1..MAX_ITERATIONS) throw BackupException.Damaged("iteration count $iterations")
                if (salt.size !in 8..64 || prefix.size != NONCE_PREFIX_LENGTH || check.size !in 8..32) throw BackupException.Damaged("field lengths")
                if (chunkSize !in MIN_CHUNK..MAX_CHUNK) throw BackupException.Damaged("chunk size $chunkSize")
                return BackupHeader(major, minor, kdf, iterations, salt, cipher, prefix, chunkSize, check, preamble + rest)
            } catch (error: EOFException) {
                throw BackupException.Damaged("header fields cut short", error)
            }
        }
    }
}

/** The two values a password yields: the chunk key, and a check that tells a wrong password from damage. */
class BackupKeys internal constructor(internal val encryption: SecretKeySpec, val passwordCheck: ByteArray)

object BackupCrypto {
    const val TAG_LENGTH = 16
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val ENCRYPTION_LABEL = "Keepr backup v1 encryption key".toByteArray(Charsets.US_ASCII)
    private val CHECK_LABEL = "Keepr backup v1 password check".toByteArray(Charsets.US_ASCII)

    /**
     * PBKDF2-HMAC-SHA256 from the password (Unicode NFC, so the same
     * password typed on another keyboard gives the same key), then two
     * HMAC-SHA256 labels split it into the encryption key and the stored
     * password check. The check costs an attacker as much per guess as
     * trying to decrypt does, so it gives nothing away.
     */
    fun deriveKeys(password: CharArray, salt: ByteArray, iterations: Int): BackupKeys {
        val normalized = Normalizer.normalize(String(password), Normalizer.Form.NFC).toCharArray()
        val spec = PBEKeySpec(normalized, salt, iterations, 256)
        val master = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            normalized.fill('\u0000')
        }
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(master, "HmacSHA256"))
            val encryption = mac.doFinal(ENCRYPTION_LABEL)
            val check = mac.doFinal(CHECK_LABEL).copyOf(BackupHeader.PASSWORD_CHECK_LENGTH)
            return BackupKeys(SecretKeySpec(encryption, "AES"), check)
        } finally {
            master.fill(0)
        }
    }

    /** Derives the keys for [header] and confirms the password before anything is decrypted. */
    fun open(password: CharArray, header: BackupHeader): BackupKeys {
        val keys = deriveKeys(password, header.salt, header.iterations)
        val check = keys.passwordCheck.copyOf(header.passwordCheck.size)
        if (!MessageDigest.isEqual(check, header.passwordCheck)) throw BackupException.WrongPassword()
        return keys
    }

    /** A fresh header (random salt and nonce prefix) and its keys, for writing a new backup. */
    fun newHeader(password: CharArray, iterations: Int, chunkSize: Int, random: SecureRandom): Pair<BackupHeader, BackupKeys> {
        val salt = ByteArray(BackupHeader.SALT_LENGTH).also(random::nextBytes)
        val prefix = ByteArray(BackupHeader.NONCE_PREFIX_LENGTH).also(random::nextBytes)
        val keys = deriveKeys(password, salt, iterations)
        return BackupHeader.create(iterations, salt, prefix, chunkSize, keys.passwordCheck) to keys
    }

    internal fun nonce(header: BackupHeader, index: Long): ByteArray =
        ByteBuffer.allocate(12).put(header.noncePrefix).putInt(index.toInt()).array()

    internal fun associatedData(header: BackupHeader, index: Long, final: Boolean): ByteArray =
        ByteBuffer.allocate(header.bytes.size + 5).put(header.bytes).putInt(index.toInt()).put(if (final) 1 else 0).array()

    internal const val MAX_CHUNKS = 0xFFFFFFFFL
    internal const val TRANSFORM = TRANSFORMATION
}

/**
 * Encrypts everything written to it into [out], after the header. Call
 * [finish] once everything is written: it seals the final chunk. Closing
 * without finishing leaves a file that reads as cut short, never one that
 * looks complete.
 */
class BackupEncryptingOutputStream(
    private val out: OutputStream,
    private val header: BackupHeader,
    private val keys: BackupKeys,
) : OutputStream() {
    private val buffer = ByteArray(header.chunkSize)
    private var filled = 0
    private var index = 0L
    private var finished = false
    private val cipher: Cipher = Cipher.getInstance(BackupCrypto.TRANSFORM)

    init {
        out.write(header.bytes)
    }

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        check(!finished) { "Backup already finished" }
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full buffer is sealed only once more data arrives, so the
            // final chunk is never mistaken for a middle one.
            if (filled == buffer.size) seal(final = false)
            val count = minOf(remaining, buffer.size - filled)
            System.arraycopy(b, offset, buffer, filled, count)
            filled += count
            offset += count
            remaining -= count
        }
    }

    private fun seal(final: Boolean) {
        if (index > BackupCrypto.MAX_CHUNKS) throw IOException("Backup too large")
        cipher.init(Cipher.ENCRYPT_MODE, keys.encryption, GCMParameterSpec(BackupCrypto.TAG_LENGTH * 8, BackupCrypto.nonce(header, index)))
        cipher.updateAAD(BackupCrypto.associatedData(header, index, final))
        out.write(cipher.doFinal(buffer, 0, filled))
        index++
        filled = 0
    }

    /** Seals the last chunk and flushes. */
    fun finish() {
        if (finished) return
        seal(final = true)
        finished = true
        out.flush()
    }

    override fun flush() = out.flush()

    override fun close() = out.close()
}

/**
 * Decrypts a backup after its header. Each chunk is authenticated before
 * any of its bytes are returned, and the stream only reports its end once
 * the chunk marked final has been verified; a file that's cut short, has
 * chunks moved or removed, or has anything after the final chunk throws
 * [BackupException.Damaged] instead.
 *
 * Bytes returned before the end are authentic but may still belong to a
 * truncated backup, so callers stage them and use nothing until the end.
 */
class BackupDecryptingInputStream(
    private val input: InputStream,
    private val header: BackupHeader,
    private val keys: BackupKeys,
) : InputStream() {
    private val sealedSize = header.chunkSize + BackupCrypto.TAG_LENGTH
    private val sealed = ByteArray(sealedSize)
    private var lookahead = -1
    private var plain = ByteArray(0)
    private var position = 0
    private var index = 0L
    private var reachedFinal = false
    private val cipher: Cipher = Cipher.getInstance(BackupCrypto.TRANSFORM)

    /** Whether the final chunk has been read and verified. */
    val isComplete: Boolean get() = reachedFinal && position >= plain.size

    private fun nextChunk(): Boolean {
        if (reachedFinal) return false
        var count = 0
        if (lookahead >= 0) {
            sealed[0] = lookahead.toByte()
            count = 1
            lookahead = -1
        }
        while (count < sealedSize) {
            val read = input.read(sealed, count, sealedSize - count)
            if (read < 0) break
            count += read
        }
        // A chunk is the last one exactly when nothing follows it.
        val final = if (count < sealedSize) {
            true
        } else {
            val next = input.read()
            if (next < 0) true else { lookahead = next; false }
        }
        if (count < BackupCrypto.TAG_LENGTH) throw BackupException.Damaged("ends partway through chunk $index")
        if (index > BackupCrypto.MAX_CHUNKS) throw BackupException.Damaged("too many chunks")
        plain = try {
            cipher.init(Cipher.DECRYPT_MODE, keys.encryption, GCMParameterSpec(BackupCrypto.TAG_LENGTH * 8, BackupCrypto.nonce(header, index)))
            cipher.updateAAD(BackupCrypto.associatedData(header, index, final))
            cipher.doFinal(sealed, 0, count)
        } catch (error: AEADBadTagException) {
            throw BackupException.Damaged("chunk $index failed authentication", error)
        } catch (error: GeneralSecurityException) {
            throw BackupException.Damaged("chunk $index couldn't be decrypted", error)
        }
        position = 0
        index++
        reachedFinal = final
        return true
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val count = read(one, 0, 1)
        return if (count <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (position >= plain.size) {
            if (!nextChunk()) return -1
        }
        val count = minOf(len, plain.size - position)
        System.arraycopy(plain, position, b, off, count)
        position += count
        return count
    }

    override fun close() = input.close()
}

/** Reads until [buffer] is full or the stream ends; returns how many bytes were read. */
internal fun readFully(input: InputStream, buffer: ByteArray): Int {
    var count = 0
    while (count < buffer.size) {
        val read = input.read(buffer, count, buffer.size - count)
        if (read < 0) break
        count += read
    }
    return count
}

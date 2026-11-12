package com.aulorvy.keepr.ui.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.aulorvy.keepr.core.domain.PersonFileLibrary
import com.aulorvy.keepr.core.domain.PersonFileImporter
import com.aulorvy.keepr.core.domain.PersonFilePayload
import com.aulorvy.keepr.core.model.PersonFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A document read from the system picker, ready to insert. */
class ImportedDocument(val payload: PersonFilePayload, val title: String?)

/**
 * Reading what the system pickers hand back — photos, camera shots,
 * documents — and preparing it with the shared [PersonFileImporter] rules.
 * Everything runs off the main thread; the caller inserts the result.
 */
object PersonFileImport {

    /**
     * The largest file accepted. Documents are kept byte-for-byte and read
     * into memory once to store them; past this a phone can run out.
     */
    const val MAX_BYTES: Long = 64L * 1024 * 1024

    class TooLarge : Exception()

    fun codec(context: Context) = AndroidImageCodec(File(context.cacheDir, "pdf-scratch"))

    /** A photo from the photo picker or the camera. Null when it can't be read or decoded. */
    suspend fun photo(context: Context, uri: Uri): PersonFilePayload? = withContext(Dispatchers.IO) {
        val bytes = read(context, uri) ?: return@withContext null
        PersonFileImporter.photo(bytes, codec(context))
    }

    /** Photo bytes already in hand (the camera's file, a test). */
    suspend fun photo(context: Context, bytes: ByteArray): PersonFilePayload? = withContext(Dispatchers.Default) {
        PersonFileImporter.photo(bytes, codec(context))
    }

    /**
     * A file from the document picker. Keeps its own name as the title, and
     * its extension — from the name, else from its type.
     * @throws TooLarge past [MAX_BYTES].
     */
    suspend fun document(context: Context, uri: Uri): ImportedDocument? = withContext(Dispatchers.IO) {
        val name = displayName(context, uri)
        val bytes = read(context, uri) ?: return@withContext null
        val fromName = name?.let(PersonFileLibrary::extension).orEmpty()
        val ext = fromName.ifEmpty {
            context.contentResolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }.orEmpty()
        }
        val payload = PersonFileLibrary.document(bytes, ext, codec(context))
        ImportedDocument(payload, name?.let(PersonFileLibrary::baseName)?.takeIf { it.isNotBlank() })
    }

    private fun read(context: Context, uri: Uri): ByteArray? {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        if (size != null && size > MAX_BYTES) throw TooLarge()
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) throw TooLarge()
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        }.getOrElse { if (it is TooLarge) throw it else null }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    // MARK: - Camera

    /** A fresh private file for the camera app to write into, and the content URI it's handed. */
    fun newCameraTarget(context: Context): Pair<File, Uri> {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "capture-${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, authority(context), file)
    }

    fun cameraFile(context: Context, name: String): File = File(File(context.cacheDir, "camera"), name)

    fun authority(context: Context) = "${context.packageName}.fileprovider"
}

/**
 * Handing a file to another app — Share, or Open In — the way iOS's
 * ShareLink and Quick Look's export do: the bytes are written to a private
 * cache folder and lent through a content URI with read permission only.
 */
object PersonFileSharing {

    fun mimeType(file: PersonFile): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.fileExtension.lowercase()) ?: when (file.kind) {
            com.aulorvy.keepr.core.model.PersonFileKind.IMAGE -> "image/jpeg"
            com.aulorvy.keepr.core.model.PersonFileKind.PDF -> "application/pdf"
            else -> "application/octet-stream"
        }

    /** Writes the file's bytes out for another app to read. Call off the main thread. */
    fun export(context: Context, file: PersonFile): Uri? {
        val bytes = file.data ?: return null
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        // Only the latest export is kept around.
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, file.suggestedFileName)
        return runCatching {
            target.writeBytes(bytes)
            FileProvider.getUriForFile(context, PersonFileImport.authority(context), target)
        }.getOrNull()
    }

    fun share(context: Context, uri: Uri, file: PersonFile): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.title)
            clipData = ClipData.newRawUri(file.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(context, Intent.createChooser(send, null))
    }

    fun openExternally(context: Context, uri: Uri, file: PersonFile): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(context, Intent.createChooser(view, null))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    }
}

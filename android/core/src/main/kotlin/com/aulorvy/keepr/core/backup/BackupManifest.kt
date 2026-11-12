package com.aulorvy.keepr.core.backup

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Fixed values of the backup file format. See docs/BACKUP_FORMAT.md. */
object BackupFormat {
    /** Bumped only for a change older readers can't handle; they refuse a newer major. */
    const val FORMAT_MAJOR = 1
    /** Bumped for additions older readers can safely ignore. */
    const val FORMAT_MINOR = 0

    /** PBKDF2-HMAC-SHA256 rounds for new backups; stored in each header, so it can rise later. */
    const val DEFAULT_ITERATIONS = 600_000
    /** Plaintext bytes per sealed chunk. */
    const val DEFAULT_CHUNK_SIZE = 256 * 1024

    const val FILE_EXTENSION = "keeprbackup"
    const val MIME_TYPE = "application/octet-stream"
    const val MIN_PASSWORD_LENGTH = 8

    const val MANIFEST_ENTRY = "manifest.json"
    const val RECORDS_ENTRY = "records.json"

    fun personPhotoBlob(personId: String) = "blobs/person-photo/$personId"
    fun fileDataBlob(fileId: String) = "blobs/file-data/$fileId"
    fun fileThumbnailBlob(fileId: String) = "blobs/file-thumbnail/$fileId"

    /** "Keepr Backup 2026-11-10.keeprbackup" */
    fun defaultFileName(date: LocalDate) = "Keepr Backup $date.$FILE_EXTENSION"
}

/** One stored entry: its name, length and SHA-256 (lowercase hex). */
data class BackupBlobInfo(val name: String, val size: Long, val sha256: String)

/** Which app wrote the backup. */
data class BackupAppInfo(
    val versionName: String,
    val versionCode: Long,
    val databaseSchemaVersion: Int,
    val platform: String = "android",
)

/**
 * manifest.json, the first entry: what the backup holds, so a reader can
 * check everything else against it — and, through [formatVersion], refuse a
 * backup laid out in a way it doesn't understand.
 */
data class BackupManifest(
    val formatVersion: Int,
    val formatMinorVersion: Int,
    val createdAt: Instant,
    val app: BackupAppInfo,
    /** How many records of each kind (the names [BackupRecords.counts] uses). */
    val counts: Map<String, Int>,
    val records: BackupBlobInfo,
    val blobs: List<BackupBlobInfo>,
) {
    fun count(kind: String): Int = counts[kind] ?: 0

    fun toJson(): String = Json.write(
        linkedMapOf(
            "formatVersion" to formatVersion,
            "formatMinorVersion" to formatMinorVersion,
            "createdAt" to createdAt.toString(),
            "app" to linkedMapOf(
                "platform" to app.platform,
                "versionName" to app.versionName,
                "versionCode" to app.versionCode,
                "databaseSchemaVersion" to app.databaseSchemaVersion,
            ),
            "counts" to counts,
            "records" to blobJson(records),
            "blobs" to blobs.map(::blobJson),
        ),
    )

    companion object {
        private fun blobJson(info: BackupBlobInfo) = linkedMapOf("name" to info.name, "size" to info.size, "sha256" to info.sha256)

        private fun blob(obj: JsonObject) = BackupBlobInfo(obj.string("name"), obj.long("size"), obj.string("sha256").lowercase())

        fun fromJson(text: String): BackupManifest {
            val root = JsonObject.parse(text, "manifest.json")
            val format = root.int("formatVersion")
            // Checked first: a newer layout may not have the fields below at all.
            if (format > BackupFormat.FORMAT_MAJOR) throw BackupException.NewerVersion()
            if (format < 1) throw BackupException.Invalid("formatVersion $format")
            val created = try {
                Instant.parse(root.string("createdAt"))
            } catch (error: DateTimeParseException) {
                throw BackupException.Invalid("createdAt isn't a date")
            }
            val app = root.obj("app")
            val counts = root.obj("counts").let { obj -> obj.map.keys.associateWith { obj.int(it) } }
            return BackupManifest(
                formatVersion = format,
                formatMinorVersion = root.optInt("formatMinorVersion") ?: 0,
                createdAt = created,
                app = BackupAppInfo(
                    versionName = app.optString("versionName") ?: "",
                    versionCode = app.optLong("versionCode") ?: 0,
                    databaseSchemaVersion = app.optInt("databaseSchemaVersion") ?: 0,
                    platform = app.optString("platform") ?: "android",
                ),
                counts = counts,
                records = blob(root.obj("records")),
                blobs = root.objects("blobs").map(::blob),
            )
        }
    }
}

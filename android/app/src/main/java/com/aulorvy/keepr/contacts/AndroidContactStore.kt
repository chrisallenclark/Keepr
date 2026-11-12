package com.aulorvy.keepr.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.aulorvy.keepr.core.persistence.Preferences
import com.aulorvy.keepr.core.services.ContactAccess
import com.aulorvy.keepr.core.services.ContactDataRow
import com.aulorvy.keepr.core.services.ContactHeader
import com.aulorvy.keepr.core.services.ContactMimeType
import com.aulorvy.keepr.core.services.ContactRowAssembler
import com.aulorvy.keepr.core.services.ContactStoreProviding
import com.aulorvy.keepr.core.services.ContactSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's contacts, read through ContactsContract — `LiveContactStore`
 * from Keepr/Services/ContactsService.swift.
 *
 * Read-only: Keepr never writes to the address book. Enumeration runs on the
 * IO dispatcher, since reading a large address book is slow enough to drop
 * frames (iOS uses an actor for the same reason).
 *
 * **Identifier.** A contact's `LOOKUP_KEY`, not its `_ID`. The row id of an
 * aggregated contact changes whenever the provider re-aggregates (a card is
 * linked or unlinked, an account syncs), which would orphan every stored
 * link; the lookup key is the platform's designated long-lived reference and
 * still resolves after re-aggregation via `Contacts.lookupContact`. It is what
 * `CNContact.identifier` is on iOS.
 *
 * **Permission.** Android has no "limited" access and no API for "the user
 * said no": only granted or not. So the app remembers that it has asked, and
 * not-granted-after-asking reads as [ContactAccess.DENIED] — the state where
 * iOS points to Settings. The system prompt itself needs an Activity, so a
 * screen installs [permissionRequester] (see `ContactPermissionHost`) while
 * it's on screen; [requestAccess] uses it.
 */
class AndroidContactStore(
    context: Context,
    private val preferences: Preferences,
) : ContactStoreProviding {

    private val app: Context = context.applicationContext
    private val resolver: ContentResolver get() = app.contentResolver

    /** Shows the system permission prompt and returns whether it was granted. Set by the UI. */
    @Volatile
    var permissionRequester: (suspend () -> Boolean)? = null

    private val isGranted: Boolean
        get() = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override fun authorizationStatus(): ContactAccess = when {
        isGranted -> ContactAccess.AUTHORIZED
        preferences.bool(HAS_ASKED_KEY) -> ContactAccess.DENIED
        else -> ContactAccess.NOT_DETERMINED
    }

    override suspend fun requestAccess(): ContactAccess {
        if (isGranted) return ContactAccess.AUTHORIZED
        val requester = permissionRequester ?: return authorizationStatus()
        val granted = requester()
        preferences.setBool(HAS_ASKED_KEY, true)
        // Re-read rather than trusting the boolean, as iOS does.
        return if (granted || isGranted) ContactAccess.AUTHORIZED else ContactAccess.DENIED
    }

    override suspend fun fetchContacts(): List<ContactSummary> = withContext(Dispatchers.IO) {
        if (!isGranted) return@withContext emptyList()
        try {
            val headers = headers(null, null)
            if (headers.isEmpty()) return@withContext emptyList()
            ContactRowAssembler.assemble(headers, dataRows(null), photos(null))
        } catch (error: RuntimeException) {
            // SecurityException if access was revoked mid-read, or a provider failure.
            Log.e(TAG, "Contact enumeration failed.")
            emptyList()
        }
    }

    override suspend fun contact(identifier: String): ContactSummary? = withContext(Dispatchers.IO) {
        if (!isGranted) return@withContext null
        try {
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(identifier))
            val contactUri = ContactsContract.Contacts.lookupContact(resolver, lookupUri) ?: return@withContext null
            val id = ContentUris.parseId(contactUri)
            val header = headers("${ContactsContract.Contacts._ID} = ?", arrayOf(id.toString())).firstOrNull()
                ?: return@withContext null
            // Keep the identifier the caller holds: it's what's stored on the person.
            ContactRowAssembler.summary(header.copy(lookupKey = identifier), dataRows(id), photos(id)[id])
        } catch (error: RuntimeException) {
            Log.e(TAG, "Contact lookup failed.")
            null
        }
    }

    // MARK: Queries

    private fun headers(selection: String?, args: Array<String>?): List<ContactHeader> {
        val result = mutableListOf<ContactHeader>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY),
            selection,
            args,
            // The device's own contact order, as iOS asks for `.userDefault`.
            ContactsContract.Contacts.SORT_KEY_PRIMARY,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            while (cursor.moveToNext()) {
                val key = cursor.getString(keyColumn) ?: continue
                result += ContactHeader(cursor.getLong(idColumn), key)
            }
        }
        return result
    }

    private fun dataRows(contactId: Long?): List<ContactDataRow> {
        val types = ContactMimeType.all
        val placeholders = types.joinToString(",") { "?" }
        var selection = "${ContactsContract.Data.MIMETYPE} IN ($placeholders)"
        val args = types.toMutableList()
        if (contactId != null) {
            selection += " AND ${ContactsContract.Data.CONTACT_ID} = ?"
            args += contactId.toString()
        }
        val rows = mutableListOf<ContactDataRow>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE) + DATA_COLUMNS,
            selection,
            args.toTypedArray(),
            // Primary values first, so "first non-empty" means the one the user chose.
            "${ContactsContract.Data.CONTACT_ID}, ${ContactsContract.Data.IS_SUPER_PRIMARY} DESC, " +
                "${ContactsContract.Data.IS_PRIMARY} DESC, ${ContactsContract.Data._ID}",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val typeColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val dataColumns = DATA_COLUMNS.map { cursor.getColumnIndexOrThrow(it) }
            while (cursor.moveToNext()) {
                val type = cursor.getString(typeColumn) ?: continue
                rows += ContactDataRow(
                    cursor.getLong(idColumn),
                    type,
                    dataColumns.map { index -> if (cursor.isNull(index)) null else cursor.getString(index) },
                )
            }
        }
        return rows
    }

    /** Thumbnail bytes by contact id: the photo row's PHOTO blob is the thumbnail. */
    private fun photos(contactId: Long?): Map<Long, ByteArray> {
        var selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Photo.PHOTO} IS NOT NULL"
        val args = mutableListOf(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
        if (contactId != null) {
            selection += " AND ${ContactsContract.Data.CONTACT_ID} = ?"
            args += contactId.toString()
        }
        val result = mutableMapOf<Long, ByteArray>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Photo.PHOTO),
            selection,
            args.toTypedArray(),
            "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC, ${ContactsContract.Data._ID}",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val photoColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Photo.PHOTO)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                if (id in result) continue
                cursor.getBlob(photoColumn)?.takeIf { it.isNotEmpty() }?.let { result[id] = it }
            }
        }
        return result
    }

    companion object {
        private const val TAG = "Keepr.Contacts"

        /** Set once the system prompt has been shown, so "not granted" can mean "denied". */
        const val HAS_ASKED_KEY = "keepr.hasRequestedContactAccess"

        private val DATA_COLUMNS = arrayOf(
            ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4, ContactsContract.Data.DATA5, ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7, ContactsContract.Data.DATA8, ContactsContract.Data.DATA9,
            ContactsContract.Data.DATA10,
        )
    }
}

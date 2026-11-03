package com.chrisallenclark.keepr.core.services

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Android's half of Keepr/Services/ContactsService.swift's mapping step.
//
// `LiveContactStore.summary(from:)` turns one CNContact into a ContactSummary.
// On Android a contact arrives as rows: one Contacts row per (aggregated)
// person, and many ContactsContract.Data rows — one per name, phone, email,
// event — each tagged with a MIME type and its values in DATA1…DATA15. The
// cursor reading is platform code; turning rows into summaries is pure, so it
// lives here where it can be tested.

/** The ContactsContract.Data MIME types Keepr reads. Public, stable platform strings. */
object ContactMimeType {
    const val NAME = "vnd.android.cursor.item/name"
    const val ORGANIZATION = "vnd.android.cursor.item/organization"
    const val PHONE = "vnd.android.cursor.item/phone_v2"
    const val EMAIL = "vnd.android.cursor.item/email_v2"
    const val EVENT = "vnd.android.cursor.item/contact_event"
    const val POSTAL = "vnd.android.cursor.item/postal-address_v2"
    const val RELATION = "vnd.android.cursor.item/relation"
    const val NOTE = "vnd.android.cursor.item/note"

    val all: List<String> = listOf(NAME, ORGANIZATION, PHONE, EMAIL, EVENT, POSTAL, RELATION, NOTE)
}

/** One aggregated contact: its row id (for joining) and the identifier Keepr stores. */
data class ContactHeader(val contactId: Long, val lookupKey: String)

/**
 * One ContactsContract.Data row, reduced to what Keepr reads. [data] holds
 * DATA1…DATA10 in order (index 0 is DATA1).
 */
data class ContactDataRow(val contactId: Long, val mimeType: String, val data: List<String?>) {
    /** `DATA[n]`, 1-based like the platform's column names; null when empty. */
    fun column(n: Int): String? = data.getOrNull(n - 1)?.trim()?.takeIf { it.isNotEmpty() }
}

object ContactRowAssembler {

    /** ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY. */
    private const val EVENT_TYPE_BIRTHDAY = "3"

    /**
     * ContactsContract.CommonDataKinds.Relation.TYPE_* → the label iOS's
     * `localizedString(forLabel:)` gives the matching CNLabel, lowercase, so
     * [com.chrisallenclark.keepr.core.domain.ContactCategorizer.familyRelationLabels]
     * reads Android cards the same way.
     */
    val relationLabels: Map<Int, String> = mapOf(
        1 to "assistant",
        2 to "brother",
        3 to "child",
        4 to "partner", // TYPE_DOMESTIC_PARTNER
        5 to "father",
        6 to "friend",
        7 to "manager",
        8 to "mother",
        9 to "parent",
        10 to "partner",
        11 to "referred by",
        12 to "relative",
        13 to "sister",
        14 to "spouse",
    )

    /**
     * Builds one summary per header, in the headers' order (the device's own
     * contact sort, as iOS uses `.userDefault`). [photos] are thumbnail bytes
     * by contact id.
     */
    fun assemble(
        headers: List<ContactHeader>,
        rows: List<ContactDataRow>,
        photos: Map<Long, ByteArray> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ContactSummary> {
        val byContact = rows.groupBy { it.contactId }
        return headers.map { header ->
            summary(header, byContact[header.contactId].orEmpty(), photos[header.contactId], zone)
        }
    }

    fun summary(
        header: ContactHeader,
        rows: List<ContactDataRow>,
        photo: ByteArray? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ContactSummary {
        fun of(type: String) = rows.filter { it.mimeType == type }

        // A unified contact can carry a name row from each linked card; the
        // first one that actually holds a name wins (rows arrive primary first).
        val names = of(ContactMimeType.NAME)
        val name = names.firstOrNull { it.column(2) != null || it.column(3) != null }
        var given = name?.column(2) ?: ""
        val family = name?.column(3) ?: ""
        if (given.isEmpty() && family.isEmpty()) {
            // Some apps fill only the display name. Better that than "No Name".
            given = names.firstNotNullOfOrNull { it.column(1) } ?: ""
        }

        val organizations = of(ContactMimeType.ORGANIZATION)
        val company = organizations.firstNotNullOfOrNull { it.column(1) }
        val title = organizations.firstNotNullOfOrNull { it.column(4) }

        val phones = of(ContactMimeType.PHONE).mapNotNull { it.column(1) }.distinct()
        val emails = of(ContactMimeType.EMAIL).mapNotNull { it.column(1) }.distinct()

        val birthday = of(ContactMimeType.EVENT)
            .filter { it.column(2) == EVENT_TYPE_BIRTHDAY }
            .firstNotNullOfOrNull { row -> row.column(1)?.let { parseBirthday(it, zone) } }

        val postal = of(ContactMimeType.POSTAL).firstNotNullOfOrNull(::postalAddress)

        val relations = of(ContactMimeType.RELATION).mapNotNull { row ->
            val relative = row.column(1)?.trim() ?: return@mapNotNull null
            val type = row.column(2)?.toIntOrNull() ?: 0
            val label = relationLabels[type] ?: row.column(3) ?: "other"
            ContactRelation(label, relative)
        }

        val note = of(ContactMimeType.NOTE).firstNotNullOfOrNull { it.column(1) }

        return ContactSummary(
            identifier = header.lookupKey,
            givenName = given,
            familyName = family,
            organizationName = company,
            jobTitle = title,
            phoneNumbers = phones,
            emailAddresses = emails,
            thumbnailData = photo,
            birthday = birthday,
            postalAddress = postal,
            relations = relations,
            note = note,
        )
    }

    /**
     * The address on one line, as iOS joins `CNPostalAddressFormatter`'s
     * lines with ", ". Uses the card's own formatted address when it has one.
     */
    fun postalAddress(row: ContactDataRow): String? {
        row.column(1)?.let { formatted ->
            val joined = formatted.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            if (joined.isNotEmpty()) return joined
        }
        // STREET(4), CITY(7), REGION(8), POSTCODE(9), COUNTRY(10)
        val regionLine = listOfNotNull(row.column(8), row.column(9)).joinToString(" ")
        val parts = listOfNotNull(row.column(4), row.column(7), regionLine.ifEmpty { null }, row.column(10))
        return parts.joinToString(", ").ifEmpty { null }
    }

    /**
     * Android stores event dates as text, in whatever shape the app that wrote
     * them chose: "1990-06-15", "--06-15" (no year), "19900615", sometimes
     * with a time after it. Midnight in [zone], as iOS's
     * `Calendar.current.date(from:)` gives. A birthday without a year lands
     * in 1604 — the leap year Apple's own Contacts uses for "no year", so
     * February 29 survives.
     */
    fun parseBirthday(raw: String, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val text = raw.trim()
        val patterns = listOf(
            Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""") to intArrayOf(1, 2, 3),
            Regex("""^--(\d{1,2})-?(\d{1,2})$""") to intArrayOf(0, 1, 2),
            Regex("""^(\d{4})(\d{2})(\d{2})$""") to intArrayOf(1, 2, 3),
            Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""") to intArrayOf(3, 1, 2),
        )
        for ((regex, order) in patterns) {
            val match = regex.find(text) ?: continue
            val groups = match.groupValues
            val year = if (order[0] == 0) NO_YEAR else groups[order[0]].toInt()
            val month = groups[order[1]].toInt()
            val day = groups[order[2]].toInt()
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
            return date.atStartOfDay(zone).toInstant()
        }
        return null
    }

    /** The year a yearless birthday is stored under. */
    const val NO_YEAR = 1604
}

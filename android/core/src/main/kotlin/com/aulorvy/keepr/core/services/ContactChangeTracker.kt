package com.aulorvy.keepr.core.services

import com.aulorvy.keepr.core.persistence.Preferences

// Translated from Keepr/Services/ContactChangeTracker.swift. iOS keeps the
// identifier set as JSON `Data` in UserDefaults behind its `KeyValueStoring`
// protocol; here it's a string list in [Preferences] under the same key.

/**
 * Notices contacts added to the phone since Keepr last looked.
 *
 * The point is the window right after you meet someone: for about a day you
 * still remember which bar it was and what they do. So the app watches for
 * cards it hasn't seen before and asks while the answer is still available.
 *
 * Two rules keep this from being annoying:
 * - **The first run establishes a baseline and reports nothing.** An address
 *   book of 900 contacts is not 900 new people to categorize.
 * - **Anything the user has answered — or waved off — is remembered as seen.**
 *   The same contact is never raised twice.
 *
 * Only identifiers are stored. No names, no numbers, nothing readable.
 * Android has no `UserDefaults.standard`, so the app constructs its shared
 * instance with its SharedPreferences-backed [Preferences].
 */
class ContactChangeTracker(private val store: Preferences) {

    private object Key {
        const val KNOWN = "keepr.knownContactIdentifiers"
        const val HAS_BASELINE = "keepr.hasContactBaseline"
    }

    private var known: Set<String> = store.stringList(Key.KNOWN)?.toSet() ?: emptySet()

    val hasBaseline: Boolean get() = store.bool(Key.HAS_BASELINE)

    /**
     * Contacts that weren't there the last time Keepr looked.
     *
     * Also drops identifiers that have left the address book, so deleting and
     * re-adding a contact surfaces them again — and the stored set doesn't
     * grow forever.
     */
    fun newContacts(contacts: List<ContactSummary>): List<ContactSummary> {
        val current = contacts.map { it.identifier }.toSet()

        if (!hasBaseline) {
            record(current)
            store.setBool(Key.HAS_BASELINE, true)
            return emptyList()
        }

        val unseen = contacts.filter { it.identifier !in known }
        val pruned = known intersect current
        if (pruned != known) record(pruned)
        return unseen
    }

    /** Marks contacts as dealt with, whether they were categorized or waved off. */
    fun acknowledge(identifiers: List<String>) {
        if (identifiers.isEmpty()) return
        record(known + identifiers)
    }

    @JvmName("acknowledgeContacts")
    fun acknowledge(contacts: List<ContactSummary>) {
        acknowledge(contacts.map { it.identifier })
    }

    /**
     * Forgets everything, so the next pass re-baselines. Used by Erase All
     * Data — a stale set after a wipe would treat a re-added contact as old.
     */
    fun reset() {
        known = emptySet()
        store.remove(Key.KNOWN)
        store.setBool(Key.HAS_BASELINE, false)
    }

    private fun record(identifiers: Set<String>) {
        known = identifiers
        store.setStringList(Key.KNOWN, identifiers.sorted())
    }
}

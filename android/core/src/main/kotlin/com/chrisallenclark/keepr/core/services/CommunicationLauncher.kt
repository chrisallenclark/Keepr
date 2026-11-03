package com.chrisallenclark.keepr.core.services

import com.chrisallenclark.keepr.core.domain.graphemes
import com.chrisallenclark.keepr.core.domain.isSwiftLetter
import com.chrisallenclark.keepr.core.model.Person

// Translated from Keepr/Services/CommunicationLauncher.swift. The URI comes
// back as a String (what iOS's `URL.absoluteString` would be); the Android app
// turns it into an Intent.

/**
 * The ways Keepr can reach someone. All of these hand off to a system app
 * through a documented URI scheme; Keepr never reads messages, mail or call
 * history — see docs/ARCHITECTURE.md §5.
 */
enum class ContactMethod(val rawValue: String) {
    MESSAGE("message"),
    CALL("call"),
    EMAIL("email");

    val id: String get() = rawValue

    val title: String
        get() = when (this) {
            MESSAGE -> "Message"
            CALL -> "Call"
            EMAIL -> "Email"
        }

    val symbolName: String
        get() = when (this) {
            MESSAGE -> "message.fill"
            CALL -> "phone.fill"
            EMAIL -> "envelope.fill"
        }

    companion object {
        fun from(raw: String?): ContactMethod? = entries.firstOrNull { it.rawValue == raw }
    }
}

object CommunicationLauncher {

    /** The URI that opens the right system app, or null when the person has no number or address for it. */
    fun url(method: ContactMethod, person: Person): String? = when (method) {
        ContactMethod.MESSAGE -> person.primaryPhone?.let { url("sms", dialable(it)) }
        ContactMethod.CALL -> person.primaryPhone?.let { url("tel", dialable(it)) }
        ContactMethod.EMAIL -> person.primaryEmail?.let { url("mailto", it) }
    }

    fun isAvailable(method: ContactMethod, person: Person): Boolean = when (method) {
        ContactMethod.MESSAGE, ContactMethod.CALL -> person.primaryPhone != null
        ContactMethod.EMAIL -> person.primaryEmail != null
    }

    /**
     * Strips formatting `tel:`/`sms:` can't handle, keeping "+" for country codes.
     *
     * Anything from the first letter onward is dropped, so "555-0142 ext 2"
     * dials the number rather than appending the extension's digits and
     * calling someone else.
     */
    fun dialable(phoneNumber: String): String {
        val dialPart = phoneNumber.graphemes().takeWhile { !isSwiftLetter(it) }.joinToString("")
        return dialPart.filter { it == '+' || it in '0'..'9' }
    }

    /** `CharacterSet.urlQueryAllowed`: what may appear unescaped. */
    private const val QUERY_ALLOWED =
        "!$&'()*+,-./0123456789:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz~"

    /**
     * `addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)`: every
     * other byte of the UTF-8 form becomes %XX. A Swift String can't hold an
     * unpaired surrogate (it becomes U+FFFD), so that's what is encoded here too.
     */
    private fun percentEncoded(value: String): String {
        // The JVM would write '?' for an unpaired surrogate; Swift has U+FFFD.
        val sanitized = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (Character.isHighSurrogate(ch) && i + 1 < value.length && Character.isLowSurrogate(value[i + 1])) {
                sanitized.append(ch).append(value[i + 1])
                i += 2
                continue
            }
            sanitized.append(if (Character.isSurrogate(ch)) '�' else ch)
            i += 1
        }
        val out = StringBuilder()
        for (byte in sanitized.toString().toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            if (code < 0x80 && code.toChar() in QUERY_ALLOWED) out.append(code.toChar())
            else out.append("%%%02X".format(code))
        }
        return out.toString()
    }

    private fun url(scheme: String, value: String): String = "$scheme:${percentEncoded(value)}"
}

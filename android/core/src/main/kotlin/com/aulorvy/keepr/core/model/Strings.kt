package com.aulorvy.keepr.core.model

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/*
 * Foundation string operations the iOS logic leans on, written once so every
 * engine compares and folds text the same way.
 */

/** `trimmingCharacters(in: .whitespacesAndNewlines)` */
fun String.trimmed(): String = trim()

/** `trimmingCharacters(in: .whitespaces)` — spaces and tabs only. */
fun String.trimmedSpaces(): String = trim { it.isWhitespace() && it != '\n' && it != '\r' }

/** Nil for nil, empty or whitespace-only — the app's "has a value" test. */
fun String?.nonBlank(): String? = this?.trimmed()?.takeIf { it.isNotEmpty() }

/** `folding(options: [.diacriticInsensitive, .caseInsensitive], locale:)` */
fun String.folded(locale: Locale = Locale.getDefault()): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(locale)

/** `localizedCaseInsensitiveContains` */
fun String.localizedCaseInsensitiveContains(other: String, locale: Locale = Locale.getDefault()): Boolean =
    lowercase(locale).contains(other.lowercase(locale))

/** `range(of:options: [.caseInsensitive, .diacriticInsensitive]) != nil` */
fun String.foldedContains(other: String): Boolean = folded().contains(other.folded())

/**
 * `localizedStandardCompare` — how Finder sorts: case-insensitive, numbers
 * by value ("Item 2" before "Item 10"), then locale order.
 */
fun localizedStandardCompare(a: String, b: String, locale: Locale = Locale.getDefault()): Int {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    val x = chunks(a)
    val y = chunks(b)
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]
        val q = y[i]
        val result = if (p.first().isDigit() && q.first().isDigit()) {
            val byValue = p.trimStart('0').length.compareTo(q.trimStart('0').length)
            if (byValue != 0) byValue else p.trimStart('0').compareTo(q.trimStart('0'))
        } else {
            collator.compare(p, q)
        }
        if (result != 0) return result
    }
    return x.size.compareTo(y.size)
}

/** `localizedCaseInsensitiveCompare` */
fun localizedCaseInsensitiveCompare(a: String, b: String, locale: Locale = Locale.getDefault()): Int =
    Collator.getInstance(locale).apply { strength = Collator.SECONDARY }.compare(a, b)

private fun chunks(text: String): List<String> {
    if (text.isEmpty()) return listOf("")
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var digits = text.first().isDigit()
    for (ch in text) {
        if (ch.isDigit() != digits) {
            out += current.toString()
            current.clear()
            digits = ch.isDigit()
        }
        current.append(ch)
    }
    out += current.toString()
    return out
}

/** `String.capitalized` — first letter of every word upper, the rest lower. */
fun String.capitalizedWords(locale: Locale = Locale.getDefault()): String =
    split(" ").joinToString(" ") { word ->
        word.lowercase(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

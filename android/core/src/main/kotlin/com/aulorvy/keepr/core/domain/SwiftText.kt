package com.aulorvy.keepr.core.domain

import java.text.BreakIterator
import java.text.Normalizer

/*
 * Swift `String`/`Character` semantics the contact-card parsing relies on.
 *
 * Swift iterates a string by Character (extended grapheme cluster), counts in
 * Characters, and answers `isLetter`/`isNumber`/`isWhitespace` from a
 * Character's first Unicode scalar. Kotlin iterates UTF-16 units, so these
 * helpers restore the Swift view wherever the parser's guardrails depend on it
 * ("short markers must be shouted" counts Characters, word boundaries look at
 * the neighbouring Character, and so on).
 */

/** Start offsets of every Character, plus the string's length as the last entry. */
internal fun String.graphemeBoundaries(): IntArray {
    if (isEmpty()) return intArrayOf(0)
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    val result = ArrayList<Int>()
    var boundary = iterator.first()
    while (boundary != BreakIterator.DONE) {
        result += boundary
        boundary = iterator.next()
    }
    return result.toIntArray()
}

/** The string as Swift Characters. */
internal fun String.graphemes(): List<String> {
    val bounds = graphemeBoundaries()
    return (0 until bounds.size - 1).map { substring(bounds[it], bounds[it + 1]) }
}

/** Swift's `String.count`. */
internal val String.characterCount: Int get() = graphemeBoundaries().size - 1

/** `Character.isLetter`: the first scalar has the Unicode Alphabetic property. */
internal fun isSwiftLetter(character: String): Boolean =
    character.isNotEmpty() && Character.isAlphabetic(character.codePointAt(0))

/** `Character.isNumber`: the first scalar has a numeric type. */
internal fun isSwiftNumber(character: String): Boolean {
    if (character.isEmpty()) return false
    return when (Character.getType(character.codePointAt(0)).toByte()) {
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }
}

/** Unicode `White_Space`, which is what Swift's `isWhitespace` tests. */
internal fun isUnicodeWhitespace(codePoint: Int): Boolean = when (codePoint) {
    0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x85 -> true
    else -> when (Character.getType(codePoint).toByte()) {
        Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
        else -> false
    }
}

/** `Character.isWhitespace`. */
internal fun isSwiftWhitespace(character: String): Boolean =
    character.isNotEmpty() && isUnicodeWhitespace(character.codePointAt(0))

/** `split(whereSeparator:)` over Characters, dropping empty pieces as Swift does by default. */
internal fun String.splitCharacters(isSeparator: (String) -> Boolean): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    for (character in graphemes()) {
        if (isSeparator(character)) {
            if (current.isNotEmpty()) result += current.toString()
            current.clear()
        } else {
            current.append(character)
        }
    }
    if (current.isNotEmpty()) result += current.toString()
    return result
}

private fun isSpaceOrTab(codePoint: Int): Boolean =
    codePoint == 0x09 || Character.getType(codePoint).toByte() == Character.SPACE_SEPARATOR

private fun isWhitespaceOrNewline(codePoint: Int): Boolean =
    isSpaceOrTab(codePoint) || codePoint in 0x0A..0x0D || codePoint == 0x85 ||
        codePoint == 0x2028 || codePoint == 0x2029

private fun String.trimmingScalars(trim: (Int) -> Boolean): String {
    var start = 0
    var end = length
    while (start < end) {
        val cp = codePointAt(start)
        if (!trim(cp)) break
        start += Character.charCount(cp)
    }
    while (end > start) {
        val cp = codePointBefore(end)
        if (!trim(cp)) break
        end -= Character.charCount(cp)
    }
    return substring(start, end)
}

/** `trimmingCharacters(in: .whitespacesAndNewlines)`, scalar for scalar. */
internal fun String.trimmingWhitespacesAndNewlines(): String = trimmingScalars(::isWhitespaceOrNewline)

/** `trimmingCharacters(in: .whitespaces)`: Zs and tab only. */
internal fun String.trimmingWhitespaces(): String = trimmingScalars(::isSpaceOrTab)

/** Swift's `String <`: canonical (NFC) scalars compared in order. */
internal fun swiftCompare(a: String, b: String): Int {
    val x = Normalizer.normalize(a, Normalizer.Form.NFC).codePoints().toArray()
    val y = Normalizer.normalize(b, Normalizer.Form.NFC).codePoints().toArray()
    for (i in 0 until minOf(x.size, y.size)) {
        if (x[i] != y[i]) return x[i].compareTo(y[i])
    }
    return x.size.compareTo(y.size)
}

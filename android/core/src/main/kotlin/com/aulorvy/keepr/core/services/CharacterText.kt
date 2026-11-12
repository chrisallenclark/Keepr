package com.aulorvy.keepr.core.services

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/*
 * The Swift `String` behaviour the extractor and titler rely on, made explicit.
 *
 * Swift counts, slices and compares by `Character` (an extended grapheme
 * cluster); Kotlin's `String` is UTF-16 code units. The rules here are counted
 * in characters ("longer than 3", "first 140"), so they go through
 * [graphemes] rather than `length`, which keeps "café" (decomposed) or an emoji
 * from counting as two or seven. Foundation's `trimmingCharacters(in:)` works on
 * Unicode scalars, so trimming here works on code points.
 */

/** The string as Swift `Character`s (extended grapheme clusters). */
internal fun String.graphemes(): List<String> {
    if (isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(this)
    val out = ArrayList<String>(length)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        out += substring(start, end)
        start = end
        end = iterator.next()
    }
    return out
}

/** Swift's `string.count`. */
internal val String.characterCount: Int get() = graphemes().size

/** Swift's `prefix(n)`, in characters. */
internal fun String.prefixCharacters(n: Int): String = graphemes().take(n).joinToString("")

/** Swift's `dropFirst(n)`, in characters. */
internal fun String.dropFirstCharacters(n: Int): String = graphemes().drop(n).joinToString("")

/** `trimmingCharacters(in:)` with a scalar predicate. */
internal fun String.trimmingScalars(inSet: (Int) -> Boolean): String {
    var start = 0
    var end = length
    while (start < end) {
        val cp = codePointAt(start)
        if (!inSet(cp)) break
        start += Character.charCount(cp)
    }
    while (end > start) {
        val cp = codePointBefore(end)
        if (!inSet(cp)) break
        end -= Character.charCount(cp)
    }
    return substring(start, end)
}

/** `trimmingCharacters(in: CharacterSet(charactersIn: chars))` */
internal fun String.trimmingScalars(chars: String): String = trimmingScalars { cp -> cp <= 0xFFFF && chars.indexOf(cp.toChar()) >= 0 }

/** `CharacterSet.whitespaces`: space separators and tab. */
internal fun isWhitespaceScalar(cp: Int): Boolean = cp == '\t'.code || Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()

/** `CharacterSet.punctuationCharacters`: Unicode general category P*. */
internal fun isPunctuationScalar(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
    Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
    Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
    Character.OTHER_PUNCTUATION -> true
    else -> false
}

/** `trimmingCharacters(in: .punctuationCharacters)` */
internal fun String.trimmingPunctuation(): String = trimmingScalars(::isPunctuationScalar)

/** `CharacterSet.whitespacesAndNewlines`: Z*, tab, U+000A–U+000D and U+0085. */
internal fun isWhitespaceOrNewlineScalar(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
    Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
    else -> cp == 0x09 || cp in 0x0A..0x0D || cp == 0x85
}

/** `trimmingCharacters(in: .whitespacesAndNewlines)` */
internal fun String.trimmingWhitespacesAndNewlines(): String = trimmingScalars(::isWhitespaceOrNewlineScalar)

/** `trimmingCharacters(in: .whitespaces)` */
internal fun String.trimmingWhitespaces(): String = trimmingScalars(::isWhitespaceScalar)

/** Swift's `Character.isNewline`. */
internal fun isNewlineCharacter(character: String): Boolean = when (character) {
    "\n", "\r", "\r\n", "\u000B", "\u000C", "\u0085", " ", " " -> true
    else -> false
}

/** Swift's `Character.isWhitespace` (Unicode White_Space of the first scalar). */
internal fun isWhitespaceCharacter(character: String): Boolean {
    if (character.isEmpty()) return false
    val cp = character.codePointAt(0)
    return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85
}

/** Swift's `Character.isLetter`: the first scalar is Alphabetic. */
internal fun isLetterCharacter(character: String): Boolean =
    character.isNotEmpty() && Character.isAlphabetic(character.codePointAt(0))

/** Swift's `Character.isNumber`: the first scalar has a numeric type. */
internal fun isNumberCharacter(character: String): Boolean {
    if (character.isEmpty()) return false
    return when (Character.getType(character.codePointAt(0)).toByte()) {
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }
}

/** Swift's `Character.isUppercase`. */
internal fun isUppercaseCharacter(character: String): Boolean {
    if (character.isEmpty()) return false
    val cp = character.codePointAt(0)
    if (Character.charCount(cp) == character.length) return Character.isUpperCase(cp)
    // A multi-scalar character: uppercase when uppercasing changes nothing and it is cased.
    return character.uppercase() == character && character.lowercase() != character
}

/** Swift's `split(whereSeparator:)` over characters, omitting empty pieces. */
internal fun String.splitCharacters(isSeparator: (String) -> Boolean): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    for (g in graphemes()) {
        if (isSeparator(g)) {
            if (current.isNotEmpty()) out += current.toString()
            current.clear()
        } else {
            current.append(g)
        }
    }
    if (current.isNotEmpty()) out += current.toString()
    return out
}

/** Swift's `split(separator:)` with a one-character separator, omitting empty pieces. */
internal fun String.splitOmittingEmpty(separator: String): List<String> = splitCharacters { it == separator }

/** Canonical form for comparisons Swift makes by canonical equivalence (`==`, `contains`, `Set`). */
internal fun String.canonical(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

/**
 * `enumerateSubstrings(in:options: .bySentences)`, each sentence trimmed.
 *
 * Apple segments with ICU's sentence rules, which always break after a
 * paragraph separator (CR, LF, NEL, LS, PS). The JDK's sentence iterator does
 * not, so paragraphs are split first and each is segmented on its own; within a
 * paragraph the JDK rules agree with ICU's for ordinary prose (no break before
 * a lowercase word after a period, a break after "?" or "!", "3.5" kept whole).
 */
internal fun sentenceSubstrings(text: String): List<String> {
    val out = mutableListOf<String>()
    for (paragraph in paragraphs(text)) {
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT)
        iterator.setText(paragraph)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            out += paragraph.substring(start, end)
            start = end
            end = iterator.next()
        }
    }
    return out
}

private fun paragraphs(text: String): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\r' || c == '\n' || c == '\u0085' || c == ' ' || c == ' ') {
            var end = i + 1
            if (c == '\r' && end < text.length && text[end] == '\n') end++
            out += text.substring(start, end)
            start = end
            i = end
        } else {
            i++
        }
    }
    if (start < text.length) out += text.substring(start)
    return out
}

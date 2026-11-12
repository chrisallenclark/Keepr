package com.aulorvy.keepr.core.backup

/**
 * The small JSON reader and writer the backup format needs. Core has no JSON
 * library (and Android's org.json isn't available on the plain JVM where
 * these tests run), so this handles exactly RFC 8259: objects become
 * [Map]s (insertion-ordered), arrays [List]s, numbers [Long] when they're
 * whole and [Double] otherwise, plus [String], [Boolean] and null.
 */
internal object Json {

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.value(depth = 0)
        parser.skipWhitespace()
        if (!parser.atEnd) throw JsonException("Unexpected text after the JSON value at ${parser.position}")
        return value
    }

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Double -> {
                require(value.isFinite()) { "JSON can't hold $value" }
                out.append(value.toString())
            }
            is Float -> append(out, value.toDouble())
            is String -> appendString(out, value)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    appendString(out, key as? String ?: throw IllegalArgumentException("JSON object keys must be strings"))
                    out.append(':')
                    append(out, item)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    append(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Can't write ${value::class} as JSON")
        }
    }

    private fun appendString(out: StringBuilder, value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ' || char == ' ' || char == ' ') {
                    out.append("\\u").append(Integer.toHexString(char.code).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        var position = 0
        val atEnd: Boolean get() = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) position++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonException("JSON nested too deeply")
            if (atEnd) throw JsonException("Unexpected end of JSON")
            return when (val char = text[position]) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char in '0'..'9') number() else throw JsonException("Unexpected '$char' at $position")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, position)) throw JsonException("Unexpected text at $position")
            position += word.length
            return result
        }

        private fun obj(depth: Int): Map<String, Any?> {
            position++ // {
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { position++; return result }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonException("Expected a key at $position")
                val key = string()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = value(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> { position++; return result }
                    else -> throw JsonException("Expected ',' or '}' at $position")
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            position++ // [
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { position++; return result }
            while (true) {
                skipWhitespace()
                result.add(value(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> { position++; return result }
                    else -> throw JsonException("Expected ',' or ']' at $position")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd) throw JsonException("Unterminated string")
                val char = text[position++]
                when {
                    char == '"' -> return out.toString()
                    char == '\\' -> {
                        if (atEnd) throw JsonException("Unterminated escape")
                        when (val escaped = text[position++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (position + 4 > text.length) throw JsonException("Bad \\u escape")
                                val hex = text.substring(position, position + 4)
                                val code = hex.toIntOrNull(16) ?: throw JsonException("Bad \\u escape")
                                out.append(code.toChar())
                                position += 4
                            }
                            else -> throw JsonException("Bad escape '\\$escaped'")
                        }
                    }
                    char < ' ' -> throw JsonException("Control character in string")
                    else -> out.append(char)
                }
            }
        }

        private fun number(): Any {
            val start = position
            if (peek() == '-') position++
            while (!atEnd && text[position] in '0'..'9') position++
            var isWhole = true
            if (!atEnd && text[position] == '.') {
                isWhole = false
                position++
                while (!atEnd && text[position] in '0'..'9') position++
            }
            if (!atEnd && (text[position] == 'e' || text[position] == 'E')) {
                isWhole = false
                position++
                if (!atEnd && (text[position] == '+' || text[position] == '-')) position++
                while (!atEnd && text[position] in '0'..'9') position++
            }
            val literal = text.substring(start, position)
            if (literal == "-" || literal.isEmpty()) throw JsonException("Bad number at $start")
            if (isWhole) literal.toLongOrNull()?.let { return it }
            return literal.toDoubleOrNull() ?: throw JsonException("Bad number at $start")
        }

        private fun peek(): Char = if (atEnd) throw JsonException("Unexpected end of JSON") else text[position]

        private fun expect(char: Char) {
            if (peek() != char) throw JsonException("Expected '$char' at $position")
            position++
        }
    }

    private const val MAX_DEPTH = 64
}

internal class JsonException(message: String) : Exception(message)

/** Typed reads from a parsed JSON object, failing with a clear message when a required field is missing. */
internal class JsonObject(val map: Map<String, Any?>, private val where: String) {

    private fun missing(key: String): Nothing = throw BackupException.Invalid("$where is missing \"$key\"")
    private fun wrong(key: String, type: String): Nothing = throw BackupException.Invalid("$where has \"$key\" that isn't $type")

    fun has(key: String) = map[key] != null

    fun string(key: String): String = optString(key) ?: missing(key)
    fun optString(key: String): String? = when (val value = map[key]) {
        null -> null
        is String -> value
        else -> wrong(key, "text")
    }

    fun long(key: String): Long = optLong(key) ?: missing(key)
    fun optLong(key: String): Long? = when (val value = map[key]) {
        null -> null
        is Long -> value
        is Double -> if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble()) value.toLong() else wrong(key, "a whole number")
        else -> wrong(key, "a number")
    }

    fun int(key: String): Int = optInt(key) ?: missing(key)
    fun optInt(key: String): Int? = optLong(key)?.let {
        if (it < Int.MIN_VALUE || it > Int.MAX_VALUE) wrong(key, "a small whole number") else it.toInt()
    }

    fun bool(key: String): Boolean = optBool(key) ?: missing(key)
    fun optBool(key: String): Boolean? = when (val value = map[key]) {
        null -> null
        is Boolean -> value
        else -> wrong(key, "true or false")
    }

    fun obj(key: String): JsonObject = optObj(key) ?: missing(key)
    fun optObj(key: String): JsonObject? = when (val value = map[key]) {
        null -> null
        is Map<*, *> -> JsonObject(value.asStringMap(), "$where.$key")
        else -> wrong(key, "an object")
    }

    fun array(key: String): List<Any?> = optArray(key) ?: missing(key)
    fun optArray(key: String): List<Any?>? = when (val value = map[key]) {
        null -> null
        is List<*> -> value
        else -> wrong(key, "a list")
    }

    /** An array of objects; a missing array reads as empty (older or newer writers may leave a table out). */
    fun objects(key: String): List<JsonObject> = optArray(key).orEmpty().mapIndexed { index, item ->
        val itemMap = item as? Map<*, *> ?: throw BackupException.Invalid("$where.$key[$index] isn't an object")
        JsonObject(itemMap.asStringMap(), "$where.$key[$index]")
    }

    companion object {
        fun parse(text: String, where: String): JsonObject {
            val parsed = try {
                Json.parse(text)
            } catch (error: JsonException) {
                throw BackupException.Invalid("$where isn't valid JSON: ${error.message}")
            }
            val map = parsed as? Map<*, *> ?: throw BackupException.Invalid("$where isn't a JSON object")
            return JsonObject(map.asStringMap(), where)
        }

        @Suppress("UNCHECKED_CAST")
        private fun Map<*, *>.asStringMap(): Map<String, Any?> = this as Map<String, Any?>
    }
}

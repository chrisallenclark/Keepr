package com.aulorvy.keepr.core.backup

/**
 * A rough read of how hard a backup password would be to guess, for the
 * hint under the field. Deliberately simple — length matters most, mixing
 * kinds of characters helps, and repeating one character or a well-known
 * pattern doesn't count.
 */
enum class PasswordStrength(val title: String) {
    TOO_SHORT("Too short"),
    WEAK("Weak"),
    FAIR("Fair"),
    STRONG("Strong"),
    VERY_STRONG("Very strong");

    companion object {
        private val common = listOf("password", "12345678", "123456789", "qwerty", "11111111", "abcdefgh", "iloveyou", "keepr")

        fun of(password: String): PasswordStrength {
            if (password.length < BackupFormat.MIN_PASSWORD_LENGTH) return TOO_SHORT
            val lower = password.lowercase()
            if (password.toSet().size <= 2 || common.any { lower.startsWith(it) || lower == it }) return WEAK
            val kinds = listOf(
                password.any { it.isLowerCase() },
                password.any { it.isUpperCase() },
                password.any { it.isDigit() },
                password.any { !it.isLetterOrDigit() },
            ).count { it }
            val score = when {
                password.length >= 20 -> 3
                password.length >= 14 -> 2
                password.length >= 10 -> 1
                else -> 0
            } + (kinds - 1).coerceAtLeast(0)
            return when {
                score >= 5 -> VERY_STRONG
                score >= 3 -> STRONG
                score >= 1 -> FAIR
                else -> WEAK
            }
        }
    }
}

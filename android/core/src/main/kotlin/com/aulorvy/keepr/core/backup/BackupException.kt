package com.aulorvy.keepr.core.backup

/**
 * Why a backup couldn't be read. Every failure while opening a backup ends
 * up as one of these, with a message that can be shown as is; nothing a
 * reader produces is used until the whole file has been checked.
 */
sealed class BackupException(message: String, val detail: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    /** Not a Keepr backup at all (wrong file picked). */
    class NotABackup : BackupException("This file isn't a Keepr backup.")

    /** The password doesn't match the one the backup was made with. */
    class WrongPassword : BackupException("That password doesn't open this backup. Check it and try again.")

    /** Made by a later version of the format than this app understands. */
    class NewerVersion : BackupException("This backup was made by a newer version of Keepr. Update the app, then try again.")

    /** Cut short, altered, or corrupted: an authentication tag, a hash or a length didn't check out. */
    class Damaged(detail: String, cause: Throwable? = null) :
        BackupException("This backup is damaged or has been changed since it was made, so it can't be restored.", detail, cause)

    /** Decrypted and authentic, but its contents don't make sense (a bug in whatever wrote it). */
    class Invalid(detail: String) :
        BackupException("This backup's contents couldn't be read, so it can't be restored.", detail)
}

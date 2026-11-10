package com.chrisallenclark.keepr.ui.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.backup.BackupService
import com.chrisallenclark.keepr.backup.RestoreResult
import com.chrisallenclark.keepr.core.backup.BackupException
import com.chrisallenclark.keepr.core.backup.BackupFormat
import com.chrisallenclark.keepr.core.backup.BackupSummary
import com.chrisallenclark.keepr.core.backup.PasswordStrength
import com.chrisallenclark.keepr.core.backup.StagedBackup
import com.chrisallenclark.keepr.core.domain.GroupVocabulary
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprButtonStyle
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.LabeledRow
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.screens.AppState
import com.chrisallenclark.keepr.ui.screens.ConfirmDialog
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.semibold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/*
 * Back Up to File… and Restore from Backup… (Settings → Data, and the first
 * onboarding page). The system file picker chooses where the file goes —
 * Drive, Downloads, a USB stick — and the work is BackupService's.
 */

/** "Restore from Backup…": the system picker for an existing backup file. Any type, since providers label .keeprbackup differently. */
@Composable
fun rememberBackupFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(arrayOf("*/*")) }
}

private enum class BackupStep { PASSWORD, WORKING, DONE, FAILED }

/** Back Up to File…: a password (twice), then where to save it, then the file is written. */
@Composable
fun BackupSheet(onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val service = remember(state) { BackupService(state, context.cacheDir) }

    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(BackupStep.PASSWORD) }
    var progress by remember { mutableFloatStateOf(0f) }
    var saved by remember { mutableStateOf<BackupSummary?>(null) }
    var failure by remember { mutableStateOf("") }

    val strength = PasswordStrength.of(password)
    val longEnough = password.length >= BackupFormat.MIN_PASSWORD_LENGTH
    val matches = password == confirmation
    val ready = longEnough && matches

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val secret = password.toCharArray()
        step = BackupStep.WORKING
        progress = 0f
        // On the app's scope: a backup in progress finishes even if the sheet is swiped away.
        state.scope.launch {
            try {
                val out = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) }
                    ?: throw IOException("The file couldn't be opened for writing.")
                val manifest = try {
                    service.backUp(out, secret, onProgress = { fraction -> state.scope.launch { progress = fraction } })
                } finally {
                    withContext(Dispatchers.IO) { runCatching { out.close() } }
                }
                saved = BackupSummary.of(manifest)
                step = BackupStep.DONE
                haptics.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // Never leave half a backup behind looking like a whole one.
                withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
                failure = "The backup couldn't be saved there. ${error.message ?: ""}".trim()
                step = BackupStep.FAILED
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    KeeprSheet(onDismiss = onDismiss) {
        when (step) {
            BackupStep.PASSWORD -> SheetHeader(
                "Back Up",
                onCancel = onDismiss,
                confirmTitle = "Next",
                confirmEnabled = ready,
                onConfirm = { createDocument.launch(BackupFormat.defaultFileName(state.calendar.localDate(state.now()))) },
            )
            BackupStep.WORKING -> SheetHeader("Back Up")
            BackupStep.DONE -> SheetHeader("Back Up", confirmTitle = "Done", onConfirm = onDismiss)
            BackupStep.FAILED -> SheetHeader("Back Up", cancelTitle = "Close", onCancel = onDismiss, confirmTitle = "Try Again", onConfirm = { step = BackupStep.PASSWORD })
        }
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            when (step) {
                BackupStep.PASSWORD -> {
                    Intro(
                        symbol = "lock",
                        title = "Back Up to a File",
                        message = "Everyone in Keepr — their notes, memories, interactions, follow-ups and files — plus your settings, " +
                            "in one file locked with a password you choose. Save it to Google Drive, Downloads or anywhere else, " +
                            "and restore it on a new phone.",
                    )
                    SectionSpacer()
                    GroupedSection(
                        header = "Password",
                        footer = when {
                            confirmation.isNotEmpty() && !matches -> "The passwords don't match."
                            password.isNotEmpty() && !longEnough -> "Use at least ${BackupFormat.MIN_PASSWORD_LENGTH} characters."
                            else -> "At least ${BackupFormat.MIN_PASSWORD_LENGTH} characters. A few unrelated words is easy to remember and hard to guess."
                        },
                    ) {
                        row { PasswordField(password, { password = it }, "Password") }
                        row { PasswordField(confirmation, { confirmation = it }, "Confirm Password") }
                        if (password.isNotEmpty()) {
                            row { LabeledStrength(strength) }
                        }
                    }
                    SectionSpacer()
                    GroupedSection {
                        row {
                            ListRow(verticalAlignment = Alignment.Top) {
                                SfSymbol("exclamationmark.triangle", colors.orange, KeeprTheme.typography.body)
                                KText(
                                    "There's no way to recover a forgotten password — not by Keepr, not by anyone. " +
                                        "Without it, this backup can't be opened.",
                                    Modifier.weight(1f),
                                    style = KeeprTheme.typography.subheadline,
                                )
                            }
                        }
                    }
                }
                BackupStep.WORKING -> Working("Encrypting and saving…", progress, "Keep Keepr open until it's done.")
                BackupStep.DONE -> {
                    Status(
                        symbol = "checkmark.circle.fill",
                        tint = colors.green,
                        title = "Backup Saved",
                        message = "Keep the file and its password somewhere safe. On a new phone, choose Restore from Backup when Keepr first opens.",
                    )
                    saved?.let { summary ->
                        SectionSpacer()
                        SummarySection("In This Backup", summary, groupPlural(state))
                    }
                }
                BackupStep.FAILED -> Status(symbol = "exclamationmark.triangle", tint = colors.orange, title = "Couldn't Back Up", message = failure)
            }
        }
    }
}

private enum class RestoreStep { PASSWORD, CHECKING, PREVIEW, RESTORING, DONE, FAILED }

/** Holds what must be cleaned up if the sheet goes away mid-flow. */
private class StagingHolder {
    var staged: StagedBackup? = null
    var gone = false
    var restoring = false
}

/**
 * Restore from Backup…, after the file is picked: password → the whole file
 * checked → what's in it (and what it would replace) → confirm → restored.
 * [onRestored] runs once the restore has committed.
 */
@Composable
fun RestoreSheet(uri: Uri, onDismiss: () -> Unit, onRestored: (RestoreResult) -> Unit) {
    val state = LocalAppState.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val service = remember(state) { BackupService(state, context.cacheDir) }
    val holder = remember { StagingHolder() }
    val fileName = remember(uri) { displayName(context, uri) }

    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(RestoreStep.PASSWORD) }
    var progress by remember { mutableFloatStateOf(0f) }
    var preview by remember { mutableStateOf<StagedBackup?>(null) }
    var onPhone by remember { mutableStateOf(BackupSummary(0, 0, 0, 0, 0, 0, 0, 0)) }
    var restored by remember { mutableStateOf<BackupSummary?>(null) }
    var failure by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }

    // A backup that was checked but not restored is removed when the sheet goes.
    DisposableEffect(Unit) {
        onDispose {
            holder.gone = true
            if (!holder.restoring) holder.staged?.let(service::discard)
            holder.staged = null
        }
    }

    fun openBackup() {
        val secret = password.toCharArray()
        passwordError = null
        step = RestoreStep.CHECKING
        progress = 0f
        state.scope.launch {
            try {
                val size = withContext(Dispatchers.IO) { fileSize(context, uri) }
                val input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }
                    ?: throw IOException("The file couldn't be opened.")
                val staged = try {
                    service.stage(input, secret, size, onProgress = { fraction -> state.scope.launch { progress = fraction } })
                } finally {
                    withContext(Dispatchers.IO) { runCatching { input.close() } }
                }
                if (holder.gone) {
                    service.discard(staged)
                    return@launch
                }
                holder.staged = staged
                preview = staged
                onPhone = service.currentSummary()
                step = RestoreStep.PREVIEW
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (wrong: BackupException.WrongPassword) {
                passwordError = wrong.message
                step = RestoreStep.PASSWORD
            } catch (error: BackupException) {
                failure = error.message ?: "This backup can't be restored."
                step = RestoreStep.FAILED
            } catch (error: Throwable) {
                failure = "The file couldn't be read. ${error.message ?: ""}".trim()
                step = RestoreStep.FAILED
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    fun restoreBackup() {
        val staged = holder.staged ?: return
        holder.restoring = true
        holder.staged = null
        step = RestoreStep.RESTORING
        state.scope.launch {
            try {
                val result = service.restore(staged)
                restored = result.summary
                step = RestoreStep.DONE
                haptics.success()
                onRestored(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = "Nothing on this phone was changed. ${(error as? BackupException)?.message ?: error.message ?: ""}".trim()
                step = RestoreStep.FAILED
            } finally {
                holder.restoring = false
            }
        }
    }

    KeeprSheet(onDismiss = onDismiss) {
        when (step) {
            RestoreStep.PASSWORD -> SheetHeader(
                "Restore",
                onCancel = onDismiss,
                confirmTitle = "Open",
                confirmEnabled = password.isNotEmpty(),
                onConfirm = ::openBackup,
            )
            RestoreStep.CHECKING, RestoreStep.RESTORING -> SheetHeader("Restore")
            RestoreStep.PREVIEW -> SheetHeader("Restore", onCancel = onDismiss)
            RestoreStep.DONE -> SheetHeader("Restore", confirmTitle = "Done", onConfirm = onDismiss)
            RestoreStep.FAILED -> SheetHeader("Restore", cancelTitle = "Close", onCancel = onDismiss)
        }
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            when (step) {
                RestoreStep.PASSWORD -> {
                    Intro(
                        symbol = "clock.arrow.circlepath",
                        title = "Restore from Backup",
                        message = "Enter the password that was chosen when this backup was made. The whole file is checked before anything on this phone changes.",
                    )
                    SectionSpacer()
                    GroupedSection(header = "Backup") {
                        if (fileName != null) row { LabeledRow("File", fileName) }
                        row { PasswordField(password, { password = it; passwordError = null }, "Backup Password", onSubmit = { if (password.isNotEmpty()) openBackup() }) }
                    }
                    passwordError?.let { message ->
                        KText(
                            message,
                            style = KeeprTheme.typography.footnote,
                            color = colors.red,
                            modifier = Modifier.padding(horizontal = 33.dp, vertical = 8.dp),
                        )
                    }
                }
                RestoreStep.CHECKING -> Working("Checking backup…", progress, "Decrypting and verifying every record and file.")
                RestoreStep.PREVIEW -> preview?.let { staged ->
                    val inBackup = BackupSummary.of(staged.manifest)
                    val created = staged.manifest.createdAt
                    Intro(
                        symbol = "checkmark.circle.fill",
                        title = "Backup from ${state.calendar.monthDayYear(created)}",
                        message = "Made at ${state.calendar.shortTime(created)}" +
                            (staged.manifest.app.versionName.takeIf { it.isNotEmpty() }?.let { " with Keepr $it" } ?: "") + ". Everything in it checks out.",
                        tint = colors.green,
                    )
                    SectionSpacer()
                    SummarySection("In This Backup", inBackup, groupPlural(state))
                    if (!onPhone.isEmpty) {
                        SectionSpacer()
                        SummarySection(
                            "On This Phone Now",
                            onPhone,
                            groupPlural(state),
                            footer = "Restoring replaces everything on this phone with what's in the backup. This can't be undone.",
                        )
                    }
                    SectionSpacer()
                    KeeprButton(
                        if (onPhone.isEmpty) "Restore" else "Replace with Backup",
                        onClick = { if (onPhone.isEmpty) restoreBackup() else confirming = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp),
                        style = KeeprButtonStyle.Prominent,
                        isDestructive = !onPhone.isEmpty,
                        textStyle = KeeprTheme.typography.headline,
                    )
                    if (confirming) {
                        ConfirmDialog(
                            title = BackupSummary.replaceQuestion(onPhone, inBackup),
                            message = "Everything on this phone now is replaced by the backup. This can't be undone.",
                            confirmTitle = "Replace",
                            onConfirm = ::restoreBackup,
                            onDismiss = { confirming = false },
                        )
                    }
                }
                RestoreStep.RESTORING -> Working("Restoring…", null, "Keep Keepr open until it's done.")
                RestoreStep.DONE -> {
                    Status(
                        symbol = "checkmark.circle.fill",
                        tint = colors.green,
                        title = "Restore Complete",
                        message = "Everything from the backup is on this phone, and its reminders are set again.",
                    )
                    restored?.let { summary ->
                        SectionSpacer()
                        SummarySection("Restored", summary, groupPlural(state))
                    }
                }
                RestoreStep.FAILED -> Status(symbol = "exclamationmark.triangle", tint = colors.orange, title = "Couldn't Restore", message = failure)
            }
        }
    }
}

// MARK: - Pieces

private fun groupPlural(state: AppState): String =
    GroupVocabulary.plural(state.repository.preferences.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular)

/** `SecureField` in a form row. */
@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, placeholder: String, onSubmit: (() -> Unit)? = null) {
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        visualTransformation = PasswordVisualTransformation(),
        onSubmit = onSubmit,
    )
}

@Composable
private fun LabeledStrength(strength: PasswordStrength) {
    val colors = KeeprTheme.colors
    val tint = when (strength) {
        PasswordStrength.TOO_SHORT, PasswordStrength.WEAK -> colors.red
        PasswordStrength.FAIR -> colors.orange
        PasswordStrength.STRONG, PasswordStrength.VERY_STRONG -> colors.green
    }
    val filled = when (strength) {
        PasswordStrength.TOO_SHORT -> 0
        PasswordStrength.WEAK -> 1
        PasswordStrength.FAIR -> 2
        PasswordStrength.STRONG -> 3
        PasswordStrength.VERY_STRONG -> 4
    }
    ListRow {
        KText("Strength", Modifier.weight(1f))
        Box(Modifier.width(72.dp)) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(4) { index ->
                    Box(
                        Modifier
                            .width(15.dp)
                            .height(5.dp)
                            .background(if (index < filled) tint else colors.tertiaryFill, CircleShape),
                    )
                }
            }
        }
        KText(strength.title, color = tint)
    }
}

/** A symbol, a title and a line of explanation, centered above the form. */
@Composable
private fun Intro(symbol: String, title: String, message: String, tint: Color = KeeprTheme.colors.accent) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        SfSymbol(symbol, tint, 44.dp)
        KText(title, style = KeeprTheme.typography.title3.semibold, textAlign = TextAlign.Center)
        KText(message, style = KeeprTheme.typography.subheadline, color = KeeprTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
    }
}

/** The finished (or failed) state. */
@Composable
private fun Status(symbol: String, tint: Color, title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SfSymbol(symbol, tint, 56.dp)
        KText(title, style = KeeprTheme.typography.title3.semibold, textAlign = TextAlign.Center)
        if (message.isNotEmpty()) {
            KText(message, style = KeeprTheme.typography.subheadline, color = KeeprTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}

/** Work in progress: a title, a bar ([fraction] null for one that just moves) and a note. */
@Composable
private fun Working(title: String, fraction: Float?, note: String) {
    val colors = KeeprTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KText(title, style = KeeprTheme.typography.headline, textAlign = TextAlign.Center)
        ProgressBar(fraction)
        KText(note, style = KeeprTheme.typography.footnote, color = colors.secondaryLabel, textAlign = TextAlign.Center)
    }
}

/** `ProgressView(value:)` as iOS draws it: a thin accent capsule on a gray track. */
@Composable
private fun ProgressBar(fraction: Float?) {
    val colors = KeeprTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.tertiaryFill),
    ) {
        val full = maxWidth
        if (fraction != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(full * fraction.coerceIn(0f, 1f))
                    .background(colors.accent, CircleShape),
            )
        } else {
            val transition = rememberInfiniteTransition(label = "indeterminate")
            val position by transition.animateFloat(
                initialValue = -0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
                label = "indeterminatePosition",
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(full * 0.3f)
                    .offset(x = full * position)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

@Composable
private fun SummarySection(header: String, summary: BackupSummary, groupPlural: String, footer: String? = null) {
    GroupedSection(header = header, footer = footer) {
        summary.rows(groupPlural).forEach { (title, count) ->
            row { LabeledRow(title, "$count") }
        }
    }
}

/** The picked file's name, as the provider reports it. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

/** The picked file's size, when the provider knows it (for progress). */
private fun fileSize(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
}.getOrNull()

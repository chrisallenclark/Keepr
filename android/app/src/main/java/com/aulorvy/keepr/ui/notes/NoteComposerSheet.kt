package com.aulorvy.keepr.ui.notes

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.NoteEditor
import com.aulorvy.keepr.core.domain.PersonFilePayload
import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.NoteSource
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.ui.components.FormDateRow
import com.aulorvy.keepr.ui.components.FormMenuPicker
import com.aulorvy.keepr.ui.components.FormTextField
import com.aulorvy.keepr.ui.components.FormToggle
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.IconLabel
import com.aulorvy.keepr.ui.components.KeeprSheet
import com.aulorvy.keepr.ui.components.ListMetrics
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.SectionSpacer
import com.aulorvy.keepr.ui.components.SheetBody
import com.aulorvy.keepr.ui.components.SheetHeader
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.files.FileThumbnail
import com.aulorvy.keepr.ui.files.PersonFileImport
import com.aulorvy.keepr.ui.files.PersonFileThumbnail
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.medium
import kotlinx.coroutines.launch

/**
 * Put a conversation write-up on someone's profile without retyping it.
 * Translated from Keepr/Features/Person/NoteComposerView.swift.
 *
 * Built for text that already exists somewhere else — a recorder's summary, a
 * consultation sheet, meeting notes from another app. One tap pastes the
 * clipboard, or screenshots come in as images for summaries that only exist
 * as pictures. The title is guessed from the text and shown in a field, so
 * the common case is paste, glance, save.
 */
@Composable
fun NoteComposerSheet(person: Person, note: ConversationNote?, onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val isEditing = note != null

    var body by rememberSaveable { mutableStateOf(note?.body.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(note?.title.orEmpty()) }
    // Once the user edits the title themselves, the suggestion stops
    // overwriting it — the app's guess never beats a decision.
    var titleIsMine by rememberSaveable { mutableStateOf(note != null) }
    var occurredAt by remember { mutableStateOf(note?.occurredAt ?: state.now()) }
    var source by rememberSaveable { mutableStateOf(note?.source ?: NoteSource.PASTED) }
    var alsoLog by rememberSaveable { mutableStateOf(false) }
    var interactionKind by rememberSaveable { mutableStateOf(InteractionKind.MEETING) }
    // Images chosen for this note, held until save so cancelling leaves nothing behind.
    var pendingImages by remember { mutableStateOf(listOf<PersonFilePayload>()) }
    var isImporting by remember { mutableStateOf(false) }

    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    // Checking the clip's type doesn't read it, so no "pasted from clipboard" notice appears.
    val clipboardHasText = remember {
        runCatching {
            clipboard?.hasPrimaryClip() == true &&
                (clipboard?.primaryClipDescription?.let { it.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || it.hasMimeType("text/*") } ?: false)
        }.getOrDefault(false)
    }

    fun refreshSuggestedTitle(text: String = body, images: List<PersonFilePayload> = pendingImages) {
        if (titleIsMine) return
        title = NoteEditor.suggestedTitle(text, images.isNotEmpty(), occurredAt, state.calendar)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            val loaded = uris.mapNotNull { uri -> runCatching { PersonFileImport.photo(context, uri) }.getOrNull() }
            pendingImages = pendingImages + loaded
            isImporting = false
            if (body.trim().isEmpty()) source = NoteSource.SCREENSHOT
            refreshSuggestedTitle()
        }
    }

    fun pasteFromClipboard() {
        val pasted = runCatching { clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        if (pasted.isNullOrEmpty()) return
        body = pasted
        source = NoteSource.PASTED
        refreshSuggestedTitle(text = pasted)
        haptics.light()
    }

    val canSave = NoteEditor.canSave(body, pendingImages.size, note) && !isImporting

    fun save() {
        NoteEditor.save(
            context = state.context,
            person = person,
            existing = note,
            title = title,
            body = body,
            occurredAt = occurredAt,
            source = source,
            images = pendingImages,
            logAs = if (alsoLog && note == null) interactionKind else null,
            now = state.now(),
            calendar = state.calendar,
        )
        state.save()
        haptics.success()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            if (isEditing) "Edit Note" else "Add Note",
            onCancel = onDismiss,
            confirmTitle = "Save",
            confirmEnabled = canSave,
            onConfirm = ::save,
        )
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))

            // The write-up
            GroupedSection(header = "Notes", footer = "Kept word for word. Nothing is rewritten or uploaded.") {
                if (body.isEmpty() && clipboardHasText) {
                    row {
                        ListRow(onClick = ::pasteFromClipboard) {
                            IconLabel("Paste from Clipboard", "doc.on.clipboard", typography.subheadline.medium, colors.accent)
                        }
                    }
                }
                row {
                    FormTextField(
                        body,
                        { text ->
                            body = text
                            refreshSuggestedTitle(text = text)
                        },
                        "Paste the summary, or type what was said.",
                        style = typography.subheadline,
                        minLines = 9,
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
            SectionSpacer()

            // Screenshots
            GroupedSection(
                header = "Screenshots",
                footer = "For summaries that only exist as a picture. They live inside this note, not loose in Files.",
            ) {
                val existingImages = note?.fileList.orEmpty()
                if (existingImages.isNotEmpty()) {
                    row {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = ListMetrics.rowHorizontal, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            existingImages.forEach { PersonFileThumbnail(it) }
                        }
                    }
                }
                if (pendingImages.isNotEmpty()) {
                    row {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = ListMetrics.rowHorizontal, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            pendingImages.forEachIndexed { index, payload ->
                                PendingImageThumbnail(payload) {
                                    pendingImages = pendingImages.filterIndexed { i, _ -> i != index }
                                    refreshSuggestedTitle()
                                }
                            }
                        }
                    }
                }
                row {
                    ListRow(onClick = {
                        if (!isImporting) photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        IconLabel(
                            when {
                                isImporting -> "Adding…"
                                pendingImages.isEmpty() -> "Add Screenshots"
                                else -> "Add More"
                            },
                            "photo.on.rectangle.angled",
                            typography.subheadline,
                            if (isImporting) colors.tertiaryLabel else colors.accent,
                        )
                    }
                }
            }
            SectionSpacer()

            // Title, date, source
            GroupedSection(
                header = "Details",
                footer = if (titleIsMine) "Your title." else "Suggested from the text — type over it any time.",
            ) {
                row {
                    FormTextField(title, { text ->
                        title = text
                        titleIsMine = true
                    }, "Title")
                }
                row {
                    FormDateRow("When", occurredAt, { date ->
                        occurredAt = date
                        refreshSuggestedTitle()
                    }, calendar = state.calendar)
                }
                row {
                    FormMenuPicker("Where from", NoteSource.entries, source, { it.title }, { source = it }, symbol = { it.symbolName })
                }
            }

            // Also log it
            if (!isEditing) {
                SectionSpacer()
                GroupedSection(footer = "Puts it on their timeline and resets their rhythm, so adding notes counts as having talked.") {
                    row { FormToggle("Also log this as an interaction", alsoLog, { alsoLog = it }) }
                    if (alsoLog) {
                        row {
                            FormMenuPicker("Type", InteractionKind.entries, interactionKind, { it.title }, { interactionKind = it }, symbol = { it.symbolName })
                        }
                    }
                }
            }
        }
    }
}

/** A screenshot chosen but not yet saved, with a way to take it back out. */
@Composable
private fun PendingImageThumbnail(payload: PersonFilePayload, onRemove: () -> Unit) {
    Box {
        FileThumbnail(payload.thumbnail, payload.kind, 64.dp)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onRemove),
        ) {
            SfSymbol("xmark.circle.fill", Color.Black.copy(alpha = 0.55f), 22.dp, contentDescription = "Remove screenshot")
        }
    }
}

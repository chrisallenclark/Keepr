package com.aulorvy.keepr.ui.navigation

import androidx.compose.runtime.Composable
import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.ui.capture.BrainDumpSheet
import com.aulorvy.keepr.ui.capture.QuickCaptureSheet
import com.aulorvy.keepr.ui.contacts.ContactImportSheet
import com.aulorvy.keepr.ui.files.PersonFilesScreen
import com.aulorvy.keepr.ui.notes.MemoryLibraryScreen
import com.aulorvy.keepr.ui.notes.NoteComposerSheet
import com.aulorvy.keepr.ui.notes.NotesListScreen
import com.aulorvy.keepr.ui.organize.GroupEditorSheet
import com.aulorvy.keepr.ui.organize.GroupsSheet
import com.aulorvy.keepr.ui.organize.PersonLinkEditorSheet
import com.aulorvy.keepr.ui.organize.RelationshipTypesSheet
import com.aulorvy.keepr.ui.review.ReviewSheet
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.screens.PersonProfileScreen
import com.aulorvy.keepr.ui.screens.SettingsSheet
import java.util.UUID

@Composable
private fun person(id: UUID): Person? =
    LocalAppState.current.context.fetch<Person>().firstOrNull { it.id == id && !it.isDeleted }

/**
 * Draws a pushed screen. Returns false when what it points at no longer
 * exists (deleted), so the tab shows its root instead.
 */
@Composable
fun RouteContent(
    route: Route,
    onBack: () -> Unit,
    onOpenPerson: (Person, ContextMode) -> Unit,
    onUnavailable: (String) -> Unit,
): Boolean {
    when (route) {
        is Route.Profile -> {
            val person = person(route.personId) ?: return false
            PersonProfileScreen(
                person = person,
                mode = route.mode,
                onBack = onBack,
                onOpenPerson = { onOpenPerson(it, route.mode) },
                onUnavailable = onUnavailable,
            )
        }
        is Route.MemoryLibrary -> MemoryLibraryScreen(person(route.personId) ?: return false, onBack)
        is Route.Notes -> NotesListScreen(person(route.personId) ?: return false, onBack)
        is Route.Files -> PersonFilesScreen(person(route.personId) ?: return false, onBack)
    }
    return true
}

/** Presents a sheet. Every sheet dismisses through [onDismiss]. */
@Composable
fun SheetContent(
    sheet: SheetRoute,
    mode: ContextMode,
    onDismiss: () -> Unit,
    onOpenGallery: (() -> Unit)?,
) {
    val context = LocalAppState.current.context
    when (sheet) {
        SheetRoute.QuickCapture -> QuickCaptureSheet(mode, onDismiss)
        is SheetRoute.BrainDump -> person(sheet.personId)?.let { BrainDumpSheet(it, onDismiss, initialText = sheet.initialText.orEmpty()) } ?: DismissNow(onDismiss)
        is SheetRoute.NoteComposer -> person(sheet.personId)?.let { owner ->
            val note = sheet.noteId?.let { id -> context.fetch<ConversationNote>().firstOrNull { it.id == id } }
            NoteComposerSheet(owner, note, onDismiss)
        } ?: DismissNow(onDismiss)
        is SheetRoute.LinkEditor -> person(sheet.personId)?.let { PersonLinkEditorSheet(it, onDismiss) } ?: DismissNow(onDismiss)
        is SheetRoute.NewGroup -> GroupEditorSheet(null, sheet.personId?.let { person(it) }, onDismiss)
        SheetRoute.ContactImport -> ContactImportSheet(mode, onDismiss)
        SheetRoute.Groups -> GroupsSheet(onDismiss)
        SheetRoute.RelationshipTypes -> RelationshipTypesSheet(onDismiss)
        SheetRoute.Review -> ReviewSheet(onDismiss)
        SheetRoute.Settings -> SettingsSheet(onDismiss = onDismiss, onOpenGallery = onOpenGallery)
    }
}


/** The thing a sheet was opened for is gone; close it after this frame. */
@Composable
private fun DismissNow(onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
}

package com.aulorvy.keepr.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.aulorvy.keepr.core.model.ContextMode
import java.util.UUID

/**
 * Screens pushed onto a tab's stack, as `NavigationLink` pushes on iOS.
 * Android's back gesture pops them.
 */
sealed interface Route {
    data class Profile(val personId: UUID, val mode: ContextMode) : Route
    /** `MemoryLibraryView`: everything saved about someone. */
    data class MemoryLibrary(val personId: UUID) : Route
    /** `NotesListView`: every conversation note for someone. */
    data class Notes(val personId: UUID) : Route
    /** `PersonFilesView`: photos and documents attached to someone. */
    data class Files(val personId: UUID) : Route
}

/** Sheets presented over everything, as `.sheet` does on iOS. */
sealed interface SheetRoute {
    data object QuickCapture : SheetRoute
    /** [initialText] seeds the dump, as a note's "Pull Facts" does on iOS (`BrainDumpView(initialText:)`). */
    data class BrainDump(val personId: UUID, val initialText: String? = null) : SheetRoute
    /** A new note when [noteId] is null, otherwise editing that note. */
    data class NoteComposer(val personId: UUID, val noteId: UUID? = null) : SheetRoute
    data class LinkEditor(val personId: UUID) : SheetRoute
    /** A new group, optionally adding [personId] to it once saved. */
    data class NewGroup(val personId: UUID? = null) : SheetRoute
    data object ContactImport : SheetRoute
    data object Groups : SheetRoute
    data object RelationshipTypes : SheetRoute
    /** Catch Up: the review queue of contacts that need sorting. */
    data object Review : SheetRoute
    data object Settings : SheetRoute
}

/** How any screen moves around: push onto the current tab, pop, or present a sheet. */
class Navigator(
    val mode: ContextMode,
    val push: (Route) -> Unit,
    val pop: () -> Unit,
    val present: (SheetRoute) -> Unit,
    /**
     * Switches the app-wide Business/Personal lens and remembers it, as a
     * sheet bound to the app's `mode` (`GroupsView(mode: $mode)`) does on iOS.
     */
    val setMode: (ContextMode) -> Unit = {},
) {
    fun openPerson(personId: UUID, through: ContextMode = mode) = push(Route.Profile(personId, through))
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }

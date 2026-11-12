package com.aulorvy.keepr.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.MemoryLibrary
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.Priority
import com.aulorvy.keepr.ui.components.EmptyState
import com.aulorvy.keepr.ui.components.EmptyStateArea
import com.aulorvy.keepr.ui.components.FormTextField
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.GroupedSectionScope
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.KeeprButton
import com.aulorvy.keepr.ui.components.KeeprButtonStyle
import com.aulorvy.keepr.ui.components.KeeprMenu
import com.aulorvy.keepr.ui.components.KeeprTopBar
import com.aulorvy.keepr.ui.components.ListMetrics
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.MenuEntry
import com.aulorvy.keepr.ui.components.SearchField
import com.aulorvy.keepr.ui.components.SectionSpacer
import com.aulorvy.keepr.ui.components.SwipeAction
import com.aulorvy.keepr.ui.components.SwipeActionsRow
import com.aulorvy.keepr.ui.components.TabBarClearance
import com.aulorvy.keepr.ui.components.ToolbarButton
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.files.InfoAlert
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.navigation.LocalNavigator
import com.aulorvy.keepr.ui.navigation.SheetRoute
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.screens.observeStore
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.semibold
import java.util.UUID

/**
 * Everything you know about one person, filed. Translated from
 * Keepr/Features/Person/MemoryLibraryView.swift.
 *
 * The profile deliberately shows only a few facts; this is where the rest
 * lives. Grouped rather than listed, because forty facts in one column is the
 * same as no facts at all — and dated, because "why is this here?" is the
 * question a two-year-old note has to be able to answer.
 *
 * Beyond iOS: a long press also edits a fact, and archived facts are listed
 * at the end so an archive can be undone.
 */
@Composable
fun MemoryLibraryScreen(person: Person, onBack: () -> Unit) {
    val state = LocalAppState.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    observeStore().value
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography

    var query by rememberSaveable { mutableStateOf("") }
    var refileResult by remember { mutableStateOf<Int?>(null) }
    var menuFor by remember { mutableStateOf<UUID?>(null) }
    var moveMenuFor by remember { mutableStateOf<UUID?>(null) }
    var editingId by remember { mutableStateOf<UUID?>(null) }
    var editDraft by rememberSaveable { mutableStateOf("") }
    var showsArchived by rememberSaveable { mutableStateOf(false) }

    if (person.isDeleted) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val groups = MemoryLibrary.groups(person, query)
    val archived = MemoryLibrary.archived(person, query)
    val refilable = MemoryLibrary.refilable(person)

    fun brainDump() = navigator.present(SheetRoute.BrainDump(person.id))

    fun saveEdit(memory: Memory) {
        if (MemoryLibrary.edit(memory, editDraft, state.now())) {
            state.save()
            haptics.success()
        }
        editingId = null
    }

    fun delete(memory: Memory) {
        state.context.delete(memory)
        state.save()
    }

    fun archive(memory: Memory, toArchive: Boolean) {
        MemoryLibrary.setArchived(memory, toArchive, state.now())
        state.save()
        if (!toArchive) haptics.success()
    }

    fun toggleImportance(memory: Memory) {
        MemoryLibrary.toggleImportance(memory, state.now())
        state.save()
        haptics.light()
    }

    fun move(memory: Memory, category: MemoryCategory) {
        MemoryLibrary.move(memory, category, state.now())
        state.save()
        haptics.selection()
    }

    fun refileOthers() {
        if (refilable.isEmpty()) return
        refileResult = MemoryLibrary.refile(refilable, state.now())
        state.save()
        haptics.success()
    }

    /** One fact as a row: swipe, long press, or edit in place. */
    fun GroupedSectionScope.memoryRow(memory: Memory, isArchived: Boolean) {
        row {
            key(memory.id) {
                if (editingId == memory.id) {
                    ListRow(verticalPadding = 0.dp) {
                        FormTextField(editDraft, { editDraft = it }, "Something worth remembering", Modifier.weight(1f), style = typography.subheadline, maxLines = 6, onSubmit = { saveEdit(memory) })
                        KeeprButton("Save", { saveEdit(memory) }, enabled = editDraft.isNotBlank(), textStyle = typography.subheadline.semibold)
                    }
                } else {
                    Box {
                        SwipeActionsRow(
                            leading = if (isArchived) {
                                listOf(SwipeAction("Restore", "arrow.uturn.backward", colors.accent) { archive(memory, false) })
                            } else {
                                listOf(SwipeAction("Important", "star", colors.yellow) { toggleImportance(memory) })
                            },
                            trailing = if (isArchived) {
                                listOf(SwipeAction("Delete", "trash", colors.red) { delete(memory) })
                            } else {
                                listOf(
                                    SwipeAction("Delete", "trash", colors.red) { delete(memory) },
                                    SwipeAction("Archive", "archivebox", colors.gray) { archive(memory, true) },
                                )
                            },
                        ) {
                            ListRow(onLongClick = { menuFor = memory.id }, verticalPadding = 10.dp) {
                                LibraryMemoryRow(memory, MemoryLibrary.provenance(memory, state.now(), state.calendar), isArchived, Modifier.weight(1f))
                            }
                        }
                        KeeprMenu(
                            menuFor == memory.id,
                            { menuFor = null },
                            if (isArchived) {
                                listOf(
                                    MenuEntry.Action("Restore", "arrow.uturn.backward") { archive(memory, false) },
                                    MenuEntry.Divider,
                                    MenuEntry.Action("Delete", "trash", isDestructive = true) { delete(memory) },
                                )
                            } else {
                                listOf(
                                    MenuEntry.Action("Edit", "pencil") {
                                        editDraft = memory.content
                                        editingId = memory.id
                                    },
                                    MenuEntry.Action("Move to…", "folder") { moveMenuFor = memory.id },
                                    MenuEntry.Action(
                                        if (memory.importance == Priority.HIGH) "Not Important" else "Important",
                                        if (memory.importance == Priority.HIGH) "star.slash" else "star",
                                    ) { toggleImportance(memory) },
                                    MenuEntry.Action("Archive", "archivebox") { archive(memory, true) },
                                    MenuEntry.Divider,
                                    MenuEntry.Action("Delete", "trash", isDestructive = true) { delete(memory) },
                                )
                            },
                        )
                        KeeprMenu(
                            moveMenuFor == memory.id,
                            { moveMenuFor = null },
                            MemoryCategory.entries.map { category ->
                                MenuEntry.Action(category.title, category.symbolName, isChecked = category == memory.category) { move(memory, category) }
                            },
                        )
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        KeeprTopBar(
            title = "What You Know",
            background = colors.groupedBackground,
            leading = { ToolbarButton("chevron.left", "Back", onBack) },
            trailing = { ToolbarButton("square.and.pencil", "Brain Dump", ::brainDump) },
            pinned = {
                SearchField(query, { query = it }, "Search what you know")
                Spacer(Modifier.height(Spacing.small))
            },
        )

        if (groups.isEmpty() && archived.isEmpty()) {
            EmptyStateArea(Modifier.weight(1f).fillMaxWidth()) {
                if (query.isBlank()) {
                    EmptyState(
                        "Nothing recorded yet",
                        "brain",
                        description = "Everything you learn about ${person.displayName} can live here.",
                    ) {
                        KeeprButton("Brain Dump", ::brainDump, style = KeeprButtonStyle.Prominent)
                    }
                } else {
                    EmptyState(
                        "No Results for “$query”",
                        "magnifyingglass",
                        description = "Check the spelling or try a new search.",
                    )
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(Spacing.small))
                groups.forEach { group ->
                    CategoryHeader(group.category.title, group.category.symbolName, group.count) {
                        if (group.category == MemoryCategory.OTHER && refilable.isNotEmpty()) {
                            KeeprButton("File ${refilable.size}", ::refileOthers, textStyle = typography.caption.semibold)
                        }
                    }
                    GroupedSection {
                        group.memories.forEach { memory -> memoryRow(memory, isArchived = false) }
                    }
                    SectionSpacer()
                }
                if (archived.isNotEmpty()) {
                    CategoryHeader("Archived", "archivebox", archived.size) {
                        KeeprButton(if (showsArchived) "Hide" else "Show", { showsArchived = !showsArchived }, textStyle = typography.caption.semibold)
                    }
                    if (showsArchived) {
                        GroupedSection(footer = "Kept off the profile. Swipe right to put one back.") {
                            archived.forEach { memory -> memoryRow(memory, isArchived = true) }
                        }
                    }
                    SectionSpacer()
                }
                TabBarClearance()
            }
        }
    }

    refileResult?.let { count ->
        InfoAlert("Filed", MemoryLibrary.refileMessage(count), onDismiss = { refileResult = null })
    }
}

/** The section header: the category's symbol and name, any action, and how many. */
@Composable
private fun CategoryHeader(title: String, symbolName: String, count: Int, action: @Composable () -> Unit) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = ListMetrics.sectionInset + ListMetrics.textInset,
                end = ListMetrics.sectionInset + ListMetrics.textInset,
                bottom = 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        SfSymbol(symbolName, colors.secondaryLabel, typography.subheadline)
        KText(title, style = typography.headline, color = colors.secondaryLabel, modifier = Modifier.weight(1f).semantics { heading() })
        action()
        KText("$count", style = typography.headline, color = colors.tertiaryLabel)
    }
}

/** One fact, with a star when it's marked important and a line saying where it came from. */
@Composable
private fun LibraryMemoryRow(memory: Memory, provenance: String, isArchived: Boolean, modifier: Modifier) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            KText(
                memory.content,
                style = typography.subheadline,
                color = if (isArchived) colors.secondaryLabel else colors.label,
                modifier = Modifier.weight(1f),
            )
            if (memory.importance == Priority.HIGH) {
                SfSymbol("star.fill", colors.yellow, typography.caption2, Modifier.padding(top = 3.dp), contentDescription = "Important")
            }
        }
        KText(
            if (isArchived) "${memory.category.title} · $provenance" else provenance,
            style = typography.caption2,
            color = colors.tertiaryLabel,
        )
    }
}

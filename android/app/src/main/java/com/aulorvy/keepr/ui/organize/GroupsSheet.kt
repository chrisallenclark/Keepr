package com.aulorvy.keepr.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.GroupRules
import com.aulorvy.keepr.core.domain.GroupVocabulary
import com.aulorvy.keepr.core.domain.PeopleEngine
import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.persistence.PreferenceKey
import com.aulorvy.keepr.ui.components.ContextSwitcher
import com.aulorvy.keepr.ui.components.EmptyState
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.KeeprButton
import com.aulorvy.keepr.ui.components.KeeprButtonStyle
import com.aulorvy.keepr.ui.components.KeeprSheet
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.PersonCompactRow
import com.aulorvy.keepr.ui.components.SectionSpacer
import com.aulorvy.keepr.ui.components.Separator
import com.aulorvy.keepr.ui.components.SwipeAction
import com.aulorvy.keepr.ui.components.SwipeActionsRow
import com.aulorvy.keepr.ui.components.ToolbarButton
import com.aulorvy.keepr.ui.components.ToolbarTextButton
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.navigation.LocalNavigator
import com.aulorvy.keepr.ui.screens.ConfirmDialog
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.screens.observeStore
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.medium
import java.util.UUID

/**
 * The groups in the current context, and who's in them. Translated from
 * Keepr/Features/People/GroupsView.swift.
 *
 * Opening a group pushes its members inside the sheet (with a back button and
 * the back gesture), as the iOS sheet's own `NavigationStack` does. Tapping a
 * member closes the sheet and opens their profile.
 */
@Composable
fun GroupsSheet(onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    observeStore().value
    val colors = KeeprTheme.colors
    val prefs = state.repository.preferences
    val groupLabel = prefs.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular
    val groupPlural = GroupVocabulary.plural(groupLabel)
    val article = GroupVocabulary.article(groupLabel)

    // iOS binds this sheet to the app's `mode`: switching here switches People too.
    var mode by remember { mutableStateOf(navigator.mode) }
    fun switchMode(to: ContextMode) {
        mode = to
        navigator.setMode(to)
    }
    var openedID by remember { mutableStateOf<UUID?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PersonGroup?>(null) }
    var pendingDeletion by remember { mutableStateOf<PersonGroup?>(null) }

    val allGroups = state.context.fetch<PersonGroup>().sortedBy { it.sortOrder }
    val groups = allGroups.filter { it.matches(mode) }
    val opened = openedID?.let { id -> allGroups.firstOrNull { it.id == id } }

    KeeprSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().background(if (opened == null) colors.background else colors.groupedBackground)) {
            if (opened != null) {
                BackHandler { openedID = null }
                GroupMembers(
                    group = opened,
                    onBack = { openedID = null },
                    onDone = onDismiss,
                    onOpenPerson = { person ->
                        onDismiss()
                        navigator.openPerson(person.id, mode)
                    },
                )
            } else {
                OrganizeHeader(
                    groupPlural,
                    trailing = {
                        ToolbarButton("plus", "New $groupLabel", { isCreating = true })
                        ToolbarTextButton("Done", onDismiss, isProminent = true)
                    },
                )
                ContextSwitcher(mode == ContextMode.PERSONAL, { switchMode(if (it) ContextMode.PERSONAL else ContextMode.BUSINESS) })
                if (groups.isEmpty()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 420.dp), contentAlignment = Alignment.Center) {
                        if (allGroups.isEmpty()) {
                            EmptyState(
                                "No ${groupPlural.lowercase()} yet",
                                "mappin.and.ellipse",
                                description = "Life Time, your training company, a dating app, a bar — whatever a relationship comes through. One of these can hold clients and colleagues at the same time, and someone can be in several.",
                            ) {
                                KeeprButton("Create $article $groupLabel", { isCreating = true }, style = KeeprButtonStyle.Prominent)
                            }
                        } else {
                            EmptyState(
                                "No ${mode.title.lowercase()} ${groupPlural.lowercase()}",
                                mode.symbolName,
                                description = "Your other ${groupPlural.lowercase()} are in ${mode.other.title}.",
                            ) {
                                KeeprButton("Create $article $groupLabel", { isCreating = true }, style = KeeprButtonStyle.Bordered)
                            }
                        }
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                        groups.forEachIndexed { index, group ->
                            if (index > 0) Separator(start = 16.dp + 30.dp + Spacing.medium)
                            SwipeActionsRow(
                                leading = listOf(SwipeAction("Edit", "pencil", colors.blue) { editing = group }),
                                // Full swipe is off: deletion asks first.
                                trailing = listOf(SwipeAction("Delete", "trash", colors.red) { pendingDeletion = group }),
                                allowsFullSwipe = false,
                                rowBackground = colors.background,
                            ) {
                                ListRow(onClick = { openedID = group.id }) { GroupRow(group) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreating) {
        GroupEditorSheet(null, null, onDismiss = { isCreating = false }, mode = mode)
    }
    editing?.let { group ->
        GroupEditorSheet(group, null, onDismiss = { editing = null }, mode = mode)
    }
    pendingDeletion?.let { group ->
        ConfirmDialog(
            title = "Delete ${group.name}?",
            message = GroupRules.listDeleteMessage(group.memberCount),
            confirmTitle = "Delete $groupLabel",
            onConfirm = {
                if (openedID == group.id) openedID = null
                state.context.delete(group)
                state.save()
                haptics.success()
            },
            onDismiss = { pendingDeletion = null },
        )
    }
}

/** iOS `GroupRow`: symbol, name, detail, and how many people are in it. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.GroupRow(group: PersonGroup) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
        SfSymbol(group.symbolName, colors.accent, typography.title3)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        KText(group.name, style = typography.body.medium, maxLines = 1)
        group.detail?.takeIf { it.isNotEmpty() }?.let {
            KText(it, style = typography.footnote, color = colors.secondaryLabel, maxLines = 1)
        }
    }
    KText(GroupRules.memberCountLabel(group.memberCount), style = typography.caption, color = colors.tertiaryLabel)
}

/**
 * Everyone at one place, split by what they are to you. iOS `GroupMembersView`.
 */
@Composable
private fun GroupMembers(
    group: PersonGroup,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenPerson: (Person) -> Unit,
) {
    val colors = KeeprTheme.colors
    val members = group.memberList.filter { !it.isDeleted }
    OrganizeHeader(
        group.name,
        leading = { ToolbarButton("chevron.left", "Back", onBack) },
        trailing = { ToolbarTextButton("Done", onDone, isProminent = true) },
    )
    if (members.isEmpty()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 420.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                "No one at ${group.name} yet",
                group.symbolName,
                description = "Add someone here from their profile, or select several people at once in People.",
            )
        }
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Spacer(Modifier.height(8.dp))
        PeopleEngine.byType(members).forEach { section ->
            GroupedSection(
                header = section.title,
                headerTrailing = {
                    KText("${section.people.size}", style = KeeprTheme.typography.headline, color = colors.secondaryLabel)
                },
            ) {
                section.people.forEach { person ->
                    row(16.dp + 36.dp + Spacing.medium) {
                        ListRow(onClick = { onOpenPerson(person) }) {
                            PersonCompactRow(
                                person.displayName,
                                person.initials,
                                Modifier.weight(1f),
                                detail = person.subtitle ?: person.context.title,
                                photo = person.photoData,
                            )
                        }
                    }
                }
            }
            SectionSpacer()
        }
    }
}

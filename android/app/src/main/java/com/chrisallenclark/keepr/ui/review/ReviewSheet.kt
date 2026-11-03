package com.chrisallenclark.keepr.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.CategorySuggestion
import com.chrisallenclark.keepr.core.domain.MarkerVocabulary
import com.chrisallenclark.keepr.core.domain.ReviewItem
import com.chrisallenclark.keepr.core.domain.ReviewQueue
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.persistence.ReviewActions
import com.chrisallenclark.keepr.core.services.ContactSummary
import com.chrisallenclark.keepr.ui.components.Avatar
import com.chrisallenclark.keepr.ui.components.AvatarSize
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprButtonStyle
import com.chrisallenclark.keepr.ui.components.KeeprMenu
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.MenuEntry
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.ToolbarTextButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.contacts.ContactReviewSignal
import com.chrisallenclark.keepr.ui.contacts.InsetGroupedRow
import com.chrisallenclark.keepr.ui.contacts.InsetSectionFooter
import com.chrisallenclark.keepr.ui.contacts.InsetSectionHeader
import com.chrisallenclark.keepr.ui.contacts.ListBottomClearance
import com.chrisallenclark.keepr.ui.contacts.LoadingState
import com.chrisallenclark.keepr.ui.contacts.SheetToolbar
import com.chrisallenclark.keepr.ui.contacts.avatarTextStart
import com.chrisallenclark.keepr.ui.contacts.openAppSettings
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.navigation.LocalNavigator
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.observeStore
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * Catch Up: people who haven't been placed yet. Translated from
 * Keepr/Features/Review/ReviewView.swift.
 *
 * Two groups, one screen. Contacts the phone picked up since Keepr last looked
 * come first, because that answer has a shelf life — you know today which bar
 * it was. Underneath sits everyone already in Keepr who never got a
 * relationship type, which is the part that actually accumulates.
 */
@Composable
fun ReviewSheet(onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val store = state.contacts
    val mode = LocalNavigator.current.mode
    val haptics = rememberHaptics()
    val context = LocalContext.current
    observeStore().value

    val people = state.context.fetch<Person>()
    val groups = state.context.fetch<PersonGroup>().sortedBy { it.sortOrder }
    val tags = state.context.fetch<RelationshipTag>().sortedBy { it.sortOrder }

    var newContacts by remember { mutableStateOf<List<ContactSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<ReviewItem?>(null) }
    var optionsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (store.authorizationStatus().canRead) {
            val fetched = store.fetchContacts()
            newContacts = state.contactTracker.newContacts(fetched)
        }
        isLoading = false
    }
    // The review consumes items; Today's banner has to stop claiming them.
    DisposableEffect(Unit) { onDispose { ContactReviewSignal.changed() } }

    val items = ReviewQueue.items(newContacts, people, MarkerVocabulary.build(groups, tags))
    val fresh = items.filter { it.isNew }
    val backlog = items.filter { !it.isNew }
    val withSuggestions = items.filter { it.suggestion != null }

    fun removeContact(item: ReviewItem) {
        val subject = item.subject
        if (subject is ReviewItem.Subject.NewContact) {
            newContacts = newContacts.filter { it.identifier != subject.contact.identifier }
        }
    }

    /**
     * Waving someone off has to stick, or the queue becomes a nag. For a
     * contact that means remembering the identifier; for someone already in
     * Keepr it's a date on their record.
     */
    fun skip(item: ReviewItem) {
        ReviewActions.skip(item, state.contactTracker, state.now())
        removeContact(item)
        if (!item.isNew) state.save()
        haptics.light()
    }

    fun accept(item: ReviewItem, suggestion: CategorySuggestion) {
        ReviewActions.accept(item, suggestion, state.context, groups, state.contactTracker, state.calendar, state.now())
        removeContact(item)
    }

    fun acceptOne(item: ReviewItem, suggestion: CategorySuggestion) {
        accept(item, suggestion)
        state.save()
        haptics.success()
    }

    fun applyAllSuggestions() {
        for (item in withSuggestions) {
            val suggestion = item.suggestion ?: continue
            accept(item, suggestion)
        }
        state.save()
        haptics.success()
    }

    KeeprSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight()) {
            SheetToolbar(
                "Catch Up",
                leading = { ToolbarTextButton("Done", onDismiss) },
                trailing = {
                    if (withSuggestions.isNotEmpty()) {
                        Box {
                            ToolbarButton("ellipsis", "Options", { optionsOpen = true })
                            KeeprMenu(
                                expanded = optionsOpen,
                                onDismiss = { optionsOpen = false },
                                entries = listOf(
                                    MenuEntry.Action("Apply ${withSuggestions.size} Suggestions", "wand.and.stars") { applyAllSuggestions() },
                                ),
                            )
                        }
                    } else {
                        // Balances "Done" so the title stays centered.
                        Spacer(Modifier.width(74.dp))
                    }
                },
            )

            when {
                isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingState("Checking your contacts", Modifier.padding(bottom = 60.dp))
                }
                items.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val canRead = store.authorizationStatus().canRead
                    EmptyState(
                        "Everyone's placed",
                        "checkmark.circle",
                        description = if (canRead) "Nobody is waiting to be categorized. New contacts will show up here as you add them."
                        else "Turn on contact access and Keepr will notice new contacts as you add them.",
                        modifier = Modifier.padding(bottom = 60.dp),
                    ) {
                        if (!canRead) {
                            KeeprButton("Open Settings", { openAppSettings(context) }, style = KeeprButtonStyle.Bordered)
                        }
                    }
                }
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    item(key = "top") { SectionSpacer(8.dp) }
                    if (fresh.isNotEmpty()) {
                        reviewSection(
                            key = "fresh",
                            header = "New in Contacts",
                            footer = "Added to your phone since Keepr last looked. Categorize them now, while you still remember how you met.",
                            items = fresh,
                            onOpen = { editing = it },
                            onSkip = ::skip,
                            onAccept = ::acceptOne,
                        )
                        if (backlog.isNotEmpty()) item(key = "gap") { SectionSpacer() }
                    }
                    if (backlog.isNotEmpty()) {
                        reviewSection(
                            key = "backlog",
                            header = "No Type Yet",
                            footer = "Already in Keepr, never given a relationship type.",
                            items = backlog,
                            onOpen = { editing = it },
                            onSkip = ::skip,
                            onAccept = ::acceptOne,
                        )
                    }
                    item(key = "bottom") { ListBottomClearance() }
                }
            }
        }
    }

    editing?.let { item ->
        CategorizeSheet(item = item, mode = mode, onDismiss = { editing = null }, onSaved = { removeContact(item) })
    }
}

private fun LazyListScope.reviewSection(
    key: String,
    header: String,
    footer: String,
    items: List<ReviewItem>,
    onOpen: (ReviewItem) -> Unit,
    onSkip: (ReviewItem) -> Unit,
    onAccept: (ReviewItem, CategorySuggestion) -> Unit,
) {
    item(key = "$key-header") { InsetSectionHeader(header) }
    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
        InsetGroupedRow(index, items.size, separatorStart = avatarTextStart) {
            ReviewRow(item, onOpen, onSkip, onAccept)
        }
    }
    item(key = "$key-footer") { InsetSectionFooter(footer) }
}

@Composable
private fun ReviewRow(
    item: ReviewItem,
    onOpen: (ReviewItem) -> Unit,
    onSkip: (ReviewItem) -> Unit,
    onAccept: (ReviewItem, CategorySuggestion) -> Unit,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val suggestion = item.suggestion
    SwipeActionsRow(
        leading = if (suggestion != null) listOf(SwipeAction("Accept", "checkmark", colors.green) { onAccept(item, suggestion) }) else emptyList(),
        trailing = listOf(SwipeAction("Not Now", "clock", colors.gray) { onSkip(item) }),
    ) {
        ListRow(onClick = { onOpen(item) }) {
            when (val subject = item.subject) {
                is ReviewItem.Subject.NewContact ->
                    Avatar(subject.contact.initials, size = AvatarSize.Small, imageData = subject.contact.thumbnailData)
                is ReviewItem.Subject.Existing ->
                    Avatar(subject.person.initials, size = AvatarSize.Small, imageData = subject.person.photoData)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KText(item.name, style = typography.subheadline.medium)
                item.detail?.let { KText(it, style = typography.caption, color = colors.secondaryLabel, maxLines = 1) }
                if (suggestion != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                        SfSymbol(suggestion.context.symbolName, colors.tertiaryLabel, typography.caption2)
                        KText(suggestion.tagName ?: suggestion.context.title, style = typography.caption2, color = colors.tertiaryLabel, maxLines = 1)
                        KText("·", style = typography.caption2, color = colors.tertiaryLabel)
                        KText(suggestion.reason, style = typography.caption2, color = colors.tertiaryLabel, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
            SfSymbol("chevron.right", colors.tertiaryLabel, typography.caption.semibold)
        }
    }
}

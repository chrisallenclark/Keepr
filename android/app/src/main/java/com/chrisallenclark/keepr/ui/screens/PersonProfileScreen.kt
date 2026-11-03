package com.chrisallenclark.keepr.ui.screens

import com.chrisallenclark.keepr.ui.navigation.Route
import com.chrisallenclark.keepr.ui.navigation.SheetRoute
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.CadenceEngine
import com.chrisallenclark.keepr.core.domain.GroupVocabulary
import com.chrisallenclark.keepr.core.domain.MemoryEngine
import com.chrisallenclark.keepr.core.domain.RelativeDate
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.nonBlank
import com.chrisallenclark.keepr.core.model.trimmed
import com.chrisallenclark.keepr.core.model.trimmedSpaces
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.core.services.CommunicationLauncher
import com.chrisallenclark.keepr.core.services.ContactMethod
import com.chrisallenclark.keepr.ui.components.Avatar
import com.chrisallenclark.keepr.ui.components.AvatarSize
import com.chrisallenclark.keepr.ui.components.FloatingCard
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprMenu
import com.chrisallenclark.keepr.ui.components.KeeprTopBar
import com.chrisallenclark.keepr.ui.components.LabeledRow
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.MenuEntry
import com.chrisallenclark.keepr.ui.components.PersonHeader
import com.chrisallenclark.keepr.ui.components.QuickAction
import com.chrisallenclark.keepr.ui.components.QuickActionsRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.TabBarClearance
import com.chrisallenclark.keepr.ui.components.TagLabel
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * A person's profile. Translated from Keepr/Features/Person/PersonProfileView.swift.
 * Note composing, files, Brain Dump, the memory library, the link editor and
 * new groups open from here on iOS and arrive in the next phase; what they
 * have already recorded is shown.
 */
@Composable
fun PersonProfileScreen(
    person: Person,
    mode: ContextMode,
    onBack: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val state = LocalAppState.current
    val navigator = com.chrisallenclark.keepr.ui.navigation.LocalNavigator.current
    val haptics = rememberHaptics()
    val androidContext = LocalContext.current
    observeStore().value
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val prefs = state.repository.preferences
    val groupLabel = prefs.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular
    val groupPlural = GroupVocabulary.plural(groupLabel)
    val article = GroupVocabulary.article(groupLabel)

    var moreOpen by remember { mutableStateOf(false) }
    var showsLog by remember { mutableStateOf(false) }
    var showsEdit by remember { mutableStateOf(false) }
    var showsNewFollowUp by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var showsAllInteractions by rememberSaveable { mutableStateOf(false) }
    var isAddingMemory by remember { mutableStateOf(false) }
    var newMemory by rememberSaveable { mutableStateOf("") }
    var isEditingWorkNote by remember { mutableStateOf(false) }
    var workNoteDraft by rememberSaveable { mutableStateOf("") }
    var placeMenuOpen by remember { mutableStateOf(false) }
    var cadenceMenuOpen by remember { mutableStateOf(false) }
    var memoryAddMenuOpen by remember { mutableStateOf(false) }

    if (person.isDeleted) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    fun saveMemory() {
        val content = newMemory.trimmed()
        if (content.isEmpty()) return
        state.context.insert(Memory(content, createdAt = state.now(), person = person))
        person.touch(state.now())
        state.save()
        newMemory = ""
        isAddingMemory = false
        haptics.success()
    }

    fun saveWorkNote() {
        person.workNote = workNoteDraft.nonBlank()
        person.touch(state.now())
        state.save()
        isEditingWorkNote = false
        haptics.success()
    }

    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        KeeprTopBar(
            title = person.displayName,
            background = colors.groupedBackground,
            leading = { ToolbarButton("chevron.left", "Back", onBack) },
            trailing = {
                Box {
                    ToolbarButton("ellipsis.circle", "More", { moreOpen = true })
                    KeeprMenu(
                        expanded = moreOpen,
                        onDismiss = { moreOpen = false },
                        entries = listOf(
                            MenuEntry.Action("Edit", "pencil") { showsEdit = true },
                            MenuEntry.Action(if (person.isFavorite) "Remove Favorite" else "Favorite", if (person.isFavorite) "star.slash" else "star") { state.toggleFavorite(person, haptics) },
                            if (person.awaitingReplySince == null) {
                                MenuEntry.Action("Reached Out — No Reply", "paperplane") { state.markReachedOut(person, haptics) }
                            } else {
                                MenuEntry.Action("They Replied", "checkmark.bubble") { state.markReplied(person, haptics) }
                            },
                            MenuEntry.Divider,
                            MenuEntry.Action("Delete Person", "trash", isDestructive = true) { confirmingDelete = true },
                        ),
                    )
                }
            },
        )

        Column(Modifier.verticalScroll(rememberScrollState())) {
            // On iOS the header is the first row of an inset-grouped List: the
            // list's top margin, the first section's spacing and the row's own
            // `Spacing.large` inset put the avatar ~65pt under the bar, and
            // the section's 16pt inset adds to the rows' own side padding.
            Spacer(Modifier.height(profileHeaderTopInset))
            PersonHeader(
                person.displayName, person.initials,
                modifier = Modifier.padding(horizontal = ListMetrics.sectionInset),
                fullName = person.fullName, subtitle = person.subtitle,
                tags = person.headlineTags(mode).map { TagLabel(it.name, it.symbolName) },
                photo = person.photoData,
            )
            Spacer(Modifier.height(Spacing.medium))
            QuickActionsRow(
                modifier = Modifier.padding(horizontal = ListMetrics.sectionInset),
                actions = ContactMethod.entries.filter { CommunicationLauncher.isAvailable(it, person) }.map { method ->
                    QuickAction(method.title, method.symbolName) {
                        if (androidContext.open(method, person)) haptics.light()
                    }
                } + QuickAction("Log", "plus.circle.fill", isProminent = true) { showsLog = true },
            )
            Spacer(Modifier.height(Spacing.section))

            // Waiting on a reply
            person.awaitingReplySince?.let { since ->
                GroupedSection {
                    row {
                        ListRow {
                            SfSymbol("hourglass", colors.secondaryLabel, typography.body)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                KText("Waiting on a reply", style = typography.subheadline.medium)
                                KText("You reached out ${RelativeDate.past(since, state.now(), state.calendar).lowercase()}.", style = typography.caption, color = colors.secondaryLabel)
                            }
                            KeeprButton("Replied", { state.markReplied(person, haptics) }, textStyle = typography.caption.semibold)
                        }
                    }
                }
                SectionSpacer()
            }

            // Next
            val open = person.openFollowUps
            GroupedSection(header = "Next", headerTrailing = { if (open.isNotEmpty()) HeaderButton("Add") { showsNewFollowUp = true } }) {
                if (open.isEmpty()) {
                    row { ListRow(onClick = { showsNewFollowUp = true }) { IconLabel("Add a follow-up", "plus.circle", typography.subheadline, colors.accent) } }
                } else {
                    open.forEach { followUp ->
                        row(followUpTextStart) {
                            SwipeActionsRow(trailing = listOf(SwipeAction("Done", "checkmark", colors.green) { state.toggle(followUp, haptics) })) {
                                ListRow { FollowUpRecordRow(state, followUp, haptics, Modifier.weight(1f), showsPerson = false) }
                            }
                        }
                    }
                }
            }
            SectionSpacer()

            // What They Do
            val employer = listOfNotNull(person.jobTitle, person.company).filter { it.isNotEmpty() }.joinToString(" · ")
            val workNote = person.workNote.orEmpty()
            GroupedSection(
                header = "What They Do",
                footer = if (employer.isEmpty() && workNote.isEmpty() && !isEditingWorkNote) "Who they work for, what they run, what they're good at. The thing you'd want in front of you before the next conversation." else null,
            ) {
                if (employer.isNotEmpty()) row(ListMetrics.rowHorizontal + 40.dp) { ListRow { ListLabel(employer, "building.2") } }
                when {
                    isEditingWorkNote -> row {
                        ListRow(verticalPadding = 0.dp) {
                            FormTextField(workNoteDraft, { workNoteDraft = it }, "Owns a Shopify brand — good person to learn ads from", Modifier.weight(1f), style = typography.subheadline, maxLines = 6)
                            KeeprButton("Save", ::saveWorkNote, textStyle = typography.subheadline.semibold)
                        }
                    }
                    workNote.isNotEmpty() -> row {
                        ListRow(onClick = { workNoteDraft = workNote; isEditingWorkNote = true }) { KText(workNote, style = typography.subheadline) }
                    }
                    else -> row {
                        ListRow(onClick = { workNoteDraft = ""; isEditingWorkNote = true }) { IconLabel("What do they do?", "plus.circle", typography.subheadline, colors.accent) }
                    }
                }
            }
            SectionSpacer()

            // Groups
            val places = person.groupList.sortedBy { it.sortOrder }
            val available = state.context.fetch<PersonGroup>().sortedBy { it.sortOrder }.filter { !person.isMember(it) && it.matches(mode) }
            val placeEntries = available.map { place ->
                MenuEntry.Action(place.name, place.symbolName) {
                    person.groups = person.groupList + place
                    person.touch(state.now())
                    state.save()
                    haptics.success()
                }
            } + listOfNotNull(if (available.isNotEmpty()) MenuEntry.Divider else null) +
                MenuEntry.Action("New $groupLabel…", "plus") { navigator.present(SheetRoute.NewGroup(person.id)) }
            GroupedSection(
                header = groupPlural,
                headerTrailing = {
                    if (places.isNotEmpty()) {
                        Box {
                            HeaderButton("Add") { placeMenuOpen = true }
                            KeeprMenu(placeMenuOpen, { placeMenuOpen = false }, placeEntries)
                        }
                    }
                },
                footer = if (places.isEmpty()) "Life Time, your training company, the bar you met in. One of these shows you everyone from it at once — clients and colleagues together." else null,
            ) {
                if (places.isEmpty()) {
                    row {
                        Box {
                            ListRow(onClick = { placeMenuOpen = true }) {
                                IconLabel("Add $article ${groupLabel.lowercase()}", "mappin.and.ellipse", typography.subheadline, colors.accent)
                            }
                            KeeprMenu(placeMenuOpen, { placeMenuOpen = false }, placeEntries)
                        }
                    }
                } else {
                    places.forEach { place ->
                        row(followUpTextStart + 8.dp) {
                            SwipeActionsRow(trailing = listOf(SwipeAction("Remove", "minus.circle", colors.red) {
                                person.groups = person.groupList.filter { it.id != place.id }
                                person.touch(state.now())
                                state.save()
                            })) {
                                ListRow {
                                    IconLabel(place.name, place.symbolName, typography.subheadline, colors.label)
                                    place.detail?.takeIf { it.isNotEmpty() }?.let { KText(it, style = typography.caption, color = colors.tertiaryLabel) }
                                }
                            }
                        }
                    }
                }
            }
            SectionSpacer()

            // What You Know
            val all = person.visibleMemories
            val headline = MemoryEngine.headline(all)
            val counts = MemoryEngine.summaryCounts(all)
            val memoryMenu = listOf(
                MenuEntry.Action("Brain Dump", "brain") { navigator.present(SheetRoute.BrainDump(person.id)) },
                MenuEntry.Action("Add One Thing", "plus.circle") { isAddingMemory = true },
            )
            GroupedSection(
                header = "What You Know",
                headerTrailing = {
                    if (all.isNotEmpty() || isAddingMemory) {
                        Box {
                            HeaderButton("Add") { memoryAddMenuOpen = true }
                            KeeprMenu(memoryAddMenuOpen, { memoryAddMenuOpen = false }, memoryMenu)
                        }
                    }
                },
            ) {
                if (all.isEmpty() && !isAddingMemory) {
                    row { ListRow(onClick = { navigator.present(SheetRoute.BrainDump(person.id)) }) { IconLabel("Brain dump what you know", "brain", typography.subheadline, colors.accent) } }
                    row { ListRow(onClick = { isAddingMemory = true }) { IconLabel("Add one thing", "plus.circle", typography.subheadline, colors.accent) } }
                }
                headline.forEach { memory ->
                    row(followUpTextStart - 4.dp) {
                        SwipeActionsRow(trailing = listOf(
                            SwipeAction("Delete", "trash", colors.red) { state.context.delete(memory); state.save() },
                            SwipeAction("Archive", "archivebox", colors.gray) { memory.isArchived = true; memory.updatedAt = state.now(); state.save() },
                        )) {
                            ListRow { MemoryRow(memory, Modifier.weight(1f)) }
                        }
                    }
                }
                if (isAddingMemory) {
                    row {
                        ListRow(verticalPadding = 0.dp) {
                            FormTextField(newMemory, { newMemory = it }, "Something worth remembering", Modifier.weight(1f), style = typography.subheadline, maxLines = 4, onSubmit = ::saveMemory)
                            KeeprButton("Save", ::saveMemory, enabled = newMemory.trimmedSpaces().isNotEmpty(), textStyle = typography.subheadline.semibold)
                        }
                    }
                }
                if (counts.size > 1 || all.size > headline.size) {
                    row {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            counts.forEach { group ->
                                Row(
                                    Modifier
                                        .background(colors.quaternaryLabel, CircleShape)
                                        .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {
                                            navigator.push(Route.MemoryLibrary(person.id))
                                        }
                                        .padding(horizontal = Spacing.small, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SfSymbol(group.category.symbolName, colors.label, typography.caption)
                                    KText(group.category.title, style = typography.caption)
                                    KText("${group.count}", style = typography.caption, color = colors.secondaryLabel)
                                }
                            }
                        }
                    }
                }
                if (all.isNotEmpty()) {
                    row {
                        ListRow(onClick = { navigator.push(Route.MemoryLibrary(person.id)) }) {
                            IconLabel("See everything · ${MemoryEngine.totalLabel(all.size)}", "chevron.right.circle", typography.subheadline, colors.accent)
                        }
                    }
                }
            }

            // Notes
            SectionSpacer()
            com.chrisallenclark.keepr.ui.notes.ProfileNotesSection(person)
            SectionSpacer()

            // Files
            com.chrisallenclark.keepr.ui.files.ProfileFilesSection(person)
            SectionSpacer()

            // Connections
            val connections = person.connections
            if (connections.isNotEmpty()) {
                GroupedSection(header = "Connections", headerTrailing = { HeaderButton("Link") { navigator.present(SheetRoute.LinkEditor(person.id)) } }) {
                    connections.forEach { connection ->
                        row(compactTextStart) {
                            SwipeActionsRow(trailing = listOf(SwipeAction("Unlink", "minus.circle", colors.red) {
                                state.context.delete(connection.link)
                                state.save()
                            })) {
                                ListRow(onClick = { onOpenPerson(connection.person) }) {
                                    Avatar(connection.person.initials, size = AvatarSize.Small, imageData = connection.person.photoData)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        KText(connection.person.displayName, style = typography.subheadline.medium)
                                        KText(connection.label, style = typography.caption, color = colors.secondaryLabel)
                                        connection.link.note?.takeIf { it.isNotEmpty() }?.let { KText(it, style = typography.caption2, color = colors.tertiaryLabel, maxLines = 2) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                GroupedSection(header = "Connections", footer = "Their partner, their assistant, whoever introduced you.") {
                    row { ListRow(onClick = { navigator.present(SheetRoute.LinkEditor(person.id)) }) { IconLabel("Link to someone else", "link", typography.subheadline, colors.accent) } }
                }
            }
            SectionSpacer()

            // Interactions
            val timeline = person.timeline
            val visible = if (showsAllInteractions) timeline else timeline.take(5)
            GroupedSection(header = "Interactions", headerTrailing = { if (timeline.isNotEmpty()) HeaderButton("Log") { showsLog = true } }) {
                if (timeline.isEmpty()) {
                    row { ListRow(onClick = { showsLog = true }) { IconLabel("Log your first interaction", "plus.circle", typography.subheadline, colors.accent) } }
                }
                visible.forEach { interaction ->
                    row(followUpTextStart - 4.dp) {
                        SwipeActionsRow(trailing = listOf(SwipeAction("Delete", "trash", colors.red) {
                            state.context.delete(interaction)
                            person.lastInteractionAt = person.timeline.firstOrNull { it.id != interaction.id }?.occurredAt
                            state.save()
                        })) {
                            ListRow { InteractionRow(interaction, state, Modifier.weight(1f)) }
                        }
                    }
                }
                if (timeline.size > 5) {
                    row {
                        ListRow(onClick = { showsAllInteractions = !showsAllInteractions }) {
                            KText(if (showsAllInteractions) "Show Less" else "Show All ${timeline.size}", style = typography.subheadline, color = colors.accent)
                        }
                    }
                }
            }
            SectionSpacer()

            // Details
            GroupedSection(header = "Details") {
                row { LabeledRow("Context", person.context.title) }
                row {
                    val status = CadenceEngine.status(person, state.now(), state.calendar)
                    val value = status?.let { CadenceEngine.label(it.days) } ?: if (person.cadenceDays == 0) "Never" else "No rhythm"
                    Box {
                        LabeledRow("Reach out", value, detail = status?.source?.let { "from $it" }, onClick = { cadenceMenuOpen = true })
                        Box(Modifier.align(Alignment.TopEnd)) {
                            KeeprMenu(
                                cadenceMenuOpen, { cadenceMenuOpen = false },
                                listOf(
                                    MenuEntry.Action("Use my type's rhythm") { setCadence(person, null, state) },
                                    MenuEntry.Action("Never remind me") { setCadence(person, 0, state) },
                                    MenuEntry.Divider,
                                ) + CadenceEngine.presets.map { days -> MenuEntry.Action(CadenceEngine.label(days)) { setCadence(person, days, state) } },
                            )
                        }
                    }
                }
                if (person.tagList.isNotEmpty()) row { LabeledRow("Type", person.tagList.joinToString(", ") { it.name }) }
                person.howWeMet?.takeIf { it.isNotEmpty() }?.let { row { LabeledRow("How we met", it) } }
                person.introducedBy?.takeIf { it.isNotEmpty() }?.let { row { LabeledRow("Introduced by", it) } }
                row { LabeledRow("Last interaction", RelativeDate.lastContact(person.lastInteractionAt, state.now(), state.calendar)) }
                row { LabeledRow("Added", RelativeDate.absolute(person.createdAt, state.now(), state.calendar)) }
                person.notes?.takeIf { it.isNotEmpty() }?.let { notesText ->
                    row {
                        ListRow {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                                KText("Notes", style = typography.caption, color = colors.secondaryLabel)
                                KText(notesText, style = typography.subheadline)
                            }
                        }
                    }
                }
                if (!person.isLinkedToContact) {
                    row { ListRow { IconLabel("Not linked to a contact", "person.crop.circle.badge.questionmark", typography.caption, colors.secondaryLabel) } }
                }
            }
            TabBarClearance()
        }
    }

    if (showsLog) LogInteractionSheet(person, onDismiss = { showsLog = false })
    if (showsEdit) EditPersonSheet(person, mode, onDismiss = { showsEdit = false })
    if (showsNewFollowUp) FollowUpEditorSheet(person, null, onDismiss = { showsNewFollowUp = false })
    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete ${person.displayName}?",
            message = "This removes everything you've recorded about them. Their contact card is not affected.",
            confirmTitle = "Delete",
            onConfirm = {
                state.context.delete(person)
                state.save()
                onBack()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

private fun setCadence(person: Person, days: Int?, state: AppState) {
    person.cadenceDays = days
    person.touch(state.now())
    state.save()
}

/** The small accent button in a section header: "Add", "Log", "Link". */
@Composable
private fun HeaderButton(title: String, onClick: () -> Unit) {
    KeeprButton(title, onClick, textStyle = KeeprTheme.typography.caption.semibold)
}

/** Space above the avatar, matching where iOS's List puts its first row. */
private val profileHeaderTopInset = 56.dp

/**
 * `Label(title, systemImage:)` as a List row draws it: the icon tinted with
 * the accent color and sized a step above the text, in a fixed-width column
 * so the title lines up with the hairline under it.
 */
@Composable
private fun ListLabel(title: String, symbolName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            SfSymbol(symbolName, KeeprTheme.colors.accent, 22.dp)
        }
        Spacer(Modifier.width(16.dp))
        KText(title, style = KeeprTheme.typography.subheadline)
    }
}

/** iOS `MemoryRow`. */
@Composable
private fun MemoryRow(memory: Memory, modifier: Modifier) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SfSymbol(memory.category.symbolName, colors.secondaryLabel, typography.footnote, Modifier.width(20.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(memory.content, style = typography.subheadline)
            if (memory.category != com.chrisallenclark.keepr.core.model.MemoryCategory.OTHER) {
                KText(memory.category.title, style = typography.caption2, color = colors.tertiaryLabel)
            }
        }
        if (memory.importance == Priority.HIGH) SfSymbol("star.fill", colors.yellow, typography.caption2, contentDescription = "Important")
    }
}

/** iOS `InteractionRow`. */
@Composable
private fun InteractionRow(interaction: Interaction, state: AppState, modifier: Modifier) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SfSymbol(interaction.kind.symbolName, colors.secondaryLabel, typography.footnote, Modifier.width(20.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                KText(RelativeDate.past(interaction.occurredAt, state.now(), state.calendar), style = typography.caption.medium, color = colors.secondaryLabel)
                KText("·", style = typography.caption, color = colors.tertiaryLabel)
                KText(interaction.kind.title, style = typography.caption, color = colors.tertiaryLabel)
            }
            KText(interaction.headline, style = typography.subheadline)
            interaction.detail?.let { KText(it, style = typography.footnote, color = colors.secondaryLabel, maxLines = 3) }
        }
    }
}

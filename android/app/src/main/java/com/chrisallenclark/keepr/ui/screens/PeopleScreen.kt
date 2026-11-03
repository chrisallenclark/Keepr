package com.chrisallenclark.keepr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.GroupVocabulary
import com.chrisallenclark.keepr.core.domain.PeopleEngine
import com.chrisallenclark.keepr.core.domain.PeopleSort
import com.chrisallenclark.keepr.core.domain.PlaceFilter
import com.chrisallenclark.keepr.core.domain.RelativeDate
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.core.services.CommunicationLauncher
import com.chrisallenclark.keepr.core.services.ContactMethod
import com.chrisallenclark.keepr.ui.components.ContextSwitcher
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.FilterChipRow
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprButtonStyle
import com.chrisallenclark.keepr.ui.components.KeeprMenu
import com.chrisallenclark.keepr.ui.components.KeeprTopBar
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.MenuEntry
import com.chrisallenclark.keepr.ui.components.PersonRow
import com.chrisallenclark.keepr.ui.components.SearchField
import com.chrisallenclark.keepr.ui.components.SectionHeader
import com.chrisallenclark.keepr.ui.components.SectionIndexBar
import com.chrisallenclark.keepr.ui.components.Separator
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.TabBarClearance
import com.chrisallenclark.keepr.ui.components.TagLabel
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.ToolbarTextButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.Chrome
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold
import kotlinx.coroutines.launch
import com.chrisallenclark.keepr.ui.navigation.SheetRoute
import java.util.UUID

/** Which bulk menu is open in select mode — Android menus don't nest, so submenus open in turn. */
private enum class BulkMenu { ROOT, ADD_TYPE, REMOVE_TYPE, ADD_GROUP, MOVE_TO, PRIORITY }

/**
 * All relationships in the current context: filter by type and group, five
 * sorts, A–Z sections with an index, search, swipe to favorite, and select
 * mode for changing many at once. Translated from
 * Keepr/Features/People/PeopleView.swift.
 */
@Composable
fun PeopleScreen(
    mode: ModeBinding,
    onOpenPerson: (Person) -> Unit,
    onAddPerson: () -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val state = LocalAppState.current
    val navigator = com.chrisallenclark.keepr.ui.navigation.LocalNavigator.current
    val haptics = rememberHaptics()
    val androidContext = LocalContext.current
    observeStore().value
    val colors = KeeprTheme.colors
    val prefs = state.repository.preferences

    var query by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlaceID by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by remember {
        // iOS: the first launch after this sort existed switches to alphabetical once.
        if (!prefs.bool(PreferenceKey.DID_DEFAULT_TO_NAME_SORT)) {
            prefs.setBool(PreferenceKey.DID_DEFAULT_TO_NAME_SORT, true)
            prefs.setString(PreferenceKey.PEOPLE_SORT, PeopleSort.NAME.rawValue)
        }
        mutableStateOf(PeopleSort.from(prefs.string(PreferenceKey.PEOPLE_SORT)) ?: PeopleSort.NAME)
    }
    var isSelecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<UUID>()) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var bulkMenu by remember { mutableStateOf<BulkMenu?>(null) }
    var contextMenuFor by remember { mutableStateOf<Person?>(null) }
    var confirmingBulkDelete by remember { mutableStateOf(false) }

    val groupLabel = prefs.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular
    val groupPlural = GroupVocabulary.plural(groupLabel)
    val article = GroupVocabulary.article(groupLabel)

    val people = state.context.fetch<Person>().sortedBy { it.familyName }
    val tags = state.context.fetch<RelationshipTag>().sortedBy { it.sortOrder }
    val groups = state.context.fetch<PersonGroup>().sortedBy { it.sortOrder }
    val place = PlaceFilter(selectedPlaceID)
    val filtered = PeopleEngine.sort(PeopleEngine.filter(people, mode.mode, selectedTag, query, place = place), sort)
    val typeFacets = PeopleEngine.typeFacets(PeopleEngine.filter(people, mode.mode, query = query, place = place), tags, mode.mode, selectedTag)
    val placeFacets = PeopleEngine.placeFacets(PeopleEngine.filter(people, mode.mode, selectedTag, query), groups, mode.mode, selectedPlaceID)
    val hasActiveFilters = selectedTag != null || selectedPlaceID != null
    val selectedPeople = people.filter { it.id in selection }

    fun beginSelecting(with: Person? = null) {
        isSelecting = true
        selection = with?.let { setOf(it.id) } ?: emptySet()
    }
    fun endSelecting() {
        isSelecting = false
        selection = emptySet()
    }
    fun applyToSelection(endsSelection: Boolean = false, change: (Person) -> Unit) {
        val targets = selectedPeople
        if (targets.isEmpty()) return
        targets.forEach { change(it); it.touch(state.now()) }
        if (endsSelection) endSelecting()
        state.save()
        haptics.success()
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        KeeprTopBar(
            title = if (isSelecting) (if (selection.isEmpty()) "Select People" else "${selection.size} Selected") else "People",
            leading = {
                Box {
                    ToolbarButton(
                        if (hasActiveFilters) "line.3.horizontal.decrease.circle.fill" else "line.3.horizontal.decrease.circle",
                        "Sort and Filter",
                        { filterMenuOpen = true },
                    )
                    KeeprMenu(
                        expanded = filterMenuOpen,
                        onDismiss = { filterMenuOpen = false },
                        entries = buildList {
                            add(MenuEntry.Action("Select", "checkmark.circle") { beginSelecting() })
                            add(MenuEntry.Divider)
                            PeopleSort.entries.forEach { option ->
                                add(MenuEntry.Action(option.title, option.symbolName, isChecked = option == sort) {
                                    sort = option
                                    prefs.setString(PreferenceKey.PEOPLE_SORT, option.rawValue)
                                })
                            }
                            if (hasActiveFilters) {
                                add(MenuEntry.Divider)
                                add(MenuEntry.Action("Clear Filters", "xmark.circle") { selectedTag = null; selectedPlaceID = null })
                            }
                            add(MenuEntry.Divider)
                            add(MenuEntry.Action("Manage $groupPlural", "mappin.and.ellipse") { navigator.present(SheetRoute.Groups) })
                            add(MenuEntry.Action("Manage Types", "tag") { navigator.present(SheetRoute.RelationshipTypes) })
                        },
                    )
                }
            },
            trailing = {
                if (isSelecting) {
                    ToolbarTextButton("Done", ::endSelecting, isProminent = false)
                } else {
                    Box {
                        ToolbarButton("plus", "Add Person", { addMenuOpen = true })
                        KeeprMenu(
                            expanded = addMenuOpen,
                            onDismiss = { addMenuOpen = false },
                            entries = listOf(
                                MenuEntry.Action("Import from Contacts", "person.crop.circle.badge.plus") { navigator.present(SheetRoute.ContactImport) },
                                MenuEntry.Action("Catch Up", "person.crop.circle.badge.questionmark") { navigator.present(SheetRoute.Review) },
                                MenuEntry.Action("Add Manually", "square.and.pencil", onClick = onAddPerson),
                            ),
                        )
                    }
                }
            },
            pinned = {
                SearchField(query, { query = it }, "Name, company, or anything you noted")
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 15.dp))
                ContextSwitcher(mode.mode == ContextMode.PERSONAL, { mode.set(if (it) ContextMode.PERSONAL else ContextMode.BUSINESS) })
            },
        )

        if (filtered.isEmpty() && !hasActiveFilters) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (query.isNotBlank()) {
                    EmptyState("No Results for “$query”", "magnifyingglass", description = "Check the spelling or try a new search.", modifier = Modifier.padding(bottom = 60.dp))
                } else {
                    EmptyState(
                        "No ${mode.mode.title.lowercase()} relationships",
                        mode.mode.symbolName,
                        description = "Import from your contacts, or add someone by hand.",
                        modifier = Modifier.padding(bottom = 60.dp),
                    ) {
                        KeeprButton("Import from Contacts", { navigator.present(SheetRoute.ContactImport) }, style = KeeprButtonStyle.Prominent)
                        KeeprButton("Add Someone", onAddPerson)
                    }
                }
            }
            return@Column
        }

        val sections = if (sort == PeopleSort.NAME) PeopleEngine.alphabeticalSections(filtered) else null
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val headerIndex = mutableMapOf<String, Int>()

        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                var index = 0
                if (!isSelecting) {
                    item(key = "filters") {
                        Column(Modifier.padding(vertical = 2.dp)) {
                            if (typeFacets.isNotEmpty()) FilterChipRow("Type", typeFacets, selectedTag, { selectedTag = it })
                            if (placeFacets.isNotEmpty()) {
                                FilterChipRow(groupLabel, placeFacets, selectedPlaceID, { selectedPlaceID = it })
                            } else {
                                IconLabel(
                                    "Add $article ${groupLabel.lowercase()} — a gym, one of your businesses, an app",
                                    "mappin.and.ellipse",
                                    KeeprTheme.typography.caption,
                                    colors.secondaryLabel,
                                    Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.tight),
                                )
                            }
                        }
                    }
                    index++
                }
                if (filtered.isEmpty()) {
                    item(key = "nomatch") {
                        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.large), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                            KText("Nobody matches that combination", style = KeeprTheme.typography.subheadline.medium)
                            KeeprButton("Clear Filters", { selectedTag = null; selectedPlaceID = null }, textStyle = KeeprTheme.typography.subheadline)
                        }
                    }
                    index++
                }

                fun rowItem(person: Person, isFirst: Boolean) {
                    item(key = person.id) {
                        PersonListRow(
                            person = person,
                            mode = mode.mode,
                            query = query,
                            showsSeparator = !isFirst,
                            isSelecting = isSelecting,
                            isSelected = person.id in selection,
                            onTap = {
                                if (isSelecting) {
                                    selection = if (person.id in selection) selection - person.id else selection + person.id
                                    haptics.selection()
                                } else {
                                    onOpenPerson(person)
                                }
                            },
                            onLongPress = { if (!isSelecting) contextMenuFor = person },
                            onToggleFavorite = { state.toggleFavorite(person, haptics) },
                            menuExpanded = contextMenuFor === person,
                            onDismissMenu = { contextMenuFor = null },
                            menuEntries = buildList {
                                ContactMethod.entries.forEach { method ->
                                    if (CommunicationLauncher.url(method, person) != null) {
                                        add(MenuEntry.Action(method.title, method.symbolName) { androidContext.open(method, person) })
                                    }
                                }
                                add(MenuEntry.Divider)
                                add(MenuEntry.Action(if (person.isFavorite) "Remove Favorite" else "Favorite", if (person.isFavorite) "star.slash" else "star") { state.toggleFavorite(person, haptics) })
                                add(MenuEntry.Action("Select…", "checkmark.circle") { beginSelecting(person) })
                            },
                        )
                    }
                }

                if (sections != null) {
                    sections.forEach { section ->
                        headerIndex[section.key] = index
                        item(key = "h-${section.key}") {
                            SectionHeader(section.key, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
                        }
                        index++
                        section.people.forEachIndexed { i, person -> rowItem(person, i == 0); index++ }
                    }
                } else {
                    filtered.forEachIndexed { i, person -> rowItem(person, i == 0) }
                }
                item(key = "clearance") { TabBarClearance() }
            }

            if (sections != null && !isSelecting && sections.size > 2) {
                SectionIndexBar(
                    titles = sections.map { it.key },
                    onSelect = { key -> headerIndex[key]?.let { scope.launch { listState.animateScrollToItem(it) } } },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 80.dp),
                )
            }

            if (isSelecting) {
                val allVisibleSelected = filtered.isNotEmpty() && filtered.all { it.id in selection }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Chrome.tabBarClearance - 8.dp)
                        .fillMaxWidth()
                        .background(colors.bar)
                        .padding(horizontal = 16.dp, vertical = Spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.large),
                ) {
                    KeeprButton("Done", ::endSelecting, textStyle = KeeprTheme.typography.subheadline.semibold)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    KeeprButton(
                        if (allVisibleSelected) "Deselect All" else "Select All",
                        {
                            val visible = filtered.map { it.id }.toSet()
                            selection = if (allVisibleSelected) selection - visible else selection + visible
                            haptics.selection()
                        },
                        enabled = filtered.isNotEmpty(),
                        textStyle = KeeprTheme.typography.subheadline,
                    )
                    Box {
                        KeeprButton("Categorize", { bulkMenu = BulkMenu.ROOT }, symbolName = "square.and.pencil", enabled = selection.isNotEmpty(), textStyle = KeeprTheme.typography.subheadline.semibold)
                        val relevantTags = tags.filter { it.kind == TagKind.of(mode.mode) }
                        val relevantGroups = groups.filter { it.matches(mode.mode) }
                        KeeprMenu(
                            expanded = bulkMenu != null,
                            onDismiss = { bulkMenu = null },
                            entries = when (bulkMenu) {
                                BulkMenu.ADD_TYPE -> relevantTags.map { tag ->
                                    MenuEntry.Action(tag.name, tag.symbolName) { applyToSelection { if (!it.hasTag(tag.name)) it.tags = it.tagList + tag } }
                                }
                                BulkMenu.REMOVE_TYPE -> relevantTags.map { tag ->
                                    MenuEntry.Action(tag.name, tag.symbolName) { applyToSelection { p -> p.tags = p.tagList.filter { it.id != tag.id } } }
                                }
                                BulkMenu.ADD_GROUP -> relevantGroups.map { group ->
                                    MenuEntry.Action(group.name, group.symbolName) { applyToSelection { if (!it.isMember(group)) it.groups = it.groupList + group } }
                                }
                                BulkMenu.MOVE_TO -> RelationshipContext.entries.map { option ->
                                    MenuEntry.Action(option.title, option.symbolName) { applyToSelection { it.context = option } }
                                }
                                BulkMenu.PRIORITY -> Priority.entries.map { option ->
                                    MenuEntry.Action(option.title) { applyToSelection { it.priority = option } }
                                }
                                else -> buildList {
                                    if (relevantTags.isNotEmpty()) {
                                        add(MenuEntry.Action("Add Relationship Type…", "tag") { bulkMenu = BulkMenu.ADD_TYPE })
                                        add(MenuEntry.Action("Remove Relationship Type…", "tag") { bulkMenu = BulkMenu.REMOVE_TYPE })
                                    }
                                    if (relevantGroups.isNotEmpty()) add(MenuEntry.Action("Add to $groupLabel…", "mappin.and.ellipse") { bulkMenu = BulkMenu.ADD_GROUP })
                                    add(MenuEntry.Divider)
                                    add(MenuEntry.Action("Move to…", "arrow.triangle.branch") { bulkMenu = BulkMenu.MOVE_TO })
                                    add(MenuEntry.Action("Priority…", "exclamationmark") { bulkMenu = BulkMenu.PRIORITY })
                                    add(MenuEntry.Action("Favorite", "star") { applyToSelection { it.isFavorite = true } })
                                    add(MenuEntry.Action("Remove Favorite", "star.slash") { applyToSelection { it.isFavorite = false } })
                                    add(MenuEntry.Divider)
                                    add(MenuEntry.Action("Archive", "archivebox") { applyToSelection(endsSelection = true) { it.status = RelationshipStatus.ARCHIVED } })
                                    add(MenuEntry.Action("Delete", "trash", isDestructive = true) { confirmingBulkDelete = true })
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmingBulkDelete) {
        ConfirmDialog(
            title = "Delete ${selection.size} ${if (selection.size == 1) "person" else "people"}?",
            message = "This removes everything recorded about them. Their contact cards are not affected.",
            confirmTitle = "Delete",
            onConfirm = {
                selectedPeople.forEach { state.context.delete(it) }
                endSelecting()
                state.save()
                haptics.success()
            },
            onDismiss = { confirmingBulkDelete = false },
        )
    }
}

/** One person in the list, with its swipe, long-press menu, and selection circle. */
@Composable
private fun PersonListRow(
    person: Person,
    mode: ContextMode,
    query: String,
    showsSeparator: Boolean,
    isSelecting: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    menuEntries: List<MenuEntry>,
) {
    val state = LocalAppState.current
    val colors = KeeprTheme.colors
    val reason = PeopleEngine.reason(person, query)
    val explanation = reason?.takeIf { it.field > com.chrisallenclark.keepr.core.domain.SearchField.TAG && !it.snippet.isNullOrEmpty() }
    Column {
        if (showsSeparator) Separator(start = 16.dp + (if (isSelecting) 36.dp else 0.dp) + 44.dp + Spacing.medium)
        Box {
            SwipeActionsRow(
                leading = if (isSelecting) emptyList() else listOf(
                    SwipeAction(if (person.isFavorite) "Unfavorite" else "Favorite", if (person.isFavorite) "star.slash" else "star", colors.yellow, onToggleFavorite),
                ),
                rowBackground = colors.background,
            ) {
                ListRow(onClick = onTap, onLongClick = onLongPress, verticalPadding = 15.5.dp) {
                    if (isSelecting) {
                        SfSymbol(
                            if (isSelected) "checkmark.circle.fill" else "circle",
                            if (isSelected) colors.accent else colors.tertiaryLabel,
                            24.dp,
                        )
                    }
                    PersonRow(
                        name = person.displayName,
                        initials = person.initials,
                        modifier = Modifier.weight(1f),
                        subtitle = person.subtitle,
                        isFavorite = person.isFavorite,
                        tags = if (explanation == null) person.headlineTags(mode).map { TagLabel(it.name, it.symbolName) } else emptyList(),
                        lastInteraction = person.lastInteractionAt?.let { RelativeDate.past(it, state.now(), state.calendar) },
                        nextDue = person.nextFollowUp?.let { RelativeDate.due(it.dueDate, state.now(), state.calendar) },
                        nextIsOverdue = person.nextFollowUp?.isOverdue(state.now(), state.calendar) == true,
                        photo = person.photoData,
                        explanation = explanation?.let { it.snippet!! to it.field.symbolName },
                    )
                }
            }
            KeeprMenu(expanded = menuExpanded, onDismiss = onDismissMenu, entries = menuEntries)
        }
    }
}

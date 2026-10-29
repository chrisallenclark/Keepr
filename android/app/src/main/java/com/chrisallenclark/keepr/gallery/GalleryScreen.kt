package com.chrisallenclark.keepr.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.components.Avatar
import com.chrisallenclark.keepr.ui.components.AvatarSize
import com.chrisallenclark.keepr.ui.components.ContextSwitcher
import com.chrisallenclark.keepr.ui.components.DisclosureChevron
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.FilterChipRow
import com.chrisallenclark.keepr.ui.components.FilterFacet
import com.chrisallenclark.keepr.ui.components.FloatingCard
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprButtonStyle
import com.chrisallenclark.keepr.ui.components.KeeprMenu
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.KeeprTabBar
import com.chrisallenclark.keepr.ui.components.KeeprToggle
import com.chrisallenclark.keepr.ui.components.KeeprTopBar
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.MenuEntry
import com.chrisallenclark.keepr.ui.components.SearchField
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.TabItem
import com.chrisallenclark.keepr.ui.components.TagChip
import com.chrisallenclark.keepr.ui.components.TagRow
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.ToolbarTextButton
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.icons.sfSymbolNames
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.bold
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * Every piece of the design system on a handful of pages, in both
 * appearances, for visual testing. CI opens each page directly
 * (`--es keepr.screen gallery-<page>`) and screenshots it on two phone sizes;
 * in the app it's reachable from Today's gear button while Settings is built.
 */
private val pages = listOf(
    "type" to "Typography",
    "color" to "Colors",
    "controls" to "Controls",
    "lists" to "Lists & Swipe Actions",
    "icons" to "Symbols",
    "sheet" to "Sheet",
    "menu" to "Menu",
)

@Composable
fun GalleryScreen(page: String?, onBack: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(page) }
    val launchedOnPage = page != null
    BackHandler(enabled = current != null && !launchedOnPage) { current = null }

    val title = pages.firstOrNull { it.first == current }?.second ?: "Design Gallery"
    Column(Modifier.fillMaxSize().background(KeeprTheme.colors.groupedBackground)) {
        KeeprTopBar(
            title = title,
            leading = {
                ToolbarButton("chevron.left", "Back", {
                    if (current != null && !launchedOnPage) current = null else onBack()
                })
            },
        )
        when (current) {
            null -> GalleryIndex { current = it }
            "type" -> TypePage()
            "color" -> ColorPage()
            "controls" -> ControlsPage()
            "lists" -> ListsPage()
            "icons" -> IconsPage()
            "sheet" -> SheetPage()
            "menu" -> MenuPage()
            else -> GalleryIndex { current = it }
        }
    }
}

@Composable
private fun GalleryIndex(onOpen: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.small)) {
        GroupedSection(footer = "Each page is captured in light and dark on two phone sizes by CI and compared with the iOS screenshots.") {
            pages.forEach { (id, name) ->
                row {
                    ListRow(onClick = { onOpen(id) }) {
                        KText(name, Modifier.weight(1f))
                        SfSymbol("chevron.right", KeeprTheme.colors.tertiaryLabel, KeeprTheme.typography.caption.semibold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Spacing.small, bottom = 40.dp),
    ) { content() }
}

@Composable
private fun TypePage() {
    val t = KeeprTheme.typography
    val styles: List<Pair<String, TextStyle>> = listOf(
        "Large Title" to t.largeTitle.bold,
        "Title" to t.title,
        "Title 2" to t.title2,
        "Title 3" to t.title3,
        "Headline" to t.headline,
        "Body" to t.body,
        "Callout" to t.callout,
        "Subheadline" to t.subheadline,
        "Footnote" to t.footnote,
        "Caption" to t.caption,
        "Caption 2" to t.caption2,
    )
    PageColumn {
        GroupedSection(header = "Text styles") {
            styles.forEach { (name, style) ->
                row {
                    ListRow(verticalPadding = 10.dp) {
                        KText(name, style = style, modifier = Modifier.weight(1f), maxLines = 1)
                        KText("${style.fontSize.value.toInt()}", style = t.caption, color = KeeprTheme.colors.secondaryLabel)
                    }
                }
            }
        }
        SectionSpacer()
        GroupedSection(header = "Weights", footer = "Inter stands in for SF Pro; Inter Display above 20pt, as SF switches to Display.") {
            listOf("Regular" to t.body, "Medium" to t.body.medium, "Semibold" to t.body.semibold, "Bold" to t.body.bold).forEach { (name, style) ->
                row { ListRow { KText("$name — Sarah Miller, Coastal Design Co.", style = style, maxLines = 1) } }
            }
        }
        SectionSpacer()
        GroupedSection(header = "Hierarchy") {
            val c = KeeprTheme.colors
            listOf("Primary" to c.label, "Secondary" to c.secondaryLabel, "Tertiary" to c.tertiaryLabel, "Quaternary" to c.quaternaryLabel, "Accent" to c.accent).forEach { (name, color) ->
                row { ListRow { KText("$name — Send the September training block", color = color, style = t.subheadline, maxLines = 1) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPage() {
    val c = KeeprTheme.colors
    val swatches = listOf(
        "background" to c.background, "groupedBackground" to c.groupedBackground,
        "secondaryGrouped" to c.secondaryGroupedBackground, "tertiaryGrouped" to c.tertiaryGroupedBackground,
        "bar" to c.bar, "glass" to c.glass, "glassSelection" to c.glassSelection,
        "segmentTrack" to c.segmentTrack, "segmentThumb" to c.segmentThumb, "searchField" to c.searchField,
        "label" to c.label, "secondaryLabel" to c.secondaryLabel, "tertiaryLabel" to c.tertiaryLabel,
        "quaternaryLabel" to c.quaternaryLabel, "fill" to c.fill, "separator" to c.separator,
        "accent" to c.accent, "red" to c.red, "orange" to c.orange, "yellow" to c.yellow,
        "green" to c.green, "blue" to c.blue, "gray" to c.gray,
    )
    PageColumn {
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            swatches.forEach { (name, color) ->
                Column(Modifier.width(104.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(color, RoundedCornerShape(10.dp))
                            .border(0.5.dp, c.separator, RoundedCornerShape(10.dp)),
                    )
                    KText(name, style = KeeprTheme.typography.caption2, color = c.secondaryLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ControlsPage() {
    var personal by remember { mutableStateOf(false) }
    var toggle by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) }
    PageColumn {
        ContextSwitcher(personal, { personal = it })
        Spacer(Modifier.height(Spacing.small))
        SearchField(query, { query = it }, "Name, company, or anything you noted")
        Spacer(Modifier.height(Spacing.medium))
        FilterChipRow(
            "Type",
            listOf(FilterFacet("a", "Current Client", "tag", 1), FilterFacet("b", "Potential Client", "tag", 2)),
            type,
            { type = it },
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolbarButton("gearshape", "Settings", {})
            ToolbarButton("square.and.pencil", "Compose", {})
            ToolbarButton("plus", "Add", {})
            ToolbarTextButton("Cancel", {})
            ToolbarTextButton("Save", {}, isProminent = true)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            KeeprButton("Quick Capture", {}, style = KeeprButtonStyle.Bordered)
            KeeprButton("Import", {}, style = KeeprButtonStyle.Prominent)
            KeeprToggle(toggle, { toggle = it })
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar("JM", size = AvatarSize.Small)
            Avatar("SM", size = AvatarSize.Medium)
            Avatar("MR", size = AvatarSize.Large)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip("Current Client", symbolName = "checkmark.seal")
                TagChip("Lead", symbolName = "flame", isProminent = true)
                TagRow(listOf("Client" to "tag", "Lead" to "tag", "Vendor" to "tag"))
            }
        }
        FloatingCard(Modifier.padding(horizontal = 16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KText("Consultation — Jake", style = KeeprTheme.typography.subheadline.semibold, modifier = Modifier.weight(1f))
                    DisclosureChevron(expanded)
                }
                KText(
                    "Wants to lose about twenty pounds before the wedding. Prefers early mornings.",
                    style = KeeprTheme.typography.subheadline,
                    color = KeeprTheme.colors.secondaryLabel,
                )
            }
        }
        Spacer(Modifier.height(Spacing.large))
        EmptyState(
            title = "You're caught up.",
            symbolName = "checkmark.circle",
            description = "Nothing needs your attention in Business right now.",
        ) {
            KeeprButton("Quick Capture", {}, style = KeeprButtonStyle.Bordered)
        }
        Spacer(Modifier.height(Spacing.large))
        KeeprTabBar(
            items = listOf(
                TabItem("Today", "sun.max", "sun.max.fill"),
                TabItem("People", "person.2", "person.2.fill"),
                TabItem("Follow Up", "bell", "bell.fill"),
                TabItem("Search", "magnifyingglass"),
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
        )
    }
}

@Composable
private fun ListsPage() {
    val c = KeeprTheme.colors
    PageColumn {
        GroupedSection(header = "Swipe actions", footer = "Rows below are shown mid-swipe. Drag any row to try it; a long drag performs the first action.") {
            row(52.dp) {
                SwipeActionsRow(
                    leading = listOf(SwipeAction("Tomorrow", "clock", c.orange) {}),
                    trailing = listOf(SwipeAction("Done", "checkmark", c.green) {}),
                    previewOffset = (-90).dp,
                ) {
                    ListRow { KText("Send the September training block", style = KeeprTheme.typography.subheadline) }
                }
            }
            row(52.dp) {
                SwipeActionsRow(
                    leading = listOf(SwipeAction("Favorite", "star", c.yellow) {}),
                    previewOffset = 90.dp,
                ) {
                    ListRow { KText("Jake Martinez", style = KeeprTheme.typography.body.medium) }
                }
            }
            row {
                SwipeActionsRow(trailing = listOf(SwipeAction("Reached Out", "paperplane", c.blue) {})) {
                    ListRow { KText("Dana Whitfield", style = KeeprTheme.typography.body.medium) }
                }
            }
        }
        SectionSpacer()
        GroupedSection(header = "Settings rows") {
            row { ListRow { KText("Reminders", Modifier.weight(1f)); KeeprToggle(true, {}) } }
            row { ListRow { KText("Sample data", Modifier.weight(1f)); KeeprToggle(false, {}) } }
            row {
                ListRow(onClick = {}) {
                    KText("What Keepr can't do", Modifier.weight(1f))
                    SfSymbol("chevron.right", c.tertiaryLabel, KeeprTheme.typography.caption.semibold)
                }
            }
            row { ListRow(onClick = {}) { KText("Delete All Data", color = c.red) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconsPage() {
    val c = KeeprTheme.colors
    PageColumn {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val t = KeeprTheme.typography
            listOf(t.caption2, t.caption, t.footnote, t.subheadline, t.body, t.title3, t.title2).forEach { style ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SfSymbol("star", c.label, style)
                    KText("Aa", style = style)
                }
            }
        }
        Spacer(Modifier.height(Spacing.medium))
        FlowRow(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sfSymbolNames.forEach { name ->
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    SfSymbol(name, c.label, 24.dp, contentDescription = name)
                }
            }
        }
    }
}

@Composable
private fun SheetPage() {
    var showing by remember { mutableStateOf(true) }
    var toggle by remember { mutableStateOf(true) }
    PageColumn {
        KeeprButton("Show Sheet", { showing = true }, style = KeeprButtonStyle.Bordered, modifier = Modifier.padding(16.dp))
    }
    if (showing) {
        KeeprSheet(onDismiss = { showing = false }) {
            SheetBody {
                SheetHeader("Log Interaction", onCancel = { showing = false }, confirmTitle = "Save", onConfirm = { showing = false })
                Spacer(Modifier.height(Spacing.small))
                GroupedSection(header = "What happened") {
                    row { ListRow { KText("Type", Modifier.weight(1f)); KText("Coffee or Meal", color = KeeprTheme.colors.secondaryLabel) } }
                    row { ListRow { KText("Date", Modifier.weight(1f)); KText("Today", color = KeeprTheme.colors.secondaryLabel) } }
                }
                SectionSpacer()
                GroupedSection(header = "Follow up") {
                    row { ListRow { KText("Remind me", Modifier.weight(1f)); KeeprToggle(toggle, { toggle = it }) } }
                }
            }
        }
    }
}

@Composable
private fun MenuPage() {
    var expanded by remember { mutableStateOf(true) }
    Column(Modifier.padding(16.dp)) {
        Box {
            ToolbarButton("line.3.horizontal.decrease.circle", "Sort and Filter", { expanded = true })
            KeeprMenu(
                expanded = expanded,
                onDismiss = { expanded = false },
                entries = listOf(
                    MenuEntry.Action("Select", "checkmark.circle") {},
                    MenuEntry.Divider,
                    MenuEntry.Action("Name", isChecked = true) {},
                    MenuEntry.Action("Recently Contacted") {},
                    MenuEntry.Action("Priority") {},
                    MenuEntry.Divider,
                    MenuEntry.Action("Manage Groups", "mappin.and.ellipse") {},
                    MenuEntry.Action("Manage Types", "tag") {},
                    MenuEntry.Divider,
                    MenuEntry.Action("Delete", "trash", isDestructive = true) {},
                ),
            )
        }
    }
}


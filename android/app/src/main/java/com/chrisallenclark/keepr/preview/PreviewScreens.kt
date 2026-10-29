package com.chrisallenclark.keepr.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.chrisallenclark.keepr.ui.components.ContextSwitcher
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.FilterChipRow
import com.chrisallenclark.keepr.ui.components.FilterFacet
import com.chrisallenclark.keepr.ui.components.FollowUpRow
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprLargeTitleBar
import com.chrisallenclark.keepr.ui.components.KeeprTopBar
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.PersonCompactRow
import com.chrisallenclark.keepr.ui.components.PersonHeader
import com.chrisallenclark.keepr.ui.components.PersonRow
import com.chrisallenclark.keepr.ui.components.QuickAction
import com.chrisallenclark.keepr.ui.components.QuickActionsRow
import com.chrisallenclark.keepr.ui.components.SearchField
import com.chrisallenclark.keepr.ui.components.SectionHeader
import com.chrisallenclark.keepr.ui.components.SectionIndexBar
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.Separator
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.TabBarClearance
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import java.time.LocalTime

/*
 * Design preview screens: the four tabs and a profile, assembled from the
 * design system with the iOS demo content. They exist to be compared with the
 * iOS screenshots and to give a feel for the app before the data layer lands;
 * nothing here is wired to storage yet.
 */

private fun greeting(): String = when (LocalTime.now().hour) {
    in 0..11 -> "Good Morning"
    in 12..16 -> "Good Afternoon"
    else -> "Good Evening"
}

@Composable
private fun followUpSwipes(): Pair<List<SwipeAction>, List<SwipeAction>> {
    val colors = KeeprTheme.colors
    return listOf(SwipeAction("Tomorrow", "clock", colors.orange) {}) to
        listOf(SwipeAction("Done", "checkmark", colors.green) {})
}

/** Row content starts after the 24pt circle and a 12pt gap. */
private val followUpTextStart = ListMetrics.rowHorizontal + 24.dp + Spacing.medium
/** …or after a 36pt avatar and a 12pt gap. */
private val compactTextStart = ListMetrics.rowHorizontal + 36.dp + Spacing.medium

@Composable
fun TodayPreview(
    isPersonal: Boolean,
    onContextChange: (Boolean) -> Unit,
    onOpenPerson: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = KeeprTheme.colors
    val (leading, trailing) = followUpSwipes()
    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        KeeprTopBar(
            title = greeting(),
            leading = { ToolbarButton("gearshape", "Settings", onOpenSettings) },
            trailing = { ToolbarButton("square.and.pencil", "Quick Capture", {}) },
            pinned = { ContextSwitcher(isPersonal, onContextChange) },
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            if (!isPersonal) {
                GroupedSection(header = "Needs Attention") {
                    row(followUpTextStart) {
                        SwipeActionsRow(leading = leading, trailing = trailing) {
                            ListRow(onClick = onOpenPerson) {
                                FollowUpRow(
                                    "Send the September training block", "Overdue · Yesterday",
                                    personName = "Sarah Miller", isOverdue = true, isHighPriority = true,
                                )
                            }
                        }
                    }
                }
                SectionSpacer()
                GroupedSection(
                    header = "Time to Reach Out",
                    footer = "Based on how often you said these relationships are worth contacting. Logging anything resets the clock.",
                ) {
                    row(compactTextStart) {
                        SwipeActionsRow(trailing = listOf(SwipeAction("Reached Out", "paperplane", colors.blue) {})) {
                            ListRow(onClick = onOpenPerson) {
                                PersonCompactRow("Dana Whitfield", "DW", Modifier.weight(1f), detail = "Sep 21")
                                Spacer(Modifier.width(20.dp))
                                KText("8 days over", style = KeeprTheme.typography.caption.medium, color = colors.orange)
                            }
                        }
                    }
                }
                SectionSpacer(16.dp)
                GroupedSection(header = "Upcoming") {
                    row(followUpTextStart) {
                        SwipeActionsRow(leading = leading, trailing = trailing) {
                            ListRow(onClick = onOpenPerson) {
                                FollowUpRow("Text about starting training", "Saturday", personName = "Jake Martinez", isHighPriority = true)
                            }
                        }
                    }
                    row(followUpTextStart) {
                        SwipeActionsRow(leading = leading, trailing = trailing) {
                            ListRow(onClick = onOpenPerson) {
                                FollowUpRow("Pick a date for the workshop", "Tuesday", personName = "Michael Reed")
                            }
                        }
                    }
                }
                SectionSpacer()
                GroupedSection(header = "Going Quiet", footer = "No interaction logged in a while, and nothing planned.") {
                    row(compactTextStart) { ListRow(onClick = onOpenPerson) { PersonCompactRow("Priya Raman", "PR", Modifier.weight(1f), detail = "Oct 8") } }
                }
                SectionSpacer(16.dp)
                GroupedSection(header = "Recent") {
                    row(compactTextStart) { ListRow(onClick = onOpenPerson) { PersonCompactRow("Jake Martinez", "JM", Modifier.weight(1f), detail = "Yesterday") } }
                    row(compactTextStart) { ListRow(onClick = onOpenPerson) { PersonCompactRow("Sarah Miller", "SM", Modifier.weight(1f), detail = "3 days ago") } }
                }
            } else {
                GroupedSection(header = "Upcoming") {
                    row(followUpTextStart) {
                        SwipeActionsRow(leading = leading, trailing = trailing) {
                            ListRow { FollowUpRow("Call about October flights", "Monday", personName = "Mom") }
                        }
                    }
                    row(followUpTextStart) {
                        SwipeActionsRow(leading = leading, trailing = trailing) {
                            ListRow { FollowUpRow("Pick a date for the workshop", "Tuesday", personName = "Michael Reed") }
                        }
                    }
                }
                SectionSpacer()
                GroupedSection(header = "Going Quiet", footer = "No interaction logged in a while, and nothing planned.") {
                    row(compactTextStart) { ListRow { PersonCompactRow("Ben Ortiz", "BO", Modifier.weight(1f), detail = "Jul 26") } }
                }
                SectionSpacer(16.dp)
                GroupedSection(header = "Recent") {
                    row(compactTextStart) { ListRow { PersonCompactRow("Alex Nguyen", "AN", Modifier.weight(1f), detail = "2 days ago") } }
                    row(compactTextStart) { ListRow { PersonCompactRow("Mom", "M", Modifier.weight(1f), detail = "5 days ago") } }
                }
            }
            TabBarClearance()
        }
    }
}

@Composable
fun PeoplePreview(
    isPersonal: Boolean,
    onContextChange: (Boolean) -> Unit,
    onOpenPerson: () -> Unit,
) {
    val colors = KeeprTheme.colors
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<String?>(null) }
    var group by remember { mutableStateOf<String?>(null) }
    val sections = SampleContent.businessPeople
        .groupBy { it.name.substringAfterLast(' ').first().toString() }
        .toSortedMap()
    Column(Modifier.fillMaxSize().background(colors.background)) {
        KeeprTopBar(
            title = "People",
            leading = { ToolbarButton("line.3.horizontal.decrease.circle", "Sort and Filter", {}) },
            trailing = { ToolbarButton("plus", "Add Person", {}) },
            pinned = {
                SearchField(query, { query = it }, "Name, company, or anything you noted")
                Spacer(Modifier.height(15.dp))
                ContextSwitcher(isPersonal, onContextChange)
            },
        )
        Box(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FilterChipRow(
                    "Type",
                    listOf(
                        FilterFacet("Current Client", "Current Client", "tag", 1),
                        FilterFacet("Potential Client", "Potential Client", "tag", 2),
                        FilterFacet("Lead", "Lead", "tag", 1),
                    ),
                    type,
                    { type = it },
                )
                FilterChipRow(
                    "Group",
                    listOf(
                        FilterFacet("lt", "Life Time", "figure.run", 2),
                        FilterFacet("hs", "Home Studio", "house", 2),
                        FilterFacet("none", "No Group", "questionmark.circle", 1),
                    ),
                    group,
                    { group = it },
                )
                Spacer(Modifier.height(14.dp))
                sections.forEach { (letter, people) ->
                    SectionHeader(letter, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
                    people.forEachIndexed { index, person ->
                        if (index > 0) Separator(start = 16.dp + 44.dp + Spacing.medium)
                        SwipeActionsRow(
                            leading = listOf(SwipeAction("Favorite", "star", colors.yellow) {}),
                            rowBackground = colors.background,
                        ) {
                            ListRow(onClick = onOpenPerson, verticalPadding = 15.5.dp) {
                                PersonRow(
                                    person.name, person.initials, Modifier.weight(1f),
                                    subtitle = person.subtitle, isFavorite = person.isFavorite, tags = person.tags,
                                    lastInteraction = person.lastInteraction, nextDue = person.nextDue,
                                    nextIsOverdue = person.nextIsOverdue,
                                )
                            }
                        }
                    }
                }
                TabBarClearance()
            }
            SectionIndexBar(
                titles = sections.keys.toList(),
                onSelect = {},
                modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 80.dp),
            )
        }
    }
}

@Composable
fun FollowUpPreview(isPersonal: Boolean, onContextChange: (Boolean) -> Unit, onOpenPerson: () -> Unit) {
    val colors = KeeprTheme.colors
    val (leading, trailing) = followUpSwipes()
    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        KeeprTopBar(
            title = "Follow Up",
            trailing = { ToolbarButton("ellipsis.circle", "More", {}) },
            pinned = { ContextSwitcher(isPersonal, onContextChange) },
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            GroupedSection(header = "Overdue") {
                row(followUpTextStart) {
                    SwipeActionsRow(leading = leading, trailing = trailing) {
                        ListRow(onClick = onOpenPerson) {
                            FollowUpRow(
                                "Send the September training block", "Overdue · Yesterday",
                                personName = "Sarah Miller", isOverdue = true, isHighPriority = true,
                            )
                        }
                    }
                }
            }
            SectionSpacer()
            GroupedSection(header = "This Week") {
                row(followUpTextStart) {
                    SwipeActionsRow(leading = leading, trailing = trailing) {
                        ListRow(onClick = onOpenPerson) {
                            FollowUpRow("Text about starting training", "Saturday", personName = "Jake Martinez", isHighPriority = true)
                        }
                    }
                }
                row(followUpTextStart) {
                    SwipeActionsRow(leading = leading, trailing = trailing) {
                        ListRow(onClick = onOpenPerson) {
                            FollowUpRow("Pick a date for the workshop", "Tuesday", personName = "Michael Reed")
                        }
                    }
                }
            }
            TabBarClearance()
        }
    }
}

@Composable
fun SearchPreview() {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(KeeprTheme.colors.background)) {
        KeeprLargeTitleBar("Search") {
            SearchField(query, { query = it }, "Names, companies, anything you noted")
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "Search everyone",
                symbolName = "magnifyingglass",
                description = "Business and personal together — try a company, a place you met, or something you noted.",
                modifier = Modifier.padding(bottom = 60.dp),
            )
        }
    }
}

@Composable
fun ProfilePreview(onBack: () -> Unit) {
    val colors = KeeprTheme.colors
    val person = SampleContent.jake
    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        KeeprTopBar(
            title = person.name,
            background = colors.groupedBackground,
            leading = { ToolbarButton("chevron.left", "Back", onBack) },
            trailing = { ToolbarButton("ellipsis.circle", "More", {}) },
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // iOS's inset-grouped list puts the header this far below the bar.
            Spacer(Modifier.height(55.dp))
            PersonHeader(person.name, person.initials, subtitle = person.subtitle, tags = person.tags)
            Spacer(Modifier.height(Spacing.medium))
            QuickActionsRow(
                listOf(
                    QuickAction("Message", "message.fill") {},
                    QuickAction("Call", "phone.fill") {},
                    QuickAction("Email", "envelope.fill") {},
                    QuickAction("Log", "plus.circle.fill", isProminent = true) {},
                ),
            )
            Spacer(Modifier.height(Spacing.section))
            GroupedSection(header = "Next", headerTrailing = { AddButton() }) {
                row(followUpTextStart) {
                    ListRow { FollowUpRow("Text about starting training", "Saturday", isHighPriority = true) }
                }
            }
            SectionSpacer()
            GroupedSection(header = "What They Do") {
                row(followUpTextStart + 8.dp) {
                    ListRow {
                        SfSymbol("building.2", colors.accent, KeeprTheme.typography.title3)
                        KText("Owner · Martinez Roofing")
                    }
                }
                row {
                    ListRow { KText("Runs a roofing crew of nine. Knows every contractor in Delray.") }
                }
            }
            SectionSpacer()
            GroupedSection(header = "Groups", headerTrailing = { AddButton() }) {
                row { ListRow { PersonCompactRow("Life Time", "LT", Modifier.weight(1f), detail = "Delray") } }
            }
            TabBarClearance()
        }
    }
}

@Composable
private fun AddButton() {
    KeeprButton(
        "Add",
        onClick = {},
        textStyle = KeeprTheme.typography.subheadline.medium,
    )
}


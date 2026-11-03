package com.chrisallenclark.keepr.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.SearchEngine
import com.chrisallenclark.keepr.core.domain.SearchResult
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.trimmedSpaces
import com.chrisallenclark.keepr.ui.components.Avatar
import com.chrisallenclark.keepr.ui.components.AvatarSize
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprLargeTitleBar
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SearchField
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.TabBarClearance
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * Everyone, everything — ignoring the Business/Personal switch, because a
 * search should find the person whichever side they're on. Translated from
 * Keepr/Features/Search/SearchView.swift.
 */
@Composable
fun SearchScreen(onOpenPerson: (Person, ContextMode) -> Unit) {
    val state = LocalAppState.current
    observeStore().value
    var query by rememberSaveable { mutableStateOf("") }
    val sections = SearchEngine.grouped(SearchEngine.search(query, state.context.fetch<Person>()))

    Column(Modifier.fillMaxSize().background(if (sections.isEmpty()) KeeprTheme.colors.background else KeeprTheme.colors.groupedBackground)) {
        KeeprLargeTitleBar("Search", background = if (sections.isEmpty()) KeeprTheme.colors.background else KeeprTheme.colors.groupedBackground) {
            SearchField(query, { query = it }, "Names, companies, anything you noted")
        }
        when {
            query.trimmedSpaces().isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Search everyone",
                    symbolName = "magnifyingglass",
                    description = "Business and personal together — try a company, a place you met, or something you noted.",
                    modifier = Modifier.padding(bottom = 60.dp),
                )
            }
            sections.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // ContentUnavailableView.search(text:)
                EmptyState(
                    title = "No Results for “$query”",
                    symbolName = "magnifyingglass",
                    description = "Check the spelling or try a new search.",
                    modifier = Modifier.padding(bottom = 60.dp),
                )
            }
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                sections.forEachIndexed { index, section ->
                    if (index > 0) SectionSpacer()
                    GroupedSection(header = section.title) {
                        section.results.forEach { result ->
                            row(compactTextStart) {
                                ListRow(onClick = {
                                    val mode = if (result.person.context == RelationshipContext.PERSONAL) ContextMode.PERSONAL else ContextMode.BUSINESS
                                    onOpenPerson(result.person, mode)
                                }) {
                                    SearchResultRow(result, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                TabBarClearance()
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, modifier: Modifier) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Avatar(result.person.initials, size = AvatarSize.Small, imageData = result.person.photoData)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(result.person.displayName, style = typography.subheadline.medium)
            val snippet = result.snippet
            if (!snippet.isNullOrEmpty()) {
                IconLabel(snippet, result.field.symbolName, typography.caption, colors.secondaryLabel, maxLines = 2)
            }
        }
        SfSymbol("chevron.right", colors.tertiaryLabel, typography.caption.semibold)
    }
}

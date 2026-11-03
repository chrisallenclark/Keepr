package com.chrisallenclark.keepr.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionHeader
import com.chrisallenclark.keepr.ui.navigation.LocalNavigator
import com.chrisallenclark.keepr.ui.navigation.Route
import com.chrisallenclark.keepr.ui.navigation.SheetRoute
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * The profile's Notes section (`notesSection` in PersonProfileView.swift):
 * conversation write-ups as cards that open where they sit.
 *
 * Two on the profile, because the point of this section is "do I have notes
 * on them, and what was the last one" — not reading four meetings end to end.
 * The rest are behind one tap, in the same card language.
 */
@Composable
fun ProfileNotesSection(person: Person) {
    val navigator = LocalNavigator.current
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val notes = person.noteList
    val add = { navigator.present(SheetRoute.NoteComposer(person.id)) }

    if (notes.isEmpty()) {
        GroupedSection(header = "Notes") {
            row {
                ListRow(onClick = add) {
                    IconLabel("Paste a summary or meeting notes", "doc.on.clipboard", typography.subheadline, colors.accent)
                }
            }
        }
        return
    }

    Column {
        SectionHeader(
            "Notes",
            Modifier.padding(
                start = ListMetrics.sectionInset + ListMetrics.textInset,
                end = ListMetrics.sectionInset + ListMetrics.textInset,
                bottom = 3.dp,
            ),
            trailing = { KeeprButton("Add", add, textStyle = typography.caption.semibold) },
        )
        notes.take(2).forEach { note ->
            key(note.id) {
                NoteCard(note, Modifier.padding(horizontal = ListMetrics.sectionInset, vertical = 6.dp), showsActions = false)
            }
        }
        if (notes.size > 2) {
            Spacer(Modifier.height(6.dp))
            GroupedSection {
                row {
                    ListRow(onClick = { navigator.push(Route.Notes(person.id)) }) {
                        IconLabel("All ${notes.size} notes", "chevron.right.circle", typography.subheadline, colors.accent)
                    }
                }
            }
        }
    }
}

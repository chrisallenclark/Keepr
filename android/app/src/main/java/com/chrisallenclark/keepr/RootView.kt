package com.chrisallenclark.keepr

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chrisallenclark.keepr.gallery.GalleryScreen
import com.chrisallenclark.keepr.preview.FollowUpPreview
import com.chrisallenclark.keepr.preview.PeoplePreview
import com.chrisallenclark.keepr.preview.ProfilePreview
import com.chrisallenclark.keepr.preview.SearchPreview
import com.chrisallenclark.keepr.preview.TodayPreview
import com.chrisallenclark.keepr.ui.components.KeeprTabBar
import com.chrisallenclark.keepr.ui.components.TabItem

/** The four places the app can be. Settings lives behind a button on Today. */
private val tabs = listOf(
    TabItem("Today", "sun.max", "sun.max.fill"),
    TabItem("People", "person.2", "person.2.fill"),
    TabItem("Follow Up", "bell", "bell.fill"),
    TabItem("Search", "magnifyingglass"),
)

private fun tabIndex(screen: String?): Int = when (screen) {
    "people", "profile" -> 1
    "followUp" -> 2
    "search" -> 3
    else -> 0
}

/** What's pushed on top of the tabs. */
private enum class Pushed { None, Profile, Gallery }

/**
 * The tab root, as `RootView` is on iOS. A profile or the gallery slides in
 * from the right like a navigation push, and Android's back gesture pops it —
 * the system gesture stays in charge of "back" everywhere.
 */
@Composable
fun RootView(launch: LaunchOptions) {
    var selectedTab by rememberSaveable { mutableIntStateOf(tabIndex(launch.screen)) }
    var isPersonal by rememberSaveable { mutableStateOf(launch.context == "personal") }
    var pushed by rememberSaveable {
        mutableStateOf(if (launch.screen == "profile") Pushed.Profile else Pushed.None)
    }

    BackHandler(enabled = pushed != Pushed.None) { pushed = Pushed.None }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = pushed,
            transitionSpec = {
                val push = targetState != Pushed.None
                // UINavigationController: the new screen slides in from the
                // trailing edge while the old one drifts a third of the way.
                if (push) {
                    slideInHorizontally(tween(350)) { it } togetherWith
                        slideOutHorizontally(tween(350)) { -it / 3 } + fadeOut(tween(350), 0.9f)
                } else {
                    slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350), 0.9f) togetherWith
                        slideOutHorizontally(tween(350)) { it }
                }
            },
            label = "navigation",
        ) { screen ->
            when (screen) {
                Pushed.Profile -> ProfilePreview(onBack = { pushed = Pushed.None })
                Pushed.Gallery -> GalleryScreen(page = null, onBack = { pushed = Pushed.None })
                Pushed.None -> when (selectedTab) {
                    0 -> TodayPreview(
                        isPersonal, { isPersonal = it },
                        onOpenPerson = { pushed = Pushed.Profile },
                        onOpenSettings = { pushed = Pushed.Gallery },
                    )
                    1 -> PeoplePreview(isPersonal, { isPersonal = it }, onOpenPerson = { pushed = Pushed.Profile })
                    2 -> FollowUpPreview(isPersonal, { isPersonal = it }, onOpenPerson = { pushed = Pushed.Profile })
                    else -> SearchPreview()
                }
            }
        }
        if (pushed != Pushed.Gallery) {
            KeeprTabBar(
                items = tabs,
                selectedIndex = selectedTab,
                onSelect = {
                    selectedTab = it
                    pushed = Pushed.None
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

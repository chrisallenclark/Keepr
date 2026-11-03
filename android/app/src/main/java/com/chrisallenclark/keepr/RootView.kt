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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.gallery.GalleryScreen
import com.chrisallenclark.keepr.ui.components.KeeprTabBar
import com.chrisallenclark.keepr.ui.components.TabItem
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet
import com.chrisallenclark.keepr.ui.screens.EditPersonSheet
import com.chrisallenclark.keepr.ui.screens.FollowUpsScreen
import com.chrisallenclark.keepr.ui.navigation.LocalNavigator
import com.chrisallenclark.keepr.ui.navigation.Navigator
import com.chrisallenclark.keepr.ui.navigation.Route
import com.chrisallenclark.keepr.ui.navigation.RouteContent
import com.chrisallenclark.keepr.ui.navigation.SheetContent
import com.chrisallenclark.keepr.ui.navigation.SheetRoute
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.ModeBinding
import com.chrisallenclark.keepr.ui.screens.PeopleScreen
import com.chrisallenclark.keepr.ui.screens.SearchScreen
import com.chrisallenclark.keepr.ui.screens.TodayScreen
import com.chrisallenclark.keepr.ui.screens.observeStore

/** The four places the app can be. Settings lives behind a button on Today. */
private val tabs = listOf(
    TabItem("Today", "sun.max.fill"),
    TabItem("People", "person.2.fill"),
    TabItem("Follow Up", "bell.fill"),
    TabItem("Search", "magnifyingglass"),
)

private fun tabIndex(screen: String?): Int = when (screen) {
    "people", "profile" -> 1
    "followUp" -> 2
    "search" -> 3
    else -> 0
}


/**
 * The tab root, as `RootView` is on iOS: each tab keeps its own navigation
 * stack, a profile slides in like a push, and Android's back gesture pops it.
 */
@Composable
fun RootView(launch: LaunchOptions) {
    val state = LocalAppState.current
    val prefs = state.repository.preferences
    observeStore().value

    var selectedTab by rememberSaveable { mutableIntStateOf(tabIndex(launch.screen)) }
    var mode by remember {
        val launched = ContextMode.from(launch.context)
        launched?.let { prefs.setString(PreferenceKey.CONTEXT_MODE, it.rawValue) }
        mutableStateOf(launched ?: ContextMode.from(prefs.string(PreferenceKey.CONTEXT_MODE)) ?: ContextMode.BUSINESS)
    }
    val modeBinding = ModeBinding(mode) {
        mode = it
        prefs.setString(PreferenceKey.CONTEXT_MODE, it.rawValue)
    }
    var stacks by remember {
        val initial = MutableList(tabs.size) { emptyList<Route>() }
        // Screenshot runs open the first person on People, as the iOS ones do.
        if (launch.screen == "profile") {
            com.chrisallenclark.keepr.core.domain.PeopleEngine.sort(
                com.chrisallenclark.keepr.core.domain.PeopleEngine.filter(state.context.fetch<Person>(), mode),
                com.chrisallenclark.keepr.core.domain.PeopleSort.NAME,
            ).firstOrNull()?.let { initial[1] = listOf(Route.Profile(it.id, mode)) }
        }
        mutableStateOf(initial.toList())
    }
    var showsGallery by remember { mutableStateOf(false) }
    var addingPerson by remember { mutableStateOf(false) }
    var comingSoon by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<SheetRoute?>(null) }

    fun push(route: Route) {
        stacks = stacks.toMutableList().also { it[selectedTab] = it[selectedTab] + route }
    }
    fun push(person: Person, through: ContextMode = mode) = push(Route.Profile(person.id, through))
    fun pop() {
        stacks = stacks.toMutableList().also { it[selectedTab] = it[selectedTab].dropLast(1) }
    }
    val navigator = Navigator(mode, push = { push(it) }, pop = { pop() }, present = { sheet = it })

    val stack = stacks[selectedTab]
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    if (showsGallery) {
        BackHandler { showsGallery = false }
        GalleryScreen(page = null, onBack = { showsGallery = false })
        return
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedTab to stack,
            transitionSpec = {
                val sameTab = initialState.first == targetState.first
                val push = sameTab && targetState.second.size > initialState.second.size
                val pop = sameTab && targetState.second.size < initialState.second.size
                // UINavigationController: the new screen slides in from the
                // trailing edge while the old one drifts a third of the way.
                when {
                    push -> slideInHorizontally(tween(350)) { it } togetherWith
                        slideOutHorizontally(tween(350)) { -it / 3 } + fadeOut(tween(350), 0.9f)
                    pop -> slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350), 0.9f) togetherWith
                        slideOutHorizontally(tween(350)) { it }
                    else -> fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                }
            },
            label = "navigation",
        ) { (tab, routes) ->
            val top = routes.lastOrNull()
            if (top == null || !RouteContent(top, onBack = ::pop, onOpenPerson = { person, through -> push(person, through) }, onUnavailable = { comingSoon = it })) {
                when (tab) {
                    0 -> TodayScreen(modeBinding, onOpenPerson = { push(it) }, onOpenSettings = { sheet = SheetRoute.Settings }, onQuickCapture = { sheet = SheetRoute.QuickCapture })
                    1 -> PeopleScreen(modeBinding, onOpenPerson = { push(it) }, onAddPerson = { addingPerson = true }, onUnavailable = { comingSoon = it })
                    2 -> FollowUpsScreen(modeBinding, onOpenPerson = { push(it) })
                    else -> SearchScreen(onOpenPerson = { person, through -> push(person, through) })
                }
            }
        }
        KeeprTabBar(
            items = tabs,
            selectedIndex = selectedTab,
            onSelect = { index ->
                // Tapping the current tab returns it to its root, as on iOS.
                if (index == selectedTab) stacks = stacks.toMutableList().also { it[index] = emptyList() }
                selectedTab = index
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    sheet?.let { current ->
        SheetContent(
            current,
            mode = mode,
            onDismiss = { if (sheet == current) sheet = null },
            onOpenGallery = if (BuildConfig.DEBUG) ({ sheet = null; showsGallery = true }) else null,
        )
    }
    if (addingPerson) {
        EditPersonSheet(null, mode, onDismiss = { addingPerson = false }, onSaved = { push(it) })
    }
    comingSoon?.let { ComingSoonSheet(it, onDismiss = { comingSoon = null }) }
    }
}

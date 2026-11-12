package com.aulorvy.keepr

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.persistence.PreferenceKey
import com.aulorvy.keepr.gallery.GalleryScreen
import com.aulorvy.keepr.notifications.AndroidNotificationService
import com.aulorvy.keepr.notifications.rescheduleRhythmReminders
import com.aulorvy.keepr.ui.onboarding.OnboardingScreen
import com.aulorvy.keepr.ui.components.KeeprTabBar
import com.aulorvy.keepr.ui.components.TabItem
import com.aulorvy.keepr.ui.screens.ComingSoonSheet
import com.aulorvy.keepr.ui.screens.EditPersonSheet
import com.aulorvy.keepr.ui.screens.FollowUpsScreen
import com.aulorvy.keepr.ui.navigation.LocalNavigator
import com.aulorvy.keepr.ui.navigation.Navigator
import com.aulorvy.keepr.ui.navigation.Route
import com.aulorvy.keepr.ui.navigation.RouteContent
import com.aulorvy.keepr.ui.navigation.SheetContent
import com.aulorvy.keepr.ui.navigation.SheetRoute
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.screens.ModeBinding
import com.aulorvy.keepr.ui.screens.PeopleScreen
import com.aulorvy.keepr.ui.screens.SearchScreen
import com.aulorvy.keepr.ui.screens.TodayScreen
import com.aulorvy.keepr.ui.screens.observeStore

/** The four places the app can be. Settings lives behind a button on Today. */
private val tabs = listOf(
    TabItem("Today", "sun.max.fill"),
    TabItem("People", "person.2.fill"),
    TabItem("Follow Up", "bell.fill"),
    TabItem("Search", "magnifyingglass"),
)

private fun tabIndex(screen: String?): Int = when (screen) {
    "people", "profile", "groups", "types", "import", "memories", "notes", "files", "link" -> 1
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
        if (launch.screen in setOf("profile", "memories", "notes", "files", "link")) {
            com.aulorvy.keepr.core.domain.PeopleEngine.sort(
                com.aulorvy.keepr.core.domain.PeopleEngine.filter(state.context.fetch<Person>(), mode),
                com.aulorvy.keepr.core.domain.PeopleSort.NAME,
            ).firstOrNull()?.let { first ->
                initial[1] = listOfNotNull(
                    Route.Profile(first.id, mode),
                    when (launch.screen) {
                        "memories" -> Route.MemoryLibrary(first.id)
                        "notes" -> Route.Notes(first.id)
                        "files" -> Route.Files(first.id)
                        else -> null
                    },
                )
            }
        }
        mutableStateOf(initial.toList())
    }
    var showsGallery by remember { mutableStateOf(false) }
    var addingPerson by remember { mutableStateOf(false) }
    var comingSoon by remember { mutableStateOf<String?>(null) }
    var sheet by remember {
        // Screenshot runs can open straight onto a sheet.
        val first = if (launch.screen == "link") {
            com.aulorvy.keepr.core.domain.PeopleEngine.sort(
                com.aulorvy.keepr.core.domain.PeopleEngine.filter(state.context.fetch<Person>(), mode),
                com.aulorvy.keepr.core.domain.PeopleSort.NAME,
            ).firstOrNull()
        } else null
        mutableStateOf<SheetRoute?>(
            when (launch.screen) {
                "settings" -> SheetRoute.Settings
                "capture" -> SheetRoute.QuickCapture
                "groups" -> SheetRoute.Groups
                "types" -> SheetRoute.RelationshipTypes
                "review" -> SheetRoute.Review
                "import" -> SheetRoute.ContactImport
                "link" -> first?.let { SheetRoute.LinkEditor(it.id) }
                else -> null
            },
        )
    }
    // iOS: a full-screen cover on first launch, decided from stored state;
    // screenshot runs skip it.
    var showsOnboarding by remember { mutableStateOf(launch.screen == "onboarding" || (!launch.isDemo && !prefs.bool(PreferenceKey.HAS_ONBOARDED))) }

    fun push(route: Route) {
        stacks = stacks.toMutableList().also { it[selectedTab] = it[selectedTab] + route }
    }
    fun push(person: Person, through: ContextMode = mode) = push(Route.Profile(person.id, through))
    fun pop() {
        stacks = stacks.toMutableList().also { it[selectedTab] = it[selectedTab].dropLast(1) }
    }
    val navigator = Navigator(mode, push = { push(it) }, pop = { pop() }, present = { sheet = it }, setMode = modeBinding.set)

    val stack = stacks[selectedTab]
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    // Today's `.task`: rhythm reminders are rebuilt every time Today appears.
    val onTodayRoot = selectedTab == 0 && stack.isEmpty()
    LaunchedEffect(onTodayRoot) {
        if (onTodayRoot && !launch.isDemo) state.rescheduleRhythmReminders()
    }

    // A tapped reminder opens whoever it's about: a follow-up's person on the
    // Follow Up tab, a rhythm nudge on Today. (iOS just opens the app.)
    val openedReminder by AndroidNotificationService.openedReminder.collectAsState()
    LaunchedEffect(openedReminder) {
        val identifier = openedReminder ?: return@LaunchedEffect
        AndroidNotificationService.openedReminder.value = null
        if (showsOnboarding) return@LaunchedEffect
        val person = state.context.fetch<com.aulorvy.keepr.core.model.FollowUp>()
            .firstOrNull { !it.isDeleted && com.aulorvy.keepr.core.services.FollowUpScheduler.identifier(it) == identifier }
            ?.person
            ?.takeIf { !it.isDeleted }
        sheet = null
        if (person != null) {
            val visible = com.aulorvy.keepr.core.domain.PeopleEngine.visible(listOf(person), mode).isNotEmpty()
            selectedTab = 2
            stacks = stacks.toMutableList().also { it[2] = listOf(Route.Profile(person.id, if (visible) mode else mode.other)) }
        } else {
            selectedTab = 0
            stacks = stacks.toMutableList().also { it[0] = emptyList() }
        }
    }

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
        if (showsOnboarding) {
            OnboardingScreen(
                modeBinding,
                onFinish = {
                    prefs.setBool(PreferenceKey.HAS_ONBOARDED, true)
                    showsOnboarding = false
                },
            )
        }
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

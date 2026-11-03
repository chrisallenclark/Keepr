package com.chrisallenclark.keepr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.chrisallenclark.keepr.gallery.GalleryScreen
import com.chrisallenclark.keepr.notifications.AndroidNotificationService
import com.chrisallenclark.keepr.notifications.rescheduleAllReminders
import com.chrisallenclark.keepr.ui.screens.AppState
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private lateinit var launchOptions: LaunchOptions
    private val appState: AppState by lazy {
        AppState(container.repository, container.notificationService, container.repository.scope, contacts = container.contactStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launch = LaunchOptions.from(intent)
        launchOptions = launch
        container = AppContainer.get(this, isDemo = launch.isDemo)
        (container.notificationService as? AndroidNotificationService)?.let { reminders ->
            // The permission prompt needs an activity; registering must happen in onCreate.
            reminders.attach(this)
            // A force-stop forgets every alarm, and nothing else re-arms them
            // until the app is opened — so opening it does.
            Thread { reminders.rearmAll() }.start()
        }
        handleReminderTap(intent)

        setContent {
            KeeprTheme {
                if (launch.screen?.startsWith("gallery-") == true) {
                    GalleryScreen(page = launch.screen.removePrefix("gallery-"), onBack = { finish() })
                    return@KeeprTheme
                }
                val loaded by container.repository.isLoaded.collectAsState()
                LaunchedEffect(Unit) {
                    container.load()
                    // iOS rebuilds rhythm reminders whenever Today appears; launch is the first time.
                    if (!launch.isDemo) appState.rescheduleAllReminders()
                }
                if (!loaded) {
                    // The window's own background shows until the store is read.
                    Box(Modifier.fillMaxSize().background(KeeprTheme.colors.groupedBackground))
                    return@KeeprTheme
                }
                CompositionLocalProvider(LocalAppState provides appState) {
                    RootView(launch)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back from the background: due dates may have moved past a day boundary.
        if (!launchOptions.isDemo && container.repository.isLoaded.value) appState.rescheduleAllReminders()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminderTap(intent)
    }

    /** A tapped reminder opens whoever it's about (see RootView). */
    private fun handleReminderTap(intent: Intent?) {
        AndroidNotificationService.reminderIdentifier(intent)?.let {
            AndroidNotificationService.openedReminder.value = it
        }
    }

    override fun onStop() {
        super.onStop()
        // Make sure the last save is on disk before the app can be killed.
        container.repository.scope.launch { container.repository.flush() }
    }
}

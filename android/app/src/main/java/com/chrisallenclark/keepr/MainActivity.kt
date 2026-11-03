package com.chrisallenclark.keepr

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.chrisallenclark.keepr.gallery.GalleryScreen
import com.chrisallenclark.keepr.ui.screens.AppState
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launch = LaunchOptions.from(intent)
        container = AppContainer.get(this, isDemo = launch.isDemo)

        setContent {
            KeeprTheme {
                if (launch.screen?.startsWith("gallery-") == true) {
                    GalleryScreen(page = launch.screen.removePrefix("gallery-"), onBack = { finish() })
                    return@KeeprTheme
                }
                val loaded by container.repository.isLoaded.collectAsState()
                LaunchedEffect(Unit) { container.load() }
                val state = remember { AppState(container.repository, container.notificationService, container.repository.scope) }
                if (!loaded) {
                    // The window's own background shows until the store is read.
                    Box(Modifier.fillMaxSize().background(KeeprTheme.colors.groupedBackground))
                    return@KeeprTheme
                }
                CompositionLocalProvider(LocalAppState provides state) {
                    RootView(launch)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Make sure the last save is on disk before the app can be killed.
        container.repository.scope.launch { container.repository.flush() }
    }
}

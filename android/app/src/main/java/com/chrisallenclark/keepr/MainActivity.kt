package com.chrisallenclark.keepr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chrisallenclark.keepr.gallery.GalleryScreen
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launch = LaunchOptions.from(intent)
        setContent {
            KeeprTheme {
                val page = launch.screen?.removePrefix("gallery-")
                if (launch.screen?.startsWith("gallery-") == true) {
                    GalleryScreen(page = page, onBack = { finish() })
                } else {
                    RootView(launch)
                }
            }
        }
    }
}

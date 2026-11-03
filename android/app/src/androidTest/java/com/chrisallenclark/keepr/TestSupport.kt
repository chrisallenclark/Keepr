package com.chrisallenclark.keepr

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.chrisallenclark.keepr.data.KeeprDatabase

/** Starts from nothing: no database, no preferences, no in-memory container. */
fun resetApp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AppContainer.resetForTesting()
    context.deleteDatabase(KeeprDatabase.FILE_NAME)
    context.getSharedPreferences("keepr", Context.MODE_PRIVATE).edit().clear().commit()
}

/** Launches the app the way a person does (no screenshot extras, so the real store). */
fun launchApp(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = 10_000) {
    waitUntil(timeoutMillis) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.first(text: String): SemanticsNodeInteraction = onAllNodesWithText(text, substring = false).onFirst()

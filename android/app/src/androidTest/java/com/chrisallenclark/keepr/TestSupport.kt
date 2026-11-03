package com.chrisallenclark.keepr

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
    try {
        waitUntil(timeoutMillis) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("Timed out waiting for \"$text\". On screen:\n${screenText()}", timeout)
    }
}

fun ComposeTestRule.waitForGone(text: String, timeoutMillis: Long = 10_000) {
    try {
        waitUntil(timeoutMillis) { onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }
    } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("\"$text\" never went away. On screen:\n${screenText()}", timeout)
    }
}

/** Every piece of text in every window, for failure messages. */
fun ComposeTestRule.screenText(): String =
    onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().joinToString("\n---\n") { root ->
        fun collect(node: androidx.compose.ui.semantics.SemanticsNode): List<String> {
            val texts: List<String> = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val labels: List<String> = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().map { "[$it]" }
            return texts + labels + node.children.flatMap { collect(it) }
        }
        collect(root).joinToString(" | ")
    }

/** Waits until every save so far is on disk, as a real app restart effectively does. */
fun flushSaves() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    kotlinx.coroutines.runBlocking { AppContainer.get(context, isDemo = false).repository.flush() }
}

fun ComposeTestRule.first(text: String): SemanticsNodeInteraction = onAllNodesWithText(text, substring = false).onFirst()

/**
 * Taps a tab once the (re)launched window can take input. Right after an
 * activity starts, a tap can arrive before its window has focus and fail to
 * inject, so wait for the text and retry briefly.
 */
fun ComposeTestRule.tapTab(text: String, timeoutMillis: Long = 10_000) {
    waitForText(text, timeoutMillis)
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        waitForIdle()
        try {
            first(text).performClick()
            return
        } catch (error: AssertionError) {
            if (System.currentTimeMillis() > deadline) throw error
            Thread.sleep(250)
        }
    }
}

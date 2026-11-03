package com.chrisallenclark.keepr

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Proof that data survives the app being killed, not just closed. CI runs
 * the two halves as separate instrumentation runs with
 * `am force-stop` between them (see scripts/emulator-smoke.sh); in an ordinary
 * test run both are skipped.
 */
@RunWith(AndroidJUnit4::class)
class ProcessRestartTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val phase: String? = InstrumentationRegistry.getArguments().getString("keeprPhase")

    @Test
    fun writeBeforeKill() {
        assumeTrue(phase == "write")
        resetApp()
        val container = AppContainer.get(InstrumentationRegistry.getInstrumentation().targetContext, isDemo = false)
        runBlocking {
            withContext(Dispatchers.Main) {
                container.load()
                val person = Person("Priya", "Raman", context = RelationshipContext.BUSINESS, company = "Bright Studio")
                container.repository.context.insert(person)
                container.repository.context.insert(FollowUp("Book the headshots", dueDate = Instant.now().minusSeconds(86_400 * 2), person = person))
                container.repository.context.save()
            }
            container.repository.flush()
        }
    }

    @Test
    fun readAfterKill() {
        assumeTrue(phase == "read")
        launchApp()
        // The overdue follow-up written before the process died is on Today…
        rule.waitForText("Book the headshots")
        // …and the person is on People.
        rule.first("People").performClick()
        rule.waitForText("Priya Raman")
    }
}

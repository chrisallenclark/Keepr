package com.chrisallenclark.keepr.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.model.ModelContext
import com.chrisallenclark.keepr.core.services.NotificationScheduling
import com.chrisallenclark.keepr.data.KeeprRepository
import kotlinx.coroutines.CoroutineScope
import java.time.Instant

/**
 * What every screen reaches for, provided once at the root — the Android
 * counterpart of the iOS environment (`modelContext`, `notificationService`)
 * plus the app-wide Business/Personal lens.
 */
class AppState(
    val repository: KeeprRepository,
    val notifications: NotificationScheduling,
    /** Where fire-and-forget work (reminder scheduling) runs, outliving any one screen. */
    val scope: CoroutineScope,
    val calendar: KeeprCalendar = KeeprCalendar.current(),
    /** Fixed in screenshot runs so relative dates read the same every capture. */
    val clock: () -> Instant = Instant::now,
) {
    val context: ModelContext get() = repository.context

    val remindersEnabled: Boolean
        get() = repository.preferences.string(com.chrisallenclark.keepr.core.persistence.PreferenceKey.REMINDERS_ENABLED) != "false"

    fun now(): Instant = clock()

    /** `try? context.save()` */
    fun save() = context.save()
}

val LocalAppState = staticCompositionLocalOf<AppState> { error("No AppState provided") }

/**
 * Reading data in a composable: observing the version makes the caller
 * recompose after every save, as `@Query` does on iOS.
 */
@Composable
fun observeStore(): State<Long> = LocalAppState.current.repository.version.collectAsState()

/** The Business/Personal lens, shared by Today, People and Follow Up. */
data class ModeBinding(val mode: ContextMode, val set: (ContextMode) -> Unit)

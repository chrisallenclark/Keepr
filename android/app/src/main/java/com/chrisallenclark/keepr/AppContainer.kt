package com.chrisallenclark.keepr

import android.content.Context
import com.chrisallenclark.keepr.core.persistence.SampleData
import com.chrisallenclark.keepr.core.services.NotificationScheduling
import com.chrisallenclark.keepr.core.services.StubNotificationService
import com.chrisallenclark.keepr.data.AndroidPreferences
import com.chrisallenclark.keepr.data.KeeprDatabase
import com.chrisallenclark.keepr.data.KeeprRepository
import com.chrisallenclark.keepr.data.MemoryPreferences

/**
 * The app's services, built once per process — `KeeprApp` + `LiveServices` on
 * iOS. Demo (screenshot) launches get a separate in-memory database filled
 * with sample data, so they never read or write the user's own.
 */
class AppContainer private constructor(
    val repository: KeeprRepository,
    val notificationService: NotificationScheduling,
    val isDemo: Boolean,
) {
    companion object {
        @Volatile private var live: AppContainer? = null
        @Volatile private var demo: AppContainer? = null

        fun get(context: Context, isDemo: Boolean): AppContainer {
            val app = context.applicationContext
            return if (isDemo) {
                demo ?: synchronized(this) {
                    demo ?: AppContainer(
                        KeeprRepository(KeeprDatabase.inMemory(app), MemoryPreferences()),
                        StubNotificationService(),
                        isDemo = true,
                    ).also { demo = it }
                }
            } else {
                live ?: synchronized(this) {
                    live ?: AppContainer(
                        KeeprRepository(KeeprDatabase.open(app), AndroidPreferences(app)),
                        // Reminders arrive with the notifications phase; until then
                        // scheduling is accepted and dropped, as in iOS previews.
                        StubNotificationService(),
                        isDemo = false,
                    ).also { live = it }
                }
            }
        }

        /** Tests only: forget the process-wide instance, as if the app were relaunched. */
        fun resetForTesting() {
            live = null
            demo = null
        }
    }

    /** Loads the store; demo stores also get the sample relationships. */
    suspend fun load() {
        if (repository.isLoaded.value) return
        repository.load()
        if (isDemo) SampleData.load(repository.context, repository.preferences)
    }
}

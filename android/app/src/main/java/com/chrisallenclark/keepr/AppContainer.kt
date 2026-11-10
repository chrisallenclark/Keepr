package com.chrisallenclark.keepr

import android.content.Context
import com.chrisallenclark.keepr.core.persistence.SampleData
import com.chrisallenclark.keepr.contacts.AndroidContactStore
import com.chrisallenclark.keepr.core.services.ContactStoreProviding
import com.chrisallenclark.keepr.core.services.NotificationScheduling
import com.chrisallenclark.keepr.core.services.StubContactStore
import com.chrisallenclark.keepr.core.services.StubNotificationService
import com.chrisallenclark.keepr.data.AndroidPreferences
import com.chrisallenclark.keepr.data.KeeprDatabase
import com.chrisallenclark.keepr.data.KeeprRepository
import com.chrisallenclark.keepr.data.MemoryPreferences
import com.chrisallenclark.keepr.notifications.AndroidNotificationService

/**
 * The app's services, built once per process — `KeeprApp` + `LiveServices` on
 * iOS. Demo (screenshot) launches get a separate in-memory database filled
 * with sample data, so they never read or write the user's own.
 */
class AppContainer private constructor(
    val repository: KeeprRepository,
    val notificationService: NotificationScheduling,
    val isDemo: Boolean,
    /** The phone's contacts: ContactsContract when live, a fixed stub in demo runs. */
    val contactStore: ContactStoreProviding,
) {
    companion object {
        @Volatile private var live: AppContainer? = null
        @Volatile private var demo: AppContainer? = null

        fun get(context: Context, isDemo: Boolean): AppContainer {
            val app = context.applicationContext
            return if (isDemo) {
                demo ?: synchronized(this) {
                    demo ?: AppContainer(
                        KeeprRepository(KeeprDatabase.inMemory(app), demoPreferences()),
                        StubNotificationService(),
                        isDemo = true,
                        contactStore = StubContactStore(),
                    ).also { demo = it }
                }
            } else {
                live ?: synchronized(this) {
                    val preferences = AndroidPreferences(app)
                    // Backup: a restore a killed process left staged is removed (once per process).
                    com.chrisallenclark.keepr.backup.BackupService.clearLeftoverStaging(app.cacheDir)
                    live ?: AppContainer(
                        KeeprRepository(KeeprDatabase.open(app), preferences),
                        // Real reminders over AlarmManager. Demo runs keep the
                        // stub, so screenshots never schedule anything.
                        AndroidNotificationService(app),
                        isDemo = false,
                        contactStore = AndroidContactStore(app, preferences),
                    ).also { live = it }
                }
            }
        }

        /**
         * The iOS screenshot store is in memory, but the record of which
         * built-in types were offered lives in the simulator's real
         * UserDefaults, so its seeding is skipped and the sample data creates
         * its types with the plain "tag" symbol. Demo runs mirror that, so the
         * captured chips match the iOS references. Real stores are unaffected.
         */
        private fun demoPreferences() = MemoryPreferences().apply {
            setStringList(
                com.chrisallenclark.keepr.core.persistence.KeeprStore.SEEDED_KEYS_DEFAULT,
                com.chrisallenclark.keepr.core.model.RelationshipTag.builtInCatalog.map { it.name },
            )
        }

        /** Tests only: forget the process-wide instance, as if the app were relaunched. */
        fun resetForTesting() {
            // Close before forgetting: a test deletes the file next, and an
            // open connection to a deleted file breaks the next open on older
            // Android versions.
            val closing = listOfNotNull(live, demo)
            live = null
            demo = null
            kotlinx.coroutines.runBlocking { closing.forEach { it.repository.close() } }
        }
    }

    /** Loads the store; demo stores also get the sample relationships. */
    suspend fun load() {
        if (repository.isLoaded.value) return
        repository.load()
        if (isDemo) SampleData.load(repository.context, repository.preferences)
    }
}

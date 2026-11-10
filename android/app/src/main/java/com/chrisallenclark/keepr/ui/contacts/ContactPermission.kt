package com.chrisallenclark.keepr.ui.contacts

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.chrisallenclark.keepr.contacts.AndroidContactStore
import com.chrisallenclark.keepr.core.services.ContactStoreProviding
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/**
 * Lets [store] show the system contacts prompt while this is composed. The
 * prompt needs an Activity result launcher, which only a composable can own;
 * the store's `requestAccess()` suspends until the person answers. Stores that
 * don't need a prompt (the demo's stub) are left alone.
 */
@Composable
fun ContactPermissionHost(store: ContactStoreProviding) {
    val androidStore = store as? AndroidContactStore
    val pending = remember { AtomicReference<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending.getAndSet(null)?.complete(granted)
    }
    DisposableEffect(androidStore, launcher) {
        if (androidStore == null) return@DisposableEffect onDispose { }
        val requester: suspend () -> Boolean = {
            val deferred = CompletableDeferred<Boolean>()
            pending.getAndSet(deferred)?.complete(false)
            launcher.launch(Manifest.permission.READ_CONTACTS)
            deferred.await()
        }
        androidStore.permissionRequester = requester
        onDispose {
            if (androidStore.permissionRequester === requester) androidStore.permissionRequester = null
            pending.getAndSet(null)?.complete(false)
        }
    }
}

/**
 * Runs [action] each time the app comes back to the foreground — after the
 * person visited system Settings, say, and turned contacts on. iOS reads the
 * authorization status fresh whenever a view appears; on Android the activity
 * resumes underneath an open sheet, so the sheet has to listen for it.
 *
 * The resume that happens as this starts observing is skipped: only a return
 * after the app was paused counts.
 */
@Composable
fun OnAppResume(action: () -> Unit) {
    val context = LocalContext.current
    val latest = rememberUpdatedState(action)
    DisposableEffect(context) {
        val owner = context.findLifecycleOwner() ?: return@DisposableEffect onDispose { }
        var wasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                wasPaused = true
            } else if (event == Lifecycle.Event.ON_RESUME && wasPaused) {
                wasPaused = false
                latest.value()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

/** `UIApplication.openSettingsURLString`: this app's page in system Settings. */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Bumped whenever contacts are acknowledged or Catch Up closes, so Today's
 * Catch Up row re-counts — the job iOS's `onDismiss: refreshNewContactCount` does.
 */
object ContactReviewSignal {
    val generation = mutableIntStateOf(0)

    fun changed() {
        generation.intValue += 1
    }
}

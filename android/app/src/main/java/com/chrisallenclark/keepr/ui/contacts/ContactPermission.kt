package com.chrisallenclark.keepr.ui.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
    val android = store as? AndroidContactStore
    val pending = remember { AtomicReference<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending.getAndSet(null)?.complete(granted)
    }
    DisposableEffect(android, launcher) {
        if (android == null) return@DisposableEffect onDispose { }
        val requester: suspend () -> Boolean = {
            val deferred = CompletableDeferred<Boolean>()
            pending.getAndSet(deferred)?.complete(false)
            launcher.launch(Manifest.permission.READ_CONTACTS)
            deferred.await()
        }
        android.permissionRequester = requester
        onDispose {
            if (android.permissionRequester === requester) android.permissionRequester = null
            pending.getAndSet(null)?.complete(false)
        }
    }
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

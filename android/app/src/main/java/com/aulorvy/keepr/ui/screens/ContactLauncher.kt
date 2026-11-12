package com.aulorvy.keepr.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.services.CommunicationLauncher
import com.aulorvy.keepr.core.services.ContactMethod

/**
 * Opens Messages, the phone dialer or the mail app for a person — `openURL`
 * with the `sms:` / `tel:` / `mailto:` links CommunicationLauncher builds.
 * The dialer opens with the number filled in rather than placing the call,
 * so no phone permission is needed.
 */
fun Context.open(method: ContactMethod, person: Person): Boolean {
    val uri = CommunicationLauncher.url(method, person) ?: return false
    val action = when (method) {
        ContactMethod.CALL -> Intent.ACTION_DIAL
        ContactMethod.EMAIL -> Intent.ACTION_SENDTO
        ContactMethod.MESSAGE -> Intent.ACTION_SENDTO
    }
    return try {
        startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

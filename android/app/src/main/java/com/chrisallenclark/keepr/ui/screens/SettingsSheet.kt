package com.chrisallenclark.keepr.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chrisallenclark.keepr.BuildConfig
import com.chrisallenclark.keepr.core.domain.GroupVocabulary
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.core.persistence.SampleData
import com.chrisallenclark.keepr.core.services.ContactChangeTracker
import com.chrisallenclark.keepr.core.services.NotificationAuthorizationStatus
import com.chrisallenclark.keepr.notifications.rescheduleAllReminders
import com.chrisallenclark.keepr.ui.components.FormMenuPicker
import com.chrisallenclark.keepr.ui.components.FormToggle
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.LabeledRow
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.organize.GroupsSheet
import com.chrisallenclark.keepr.ui.organize.RelationshipTypesSheet
import com.chrisallenclark.keepr.ui.theme.Chrome
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.bold
import com.chrisallenclark.keepr.ui.theme.semibold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The pages pushed inside the Settings sheet, as its `NavigationStack` does on iOS. */
private enum class SettingsPage { MAIN, LIMITATIONS }

/** Which organizing editor is open over Settings. */
private enum class OrganizingEditor { RELATIONSHIP_TYPES, GROUPS }

/**
 * Preferences, privacy, and the honest version of what this app does and
 * doesn't do. Translated from Keepr/Features/Settings/SettingsView.swift.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onOpenGallery: (() -> Unit)?) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    KeeprSheet(onDismiss = onDismiss) {
        // The sheet is its own window, so back has to be handled inside it
        // (outside, the sheet would close instead). Composed only while there
        // is a step to go back to, so its callback is registered after the
        // sheet's own dismiss callback and always takes priority.
        if (page != SettingsPage.MAIN) BackHandler { page = SettingsPage.MAIN }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState != SettingsPage.MAIN) {
                    slideInHorizontally(tween(350)) { it } togetherWith
                        slideOutHorizontally(tween(350)) { -it / 3 } + fadeOut(tween(350), 0.9f)
                } else {
                    slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350), 0.9f) togetherWith
                        slideOutHorizontally(tween(350)) { it }
                }
            },
            label = "settingsPage",
            modifier = Modifier.fillMaxHeight(),
        ) { current ->
            Column(Modifier.fillMaxHeight()) {
                when (current) {
                    SettingsPage.MAIN -> SettingsMain(onDismiss, onOpenGallery, onOpenLimitations = { page = SettingsPage.LIMITATIONS })
                    SettingsPage.LIMITATIONS -> LimitationsPage(onBack = { page = SettingsPage.MAIN })
                }
            }
        }
    }
}

@Composable
private fun SettingsMain(onDismiss: () -> Unit, onOpenGallery: (() -> Unit)?, onOpenLimitations: () -> Unit) {
    val state = LocalAppState.current
    val prefs = state.repository.preferences
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val colors = KeeprTheme.colors
    observeStore().value

    var remindersEnabled by remember { mutableStateOf(state.remindersEnabled) }
    var notificationStatus by remember { mutableStateOf(NotificationAuthorizationStatus.NOT_DETERMINED) }
    var contactAccess by remember { mutableStateOf(contactAccessLabel(state.contacts.authorizationStatus())) }
    var groupLabel by remember { mutableStateOf(prefs.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var organizing by remember { mutableStateOf<OrganizingEditor?>(null) }

    // iOS refreshes on appear; Android also catches the user coming back from
    // system Settings with a permission changed, so check while open.
    LaunchedEffect(Unit) {
        while (true) {
            notificationStatus = state.notifications.authorizationStatus()
            contactAccess = contactAccessLabel(state.contacts.authorizationStatus())
            delay(1_000)
        }
    }

    val people = state.context.fetch<Person>().count { !it.isDeleted }
    val sampleLoaded = SampleData.isLoaded(state.context)

    /**
     * Asks for notification permission the first time reminders are switched
     * on — never at launch, and never for a feature the user hasn't chosen.
     */
    fun setReminders(enabled: Boolean) {
        remindersEnabled = enabled
        prefs.setString(PreferenceKey.REMINDERS_ENABLED, enabled.toString())
        state.scope.launch {
            if (!enabled) {
                state.notifications.cancelAll()
                return@launch
            }
            if (!prefs.bool(PreferenceKey.HAS_ASKED_FOR_NOTIFICATIONS) &&
                notificationStatus == NotificationAuthorizationStatus.NOT_DETERMINED
            ) {
                prefs.setBool(PreferenceKey.HAS_ASKED_FOR_NOTIFICATIONS, true)
                state.notifications.requestAuthorization()
            }
            notificationStatus = state.notifications.authorizationStatus()
            // Android addition: turning reminders back on brings back the ones
            // switching them off cancelled, rather than waiting for each edit.
            state.rescheduleAllReminders()
        }
    }

    SheetHeader("Settings", cancelTitle = "Done", onCancel = onDismiss)
    SheetBody(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))

        GroupedSection(header = "Reminders") {
            row { FormToggle("Follow-up reminders", remindersEnabled, ::setReminders) }
            if (remindersEnabled && notificationStatus == NotificationAuthorizationStatus.DENIED) {
                row {
                    SettingsLink("Notifications are off in Android Settings", "exclamationmark.triangle") {
                        openSystemSettings(context, notificationSettingsIntent(context))
                    }
                }
            }
        }
        SectionSpacer()

        // Settings is where people look for "how do I change the categories" —
        // before they think to look inside a filter menu on another tab.
        GroupedSection(
            header = "Organizing",
            footer = "Add your own, rename them, choose a symbol, or delete the ones you'll never use — including the ones Keepr started you with.",
        ) {
            row(separatorStart = iconTextStart) {
                NavigationRow("Relationship Types", "tag") { organizing = OrganizingEditor.RELATIONSHIP_TYPES }
            }
            row(separatorStart = iconTextStart) {
                NavigationRow(GroupVocabulary.plural(groupLabel), "mappin.and.ellipse") { organizing = OrganizingEditor.GROUPS }
            }
        }
        SectionSpacer()

        // Everyone organizes people by *something* — which of my businesses,
        // which gym, which dating app. The app asks once for their word.
        GroupedSection(
            header = "Wording",
            footer = "The second way Keepr sorts people, after relationship type. Life Time and your training company are " +
                "${GroupVocabulary.plural(groupLabel).lowercase()}; so are Hinge and Bumble, or the bar you met someone in. " +
                "Someone can be in more than one.",
        ) {
            row {
                val selection = GroupVocabulary.presets.firstOrNull { it.singular == groupLabel } ?: GroupVocabulary.default
                FormMenuPicker(
                    title = "Call them",
                    options = GroupVocabulary.presets,
                    selection = selection,
                    label = { it.plural },
                    onSelect = { option ->
                        groupLabel = option.singular
                        prefs.setString(PreferenceKey.GROUP_LABEL, option.singular)
                        // Screens read the word when they recompose; a save
                        // (with nothing to write) tells them to.
                        state.save()
                        haptics.selection()
                    },
                )
            }
        }
        SectionSpacer()

        GroupedSection(
            header = "Permissions",
            footer = "Keepr reads your contacts only to link them to relationships. Nothing is uploaded, and Keepr never writes to your contacts.",
        ) {
            row { LabeledRow("Contacts", contactAccess) }
            if (contactAccess != FULL_ACCESS) {
                row {
                    SettingsLink("Manage in Settings", "gearshape") {
                        openSystemSettings(context, appSettingsIntent(context))
                    }
                }
            }
        }
        SectionSpacer()

        GroupedSection(
            header = "Data",
            footer = "Everything you record stays on this phone. There's no account, no server, and no analytics.",
        ) {
            row { LabeledRow("People", "$people") }
            row { LabeledRow("Storage", "This phone") }
        }
        SectionSpacer()

        // Debug builds only. iOS always shows this section; the Android
        // release builds testers install leave it out, by request (see
        // docs/RELEASE.md). The Design Gallery entry is debug-only as well.
        if (BuildConfig.DEBUG) {
            GroupedSection(
                header = "Development",
                footer = "Loads a set of example relationships for evaluating the app. Turning it off removes only those, never anything you added.",
            ) {
                row {
                    FormToggle("Sample data", sampleLoaded, { on ->
                        if (on) SampleData.load(state.context, state.repository.preferences, state.now(), state.calendar)
                        else SampleData.remove(state.context)
                        haptics.light()
                    })
                }
                if (onOpenGallery != null) {
                    row { ListRow(onClick = onOpenGallery) { KText("Design Gallery", color = colors.accent) } }
                }
            }
            SectionSpacer()
        }

        GroupedSection(footer = "Removes every relationship, memory, interaction and follow-up from this phone. Your contacts are not affected.") {
            row { ListRow(onClick = { confirmingDelete = true }) { KText("Delete All Data", color = colors.red) } }
        }
        SectionSpacer()

        GroupedSection(header = "About") {
            row { LabeledRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
            row {
                ListRow(onClick = onOpenLimitations) {
                    KText("What Keepr can't do", Modifier.weight(1f))
                    SfSymbol("chevron.right", colors.tertiaryLabel, KeeprTheme.typography.footnote.semibold)
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete all Keepr data?",
            message = "This can't be undone.",
            confirmTitle = "Delete Everything",
            onConfirm = {
                state.scope.launch { state.notifications.cancelAll() }
                KeeprStore.deleteAllData(state.context, state.repository.preferences)
                // The seen-contacts list is app data too. Leaving it behind would
                // mean a wiped app treats every existing contact as handled.
                ContactChangeTracker(state.repository.preferences).reset()
                haptics.success()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    when (organizing) {
        OrganizingEditor.RELATIONSHIP_TYPES -> RelationshipTypesSheet(onDismiss = { organizing = null })
        OrganizingEditor.GROUPS -> GroupsSheet(onDismiss = { organizing = null })
        null -> Unit
    }
}

/** Said plainly, in the app, rather than discovered as a disappointment. */
@Composable
private fun LimitationsPage(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(Chrome.navigationBarHeight + Spacing.small)
            .padding(horizontal = 16.dp),
    ) {
        ToolbarButton("chevron.left", "Back", onBack, Modifier.align(Alignment.CenterStart))
        KText(
            "What Keepr can't do",
            style = KeeprTheme.typography.headline,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Chrome.toolbarButton + 8.dp),
        )
    }
    SheetBody(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        limitations.forEachIndexed { index, (header, text) ->
            if (index > 0) SectionSpacer()
            GroupedSection(header = header) {
                row { ListRow { KText(text) } }
            }
        }
    }
}

/** iOS's text, with the platform specifics made true for Android. */
private val limitations = listOf(
    "Messages" to "Keepr can't read your texts, email or call history. Google Play only allows that for apps that replace your messaging or phone app, and Keepr isn't one.",
    "What's in the timeline" to "Your interaction timeline is what you record — by hand or through Quick Capture. That's a feature: it's the interactions that mattered, not every notification you ever got.",
    "Suggestions" to "Quick Capture reads your note on this phone using plain text rules. Nothing is sent anywhere, and every suggestion it makes waits for you to approve it.",
    "Reminders" to "Android may hold a reminder back a few minutes to save battery, and some phones stop apps waking up at all unless Keepr is allowed to run in the background. If you force-stop Keepr, its reminders wait until you open it again.",
    "Sync" to "Keepr doesn't sync between devices yet. Everything lives on this phone and is left out of Android backups, so it won't move to a new phone on its own.",
)

/** Where a row's text starts after a leading symbol, for its separator. */
private val iconTextStart = ListMetrics.rowHorizontal + 24.dp + Spacing.medium

/** A `NavigationLink` with a `Label`: accent symbol, title, chevron. */
@Composable
private fun NavigationRow(title: String, symbol: String, onClick: () -> Unit) {
    val colors = KeeprTheme.colors
    ListRow(onClick = onClick) {
        SfSymbol(symbol, colors.accent, KeeprTheme.typography.body)
        KText(title, Modifier.weight(1f))
        SfSymbol("chevron.right", colors.tertiaryLabel, KeeprTheme.typography.footnote.semibold)
    }
}

/** iOS `Link` to the Settings app: a tinted label in subheadline. */
@Composable
private fun SettingsLink(title: String, symbol: String, onClick: () -> Unit) {
    ListRow(onClick = onClick) {
        IconLabel(title, symbol, style = KeeprTheme.typography.subheadline, color = KeeprTheme.colors.accent)
    }
}

private const val FULL_ACCESS = "Full access"

/** iOS's contact-access wording, from the contact store's own status. */
private fun contactAccessLabel(access: com.chrisallenclark.keepr.core.services.ContactAccess): String = when (access) {
    com.chrisallenclark.keepr.core.services.ContactAccess.AUTHORIZED -> FULL_ACCESS
    com.chrisallenclark.keepr.core.services.ContactAccess.LIMITED -> "Selected contacts"
    com.chrisallenclark.keepr.core.services.ContactAccess.DENIED -> "Off"
    com.chrisallenclark.keepr.core.services.ContactAccess.RESTRICTED -> "Unavailable"
    com.chrisallenclark.keepr.core.services.ContactAccess.NOT_DETERMINED -> "Not asked"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

private fun openSystemSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (error: ActivityNotFoundException) {
        context.startActivity(appSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Shown where iOS opens a screen this Android build doesn't have yet. */
@Composable
fun ComingSoonSheet(feature: String, onDismiss: () -> Unit) {
    val colors = KeeprTheme.colors
    KeeprSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SfSymbol("sparkles", colors.accent, 44.dp)
            KText("$feature is on its way", style = KeeprTheme.typography.title3.bold, textAlign = TextAlign.Center)
            KText(
                "It's part of the next Android update. Everything you record now will be there when it arrives.",
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

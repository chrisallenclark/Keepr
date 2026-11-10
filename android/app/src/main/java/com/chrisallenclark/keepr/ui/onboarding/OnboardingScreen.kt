package com.chrisallenclark.keepr.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.components.EmptyStateArea
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprButtonStyle
import com.chrisallenclark.keepr.ui.components.SegmentedControl
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.navigation.LocalNavigator
import com.chrisallenclark.keepr.ui.navigation.SheetRoute
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.ModeBinding
import com.chrisallenclark.keepr.ui.screens.observeStore
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.semibold

/** Contact access as onboarding sees it — iOS `ContactAccess`, narrowed to what this screen shows. */
private enum class Access { NOT_DETERMINED, DENIED, AUTHORIZED }

private enum class Step { WELCOME, CONTEXT, CONTACTS }

/**
 * Three screens: what this is, which side you're starting on, and your
 * people. Translated from Keepr/Features/Onboarding/OnboardingView.swift.
 *
 * Contact access is requested only on the screen that explains why, and
 * "Not Now" is a real option — the app works without it. Shown full screen
 * over the tabs on first launch (never in screenshot runs), and it can't be
 * swiped away: back steps to the previous page.
 */
@Composable
fun OnboardingScreen(mode: ModeBinding, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val state = LocalAppState.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val colors = KeeprTheme.colors
    observeStore().value

    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = Step.entries[stepIndex]
    var access by remember {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) Access.AUTHORIZED else Access.NOT_DETERMINED)
    }
    var isRequesting by remember { mutableStateOf(false) }
    // How many people there were before importing, so "N added" counts only the import.
    val peopleNow = state.context.fetch<Person>().count { !it.isDeleted }
    var baseline by remember { mutableStateOf<Int?>(null) }
    val importedCount = baseline?.let { (peopleNow - it).coerceAtLeast(0) } ?: 0

    fun presentImport() {
        if (baseline == null) baseline = peopleNow
        navigator.present(SheetRoute.ContactImport)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isRequesting = false
        access = if (granted) Access.AUTHORIZED else Access.DENIED
        if (granted) presentImport()
    }

    fun finish() {
        haptics.success()
        onFinish()
    }

    // A new phone: bring everything back from a backup file instead of starting over.
    var restoringFrom by remember { mutableStateOf<android.net.Uri?>(null) }
    var didRestore by remember { mutableStateOf(false) }
    val pickBackup = com.chrisallenclark.keepr.ui.backup.rememberBackupFilePicker { restoringFrom = it }

    BackHandler(enabled = stepIndex > 0) { stepIndex -= 1 }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // A full-screen cover: nothing underneath can be touched.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Centered in the room above the footer, and scrollable when a small
        // phone at a large text size can't fit the page and the buttons.
        EmptyStateArea(Modifier.weight(1f).fillMaxWidth(), bottomClearance = 0.dp) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "onboardingStep",
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = Spacing.section),
            ) { page ->
                when (page) {
                    Step.WELCOME -> Page(
                        symbol = "person.2.circle",
                        symbolSize = 67.dp,
                        title = "Keepr",
                        isLargeTitle = true,
                        message = "Your business relationships and your personal ones, finally kept apart — and remembered properly.",
                    )
                    Step.CONTEXT -> Page(
                        symbol = "rectangle.righthalf.inset.filled",
                        symbolSize = 58.dp,
                        title = "One switch",
                        message = "Every screen shows one side of your life at a time. Someone who's both a friend and a client is still one person — they just show up in both.",
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = Spacing.small),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            SegmentedControl(
                                options = ContextMode.entries.map { it.title },
                                selectedIndex = ContextMode.entries.indexOf(mode.mode),
                                onSelect = { index ->
                                    haptics.selection()
                                    mode.set(ContextMode.entries[index])
                                },
                            )
                            KText("You can switch any time.", style = KeeprTheme.typography.caption, color = colors.tertiaryLabel)
                        }
                    }
                    Step.CONTACTS -> Page(
                        symbol = "person.crop.circle.badge.plus",
                        symbolSize = 58.dp,
                        title = "Bring in your people",
                        message = "Keepr can link relationships to your contacts so you can call, text or email in one tap. Your contacts stay on your phone — nothing is uploaded and nothing is written back.",
                    ) {
                        if (importedCount > 0) {
                            IconLabel(
                                "$importedCount added",
                                "checkmark.circle.fill",
                                style = KeeprTheme.typography.subheadline,
                                color = colors.green,
                            )
                        } else if (access == Access.DENIED) {
                            KText(
                                "No problem — you can add people by hand, and turn contacts on later in Settings.",
                                style = KeeprTheme.typography.footnote,
                                color = colors.secondaryLabel,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // Footer
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = Spacing.section)
                .padding(bottom = Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (step) {
                Step.WELCOME -> {
                    PrimaryButton("Continue") { stepIndex += 1 }
                    SecondaryButton("Restore from Backup…", pickBackup)
                }
                Step.CONTEXT -> PrimaryButton("Continue") { stepIndex += 1 }
                Step.CONTACTS -> when (access) {
                    Access.AUTHORIZED -> {
                        PrimaryButton(if (importedCount > 0) "Done" else "Choose Contacts") {
                            if (importedCount > 0) finish() else presentImport()
                        }
                        SecondaryButton("Skip for now", ::finish)
                    }
                    Access.NOT_DETERMINED -> {
                        PrimaryButton(if (isRequesting) "Asking…" else "Allow Contacts", enabled = !isRequesting) {
                            isRequesting = true
                            permission.launch(Manifest.permission.READ_CONTACTS)
                        }
                        SecondaryButton("Not Now", ::finish)
                    }
                    Access.DENIED -> PrimaryButton("Get Started", onClick = ::finish)
                }
            }
            PageIndicator(stepIndex, Step.entries.size)
        }
    }

    restoringFrom?.let { uri ->
        com.chrisallenclark.keepr.ui.backup.RestoreSheet(
            uri,
            onDismiss = {
                restoringFrom = null
                // Restored: the phone is set up, so onboarding is done.
                if (didRestore) onFinish()
            },
            onRestored = { result ->
                didRestore = true
                result.mode?.let(mode.set)
            },
        )
    }
}

@Composable
private fun Page(
    symbol: String,
    symbolSize: androidx.compose.ui.unit.Dp,
    title: String,
    message: String,
    isLargeTitle: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        SfSymbol(symbol, colors.accent, symbolSize)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            KText(
                title,
                style = if (isLargeTitle) typography.largeTitle.semibold else typography.title.semibold,
                textAlign = TextAlign.Center,
            )
            KText(
                message,
                style = if (isLargeTitle) typography.body else typography.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
        extra?.invoke()
    }
}

/** `.borderedProminent`, full width and 50pt tall. */
@Composable
private fun PrimaryButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    KeeprButton(
        title,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        style = KeeprButtonStyle.Prominent,
        enabled = enabled,
        textStyle = KeeprTheme.typography.headline,
    )
}

@Composable
private fun SecondaryButton(title: String, onClick: () -> Unit) {
    KeeprButton(title, onClick = onClick, textStyle = KeeprTheme.typography.subheadline)
}

@Composable
private fun PageIndicator(current: Int, count: Int) {
    val colors = KeeprTheme.colors
    Row(
        Modifier
            .padding(top = Spacing.tight)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { page ->
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (page == current) colors.accent else colors.tertiaryLabel, CircleShape),
            )
        }
    }
}

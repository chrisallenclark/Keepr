package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold

/*
 * The rows the iOS screens are built from, translated view for view. They take
 * plain values, not models, so they work for the design gallery today and for
 * the real screens once the data layer exists.
 */

/** iOS `FollowUpRow`: a completion circle, who, what, and when. */
@Composable
fun FollowUpRow(
    title: String,
    dueText: String,
    modifier: Modifier = Modifier,
    personName: String? = null,
    isCompleted: Boolean = false,
    isOverdue: Boolean = false,
    isHighPriority: Boolean = false,
    onComplete: () -> Unit = {},
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(
        modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        SfSymbol(
            if (isCompleted) "checkmark.circle.fill" else "circle",
            if (isCompleted) colors.accent else colors.secondaryLabel,
            typography.title3,
            contentDescription = if (isCompleted) "Mark not done" else "Mark done",
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onComplete,
            ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (personName != null) {
                KText(personName, style = typography.subheadline.semibold)
            }
            KText(
                title,
                style = typography.subheadline,
                color = if (isCompleted) colors.tertiaryLabel else colors.secondaryLabel,
                strikethrough = isCompleted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight), verticalAlignment = Alignment.CenterVertically) {
                if (isHighPriority && !isCompleted) {
                    SfSymbol("exclamationmark", colors.orange, typography.caption)
                }
                KText(
                    if (isCompleted) "Completed" else dueText,
                    style = typography.caption,
                    color = if (isOverdue) colors.orange else colors.secondaryLabel,
                )
            }
        }
    }
}

/** iOS `PersonCompactRow`: small avatar, name, one line of detail, chevron. */
@Composable
fun PersonCompactRow(
    name: String,
    initials: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    photo: ByteArray? = null,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Avatar(initials, size = AvatarSize.Small, imageData = photo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            KText(name, style = typography.subheadline.medium, maxLines = 1)
            if (detail != null) {
                KText(detail, style = typography.caption, color = colors.secondaryLabel, maxLines = 1)
            }
        }
        SfSymbol("chevron.right", colors.tertiaryLabel, typography.caption.semibold)
    }
}

@Immutable
data class TagLabel(val name: String, val symbolName: String?)

/** iOS `PersonRow`, the People list's row. */
@Composable
fun PersonRow(
    name: String,
    initials: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isFavorite: Boolean = false,
    tags: List<TagLabel> = emptyList(),
    lastInteraction: String? = null,
    nextDue: String? = null,
    nextIsOverdue: Boolean = false,
    photo: ByteArray? = null,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(
        modifier.padding(vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Avatar(initials, size = AvatarSize.Medium, imageData = photo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight), verticalAlignment = Alignment.CenterVertically) {
                KText(name, style = typography.body.medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (isFavorite) {
                    SfSymbol("star.fill", colors.yellow, typography.caption2, contentDescription = "Favorite")
                }
            }
            if (subtitle != null) {
                KText(subtitle, style = typography.footnote, color = colors.secondaryLabel, maxLines = 1)
            }
            TagRow(tags.map { it.name to it.symbolName })
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (lastInteraction != null) {
                KText(lastInteraction, style = typography.caption, color = colors.tertiaryLabel, maxLines = 1)
            }
            if (nextDue != null) {
                val tint = if (nextIsOverdue) colors.orange else colors.secondaryLabel
                IconLabel(nextDue, "bell", typography.caption2, tint, maxLines = 1)
            }
        }
    }
}

/** iOS `PersonHeader`: the top of a profile. */
@Composable
fun PersonHeader(
    name: String,
    initials: String,
    modifier: Modifier = Modifier,
    fullName: String? = null,
    subtitle: String? = null,
    tags: List<TagLabel> = emptyList(),
    photo: ByteArray? = null,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Avatar(initials, size = AvatarSize.Large, imageData = photo)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            KText(name, style = typography.title2.semibold, textAlign = TextAlign.Center)
            if (fullName != null && fullName != name) {
                KText(fullName, style = typography.subheadline, color = colors.secondaryLabel)
            }
            if (subtitle != null) {
                KText(subtitle, style = typography.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            }
        }
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                tags.take(3).forEach { TagChip(it.name, symbolName = it.symbolName, isProminent = true) }
            }
        }
    }
}

@Immutable
data class QuickAction(val title: String, val symbolName: String, val isProminent: Boolean = false, val onClick: () -> Unit)

/** iOS `QuickActions`: Message / Call / Email / Log under a profile header. */
@Composable
fun QuickActionsRow(actions: List<QuickAction>, modifier: Modifier = Modifier) {
    val colors = KeeprTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        actions.forEach { action ->
            val foreground = if (action.isProminent) Color.White else colors.accent
            Column(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(
                        if (action.isProminent) colors.accent else colors.quaternaryLabel,
                        RoundedCornerShape(Radius.card),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = action.onClick,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.tight, Alignment.CenterVertically),
            ) {
                SfSymbol(action.symbolName, foreground, 20.dp)
                KText(action.title, style = KeeprTheme.typography.caption2, color = foreground, maxLines = 1)
            }
        }
    }
}

/** Space so the last row can scroll clear of the floating tab bar. */
@Composable
fun TabBarClearance() = Spacer(
    Modifier
        .navigationBarsPadding()
        .height(com.chrisallenclark.keepr.ui.theme.Chrome.tabBarClearance),
)

/**
 * SwiftUI's `Label` in `.titleAndIcon` style. iOS 26 leaves a wide gap after
 * the symbol — measured at about 1.5× the type size — which is a big part of
 * why its chips and captions look airy.
 */
@Composable
fun IconLabel(
    title: String,
    symbolName: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((style.fontSize.value * 1.5f - 2f).dp),
    ) {
        SfSymbol(symbolName, color, style)
        KText(title, style = style, color = color, maxLines = maxLines)
    }
}

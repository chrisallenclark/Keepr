package com.chrisallenclark.keepr.preview

import com.chrisallenclark.keepr.ui.components.TagLabel

/**
 * The people and words in the iOS app's demo data, as they appear in the iOS
 * screenshots, so Android captures can be compared with them line for line.
 * Dates are written as iOS rendered them on the day of capture.
 *
 * Temporary: replaced by the real data layer in the next phase.
 */
internal object SampleContent {
    data class Person(
        val name: String,
        val initials: String,
        val subtitle: String? = null,
        val isFavorite: Boolean = false,
        val tags: List<TagLabel> = emptyList(),
        val lastInteraction: String? = null,
        val nextDue: String? = null,
        val nextIsOverdue: Boolean = false,
    )

    private fun tag(name: String) = TagLabel(name, "tag")

    val jake = Person(
        "Jake Martinez", "JM", "Owner · Martinez Roofing",
        tags = listOf(tag("Potential Client"), tag("Lead")),
        lastInteraction = "Yesterday", nextDue = "Saturday",
    )
    val sarah = Person(
        "Sarah Miller", "SM", "Creative Director · Coastal Design Co.", isFavorite = true,
        tags = listOf(tag("Current Client")),
        lastInteraction = "3 days ago", nextDue = "Yesterday", nextIsOverdue = true,
    )
    val priya = Person(
        "Priya Raman", "PR", "Photographer · Bright Studio",
        tags = listOf(tag("Vendor")), lastInteraction = "Oct 8",
    )
    val michael = Person(
        "Michael Reed", "MR", "Partner · Reed Physical Therapy",
        tags = listOf(tag("Business"), tag("Referral")),
        lastInteraction = "Oct 15", nextDue = "Tuesday",
    )
    val dana = Person(
        "Dana Whitfield", "DW", "Founder · Whitfield & Co.",
        tags = listOf(tag("Investor")), lastInteraction = "Sep 21",
    )

    val businessPeople = listOf(jake, sarah, priya, michael, dana)
}

package com.chrisallenclark.keepr.core.model

/**
 * A named connection between two people and its inverse ("Parent" ↔
 * "Child"). Translated from `LinkRole` in Keepr/Models/PersonLink.swift.
 */
data class LinkRole(val name: String, val inverse: String, val symbolName: String) {
    val id: String get() = name
    val isSymmetric: Boolean get() = name == inverse
    val reversed: LinkRole get() = LinkRole(inverse, name, symbolName)

    data class Section(val title: String, val roles: List<LinkRole>) {
        val id: String get() = title
    }

    companion object {
        fun custom(name: String): LinkRole = LinkRole(name, name, "link")

        val family: List<LinkRole> = listOf(
            LinkRole("Parent", "Child", "figure.2.and.child.holdinghands"),
            LinkRole("Child", "Parent", "figure.2.and.child.holdinghands"),
            LinkRole("Spouse", "Spouse", "heart"),
            LinkRole("Partner", "Partner", "heart"),
            LinkRole("Sibling", "Sibling", "person.2"),
            LinkRole("Grandparent", "Grandchild", "house"),
            LinkRole("Grandchild", "Grandparent", "house"),
            LinkRole("Family", "Family", "house"),
        )

        val social: List<LinkRole> = listOf(
            LinkRole("Friend", "Friend", "hand.wave"),
            LinkRole("Neighbor", "Neighbor", "building.2"),
            LinkRole("Knows", "Knows", "person.2"),
        )

        val work: List<LinkRole> = listOf(
            LinkRole("Colleague", "Colleague", "briefcase"),
            LinkRole("Manager", "Direct Report", "briefcase"),
            LinkRole("Direct Report", "Manager", "briefcase"),
            LinkRole("Assistant", "Works With", "briefcase"),
            LinkRole("Referred By", "Referred", "arrow.triangle.branch"),
            LinkRole("Referred", "Referred By", "arrow.triangle.branch"),
        )

        val catalog: List<LinkRole> get() = family + social + work

        val sections: List<Section> = listOf(
            Section("Family", family),
            Section("Social", social),
            Section("Work", work),
        )

        /** A catalog role by name, case-insensitively; a custom one otherwise. */
        fun named(name: String): LinkRole =
            catalog.firstOrNull { localizedCaseInsensitiveCompare(it.name, name) == 0 } ?: custom(name)

        /** Maps a contact card's relation label ("mother") to a role. */
        fun forContactLabel(label: String): LinkRole {
            val key = label.trimmed().lowercase()
            return when (key) {
                "mother", "father", "parent", "mom", "dad" -> named("Parent")
                "son", "daughter", "child" -> named("Child")
                "brother", "sister", "sibling" -> named("Sibling")
                "spouse", "husband", "wife" -> named("Spouse")
                "partner", "girlfriend", "boyfriend" -> named("Partner")
                "grandmother", "grandfather", "grandparent" -> named("Grandparent")
                "grandchild", "grandson", "granddaughter" -> named("Grandchild")
                "friend" -> named("Friend")
                "manager" -> named("Manager")
                "assistant" -> named("Assistant")
                else -> custom(key.capitalizedWords())
            }
        }
    }
}

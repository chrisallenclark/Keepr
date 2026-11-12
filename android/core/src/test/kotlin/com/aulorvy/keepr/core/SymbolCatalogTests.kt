package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.PeopleSort
import com.aulorvy.keepr.core.domain.SymbolCatalog
import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.LinkRole
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.services.ContactMethod
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Translated from KeeprTests/SymbolCatalogTests.swift ("Symbols").
//
// iOS asks `UIImage(systemName:)` whether a name is real. On Android the
// question is whether the app's symbol table maps the name to a shipped
// drawable — an unknown name falls back to a placeholder circle, the Android
// version of the empty square. See [AndroidSymbols].

/**
 * The app's SF Symbol → drawable table, read from source because core can't
 * see the app's `R`. Same check as `PersonFileTests.symbolsExist`.
 */
internal object AndroidSymbols {
    private val table = File("../app/src/main/java/com/aulorvy/keepr/ui/icons/SfSymbolTable.kt")
    private val drawables = File("../app/src/main/res/drawable")
    private val source: String by lazy {
        assertTrue(table.isFile, "symbol table not found at ${table.absolutePath}")
        table.readText()
    }

    /** `UIImage(systemName: name) != nil`, Android edition. */
    fun exists(name: String): Boolean {
        val resource = "sf_" + name.replace('.', '_')
        return source.contains("\"$name\" to R.drawable.$resource,") && File(drawables, "$resource.xml").isFile
    }
}

/**
 * A misspelled SF Symbol is the worst kind of mistake in this app: it compiles,
 * it ships, and it renders as an empty square that nobody can explain. These
 * tests ask whether every symbol name in the app is real.
 */
class SymbolCatalogTests {

    private fun check(names: List<String>, source: String) {
        for (name in names) {
            assertTrue(
                AndroidSymbols.exists(name),
                "$source uses \"$name\", which isn't an SF Symbol on this iOS",
            )
        }
    }

    /** Every symbol in the picker exists */
    @Test
    fun pickerSymbolsResolve() {
        check(SymbolCatalog.catalogue.flatMap { category -> category.symbols.map { it.name } }, "The symbol picker")
    }

    /** Every built-in relationship type has a real symbol */
    @Test
    fun relationshipTypeSymbolsResolve() {
        check(RelationshipTag.builtInCatalog.map { it.symbolName }, "The relationship type catalog")
    }

    /** Every suggested group has a real symbol */
    @Test
    fun groupSuggestionSymbolsResolve() {
        check(PersonGroup.suggestions.map { it.symbolName }, "Group suggestions")
    }

    /** Every link role has a real symbol */
    @Test
    fun linkRoleSymbolsResolve() {
        check(LinkRole.catalog.map { it.symbolName }, "Link roles")
    }

    /** Every enum that draws itself has a real symbol */
    @Test
    fun enumSymbolsResolve() {
        check(InteractionKind.entries.map { it.symbolName }, "Interaction kinds")
        check(MemoryCategory.entries.map { it.symbolName }, "Memory categories")
        check(ContextMode.entries.map { it.symbolName }, "Context modes")
        check(RelationshipContext.entries.map { it.symbolName }, "Relationship contexts")
        check(PeopleSort.entries.map { it.symbolName }, "People sorts")
        check(ContactMethod.entries.map { it.symbolName }, "Contact methods")
    }

    // MARK: - Shape of the catalogue

    /** No symbol is offered twice in the same section */
    @Test
    fun sectionsHoldNoDuplicates() {
        for (category in SymbolCatalog.catalogue) {
            val names = category.symbols.map { it.name }
            assertEquals(names.size, names.toSet().size, "${category.title} repeats a symbol")
        }
    }

    /** The picker leads with what this app is for */
    @Test
    fun businessSectionsComeFirst() {
        val titles = SymbolCatalog.catalogue.map { it.title }
        val clients = assertNotNull(titles.indexOf("Clients & Deals").takeIf { it >= 0 })
        val personal = assertNotNull(titles.indexOf("People & Personal").takeIf { it >= 0 })
        assertTrue(clients < personal, "someone sorting clients shouldn't scroll past hobbies")
    }

    /** Searching by meaning finds the right symbol, not just by filename */
    @Test
    fun keywordSearchWorks() {
        fun names(matching: String): List<String> =
            SymbolCatalog.catalogue
                .flatMap { it.symbols }
                .filter { it.matches(matching) }
                .map { it.name }

        assertTrue(names("client").contains("checkmark.seal"))
        assertTrue(names("past").contains("clock.arrow.circlepath"))
        assertTrue(names("gym").contains("dumbbell"))
        assertTrue(names("meal").contains("fork.knife"))
        assertTrue(names("money").contains("dollarsign.circle"))
        assertTrue(names("partner").contains("person.2"))
        assertTrue(names("referral").contains("arrow.triangle.branch"))
        assertTrue(names("trainer").contains("dumbbell"))
    }

    /** Every symbol carries keywords, so nothing is unfindable */
    @Test
    fun everySymbolIsSearchable() {
        for (category in SymbolCatalog.catalogue) {
            for (symbol in category.symbols) {
                assertTrue(symbol.keywords.isNotEmpty(), "${symbol.name} has no keywords")
            }
        }
    }
}

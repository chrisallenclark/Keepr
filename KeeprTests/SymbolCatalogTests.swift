import Testing
import UIKit

@testable import Keepr

/// A misspelled SF Symbol is the worst kind of mistake in this app: it compiles,
/// it ships, and it renders as an empty square that nobody can explain. These
/// tests ask iOS itself whether every symbol name in the app is real.
@Suite("Symbols")
struct SymbolCatalogTests {

    private func check(_ names: [String], _ source: String) {
        for name in names {
            #expect(
                UIImage(systemName: name) != nil,
                "\(source) uses \"\(name)\", which isn't an SF Symbol on this iOS"
            )
        }
    }

    @Test("Every symbol in the picker exists")
    func pickerSymbolsResolve() {
        check(SymbolPicker.catalogue.flatMap { $0.symbols.map(\.name) }, "The symbol picker")
    }

    @Test("Every built-in relationship type has a real symbol")
    func relationshipTypeSymbolsResolve() {
        check(RelationshipTag.builtInCatalog.map(\.symbolName), "The relationship type catalog")
    }

    @Test("Every suggested group has a real symbol")
    func groupSuggestionSymbolsResolve() {
        check(PersonGroup.suggestions.map(\.symbolName), "Group suggestions")
    }

    @Test("Every link role has a real symbol")
    func linkRoleSymbolsResolve() {
        check(LinkRole.catalog.map(\.symbolName), "Link roles")
    }

    @Test("Every enum that draws itself has a real symbol")
    func enumSymbolsResolve() {
        check(InteractionKind.allCases.map(\.symbolName), "Interaction kinds")
        check(MemoryCategory.allCases.map(\.symbolName), "Memory categories")
        check(ContextMode.allCases.map(\.symbolName), "Context modes")
        check(RelationshipContext.allCases.map(\.symbolName), "Relationship contexts")
        check(PeopleSort.allCases.map(\.symbolName), "People sorts")
        check(ContactMethod.allCases.map(\.symbolName), "Contact methods")
    }

    // MARK: - Shape of the catalogue

    @Test("No symbol is offered twice in the same section")
    func sectionsHoldNoDuplicates() {
        for category in SymbolPicker.catalogue {
            let names = category.symbols.map(\.name)
            #expect(Set(names).count == names.count, "\(category.title) repeats a symbol")
        }
    }

    @Test("The picker leads with what this app is for")
    func businessSectionsComeFirst() throws {
        let titles = SymbolPicker.catalogue.map(\.title)
        let clients = try #require(titles.firstIndex(of: "Clients & Deals"))
        let personal = try #require(titles.firstIndex(of: "People & Personal"))
        #expect(clients < personal, "someone sorting clients shouldn't scroll past hobbies")
    }

    @Test("Searching by meaning finds the right symbol, not just by filename")
    func keywordSearchWorks() {
        func names(matching query: String) -> [String] {
            SymbolPicker.catalogue
                .flatMap(\.symbols)
                .filter { $0.matches(query) }
                .map(\.name)
        }

        #expect(names(matching: "client").contains("checkmark.seal"))
        #expect(names(matching: "past").contains("clock.arrow.circlepath"))
        #expect(names(matching: "gym").contains("dumbbell"))
        #expect(names(matching: "meal").contains("fork.knife"))
        #expect(names(matching: "money").contains("dollarsign.circle"))
        #expect(names(matching: "partner").contains("person.2"))
        #expect(names(matching: "referral").contains("arrow.triangle.branch"))
        #expect(names(matching: "trainer").contains("dumbbell"))
    }

    @Test("Every symbol carries keywords, so nothing is unfindable")
    func everySymbolIsSearchable() {
        for category in SymbolPicker.catalogue {
            for symbol in category.symbols {
                #expect(!symbol.keywords.isEmpty, "\(symbol.name) has no keywords")
            }
        }
    }
}

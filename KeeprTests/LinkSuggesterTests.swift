import Foundation
import SwiftData
import Testing

@testable import Keepr

/// Suggested links join two real people on the strength of a name written on a
/// contact card. That's a high-stakes guess, so these tests mostly pin down when
/// it refuses to make one.
@Suite("Link suggestions")
@MainActor
struct LinkSuggesterTests {

    private func relation(_ label: String, _ name: String) -> ContactRelation {
        ContactRelation(label: label, name: name)
    }

    @Test("A relation naming someone already in Keepr becomes a suggestion")
    func matchingNameSuggestsALink() throws {
        let store = try TestStore()
        let alex = Make.person(store.context, given: "Alex", family: "Nguyen")
        let linda = Make.person(store.context, given: "Linda", family: "Clark")

        let suggestions = LinkSuggester.suggestions(
            from: [relation("mother", "Linda Clark")],
            for: alex,
            among: [alex, linda]
        )

        #expect(suggestions.count == 1)
        let first = try #require(suggestions.first)
        #expect(first.candidate.id == linda.id)
        #expect(first.role.name == "Parent")
        #expect(first.reason.contains("mother"))
    }

    @Test("Case and accents don't stop a match")
    func matchingIsForgivingAboutSpelling() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Test", family: "Person")
        let jose = Make.person(store.context, given: "José", family: "Ruiz")

        let suggestions = LinkSuggester.suggestions(
            from: [relation("brother", "jose  ruiz")],
            for: person,
            among: [person, jose]
        )

        #expect(suggestions.first?.candidate.id == jose.id)
    }

    @Test("A card that says \"Mom\" matches the person filed as Mom")
    func preferredNamesCount() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Test", family: "Person")
        let mom = Make.person(
            store.context,
            given: "Linda",
            family: "Clark",
            preferred: "Mom"
        )

        let suggestions = LinkSuggester.suggestions(
            from: [relation("mother", "Mom")],
            for: person,
            among: [person, mom]
        )

        #expect(suggestions.first?.candidate.id == mom.id)
    }

    @Test("A name nobody in Keepr has is not guessed at")
    func unknownNamesAreSkipped() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Test", family: "Person")
        let similar = Make.person(store.context, given: "Linda", family: "Clarkson")

        let suggestions = LinkSuggester.suggestions(
            from: [relation("mother", "Linda Clark")],
            for: person,
            among: [person, similar]
        )

        #expect(suggestions.isEmpty, "a near-miss is not a match")
    }

    @Test("Nobody is suggested as a link to themselves")
    func selfIsExcluded() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Linda", family: "Clark")

        let suggestions = LinkSuggester.suggestions(
            from: [relation("mother", "Linda Clark")],
            for: person,
            among: [person]
        )

        #expect(suggestions.isEmpty)
    }

    @Test("An existing link isn't offered a second time")
    func alreadyLinkedIsSkipped() throws {
        let store = try TestStore()
        let alex = Make.person(store.context, given: "Alex", family: "Nguyen")
        let linda = Make.person(store.context, given: "Linda", family: "Clark")
        store.context.insert(
            PersonLink(personA: alex, personB: linda, labelAToB: "Parent", labelBToA: "Child")
        )
        try store.save()

        let suggestions = LinkSuggester.suggestions(
            from: [relation("mother", "Linda Clark")],
            for: alex,
            among: [alex, linda]
        )

        #expect(suggestions.isEmpty)
    }

    @Test("Two relations naming the same person produce one suggestion")
    func candidatesAreNotDoubleCounted() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Test", family: "Person")
        let pat = Make.person(store.context, given: "Pat", family: "Reed")

        let suggestions = LinkSuggester.suggestions(
            from: [relation("spouse", "Pat Reed"), relation("partner", "Pat Reed")],
            for: person,
            among: [person, pat]
        )

        #expect(suggestions.count == 1)
    }

    @Test("An empty name on a card matches nobody")
    func blankNamesMatchNothing() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Test", family: "Person")
        let other = Make.person(store.context, given: "Other", family: "Person")

        #expect(!LinkSuggester.matches(other, name: "   "))
        let suggestions = LinkSuggester.suggestions(
            from: [relation("mother", "")],
            for: person,
            among: [person, other]
        )
        #expect(suggestions.isEmpty)
    }
}

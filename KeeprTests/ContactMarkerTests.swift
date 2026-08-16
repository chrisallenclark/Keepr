import Foundation
import SwiftData
import Testing

@testable import Keepr

/// Reading shorthand off a contact card is the highest-leverage thing the
/// importer does and the easiest to get dangerously wrong: it renames people and
/// files them automatically. These tests are mostly about the guardrails.
@Suite("Contact markers")
@MainActor
struct ContactMarkerTests {

    private func contact(
        given: String = "Test",
        family: String = "Person",
        organization: String? = nil,
        jobTitle: String? = nil
    ) -> ContactSummary {
        ContactSummary(
            identifier: UUID().uuidString,
            givenName: given,
            familyName: family,
            organizationName: organization,
            jobTitle: jobTitle,
            phoneNumbers: [],
            emailAddresses: [],
            thumbnailData: nil
        )
    }

    /// A trainer's setup: three businesses and the built-in types.
    private func trainerVocabulary(_ store: TestStore) throws -> MarkerVocabulary {
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let lifeTime = PersonGroup(name: "Life Time", sortOrder: 0)
        let hyp = PersonGroup(name: "Hybrid Performance", aliases: "HYP", sortOrder: 10)
        let mealPrep = PersonGroup(name: "Meal Prep", sortOrder: 20)
        for group in [lifeTime, hyp, mealPrep] { store.context.insert(group) }
        try store.save()

        return MarkerVocabulary.build(
            groups: try store.fetch(PersonGroup.self),
            tags: try store.fetch(RelationshipTag.self)
        )
    }

    // MARK: - Reading the shorthand

    @Test("\"Stanley LT Client\" becomes Stanley, a client, at Life Time")
    func initialsAndTypeAreRead() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Stanley", family: "LT Client"),
            vocabulary: vocabulary
        )

        #expect(parse.groupNames == ["Life Time"])
        #expect(parse.typeNames == ["Current Client"])
        #expect(parse.givenName == "Stanley")
        #expect(parse.familyName.isEmpty, "the last name field held nothing but markers")
    }

    @Test("A user-taught alias works — \"HYP\" means Hybrid Performance")
    func explicitAliasesAreRead() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Dana", family: "Whitfield (HYP)"),
            vocabulary: vocabulary
        )

        #expect(parse.groupNames == ["Hybrid Performance"])
        #expect(parse.familyName == "Whitfield", "the brackets go with the marker")
    }

    @Test("Initials are understood without being taught")
    func initialsAreDerived() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Rob", family: "MP"),
            vocabulary: vocabulary
        )

        #expect(parse.groupNames == ["Meal Prep"])
    }

    @Test("A full name spelled out matches too, in any case")
    func fullNamesMatchCaseInsensitively() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Casey", family: "life time"),
            vocabulary: vocabulary
        )

        #expect(parse.groupNames == ["Life Time"])
    }

    @Test("Two markers on one card are both read")
    func multipleMarkersAreRead() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Sam", family: "LT Meal Prep Client"),
            vocabulary: vocabulary
        )

        #expect(Set(parse.groupNames) == ["Life Time", "Meal Prep"])
        #expect(parse.typeNames == ["Current Client"])
        #expect(parse.givenName == "Sam")
    }

    // MARK: - Not damaging real names

    @Test("A short marker in normal case is left alone — it's probably a name")
    func lowercaseInitialsAreNotMarkers() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Lt", family: "Colonel"),
            vocabulary: vocabulary
        )

        #expect(parse.isEmpty, "\"Lt\" is not shouted, so it isn't Life Time")
        #expect(parse.givenName == "Lt")
    }

    @Test("A marker inside a longer word is not a match")
    func markersMatchWholeWordsOnly() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Clientele", family: "Salton"),
            vocabulary: vocabulary
        )

        #expect(parse.isEmpty)
        #expect(parse.givenName == "Clientele")
    }

    @Test("A card that is nothing but markers keeps its name")
    func neverLeavesSomeoneNameless() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "LT", family: "Client"),
            vocabulary: vocabulary
        )

        #expect(parse.groupNames == ["Life Time"])
        #expect(parse.typeNames == ["Current Client"])
        #expect(parse.givenName == "LT", "categorized, but not erased")
        #expect(parse.changedName == false)
    }

    @Test("A marker in the company field is evidence, not something to edit out")
    func companyFieldIsNotRewritten() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let parse = ContactMarkerParser.parse(
            contact(given: "Jamie", family: "Boyd", organization: "Life Time"),
            vocabulary: vocabulary
        )

        #expect(parse.groupNames == ["Life Time"])
        #expect(parse.familyName == "Boyd")
        #expect(parse.changedName == false, "an employer is a fact, not a label")
    }

    @Test("With no groups or types set up, nothing is guessed at")
    func emptyVocabularyDoesNothing() {
        let parse = ContactMarkerParser.parse(
            contact(given: "Stanley", family: "LT Client"),
            vocabulary: MarkerVocabulary()
        )

        #expect(parse.isEmpty)
        #expect(parse.familyName == "LT Client")
    }

    // MARK: - The whole suggestion

    @Test("A marker outranks the employer field")
    func markersBeatInferredEmployer() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let summary = contact(
            given: "Stanley",
            family: "LT Client",
            organization: "Stanley Commerce Co."
        )
        let suggestion = try #require(
            ContactCategorizer.suggestion(for: summary, vocabulary: vocabulary)
        )

        #expect(suggestion.tagName == "Current Client")
        #expect(suggestion.context == .business)
        #expect(suggestion.groupNames == ["Life Time"])
        #expect(suggestion.cleanedGivenName == "Stanley")
        #expect(suggestion.originalName == "Stanley LT Client")
    }

    @Test("A personal type on the card puts them in Personal, not Business")
    func personalTypesSetPersonalContext() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let suggestion = try #require(
            ContactCategorizer.suggestion(
                for: contact(given: "Rae", family: "FAMILY"),
                vocabulary: vocabulary
            )
        )

        #expect(suggestion.context == .personal)
        #expect(suggestion.tagName == "Family")
    }

    @Test("With no markers, the old employer rules still apply")
    func fallsBackToTheOlderRules() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let suggestion = try #require(
            ContactCategorizer.suggestion(
                for: contact(given: "Nina", family: "Ortega", organization: "Coastal Design"),
                vocabulary: vocabulary
            )
        )

        #expect(suggestion.tagName == "Professional Contact")
        #expect(suggestion.groupNames.isEmpty)
        #expect(suggestion.cleanedGivenName == nil)
    }

    @Test("A type the user deleted is not suggested back into existence")
    func deletedTypesAreNotSuggested() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        // They don't think of anyone as a "Professional Contact" and got rid of it.
        let unwanted = try #require(
            try store.fetch(RelationshipTag.self).first { $0.builtInKey == "Professional Contact" }
        )
        store.context.delete(unwanted)
        try store.save()

        let vocabulary = MarkerVocabulary.build(
            groups: try store.fetch(PersonGroup.self),
            tags: try store.fetch(RelationshipTag.self)
        )
        let suggestion = try #require(
            ContactCategorizer.suggestion(
                for: contact(given: "Nina", family: "Ortega", organization: "Coastal Design"),
                vocabulary: vocabulary
            )
        )

        #expect(suggestion.tagName == nil, "no resurrecting a type they threw away")
        #expect(suggestion.context == .business, "the employer still says which side they're on")
    }

    // MARK: - Discovery

    @Test("Shorthand on several cards is offered as a group to create")
    func repeatedShorthandIsDiscovered() {
        let contacts = [
            contact(given: "Stanley", family: "LT Client"),
            contact(given: "Rae", family: "LT"),
            contact(given: "Nina", family: "Ortega (HYP)"),
            contact(given: "Sam", family: "Smith")
        ]

        let found = ContactMarkerParser.candidates(in: contacts, vocabulary: MarkerVocabulary())

        #expect(found.first?.token == "LT")
        #expect(found.first?.count == 2)
        #expect(found.contains { $0.token == "HYP" } == false, "one card isn't a habit")
    }

    @Test("Shorthand Keepr already understands isn't offered again")
    func knownShorthandIsNotOffered() throws {
        let store = try TestStore()
        let vocabulary = try trainerVocabulary(store)

        let found = ContactMarkerParser.candidates(
            in: [
                contact(given: "Stanley", family: "LT Client"),
                contact(given: "Rae", family: "LT")
            ],
            vocabulary: vocabulary
        )

        #expect(found.isEmpty)
    }

    @Test("Ordinary names are not mistaken for shorthand")
    func plainNamesAreNotCandidates() {
        let found = ContactMarkerParser.candidates(
            in: [
                contact(given: "Sarah", family: "Miller"),
                contact(given: "Sarah", family: "Miller"),
                contact(given: "Michael", family: "Reed")
            ],
            vocabulary: MarkerVocabulary()
        )

        #expect(found.isEmpty)
    }

    // MARK: - Vocabulary

    @Test("A group answers to its name, its initials, and anything you add")
    func vocabularyTermsCoverTheObviousSpellings() {
        let terms = Set(MarkerVocabulary.terms(for: "Life Time", aliases: "LTF, Delray Gym"))

        #expect(terms.contains("life time"))
        #expect(terms.contains("lifetime"))
        #expect(terms.contains("lt"))
        #expect(terms.contains("ltf"))
        #expect(terms.contains("delray gym"))
    }

    @Test("Longer terms are tried first, so \"Current Client\" beats \"Client\"")
    func longerTermsSortFirst() {
        let terms = MarkerVocabulary.terms(for: "Current Client", aliases: nil, extra: ["client"])
        #expect(terms.first == "current client")
    }
}

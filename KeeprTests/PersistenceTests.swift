import Foundation
import SwiftData
import Testing

@testable import Keepr

@MainActor
@Suite("Persistence")
struct PersistenceTests {

    // MARK: - Round trip

    @Test("A person's full graph survives a save and re-fetch")
    func graphRoundTrips() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Ada",
            family: "Lovelace",
            relationship: .business,
            company: "Analytical Engines"
        )
        let tag = Make.tag(store.context, name: "Investor", kind: .business)
        Make.attach(tag, to: person)

        let interaction = Make.interaction(
            store.context,
            kind: .meeting,
            occurredAt: TestDates.days(-2),
            summary: "Coffee at the shop",
            person: person
        )
        let memory = Make.memory(
            store.context,
            content: "Prefers early meetings",
            category: .preferences,
            person: person,
            sourceInteraction: interaction
        )
        let followUp = Make.followUp(
            store.context,
            title: "Send the deck",
            due: TestDates.dayStart(3),
            person: person,
            sourceInteraction: interaction
        )

        try store.save()

        let people = try store.fetch(Person.self)
        #expect(people.count == 1)

        let fetched = try #require(people.first)
        #expect(fetched.fullName == "Ada Lovelace")
        #expect(fetched.tagList.map(\.name) == ["Investor"])
        #expect(fetched.interactionList.map(\.id) == [interaction.id])
        #expect(fetched.memoryList.map(\.id) == [memory.id])
        #expect(fetched.followUpList.map(\.id) == [followUp.id])
        #expect(fetched.memoryList.first?.sourceInteraction?.id == interaction.id)
        #expect(fetched.followUpList.first?.sourceInteraction?.id == interaction.id)
        #expect(fetched.context == .business)
        #expect(fetched.company == "Analytical Engines")
    }

    // MARK: - Cascades

    @Test("Deleting a person cascades to interactions, memories and follow-ups")
    func deletingPersonCascades() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Ada", family: "Lovelace")
        let interaction = Make.interaction(store.context, summary: "Coffee", person: person)
        _ = Make.memory(store.context, content: "Likes coffee", person: person, sourceInteraction: interaction)
        _ = Make.followUp(store.context, due: TestDates.dayStart(1), person: person)
        try store.save()

        store.context.delete(person)
        try store.save()

        let remainingPeople = try store.fetch(Person.self)
        let remainingInteractions = try store.fetch(Interaction.self)
        let remainingMemories = try store.fetch(Memory.self)
        let remainingFollowUps = try store.fetch(FollowUp.self)

        #expect(remainingPeople.isEmpty)
        #expect(remainingInteractions.isEmpty)
        #expect(remainingMemories.isEmpty)
        #expect(remainingFollowUps.isEmpty)
    }

    @Test("Deleting a person nullifies rather than deletes its tags")
    func deletingPersonKeepsTags() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Ada", family: "Lovelace")
        let tag = Make.tag(store.context, name: "Investor", kind: .business)
        Make.attach(tag, to: person)
        try store.save()

        store.context.delete(person)
        try store.save()

        let tags = try store.fetch(RelationshipTag.self)
        #expect(tags.map(\.name) == ["Investor"])
        #expect(tags.first?.peopleList.isEmpty == true)
    }

    @Test("Deleting an interaction leaves its memory and follow-up alive with a nil source")
    func deletingInteractionNullifiesSources() throws {
        let store = try TestStore()
        let person = Make.person(store.context, given: "Ada", family: "Lovelace")
        let interaction = Make.interaction(store.context, summary: "Coffee", person: person)
        let memory = Make.memory(
            store.context,
            content: "Prefers early meetings",
            person: person,
            sourceInteraction: interaction
        )
        let followUp = Make.followUp(
            store.context,
            title: "Send the deck",
            due: TestDates.dayStart(3),
            person: person,
            sourceInteraction: interaction
        )
        try store.save()

        store.context.delete(interaction)
        try store.save()

        let remainingInteractions = try store.fetch(Interaction.self)
        #expect(remainingInteractions.isEmpty)

        let memories = try store.fetch(Memory.self)
        #expect(memories.map(\.id) == [memory.id])
        let survivingMemory = try #require(memories.first)
        #expect(survivingMemory.sourceInteraction == nil)
        #expect(survivingMemory.person?.id == person.id)

        let followUps = try store.fetch(FollowUp.self)
        #expect(followUps.map(\.id) == [followUp.id])
        let survivingFollowUp = try #require(followUps.first)
        #expect(survivingFollowUp.sourceInteraction == nil)
        #expect(survivingFollowUp.person?.id == person.id)
    }

    // MARK: - Contact linking

    @Test("isLinkedToContact reflects whether a contact identifier is set")
    func contactLinkingFlag() throws {
        let store = try TestStore()
        let linked = Make.person(
            store.context,
            given: "Lin",
            family: "Linked",
            contactIdentifier: "ABC-123"
        )
        let unlinked = Make.person(store.context, given: "Una", family: "Unlinked")
        try store.save()

        #expect(linked.isLinkedToContact)
        #expect(!unlinked.isLinkedToContact)
        #expect(linked.contactIdentifier == "ABC-123")
        #expect(unlinked.contactIdentifier == nil)
    }

    @Test("Two people can hold different contact identifiers")
    func distinctContactIdentifiers() throws {
        let store = try TestStore()
        _ = Make.person(store.context, given: "One", family: "Alpha", contactIdentifier: "ID-1")
        _ = Make.person(store.context, given: "Two", family: "Bravo", contactIdentifier: "ID-2")
        try store.save()

        let identifiers = try store.fetch(Person.self).compactMap(\.contactIdentifier).sorted()
        #expect(identifiers == ["ID-1", "ID-2"])
    }

    @Test("contactIdentifier has no unique constraint, so a duplicate is allowed")
    func duplicateContactIdentifiersAreAllowed() throws {
        let store = try TestStore()
        let first = Make.person(store.context, given: "One", family: "Alpha", contactIdentifier: "ID-1")
        let second = Make.person(store.context, given: "Two", family: "Bravo")
        // No @Attribute(.unique) anywhere in the schema — CloudKit forbids it.
        second.contactIdentifier = first.contactIdentifier
        try store.save()

        let duplicates = try store.fetch(Person.self).filter { $0.contactIdentifier == "ID-1" }
        #expect(duplicates.count == 2)
    }

    // MARK: - Shared tags

    @Test("A tag shared by two people reports both in peopleList")
    func sharedTagReportsBothPeople() throws {
        let store = try TestStore()
        let tag = Make.tag(store.context, name: "Close Friend", kind: .personal)
        let first = Make.person(store.context, given: "Ann", family: "Adams")
        let second = Make.person(store.context, given: "Bob", family: "Baker")
        Make.attach(tag, to: first)
        Make.attach(tag, to: second)
        try store.save()

        let tags = try store.fetch(RelationshipTag.self)
        #expect(tags.count == 1)

        let fetched = try #require(tags.first)
        #expect(Set(fetched.peopleList.map(\.id)) == Set([first.id, second.id]))
        #expect(first.hasTag(named: "Close Friend"))
        #expect(second.hasTag(named: "Close Friend"))
    }

    @Test("Deleting a shared tag leaves both people intact")
    func deletingTagKeepsPeople() throws {
        let store = try TestStore()
        let tag = Make.tag(store.context, name: "Lead", kind: .business)
        let first = Make.person(store.context, given: "Ann", family: "Adams")
        let second = Make.person(store.context, given: "Bob", family: "Baker")
        Make.attach(tag, to: first)
        Make.attach(tag, to: second)
        try store.save()

        store.context.delete(tag)
        try store.save()

        let remainingTags = try store.fetch(RelationshipTag.self)
        let remainingPeople = try store.fetch(Person.self)

        #expect(remainingTags.isEmpty)
        #expect(remainingPeople.count == 2)
        #expect(first.tagList.isEmpty)
        #expect(second.tagList.isEmpty)
    }
}

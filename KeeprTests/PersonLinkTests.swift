import Foundation
import SwiftData
import Testing

@testable import Keepr

/// A link joins two people with one record, and the label has to read correctly
/// from both ends. Getting the orientation wrong would tell someone their mother
/// is their child — quietly, on a screen they'd trust.
@Suite("Person links")
@MainActor
struct PersonLinkTests {

    private func link(
        _ store: TestStore,
        _ a: Person,
        _ b: Person,
        role: LinkRole
    ) -> PersonLink {
        let link = PersonLink(
            personA: a,
            personB: b,
            labelAToB: role.name,
            labelBToA: role.inverse
        )
        store.context.insert(link)
        return link
    }

    @Test("A link reads one way from each end")
    func linkReadsBothWays() throws {
        let store = try TestStore()
        let alex = Make.person(store.context, given: "Alex", family: "Nguyen")
        let linda = Make.person(store.context, given: "Linda", family: "Clark")
        _ = link(store, alex, linda, role: LinkRole.named("Parent"))
        try store.save()

        let fromAlex = try #require(alex.connections.first)
        #expect(fromAlex.person.id == linda.id)
        #expect(fromAlex.label == "Parent")

        let fromLinda = try #require(linda.connections.first)
        #expect(fromLinda.person.id == alex.id)
        #expect(fromLinda.label == "Child")
    }

    @Test("One record serves both ends rather than two mirrored rows")
    func oneRecordPerPair() throws {
        let store = try TestStore()
        let alex = Make.person(store.context, given: "Alex", family: "Nguyen")
        let linda = Make.person(store.context, given: "Linda", family: "Clark")
        _ = link(store, alex, linda, role: LinkRole.named("Parent"))
        try store.save()

        #expect(try store.fetch(PersonLink.self).count == 1)
        #expect(alex.connections.count == 1)
        #expect(linda.connections.count == 1)
    }

    @Test("Linking is recognized from either side, so the same pair isn't offered twice")
    func isLinkedIsSymmetric() throws {
        let store = try TestStore()
        let one = Make.person(store.context, given: "One", family: "Person")
        let two = Make.person(store.context, given: "Two", family: "Person")
        _ = link(store, one, two, role: LinkRole.named("Friend"))
        try store.save()

        #expect(one.isLinked(to: two))
        #expect(two.isLinked(to: one))

        let three = Make.person(store.context, given: "Three", family: "Person")
        #expect(!one.isLinked(to: three))
    }

    @Test("Deleting a person takes their links with them, leaving no dangling rows")
    func deletingAPersonRemovesTheirLinks() throws {
        let store = try TestStore()
        let one = Make.person(store.context, given: "One", family: "Person")
        let two = Make.person(store.context, given: "Two", family: "Person")
        _ = link(store, one, two, role: LinkRole.named("Colleague"))
        try store.save()

        store.context.delete(one)
        try store.save()

        #expect(try store.fetch(PersonLink.self).isEmpty)
        #expect(two.connections.isEmpty)
    }

    @Test("Unlinking removes the connection from both profiles")
    func unlinkingClearsBothEnds() throws {
        let store = try TestStore()
        let one = Make.person(store.context, given: "One", family: "Person")
        let two = Make.person(store.context, given: "Two", family: "Person")
        let record = link(store, one, two, role: LinkRole.named("Manager"))
        try store.save()

        // "one's manager is two" reads back as two's direct report.
        #expect(one.connections.first?.label == "Manager")
        #expect(two.connections.first?.label == "Direct Report")

        store.context.delete(record)
        try store.save()

        #expect(one.connections.isEmpty)
        #expect(two.connections.isEmpty)
    }

    @Test("Connections are listed alphabetically, not in insertion order")
    func connectionsAreSorted() throws {
        let store = try TestStore()
        let hub = Make.person(store.context, given: "Hub", family: "Person")
        let zane = Make.person(store.context, given: "Zane", family: "Zimmer")
        let abby = Make.person(store.context, given: "Abby", family: "Adams")
        _ = link(store, hub, zane, role: LinkRole.named("Friend"))
        _ = link(store, abby, hub, role: LinkRole.named("Friend"))
        try store.save()

        #expect(hub.connections.map(\.person.familyName) == ["Adams", "Zimmer"])
    }
}

/// The role catalog is what stops "Manager" showing up as "Manager" on the
/// employee's profile too.
@Suite("Link roles")
struct LinkRoleTests {

    @Test("Asymmetric roles carry the other word")
    func asymmetricRolesInvert() {
        #expect(LinkRole.named("Parent").inverse == "Child")
        #expect(LinkRole.named("Child").inverse == "Parent")
        #expect(LinkRole.named("Manager").inverse == "Direct Report")
        #expect(LinkRole.named("Referred By").inverse == "Referred")
    }

    @Test("Symmetric roles read the same from both ends")
    func symmetricRolesStayPut() {
        for name in ["Spouse", "Sibling", "Friend", "Colleague", "Neighbor"] {
            let role = LinkRole.named(name)
            #expect(role.isSymmetric, "\(name) should read the same from both ends")
        }
    }

    @Test("A typed-in role is symmetric rather than inventing an opposite")
    func customRolesAreSymmetric() {
        let role = LinkRole.custom("Training Partner")
        #expect(role.name == "Training Partner")
        #expect(role.inverse == "Training Partner")
    }

    @Test("Contact card labels map onto roles")
    func contactLabelsMap() {
        #expect(LinkRole.forContactLabel("mother").name == "Parent")
        #expect(LinkRole.forContactLabel("Mother").name == "Parent")
        #expect(LinkRole.forContactLabel("daughter").name == "Child")
        #expect(LinkRole.forContactLabel("wife").name == "Spouse")
        #expect(LinkRole.forContactLabel("brother").name == "Sibling")
    }

    @Test("An unknown label is kept as written rather than forced into Family")
    func unknownLabelsSurviveIntact() {
        let role = LinkRole.forContactLabel("godmother")
        #expect(role.name == "Godmother")
        #expect(role.isSymmetric)
    }

    @Test("Reversing a role swaps the two words")
    func reversingSwaps() {
        let reversed = LinkRole.named("Parent").reversed
        #expect(reversed.name == "Child")
        #expect(reversed.inverse == "Parent")
    }
}

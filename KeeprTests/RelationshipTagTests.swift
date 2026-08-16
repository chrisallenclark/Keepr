import Foundation
import SwiftData
import Testing

@testable import Keepr

/// Built-in relationship types can be renamed by the user, which makes any
/// lookup that goes by visible name a latent duplicate bug.
@Suite("Relationship types")
@MainActor
struct RelationshipTagTests {

    @Test("Seeding gives every built-in a stable key matching its original name")
    func seedingAssignsBuiltInKeys() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let tags = try store.fetch(RelationshipTag.self)
        #expect(tags.count == RelationshipTag.builtInCatalog.count)
        #expect(tags.allSatisfy { $0.isBuiltIn })
        #expect(tags.allSatisfy { $0.builtInKey != nil })

        let family = try #require(tags.first { $0.builtInKey == "Family" })
        #expect(family.name == "Family")
        #expect(family.kind == .personal)
    }

    @Test("Seeding runs once, not once per launch")
    func seedingIsIdempotent() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let tags = try store.fetch(RelationshipTag.self)
        #expect(tags.count == RelationshipTag.builtInCatalog.count)
    }

    @Test("A built-in the user deleted does not come back on the next launch")
    func deletedBuiltInsStayDeleted() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let vendor = try #require(
            try store.fetch(RelationshipTag.self).first { $0.builtInKey == "Vendor" }
        )
        store.context.delete(vendor)
        try store.save()

        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let vendorIsBack = try store.fetch(RelationshipTag.self)
            .contains { $0.builtInKey == "Vendor" }
        #expect(vendorIsBack == false)
    }

    @Test("A built-in added in a later version reaches an install that predates it")
    func newBuiltInsArriveOnUpdate() throws {
        let store = try TestStore()

        // An install seeded before "Mentor", "Advisor" and "Candidate" existed:
        // tags in the store, nothing recorded in defaults.
        for (index, builtIn) in RelationshipTag.builtInCatalog.prefix(4).enumerated() {
            store.context.insert(
                RelationshipTag(
                    name: builtIn.name,
                    kind: builtIn.kind,
                    isBuiltIn: true,
                    sortOrder: index * 10,
                    symbolName: builtIn.symbolName,
                    builtInKey: builtIn.name
                )
            )
        }
        try store.save()

        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let keys = Set(try store.fetch(RelationshipTag.self).compactMap(\.builtInKey))
        #expect(keys.count == RelationshipTag.builtInCatalog.count)
        #expect(keys.contains("Mentor"))
        #expect(
            try store.fetch(RelationshipTag.self).filter { $0.builtInKey == "Family" }.count == 1,
            "nothing already present is duplicated"
        )
    }

    /// The shipped bug: `builtInKey` was added to the model after the first
    /// releases, so rows seeded before it carry nil. Seeding matched on that key
    /// alone, decided all thirteen built-ins were missing, and inserted a second
    /// copy of every one of them.
    @Test("An install seeded before keys existed doesn't get a second copy of everything")
    func keylessBuiltInsAreRecognized() throws {
        let store = try TestStore()

        for (index, builtIn) in RelationshipTag.builtInCatalog.prefix(13).enumerated() {
            let tag = RelationshipTag(
                name: builtIn.name,
                kind: builtIn.kind,
                isBuiltIn: true,
                sortOrder: index * 10,
                symbolName: builtIn.symbolName
            )
            store.context.insert(tag)
        }
        try store.save()

        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let tags = try store.fetch(RelationshipTag.self)
        #expect(tags.count == RelationshipTag.builtInCatalog.count, "no second set")
        #expect(tags.allSatisfy { $0.builtInKey != nil }, "old rows are given their key")

        let names = tags.map(\.name)
        #expect(Set(names).count == names.count, "and no name appears twice")
    }

    @Test("Duplicates already on the device are merged, and people keep their type")
    func duplicatesAreMergedOnLaunch() throws {
        let store = try TestStore()

        _ = Make.tag(store.context, name: "Vendor", isBuiltIn: true)
        let inUse = Make.tag(store.context, name: "Vendor", isBuiltIn: true)
        let person = Make.person(store.context, given: "Priya", family: "Raman")
        Make.attach(inUse, to: person)
        try store.save()

        KeeprStore.repairTagCatalog(store.context)

        let vendors = try store.fetch(RelationshipTag.self).filter { $0.name == "Vendor" }
        #expect(vendors.count == 1)
        #expect(vendors.first?.id == inUse.id, "the copy people are actually marked with survives")
        #expect(person.tagList.count == 1, "the person is moved, not left with a deleted type")
        #expect(person.hasTag(named: "Vendor"))
    }

    @Test("Someone marked with both copies ends up with one")
    func mergingDoesNotDoubleMark() throws {
        let store = try TestStore()

        let first = Make.tag(store.context, name: "Lead", isBuiltIn: true)
        let second = Make.tag(store.context, name: "Lead", isBuiltIn: true)
        let person = Make.person(store.context, given: "Dana", family: "Whitfield")
        Make.attach(first, to: person)
        Make.attach(second, to: person)
        try store.save()

        KeeprStore.repairTagCatalog(store.context)

        #expect(person.tagList.count == 1)
        #expect(person.hasTag(named: "Lead"))
    }

    @Test("Two types that only differ by kind are left alone")
    func sameNameDifferentKindIsNotADuplicate() throws {
        let store = try TestStore()

        _ = Make.tag(store.context, name: "Gym", kind: .business)
        _ = Make.tag(store.context, name: "Gym", kind: .personal)
        try store.save()

        KeeprStore.repairTagCatalog(store.context)

        #expect(try store.fetch(RelationshipTag.self).count == 2)
    }

    @Test("Deleting sticks even on an install that needed no seeding")
    func deletionSticksWithoutAnyInsert() throws {
        let store = try TestStore()

        // Everything already present, nothing recorded: the state of an install
        // that upgraded into this bookkeeping.
        for builtIn in RelationshipTag.builtInCatalog {
            store.context.insert(
                RelationshipTag(
                    name: builtIn.name,
                    kind: builtIn.kind,
                    isBuiltIn: true,
                    symbolName: builtIn.symbolName,
                    builtInKey: builtIn.name
                )
            )
        }
        try store.save()

        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let team = try #require(
            try store.fetch(RelationshipTag.self).first { $0.builtInKey == "Team" }
        )
        store.context.delete(team)
        try store.save()

        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let teamIsBack = try store.fetch(RelationshipTag.self).contains { $0.name == "Team" }
        #expect(teamIsBack == false)
    }

    @Test("Erasing everything gives the built-ins back")
    func wipingRestoresTheCatalog() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        KeeprStore.deleteAllData(in: store.context, defaults: store.defaults)

        #expect(try store.fetch(RelationshipTag.self).count == RelationshipTag.builtInCatalog.count)
    }

    @Test("A renamed built-in is still found, rather than duplicated")
    func renamedBuiltInIsStillFound() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let family = try #require(
            try store.fetch(RelationshipTag.self).first { $0.builtInKey == "Family" }
        )
        family.name = "Familia"
        try store.save()

        // What contact import does when it decides someone is family.
        let resolved = KeeprStore.tag(named: "Family", kind: .personal, in: store.context)
        try store.save()

        #expect(resolved.id == family.id)
        #expect(resolved.name == "Familia")

        let personalTags = try store.fetch(RelationshipTag.self).filter { $0.kind == .personal }
        let familyLike = personalTags.filter { $0.builtInKey == "Family" || $0.name == "Family" }
        #expect(familyLike.count == 1, "renaming a built-in must not spawn a second one")
    }

    @Test("A tag the user deleted is recreated rather than crashing an import")
    func missingTagIsRecreated() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context, defaults: store.defaults)

        let vendor = try #require(
            try store.fetch(RelationshipTag.self).first { $0.builtInKey == "Vendor" }
        )
        store.context.delete(vendor)
        try store.save()

        let recreated = KeeprStore.tag(named: "Vendor", kind: .business, in: store.context)
        try store.save()

        #expect(recreated.name == "Vendor")
        #expect(try store.fetch(RelationshipTag.self).filter { $0.name == "Vendor" }.count == 1)
    }

    @Test("A user-created type carries no built-in key and can coexist by name across kinds")
    func customTagsAreDistinctPerKind() throws {
        let store = try TestStore()

        let business = KeeprStore.tag(named: "Gym", kind: .business, in: store.context)
        let personal = KeeprStore.tag(named: "Gym", kind: .personal, in: store.context)
        try store.save()

        #expect(business.id != personal.id)
        #expect(business.builtInKey == nil)
        #expect(business.isBuiltIn == false)
    }
}

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
        KeeprStore.seedIfNeeded(store.context)

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
        KeeprStore.seedIfNeeded(store.context)
        KeeprStore.seedIfNeeded(store.context)

        let tags = try store.fetch(RelationshipTag.self)
        #expect(tags.count == RelationshipTag.builtInCatalog.count)
    }

    @Test("A renamed built-in is still found, rather than duplicated")
    func renamedBuiltInIsStillFound() throws {
        let store = try TestStore()
        KeeprStore.seedIfNeeded(store.context)

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
        KeeprStore.seedIfNeeded(store.context)

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

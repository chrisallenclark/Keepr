import Foundation
import SwiftData

/// Turning an Apple contact into a Keepr person, in one place.
///
/// Two screens do this now — the contact importer and the review queue — and
/// they must agree exactly: same fields copied, same memories created, same
/// suggestion applied. Two near-identical copies is how one of them quietly
/// stops copying birthdays.
@MainActor
enum PersonImporter {

    /// Creates the person and copies across everything the card already knows.
    @discardableResult
    static func makePerson(
        from contact: ContactSummary,
        context relationship: RelationshipContext,
        in modelContext: ModelContext,
        suggestion: CategorySuggestion? = nil
    ) -> Person {
        let person = Person(
            givenName: suggestion?.cleanedGivenName ?? contact.givenName,
            familyName: suggestion?.cleanedFamilyName ?? contact.familyName,
            context: relationship,
            contactIdentifier: contact.identifier,
            company: contact.organizationName,
            jobTitle: contact.jobTitle,
            phoneNumbers: contact.phoneNumbers,
            emailAddresses: contact.emailAddresses,
            photoData: contact.thumbnailData
        )
        modelContext.insert(person)

        person.birthday = contact.birthday
        person.postalAddress = contact.postalAddress
        person.contactNote = contact.note

        return person
    }

    /// Facts already sitting on the contact card become memories, so a profile
    /// isn't empty the moment someone is imported. Only things the user typed
    /// themselves — nothing inferred.
    static func addMemories(
        from contact: ContactSummary,
        to person: Person,
        in modelContext: ModelContext
    ) {
        if let birthday = contact.birthday {
            let formatted = birthday.formatted(.dateTime.month(.wide).day())
            modelContext.insert(
                Memory(
                    content: "Birthday: \(formatted)",
                    category: .importantDate,
                    importance: .high,
                    person: person
                )
            )
        }

        for relation in contact.relations {
            modelContext.insert(
                Memory(
                    content: "\(relation.label.capitalized): \(relation.name)",
                    category: .family,
                    person: person
                )
            )
        }

        if let note = contact.note, !note.isEmpty {
            modelContext.insert(
                Memory(content: note, category: .other, person: person)
            )
        }
    }

    /// Applies a suggested category. Adds types and groups rather than replacing
    /// what's there — a suggestion is never allowed to remove a decision the
    /// user made.
    static func apply(
        _ suggestion: CategorySuggestion,
        to person: Person,
        in modelContext: ModelContext,
        groups: [PersonGroup] = []
    ) {
        person.context = suggestion.context

        let kind: TagKind = suggestion.context == .personal ? .personal : .business
        for name in ([suggestion.tagName].compactMap { $0 } + suggestion.extraTagNames) {
            let tag = KeeprStore.tag(named: name, kind: kind, in: modelContext)
            guard !person.tagList.contains(where: { $0.id == tag.id }) else { continue }
            person.tags = person.tagList + [tag]
        }

        for id in suggestion.groupIDs {
            guard let group = groups.first(where: { $0.id.uuidString == id }),
                  !person.isMember(of: group)
            else { continue }
            person.groups = person.groupList + [group]
        }
    }

    /// Records what the contact card actually said, when the shorthand was
    /// removed from the name.
    ///
    /// The cleanup is only safe because it's reversible by reading: the original
    /// is written to the profile as a memory, and the iPhone contact card itself
    /// is never touched.
    static func noteOriginalName(
        _ suggestion: CategorySuggestion,
        for person: Person,
        in modelContext: ModelContext
    ) {
        guard let original = suggestion.originalName else { return }
        modelContext.insert(
            Memory(
                content: "Contact card says: \(original)",
                category: .other,
                importance: .low,
                person: person
            )
        )
    }
}

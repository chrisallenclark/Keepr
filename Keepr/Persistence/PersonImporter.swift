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
        in modelContext: ModelContext
    ) -> Person {
        let person = Person(
            givenName: contact.givenName,
            familyName: contact.familyName,
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

    /// Applies a suggested category. Adds the type rather than replacing what's
    /// there — a suggestion is never allowed to remove a decision the user made.
    static func apply(
        _ suggestion: CategorySuggestion,
        to person: Person,
        in modelContext: ModelContext
    ) {
        person.context = suggestion.context

        guard let tagName = suggestion.tagName else { return }
        let kind: TagKind = suggestion.context == .personal ? .personal : .business
        let tag = KeeprStore.tag(named: tagName, kind: kind, in: modelContext)

        guard !person.tagList.contains(where: { $0.id == tag.id }) else { return }
        person.tags = person.tagList + [tag]
    }
}

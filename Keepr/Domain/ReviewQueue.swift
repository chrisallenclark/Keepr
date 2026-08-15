import Foundation

/// One person waiting to be classified.
///
/// The queue deliberately mixes two things the user experiences as one job:
/// someone whose number they saved on Saturday and never filed, and someone
/// imported months ago who never got a relationship type. Both are "I don't
/// know what this person is to me yet", and splitting them across two screens
/// would just mean one of them never gets done.
struct ReviewItem: Identifiable {

    enum Subject {
        /// In the phone's contacts, not yet in Keepr.
        case newContact(ContactSummary)
        /// Already in Keepr, with no relationship type on them.
        case existing(Person)
    }

    let subject: Subject
    let suggestion: CategorySuggestion?

    var id: String {
        switch subject {
        case let .newContact(contact): "contact-\(contact.identifier)"
        case let .existing(person): "person-\(person.id)"
        }
    }

    var name: String {
        switch subject {
        case let .newContact(contact): contact.fullName
        case let .existing(person): person.displayName
        }
    }

    var detail: String? {
        switch subject {
        case let .newContact(contact): contact.subtitle
        case let .existing(person): person.subtitle
        }
    }

    var isNew: Bool {
        if case .newContact = subject { return true }
        return false
    }
}

/// Builds the "needs categorizing" queue.
///
/// Pure input → output so the rules can be tested without a store, a contact
/// database, or a running app.
enum ReviewQueue {

    /// Someone already in Keepr counts as uncategorized when they carry no
    /// relationship type at all. Groups don't rescue them: being "from the gym"
    /// says where you met, not what they are to you.
    static func needsCategorizing(_ person: Person) -> Bool {
        person.status == .active && person.tagList.isEmpty && person.reviewedAt == nil
    }

    /// New contacts first — those are the ones where the memory is still fresh
    /// and the answer is about to be lost. Then the backlog, newest first.
    static func items(
        newContacts: [ContactSummary],
        people: [Person],
        vocabulary: MarkerVocabulary = MarkerVocabulary()
    ) -> [ReviewItem] {
        // Someone already imported is never "new", even if the tracker hasn't
        // caught up — they belong in the backlog or nowhere.
        let linked = Set(people.compactMap(\.contactIdentifier))

        let fresh = newContacts
            .filter { !linked.contains($0.identifier) }
            .map { (contact: ContactSummary) in
                ReviewItem(
                    subject: .newContact(contact),
                    suggestion: ContactCategorizer.suggestion(for: contact, vocabulary: vocabulary)
                )
            }

        let backlog = people
            .filter(needsCategorizing)
            .sorted { $0.createdAt > $1.createdAt }
            .map { (person: Person) in
                ReviewItem(
                    subject: .existing(person),
                    suggestion: ContactCategorizer.suggestion(for: person)
                )
            }

        return fresh + backlog
    }

    /// One line for a banner: what's waiting, in words rather than a bare count.
    static func summary(newCount: Int, backlogCount: Int) -> String? {
        switch (newCount, backlogCount) {
        case (0, 0):
            return nil
        case let (new, 0):
            return new == 1
                ? "1 new contact since you last looked"
                : "\(new) new contacts since you last looked"
        case let (0, backlog):
            return backlog == 1
                ? "1 person has no relationship type yet"
                : "\(backlog) people have no relationship type yet"
        case let (new, backlog):
            let newText = new == 1 ? "1 new contact" : "\(new) new contacts"
            let backlogText = backlog == 1 ? "1 more" : "\(backlog) more"
            return "\(newText), and \(backlogText) to categorize"
        }
    }
}

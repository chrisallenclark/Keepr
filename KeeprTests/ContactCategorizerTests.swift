import Foundation
import Testing

@testable import Keepr

/// Auto-categorization decides where imported people land, and a wrong guess
/// that arrives pre-selected gets accepted in bulk. These tests pin the rules
/// that make it guess — and, more importantly, the ones that make it decline.
@Suite("Contact categorization")
struct ContactCategorizerTests {

    private func contact(
        given: String = "Test",
        family: String = "Person",
        organization: String? = nil,
        jobTitle: String? = nil,
        emails: [String] = [],
        relations: [ContactRelation] = []
    ) -> ContactSummary {
        var summary = ContactSummary(
            identifier: UUID().uuidString,
            givenName: given,
            familyName: family,
            organizationName: organization,
            jobTitle: jobTitle,
            phoneNumbers: [],
            emailAddresses: emails,
            thumbnailData: nil
        )
        summary.relations = relations
        return summary
    }

    // MARK: - Declining to guess

    @Test("A bare name and phone number produces no suggestion at all")
    func noSignalMeansNoGuess() {
        #expect(ContactCategorizer.suggestion(for: contact()) == nil)
    }

    @Test("A consumer email address is not evidence of anything")
    func consumerEmailIsNotASignal() {
        for provider in ["gmail.com", "icloud.com", "yahoo.com", "outlook.com", "proton.me"] {
            let summary = contact(emails: ["someone@\(provider)"])
            #expect(
                ContactCategorizer.suggestion(for: summary) == nil,
                "\(provider) should not imply a business relationship"
            )
        }
    }

    // MARK: - Business

    @Test("An employer makes it business")
    func employerImpliesBusiness() throws {
        let summary = contact(organization: "Coastal Design Co.")
        let suggestion = try #require(ContactCategorizer.suggestion(for: summary))

        #expect(suggestion.context == .business)
        #expect(suggestion.tagName == "Professional Contact")
        #expect(suggestion.reason == "Works at Coastal Design Co.")
        #expect(suggestion.confidence == .high)
    }

    @Test("An owner-shaped job title suggests a potential client, not a colleague")
    func ownerTitlesSuggestALead() throws {
        for title in ["Owner", "Founder", "CEO", "President", "Managing Partner"] {
            let summary = contact(organization: "Martinez Roofing", jobTitle: title)
            let suggestion = try #require(ContactCategorizer.suggestion(for: summary))
            #expect(suggestion.tagName == "Potential Client", "\(title) should read as a lead")
        }
    }

    @Test("A regular job title stays a professional contact")
    func regularTitlesStayProfessional() throws {
        let summary = contact(organization: "Reed PT", jobTitle: "Physical Therapist")
        let suggestion = try #require(ContactCategorizer.suggestion(for: summary))

        #expect(suggestion.tagName == "Professional Contact")
        #expect(suggestion.reason == "Physical Therapist at Reed PT")
    }

    @Test("A work email domain implies an employer the card didn't name")
    func workEmailImpliesBusiness() throws {
        let summary = contact(emails: ["jake@martinezroofing.com"])
        let suggestion = try #require(ContactCategorizer.suggestion(for: summary))

        #expect(suggestion.context == .business)
        #expect(suggestion.reason == "Work email at martinezroofing.com")
        // Weaker evidence than a stated employer, and labelled as such.
        #expect(suggestion.confidence == .medium)
    }

    @Test("A consumer address alongside a work one doesn't hide the work domain")
    func mixedEmailsFindTheWorkDomain() {
        #expect(
            ContactCategorizer.workEmailDomain(in: ["me@gmail.com", "me@acme.co"]) == "acme.co"
        )
        #expect(ContactCategorizer.workEmailDomain(in: ["me@gmail.com"]) == nil)
        #expect(ContactCategorizer.workEmailDomain(in: ["not-an-email"]) == nil)
    }

    // MARK: - Personal

    @Test("A family relation on the card outranks an employer")
    func familyBeatsEmployer() throws {
        let summary = contact(
            organization: "Some Company",
            jobTitle: "Manager",
            relations: [ContactRelation(label: "mother", name: "Linda Clark")]
        )
        let suggestion = try #require(ContactCategorizer.suggestion(for: summary))

        #expect(suggestion.context == .personal)
        #expect(suggestion.tagName == "Family")
        #expect(suggestion.reason == "Listed as mother on the contact card")
        #expect(suggestion.confidence == .high)
    }

    @Test("Every family relation label is recognised", arguments: [
        "mother", "father", "brother", "sister", "spouse", "wife", "husband",
        "son", "daughter", "parent", "child", "partner"
    ])
    func familyLabels(label: String) throws {
        let summary = contact(relations: [ContactRelation(label: label, name: "Someone")])
        let suggestion = try #require(ContactCategorizer.suggestion(for: summary))
        #expect(suggestion.tagName == "Family")
    }

    @Test("An unrelated relation label like 'assistant' isn't treated as family")
    func nonFamilyRelationsAreIgnored() {
        let summary = contact(relations: [ContactRelation(label: "assistant", name: "Someone")])
        #expect(ContactCategorizer.suggestion(for: summary) == nil)
    }

    // MARK: - Batch

    @Test("Batch suggestions key by identifier and omit contacts that can't be placed")
    func batchOmitsUnplaceable() {
        let placeable = contact(given: "Sarah", organization: "Coastal Design Co.")
        let unplaceable = contact(given: "Alex")

        let result = ContactCategorizer.suggestions(for: [placeable, unplaceable])

        #expect(result.count == 1)
        #expect(result[placeable.identifier]?.context == .business)
        #expect(result[unplaceable.identifier] == nil)
    }
}

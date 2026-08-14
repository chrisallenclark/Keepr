import Foundation

/// Where a search query matched. Ranked in this order.
enum SearchField: Int, CaseIterable, Comparable {
    case name
    case company
    case tag
    case memory
    case interaction
    case note
    case followUp

    static func < (lhs: SearchField, rhs: SearchField) -> Bool {
        lhs.rawValue < rhs.rawValue
    }

    var label: String {
        switch self {
        case .name: "Name"
        case .company: "Company"
        case .tag: "Tag"
        case .memory: "Memory"
        case .interaction: "Interaction"
        case .note: "Note"
        case .followUp: "Follow-up"
        }
    }

    /// Section title. Identity-ish matches read as one group.
    var groupTitle: String {
        switch self {
        case .name, .company, .tag: "People"
        case .memory: "Memories"
        case .interaction: "Interactions"
        case .note: "Notes"
        case .followUp: "Follow-ups"
        }
    }

    var symbolName: String {
        switch self {
        case .name: "person"
        case .company: "building.2"
        case .tag: "tag"
        case .memory: "lightbulb"
        case .interaction: "bubble.left.and.bubble.right"
        case .note: "note.text"
        case .followUp: "bell"
        }
    }
}

/// One person, plus why they matched.
struct SearchResult: Identifiable {
    let person: Person
    let field: SearchField
    /// The line of text that matched, for the result's second line.
    let snippet: String?

    var id: UUID { person.id }
}

/// Results that matched for the same reason, shown as one section.
struct SearchSection: Identifiable {
    let field: SearchField
    let results: [SearchResult]

    var id: Int { field.rawValue }
    var title: String { field.groupTitle }
}

/// Literal, on-device search across everything the user has recorded.
///
/// Deliberately simple: one query string, scanned over pre-joined text. The
/// shape — score each person, return the best reason they matched — is what a
/// future embedding-based ranker would slot into, so callers won't change.
enum SearchEngine {

    /// Search ignores the Business/Personal switch: when you're looking for
    /// someone, you're looking for *someone*.
    static func search(_ query: String, in people: [Person], limit: Int = 60) -> [SearchResult] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 1 else { return [] }

        let results = people.compactMap { bestMatch(for: $0, query: trimmed) }

        return Array(
            results
                .sorted { lhs, rhs in
                    if lhs.field != rhs.field { return lhs.field < rhs.field }
                    return lhs.person.sortKey < rhs.person.sortKey
                }
                .prefix(limit)
        )
    }

    /// The highest-ranked field this person matched on, if any.
    static func bestMatch(for person: Person, query: String) -> SearchResult? {
        func contains(_ text: String?) -> Bool {
            guard let text, !text.isEmpty else { return false }
            return text.localizedStandardContains(query)
        }

        if contains(person.fullName) || contains(person.preferredName) {
            return SearchResult(person: person, field: .name, snippet: person.subtitle)
        }
        if contains(person.company) || contains(person.jobTitle) {
            return SearchResult(person: person, field: .company, snippet: person.subtitle)
        }
        if let tag = person.tagList.first(where: { contains($0.name) }) {
            return SearchResult(person: person, field: .tag, snippet: tag.name)
        }
        if let memory = person.memoryList.first(where: { contains($0.content) }) {
            return SearchResult(person: person, field: .memory, snippet: memory.content)
        }
        if let interaction = person.interactionList.first(where: { contains($0.searchableText) }) {
            return SearchResult(person: person, field: .interaction, snippet: interaction.headline)
        }
        if contains(person.notes) || contains(person.howWeMet) || contains(person.introducedBy) {
            let snippet = contains(person.notes) ? person.notes : person.howWeMet
            return SearchResult(person: person, field: .note, snippet: snippet)
        }
        if let followUp = person.followUpList.first(where: { contains($0.title) || contains($0.note) }) {
            return SearchResult(person: person, field: .followUp, snippet: followUp.title)
        }
        return nil
    }

    /// Groups results by why they matched, for sectioned display.
    static func grouped(_ results: [SearchResult]) -> [SearchSection] {
        var buckets: [SearchField: [SearchResult]] = [:]
        for result in results {
            buckets[result.field, default: []].append(result)
        }
        return SearchField.allCases.compactMap { field in
            guard let items = buckets[field], !items.isEmpty else { return nil }
            return SearchSection(field: field, results: items)
        }
    }
}

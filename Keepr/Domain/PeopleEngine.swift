import Foundation

/// How the People list is ordered.
enum PeopleSort: String, CaseIterable, Identifiable, Codable, Sendable {
    case recent
    case name
    case needsFollowUp
    case priority
    case newest

    var id: String { rawValue }

    var title: String {
        switch self {
        case .recent: "Recent"
        case .name: "Name"
        case .needsFollowUp: "Needs Follow-Up"
        case .priority: "Priority"
        case .newest: "Newest"
        }
    }

    var symbolName: String {
        switch self {
        case .recent: "clock"
        case .name: "textformat"
        case .needsFollowUp: "bell"
        case .priority: "star"
        case .newest: "sparkles"
        }
    }
}

/// One A–Z section of the People list.
struct PersonSection: Identifiable {
    let key: String
    let people: [Person]

    var id: String { key }
}

/// Pure filtering and sorting for the People list.
///
/// Deliberately free of SwiftData queries so every rule here is unit-testable
/// and the same helpers can serve Today, People and Search.
enum PeopleEngine {

    /// People visible in the given mode: matching context, not archived.
    static func visible(_ people: [Person], in mode: ContextMode) -> [Person] {
        people.filter { $0.status == .active && $0.context.matches(mode) }
    }

    /// Applies the optional tag filter and free-text query on top of `visible`.
    static func filter(
        _ people: [Person],
        mode: ContextMode,
        tagName: String? = nil,
        query: String = "",
        favoritesOnly: Bool = false
    ) -> [Person] {
        var result = visible(people, in: mode)

        if let tagName {
            result = result.filter { $0.hasTag(named: tagName) }
        }
        if favoritesOnly {
            result = result.filter(\.isFavorite)
        }

        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            result = result.filter { matches($0, query: trimmed) }
        }
        return result
    }

    /// Lightweight name/company/tag match used by the People search field.
    static func matches(_ person: Person, query: String) -> Bool {
        let haystack = [
            person.fullName,
            person.preferredName ?? "",
            person.company ?? "",
            person.jobTitle ?? "",
            person.tagList.map(\.name).joined(separator: " ")
        ].joined(separator: " ")
        return haystack.localizedStandardContains(query)
    }

    static func sort(_ people: [Person], by sort: PeopleSort) -> [Person] {
        switch sort {
        case .name:
            people.sorted { lhs, rhs in
                let a = lhs.sortKey, b = rhs.sortKey
                return a == b ? lhs.displayName < rhs.displayName : a < b
            }
        case .recent:
            // Never-contacted people sort last rather than pretending to be ancient.
            people.sorted { lhs, rhs in
                switch (lhs.lastInteractionAt, rhs.lastInteractionAt) {
                case let (l?, r?): return l > r
                case (nil, _?): return false
                case (_?, nil): return true
                case (nil, nil): return lhs.sortKey < rhs.sortKey
                }
            }
        case .needsFollowUp:
            people.sorted { lhs, rhs in
                switch (lhs.nextFollowUp?.dueDate, rhs.nextFollowUp?.dueDate) {
                case let (l?, r?): return l < r
                case (nil, _?): return false
                case (_?, nil): return true
                case (nil, nil): return lhs.sortKey < rhs.sortKey
                }
            }
        case .priority:
            people.sorted { lhs, rhs in
                if lhs.isFavorite != rhs.isFavorite { return lhs.isFavorite }
                if lhs.priority.weight != rhs.priority.weight {
                    return lhs.priority.weight > rhs.priority.weight
                }
                return lhs.sortKey < rhs.sortKey
            }
        case .newest:
            people.sorted { $0.createdAt > $1.createdAt }
        }
    }

    /// Groups people into A–Z sections. Anything that doesn't start with a
    /// letter lands in "#", which sorts last.
    static func alphabeticalSections(_ people: [Person]) -> [PersonSection] {
        let sorted = sort(people, by: .name)
        var order: [String] = []
        var buckets: [String: [Person]] = [:]

        for person in sorted {
            let first = person.sortKey.first.map(String.init)?.uppercased() ?? "#"
            let key = first.rangeOfCharacter(from: .letters) != nil ? first : "#"
            if buckets[key] == nil {
                buckets[key] = []
                order.append(key)
            }
            buckets[key]?.append(person)
        }

        return order
            .sorted { lhs, rhs in
                if lhs == "#" { return false }
                if rhs == "#" { return true }
                return lhs < rhs
            }
            .map { PersonSection(key: $0, people: buckets[$0] ?? []) }
    }
}

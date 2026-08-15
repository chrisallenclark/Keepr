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

/// One relationship-type section of a place — "Current Client", "Team".
struct PlaceSection: Identifiable {
    let title: String
    let people: [Person]

    var id: String { title }
}

/// The place half of the People filter: a group, nobody's group, or no filter.
///
/// "Unplaced" is a real answer rather than an absence — "who haven't I said
/// anything about yet?" is the question that keeps the rest of it honest.
enum PlaceFilter: Hashable {
    case all
    case group(UUID)
    case unplaced

    /// Matches the id used by `FilterFacet`, so the chip row can stay dumb.
    var facetID: String? {
        switch self {
        case .all: nil
        case let .group(id): id.uuidString
        case .unplaced: FilterFacet.unplacedID
        }
    }

    init(facetID: String?) {
        switch facetID {
        case .none: self = .all
        case FilterFacet.unplacedID: self = .unplaced
        case let .some(raw): self = UUID(uuidString: raw).map { .group($0) } ?? .all
        }
    }
}

/// One tappable filter chip: what it's called, and how many people are behind it
/// *given everything else that's already filtered*.
///
/// The count is the whole point. A chip that says "Clients 5" while you're
/// looking at Life Time answers the question before you tap it, and a chip that
/// would show nothing never appears.
struct FilterFacet: Identifiable, Hashable {
    static let unplacedID = "keepr.unplaced"

    let id: String
    let name: String
    let symbolName: String
    let count: Int
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
        favoritesOnly: Bool = false,
        place: PlaceFilter = .all
    ) -> [Person] {
        var result = visible(people, in: mode)

        if let tagName {
            result = result.filter { $0.hasTag(named: tagName) }
        }
        switch place {
        case .all:
            break
        case let .group(id):
            result = result.filter { person in person.groupList.contains { $0.id == id } }
        case .unplaced:
            result = result.filter { $0.groupList.isEmpty }
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
            person.workNote ?? "",
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

    // MARK: - Facets

    /// Relationship-type chips for a set of people, ordered by the tag order the
    /// user sees everywhere else.
    ///
    /// Empty chips are dropped — offering "Investor 0" is a dead end you have to
    /// tap to discover. `keeping` holds the current selection open even when the
    /// other filter has emptied it, so the user is never stranded on a chip that
    /// vanished under them.
    static func typeFacets(
        for people: [Person],
        tags: [RelationshipTag],
        mode: ContextMode,
        keeping: String? = nil
    ) -> [FilterFacet] {
        tags
            .filter { $0.kind == TagKind(mode: mode) }
            .map { tag in
                FilterFacet(
                    id: tag.name,
                    name: tag.name,
                    symbolName: tag.symbolName,
                    count: people.filter { $0.hasTag(named: tag.name) }.count
                )
            }
            .filter { $0.count > 0 || $0.id == keeping }
    }

    /// Place chips, plus an "Unplaced" chip when there are people who aren't
    /// anywhere yet.
    static func placeFacets(
        for people: [Person],
        groups: [PersonGroup],
        mode: ContextMode,
        keeping: String? = nil
    ) -> [FilterFacet] {
        var facets = groups
            .filter { $0.matches(mode) }
            .map { group in
                FilterFacet(
                    id: group.id.uuidString,
                    name: group.name,
                    symbolName: group.symbolName,
                    count: people.filter { person in
                        person.groupList.contains { $0.id == group.id }
                    }.count
                )
            }
            .filter { $0.count > 0 || $0.id == keeping }

        let unplaced = people.filter { $0.groupList.isEmpty }.count
        if (unplaced > 0 || keeping == FilterFacet.unplacedID), !facets.isEmpty {
            facets.append(
                FilterFacet(
                    id: FilterFacet.unplacedID,
                    name: "No Place",
                    symbolName: "questionmark.circle",
                    count: unplaced
                )
            )
        }
        return facets
    }

    /// Splits people by relationship type, for looking at one place at a time.
    ///
    /// Someone with two types appears under both — Stanley is genuinely a client
    /// *and* an investor, and picking one for him would be inventing an answer.
    /// Anyone with no type at all lands in a final section rather than vanishing.
    static func byType(_ people: [Person]) -> [PlaceSection] {
        var order: [String] = []
        var buckets: [String: [Person]] = [:]

        for person in people {
            let names = person.tagList
                .sorted { $0.sortOrder < $1.sortOrder }
                .map(\.name)

            for name in names.isEmpty ? ["No Type Yet"] : names {
                if buckets[name] == nil {
                    buckets[name] = []
                    order.append(name)
                }
                buckets[name]?.append(person)
            }
        }

        return order
            .sorted { lhs, rhs in
                // The unclassified pile sorts last wherever it appears.
                if lhs == "No Type Yet" { return false }
                if rhs == "No Type Yet" { return true }
                let left = buckets[lhs]?.count ?? 0
                let right = buckets[rhs]?.count ?? 0
                return left == right ? lhs < rhs : left > right
            }
            .map { title in
                PlaceSection(
                    title: title,
                    people: sort(buckets[title] ?? [], by: .name)
                )
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

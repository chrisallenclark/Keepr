import Foundation

/// One category's worth of what you know about someone.
struct MemoryGroup: Identifiable {
    let category: MemoryCategory
    let memories: [Memory]

    var id: String { category.rawValue }
    var count: Int { memories.count }
}

/// Deciding what a profile shows, and what it holds back.
///
/// A brain dump can produce fifteen facts in one go, and fifteen facts printed
/// down a profile is a wall nobody reads — the information is technically there
/// and practically gone. So the profile shows a few, says how much else exists,
/// and puts the rest one tap away. Nothing is ever hidden permanently; it's
/// filed.
enum MemoryEngine {

    /// How many facts a profile shows before deferring to the full list.
    static let headlineLimit = 3

    /// The order categories are shown in: what someone is, then what they want,
    /// then the softer detail. Roughly the order you'd want them in your head
    /// walking into a room.
    static let categoryOrder: [MemoryCategory] = [
        .work, .business, .goals, .family, .importantDate,
        .preferences, .interests, .personal, .other
    ]

    /// The few facts worth printing on the profile itself.
    ///
    /// Deliberately spread across categories before depth: three facts about
    /// someone's kids reads like a filing error, while "runs a roofing crew /
    /// wants to lose 20 lb / daughter's wedding in spring" reads like you know
    /// them. Starred facts still win — those are the ones the user said matter.
    static func headline(_ memories: [Memory], limit: Int = headlineLimit) -> [Memory] {
        let candidates = memories.filter { !$0.isArchived }
        guard !candidates.isEmpty else { return [] }

        let starred = candidates
            .filter { $0.importance == .high }
            .sorted { $0.createdAt > $1.createdAt }

        var chosen: [Memory] = []
        var usedCategories: Set<String> = []

        func take(from pool: [Memory], allowRepeats: Bool) {
            for memory in pool where chosen.count < limit {
                let key = memory.categoryRaw
                if !allowRepeats, usedCategories.contains(key) { continue }
                guard !chosen.contains(where: { $0.id == memory.id }) else { continue }
                chosen.append(memory)
                usedCategories.insert(key)
            }
        }

        let rest = candidates
            .filter { $0.importance != .high }
            .sorted { $0.createdAt > $1.createdAt }

        take(from: starred, allowRepeats: false)
        take(from: rest, allowRepeats: false)
        // Only once every category has had a turn does depth get a look in.
        take(from: starred + rest, allowRepeats: true)

        return chosen
    }

    /// Everything, filed by category, in reading order. Empty categories are
    /// left out rather than shown as zeroes.
    static func grouped(_ memories: [Memory]) -> [MemoryGroup] {
        let visible = memories.filter { !$0.isArchived }

        return categoryOrder.compactMap { category in
            let matching = visible
                .filter { $0.category == category }
                .sorted { lhs, rhs in
                    lhs.importance.weight == rhs.importance.weight
                        ? lhs.createdAt > rhs.createdAt
                        : lhs.importance.weight > rhs.importance.weight
                }
            return matching.isEmpty ? nil : MemoryGroup(category: category, memories: matching)
        }
    }

    /// The category pills under the headline facts: what else is in there, and
    /// how much of it.
    static func summaryCounts(_ memories: [Memory]) -> [MemoryGroup] {
        grouped(memories)
    }

    /// "14 things" / "1 thing" — used on the button that opens the full list.
    static func totalLabel(_ count: Int) -> String {
        count == 1 ? "1 thing" : "\(count) things"
    }
}

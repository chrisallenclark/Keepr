import Foundation

/// A proposed link between two people, with the evidence for it.
struct LinkSuggestion: Identifiable {
    let candidate: Person
    let role: LinkRole
    /// Shown under the name so the guess can be judged rather than trusted.
    let reason: String

    var id: UUID { candidate.id }
}

/// Turns the relations already written on an Apple contact card into proposed
/// links between people who are both already in Keepr.
///
/// The rule is narrow on purpose: a suggestion is only made when the name on the
/// card matches someone in Keepr outright. Fuzzy matching a card that says
/// "Mom" against a contact named "Linda Clark" would be a guess, and a wrong
/// link between two real people is worse than no link at all.
enum LinkSuggester {

    /// Case- and accent-insensitive, whitespace-collapsed. "José  Ruiz" and
    /// "jose ruiz" are the same person; nothing looser than that counts.
    static func normalized(_ name: String) -> String {
        name
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
    }

    /// True when a name off a contact card names this person.
    ///
    /// Both the stored name and the user's own nickname for them count, so a
    /// card saying "Linda Clark" still finds the person filed as "Mom".
    static func matches(_ person: Person, name: String) -> Bool {
        let target = normalized(name)
        guard !target.isEmpty else { return false }
        return [person.fullName, person.displayName]
            .map(normalized)
            .contains(target)
    }

    /// Suggestions for one person, from the relations on their contact card.
    ///
    /// - Parameters:
    ///   - relations: labelled relations read from the card, e.g. mother → Jane Smith.
    ///   - person: the person whose card this is.
    ///   - candidates: everyone already in Keepr.
    static func suggestions(
        from relations: [ContactRelation],
        for person: Person,
        among candidates: [Person]
    ) -> [LinkSuggestion] {
        var used: Set<UUID> = [person.id]
        var results: [LinkSuggestion] = []

        for relation in relations {
            guard let match = candidates.first(where: {
                !used.contains($0.id) && matches($0, name: relation.name)
            }) else { continue }
            guard !person.isLinked(to: match) else { continue }

            used.insert(match.id)
            results.append(
                LinkSuggestion(
                    candidate: match,
                    role: LinkRole.forContactLabel(relation.label),
                    reason: "Listed as \(relation.label.lowercased()) on their contact card"
                )
            )
        }
        return results
    }
}

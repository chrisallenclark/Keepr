import Foundation

/// The things this user calls their groups and relationship types, flattened
/// into something a parser can match text against.
///
/// Built from the user's own records rather than a fixed list, because the
/// shorthand on a contact card is personal: "LT Client" only means Life Time if
/// this user says LT means Life Time.
struct MarkerVocabulary {

    struct Entry: Hashable {
        /// A group's UUID string, or a tag's name.
        let id: String
        /// What to show the user: "Life Time".
        let display: String
        /// Lowercased things that mean this entry, longest first.
        let terms: [String]
        /// Which side a type sits on. Nil for groups, which span both.
        var kind: TagKind?
    }

    var groups: [Entry] = []
    var types: [Entry] = []

    var isEmpty: Bool { groups.isEmpty && types.isEmpty }

    // MARK: Building

    static func build(groups: [PersonGroup], tags: [RelationshipTag]) -> MarkerVocabulary {
        MarkerVocabulary(
            groups: groups.map { group in
                Entry(
                    id: group.id.uuidString,
                    display: group.name,
                    terms: terms(for: group.name, aliases: group.aliases)
                )
            },
            types: tags.map { tag in
                Entry(
                    id: tag.name,
                    display: tag.name,
                    terms: terms(
                        for: tag.name,
                        aliases: tag.aliases,
                        extra: builtInSynonyms[tag.builtInKey ?? ""] ?? []
                    ),
                    kind: tag.kind
                )
            }
        )
    }

    /// Everything that should count as naming this thing: its own name, the
    /// initials of a multi-word name ("Life Time" → "LT"), and anything the user
    /// typed into the aliases field.
    static func terms(for name: String, aliases: String?, extra: [String] = []) -> [String] {
        var result: Set<String> = []

        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !trimmed.isEmpty {
            result.insert(trimmed)
            // "Life Time" also matches "lifetime" — the space is a coin flip.
            let collapsed = trimmed.replacingOccurrences(of: " ", with: "")
            if collapsed != trimmed { result.insert(collapsed) }

            let words = trimmed.split(whereSeparator: { $0 == " " || $0 == "-" })
            if words.count > 1 {
                let initials = words.compactMap(\.first).map(String.init).joined()
                if initials.count >= 2 { result.insert(initials) }
            }
        }

        for alias in (aliases ?? "").split(whereSeparator: { $0 == "," || $0 == ";" }) {
            let value = alias.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if !value.isEmpty { result.insert(value) }
        }
        for value in extra { result.insert(value.lowercased()) }

        // Longest first so "current client" wins over "client".
        return result.sorted { $0.count == $1.count ? $0 < $1 : $0.count > $1.count }
    }

    /// Words people actually write on a card, mapped to the built-in they mean.
    /// Keyed by `builtInKey`, so a renamed built-in still picks them up.
    static let builtInSynonyms: [String: [String]] = [
        "Current Client": ["client", "clients", "training client", "pt client"],
        "Potential Client": ["prospect", "potential"],
        "Lead": ["lead", "inquiry"],
        "Vendor": ["vendor", "supplier"],
        "Team": ["staff", "coach", "trainer"],
        "Business Partner": ["partner"],
        "Referral Source": ["referral", "referrer"],
        "Investor": ["investor"],
        "Professional Contact": ["work", "business"],
        "Family": ["family", "fam"],
        "Close Friend": ["bestie"],
        "Friend": ["friend"],
        "Acquaintance": ["acquaintance"]
    ]
}

/// What was found on one contact card.
struct MarkerParse: Equatable {
    /// Group entry ids (UUID strings), in the order found.
    var groupIDs: [String] = []
    var groupNames: [String] = []
    /// Relationship type names.
    var typeNames: [String] = []

    /// The name with recognized markers taken out. Empty means the field held
    /// nothing but markers.
    var givenName: String = ""
    var familyName: String = ""
    /// Marker text removed from the name fields, for showing and for the record.
    var removedFromName: [String] = []

    /// Plain-language evidence, shown under the contact so a guess can be judged.
    var reasons: [String] = []

    var isEmpty: Bool { groupIDs.isEmpty && typeNames.isEmpty }
    var changedName: Bool { !removedFromName.isEmpty }
}

/// Reads the shorthand people put in contact fields — "Stanley LT Client",
/// "Dana (HYP)", "Mike - Bumble" — and turns it into a category, a group, and a
/// clean name.
///
/// Two rules keep this from doing damage:
///
/// - **Only whole words, and only words the user's own vocabulary knows.**
///   Nothing is inferred from a dictionary of assumptions about gyms.
/// - **Short markers must be shouted.** "LT" matches in "LT Client" but not in
///   "Lt Cmdr Data" or a surname, because a two-to-four character term only
///   counts when the card wrote it in capitals. Longer terms match any case.
enum ContactMarkerParser {

    /// Below this length, a marker has to be ALL CAPS on the card to count.
    private static let shoutingThreshold = 4

    static func parse(_ contact: ContactSummary, vocabulary: MarkerVocabulary) -> MarkerParse {
        var parse = MarkerParse(givenName: contact.givenName, familyName: contact.familyName)
        guard !vocabulary.isEmpty else { return parse }

        // Name fields are the only ones cleaned up. A marker in the company
        // field is still evidence, but "Life Time" as an employer is the truth
        // about where someone works and shouldn't be edited out.
        var fields: [(value: String, label: String, cleans: Bool)] = [
            (contact.givenName, "first name", true),
            (contact.familyName, "last name", true)
        ]
        if let company = contact.organizationName {
            fields.append((company, "company", false))
        }
        if let title = contact.jobTitle {
            fields.append((title, "job title", false))
        }

        var seenGroups: Set<String> = []
        var seenTypes: Set<String> = []

        func record(_ entry: MarkerVocabulary.Entry, isGroup: Bool, field: String) {
            if isGroup {
                guard seenGroups.insert(entry.id).inserted else { return }
                parse.groupIDs.append(entry.id)
                parse.groupNames.append(entry.display)
            } else {
                guard seenTypes.insert(entry.id).inserted else { return }
                parse.typeNames.append(entry.display)
            }
            parse.reasons.append("\"\(entry.display)\" from the \(field) field")
        }

        for field in fields {
            let found = matches(in: field.value, vocabulary: vocabulary)
            guard !found.isEmpty else { continue }

            for match in found {
                record(match.entry, isGroup: match.isGroup, field: field.label)
            }

            guard field.cleans else { continue }
            let cleaned = removing(found.map(\.range), from: field.value)
            if field.label == "first name" {
                parse.givenName = cleaned
            } else {
                parse.familyName = cleaned
            }
            parse.removedFromName.append(
                contentsOf: found.map { String(field.value[$0.range]) }
            )
        }

        // Never leave someone nameless. If the markers ate the whole card,
        // categorize but keep the name exactly as it was.
        if parse.givenName.isEmpty, parse.familyName.isEmpty {
            parse.givenName = contact.givenName
            parse.familyName = contact.familyName
            parse.removedFromName = []
        }

        return parse
    }

    // MARK: Matching

    private struct Match {
        let entry: MarkerVocabulary.Entry
        let isGroup: Bool
        let range: Range<String.Index>
    }

    /// Non-overlapping matches in one field, groups and types alike.
    private static func matches(
        in value: String,
        vocabulary: MarkerVocabulary
    ) -> [Match] {
        let candidates = vocabulary.groups.map { ($0, true) } + vocabulary.types.map { ($0, false) }
        var results: [Match] = []
        var claimed: [Range<String.Index>] = []

        for (entry, isGroup) in candidates {
            for term in entry.terms {
                guard let range = range(of: term, in: value) else { continue }
                guard !claimed.contains(where: { $0.overlaps(range) }) else { continue }
                claimed.append(range)
                results.append(Match(entry: entry, isGroup: isGroup, range: range))
                break
            }
        }
        return results.sorted { $0.range.lowerBound < $1.range.lowerBound }
    }

    /// Finds `term` as a whole word. Short terms additionally have to be
    /// capitalized on the card.
    static func range(of term: String, in value: String) -> Range<String.Index>? {
        guard !term.isEmpty else { return nil }
        var searchStart = value.startIndex

        while searchStart < value.endIndex,
              let found = value.range(of: term, options: [.caseInsensitive], range: searchStart..<value.endIndex) {
            let before = found.lowerBound == value.startIndex
                ? nil
                : value[value.index(before: found.lowerBound)]
            let after = found.upperBound == value.endIndex ? nil : value[found.upperBound]

            let isWhole = !(before.map(isWordCharacter) ?? false)
                && !(after.map(isWordCharacter) ?? false)
            let isShouted = term.count > shoutingThreshold
                || String(value[found]) == String(value[found]).uppercased()

            if isWhole, isShouted { return found }
            searchStart = found.upperBound
        }
        return nil
    }

    private static func isWordCharacter(_ character: Character) -> Bool {
        character.isLetter || character.isNumber
    }

    // MARK: Discovery

    /// A shorthand that keeps appearing on contact cards but means nothing to
    /// Keepr yet.
    struct Candidate: Identifiable, Equatable {
        let token: String
        let count: Int

        var id: String { token }
    }

    /// Finds the shorthand this user already uses, so the first import can offer
    /// "LT — on 8 contacts. Make it a group?" instead of requiring them to have
    /// set everything up in advance.
    ///
    /// Only two shapes count, because both are unambiguously a label rather than
    /// part of a name: SHOUTED short tokens, and anything in brackets.
    static func candidates(
        in contacts: [ContactSummary],
        vocabulary: MarkerVocabulary,
        minimum: Int = 2,
        limit: Int = 6
    ) -> [Candidate] {
        let known = Set(
            (vocabulary.groups + vocabulary.types).flatMap(\.terms)
        )
        var counts: [String: Int] = [:]
        var display: [String: String] = [:]

        for contact in contacts {
            var seen: Set<String> = []
            for value in [contact.givenName, contact.familyName] {
                for token in labelTokens(in: value) {
                    let key = token.lowercased()
                    guard !known.contains(key), seen.insert(key).inserted else { continue }
                    counts[key, default: 0] += 1
                    display[key] = display[key] ?? token
                }
            }
        }

        return counts
            .filter { $0.value >= minimum }
            .sorted { $0.value == $1.value ? $0.key < $1.key : $0.value > $1.value }
            .prefix(limit)
            .compactMap { key, count in
                display[key].map { Candidate(token: $0, count: count) }
            }
    }

    /// Tokens in a name field that read as a label rather than a name.
    static func labelTokens(in value: String) -> [String] {
        var results: [String] = []

        // Anything in brackets: "Dana (HYP)", "Mike [Bumble]".
        var buffer = ""
        var depth = 0
        for character in value {
            if character == "(" || character == "[" {
                depth += 1
                buffer = ""
            } else if character == ")" || character == "]" {
                if depth > 0 {
                    let inner = buffer.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !inner.isEmpty, inner.split(whereSeparator: \.isWhitespace).count <= 2 {
                        results.append(inner)
                    }
                }
                depth = max(0, depth - 1)
                buffer = ""
            } else if depth > 0 {
                buffer.append(character)
            }
        }

        // SHOUTED short tokens: "LT", "HYP", "MP".
        for token in value.split(whereSeparator: { !$0.isLetter && !$0.isNumber }) {
            let text = String(token)
            guard text.count >= 2, text.count <= 5,
                  text == text.uppercased(),
                  text.contains(where: \.isLetter)
            else { continue }
            results.append(text)
        }

        return results
    }

    /// Punctuation a marker tends to be wrapped in — "Dana (HYP)", "Mike - Bumble".
    /// Left behind, it looks like the app made a mess of someone's name.
    private static let wrappers: Set<Character> = ["(", ")", "[", "]", "{", "}", "|", "/", "\\", "-", "–", "—", ":", ",", "•"]

    /// Takes the matched ranges out and tidies up what's left.
    static func removing(_ ranges: [Range<String.Index>], from value: String) -> String {
        var result = value
        for range in ranges.sorted(by: { $0.lowerBound > $1.lowerBound }) {
            result.replaceSubrange(range, with: " ")
        }
        return String(result.map { wrappers.contains($0) ? " " : $0 })
            .split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
    }
}

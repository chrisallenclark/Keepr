import Foundation

/// What a raw capture becomes, before the user confirms it.
///
/// Nothing here is written to a person's record until it's reviewed — that's
/// true of the on-device extractor today and will stay true if a model-backed
/// one replaces it.
struct CaptureDraft: Sendable {
    var summary: String = ""
    /// A name spotted in the text, used to preselect a person. Never trusted.
    var suggestedName: String?
    var kind: InteractionKind = .other
    var occurredAt: Date = Date()
    var memories: [MemoryDraft] = []
    var followUp: FollowUpDraft?

    var hasSuggestions: Bool { !memories.isEmpty || followUp != nil }
}

/// Turns free text into a draft. The one seam where AI will later plug in.
protocol CaptureExtracting: Sendable {
    func extract(from text: String, now: Date) async -> CaptureDraft
}

/// On-device extraction using nothing but Foundation: sentence segmentation,
/// keyword rules and `NSDataDetector` for dates.
///
/// It is deliberately conservative — it would rather suggest nothing than
/// suggest something wrong, because every suggestion costs the user a tap to
/// dismiss. No network, no API key, no model.
struct HeuristicCaptureExtractor: CaptureExtracting {

    func extract(from text: String, now: Date = Date()) async -> CaptureDraft {
        let normalized = text
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\n", with: " ")
        guard !normalized.isEmpty else { return CaptureDraft(occurredAt: now) }

        var draft = CaptureDraft(occurredAt: now)
        draft.summary = Self.summary(from: normalized)
        draft.suggestedName = Self.personName(in: normalized)
        draft.kind = Self.kind(of: normalized)

        let clauses = Self.clauses(in: normalized)
        let actionClauses = clauses.filter { Self.isAction($0) }
        let factClauses = clauses.filter { !Self.isAction($0) }

        draft.memories = factClauses
            .compactMap { Self.memory(from: $0) }
            .reduced()

        if let action = actionClauses.first {
            draft.followUp = FollowUpDraft(
                title: Self.actionTitle(from: action),
                dueDate: Self.dueDate(in: normalized, now: now) ?? Self.defaultDue(from: now),
                hasTime: false
            )
        }
        return draft
    }

    // MARK: - Summary

    static func summary(from text: String, limit: Int = 140) -> String {
        let first = sentences(in: text).first ?? text
        guard first.count > limit else { return first }
        let cut = first.prefix(limit)
        guard let lastSpace = cut.lastIndex(of: " ") else { return String(cut) + "…" }
        return String(cut[cut.startIndex..<lastSpace]) + "…"
    }

    // MARK: - Segmentation

    static func sentences(in text: String) -> [String] {
        var result: [String] = []
        text.enumerateSubstrings(in: text.startIndex..., options: [.bySentences]) { substring, _, _, _ in
            if let substring {
                let trimmed = substring.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { result.append(trimmed) }
            }
        }
        return result.isEmpty ? [text] : result
    }

    /// Sentences split further on commas and "and", because one sentence
    /// usually carries several separate facts.
    static func clauses(in text: String) -> [String] {
        sentences(in: text)
            .flatMap { sentence -> [String] in
                sentence
                    .replacingOccurrences(of: ", and ", with: "|")
                    .replacingOccurrences(of: " and ", with: "|")
                    .replacingOccurrences(of: ", ", with: "|")
                    .split(separator: "|")
                    .map(String.init)
            }
            .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " .!?")) }
            .filter { $0.count > 3 }
    }

    // MARK: - Keyword matching

    /// Lowercases, replaces punctuation with spaces and pads the result, so
    /// keywords can be matched on word boundaries. Without this, "met" matches
    /// inside "something" and every note becomes an in-person meeting.
    static func matchable(_ text: String) -> String {
        let lowered = text.lowercased()
        let cleaned = String(lowered.map { $0.isLetter || $0.isNumber || $0 == "'" ? $0 : " " })
        return " " + cleaned.split(separator: " ").joined(separator: " ") + " "
    }

    /// `haystack` must already be `matchable`.
    static func containsWord(_ haystack: String, _ keyword: String) -> Bool {
        haystack.contains(" " + keyword.trimmingCharacters(in: .whitespaces) + " ")
    }

    // MARK: - Interaction kind

    /// Order matters: the first list to match wins, so the specific kinds sit
    /// above the general ones. "Grabbed lunch" is a meal before it's in-person,
    /// and a Zoom is a video call before it's a meeting.
    private static let kindKeywords: [(InteractionKind, [String])] = [
        (.video, ["zoom", "facetime", "video call", "google meet", "on video"]),
        (.call, ["called", "phone call", "on the phone", "rang", "hopped on a call"]),
        (.dm, ["dm'd", "dmed", "dm from", "instagram", "linkedin message", "slack"]),
        (.text, ["texted", "text from", "messaged"]),
        (.email, ["emailed", "sent an email", "email from", "replied to his email"]),
        (.meal, ["lunch", "coffee", "dinner", "breakfast", "drinks", "grabbed a bite"]),
        (.event, ["conference", "networking event", "seminar", "expo", "workshop", "party"]),
        (.meeting, ["meeting", "sat down with", "presentation"]),
        (.inPerson, ["ran into", "met", "saw", "bumped into", "in person"])
    ]

    static func kind(of text: String) -> InteractionKind {
        let haystack = matchable(text)
        for (kind, keywords) in kindKeywords
        where keywords.contains(where: { containsWord(haystack, $0) }) {
            return kind
        }
        return .other
    }

    // MARK: - Person name

    private static let nameTriggers: Set<String> = [
        "talked", "spoke", "met", "called", "texted", "emailed", "saw",
        "ran", "bumped", "caught", "messaged", "lunch", "coffee", "dinner", "with"
    ]
    private static let nameConnectors: Set<String> = ["to", "with", "up", "into", "from"]
    private static let nameStopWords: Set<String> = [
        "I", "The", "A", "An", "At", "In", "On", "My", "His", "Her", "Their", "He", "She", "They"
    ]

    /// Looks for a capitalized name right after a conversational verb.
    /// Returns nil rather than guessing when nothing looks like a name.
    static func personName(in text: String) -> String? {
        let tokens = text.split(separator: " ").map(String.init)
        guard let triggerIndex = tokens.firstIndex(where: {
            nameTriggers.contains($0.lowercased().trimmingCharacters(in: .punctuationCharacters))
        }) else { return nil }

        var index = triggerIndex + 1
        while index < tokens.count,
              nameConnectors.contains(tokens[index].lowercased()) {
            index += 1
        }

        var parts: [String] = []
        while index < tokens.count, parts.count < 2 {
            let token = tokens[index].trimmingCharacters(in: .punctuationCharacters)
            guard let first = token.first, first.isUppercase, !nameStopWords.contains(token) else { break }
            parts.append(token)
            index += 1
        }

        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }

    // MARK: - Facts

    private static let categoryKeywords: [(MemoryCategory, [String])] = [
        (.importantDate, ["birthday", "anniversary", "wedding", "vacation", "trip to", "getting married"]),
        (.family, ["wife", "husband", "kids", "children", "daughter", "son", "married", "baby", "grandkids"]),
        (.work, ["owns", "runs a", "works at", "works for", "founded", "company", "business", "job", "hiring", "ceo", "manager"]),
        (.goals, ["wants to", "trying to", "training for", "hoping to", "plans to", "goal", "lose", "aiming"]),
        (.interests, ["loves", "enjoys", "plays", "fan of", "collects", "hobby", "runs the"]),
        (.preferences, ["prefers", "allergic", "hates", "doesn't like", "likes his", "vegetarian", "vegan"]),
        (.personal, ["lives in", "moved to", "grew up", "originally from", "house"]),
        (.business, ["client", "referral", "pricing", "proposal", "contract", "invoice", "budget"])
    ]

    /// Phrases that mean "this clause states something about the person".
    private static let factMarkers: [String] = categoryKeywords.flatMap { $0.1 } + [
        "has", "is a", "is the", "just", "started", "used to"
    ]

    static func memory(from clause: String) -> MemoryDraft? {
        let haystack = matchable(clause)
        guard factMarkers.contains(where: { containsWord(haystack, $0) }) else { return nil }

        let content = cleanedFact(clause)
        guard content.count > 5 else { return nil }

        let category = categoryKeywords
            .first { _, keywords in keywords.contains { containsWord(haystack, $0) } }?
            .0 ?? .other

        return MemoryDraft(content: content, category: category)
    }

    /// Drops the leading pronoun or connector and capitalizes, so
    /// "and wants to lose 20 pounds" reads "Wants to lose 20 pounds".
    static func cleanedFact(_ clause: String) -> String {
        var text = clause.trimmingCharacters(in: .whitespaces)
        for prefix in ["he ", "she ", "they ", "and ", "also ", "but ", "who ", "that "] {
            if text.lowercased().hasPrefix(prefix) {
                text = String(text.dropFirst(prefix.count))
                break
            }
        }
        guard let first = text.first else { return text }
        return first.uppercased() + text.dropFirst()
    }

    // MARK: - Actions

    private static let actionMarkers: [String] = [
        "follow up", "text him", "text her", "text them", "text me", "call him", "call her",
        "call them", "email him", "email her", "send him", "send her", "send them",
        "reach out", "check in", "circle back", "get back to", "ask him", "ask her",
        "remind me", "need to", "should", "have to", "supposed to", "said to"
    ]

    static func isAction(_ clause: String) -> Bool {
        let haystack = matchable(clause)
        return actionMarkers.contains { containsWord(haystack, $0) }
    }

    /// "said to text him next week about training" → "Text him next week about training"
    static func actionTitle(from clause: String) -> String {
        var text = clause.trimmingCharacters(in: .whitespaces)
        for prefix in ["said to ", "he said to ", "she said to ", "need to ", "i need to ",
                       "i should ", "should ", "have to ", "i have to ", "remind me to ",
                       "supposed to ", "i'm supposed to "] {
            if text.lowercased().hasPrefix(prefix) {
                text = String(text.dropFirst(prefix.count))
                break
            }
        }
        text = cleanedFact(text)
        return text.isEmpty ? "Follow up" : text
    }

    // MARK: - Dates

    private static let relativeDays: [(String, Int)] = [
        ("day after tomorrow", 2),
        ("tomorrow", 1),
        ("next week", 7),
        ("in a week", 7),
        ("next month", 30),
        ("in a few days", 3),
        ("in a couple days", 2),
        ("this weekend", 5),
        ("next weekend", 12)
    ]

    /// Prefers an explicit date found by `NSDataDetector`; falls back to a small
    /// table of relative phrases. Returns nil when neither is present, so the
    /// caller can pick its own default rather than inventing a deadline.
    static func dueDate(in text: String, now: Date, calendar: Calendar = .current) -> Date? {
        let haystack = matchable(text)

        if let match = relativeDays.first(where: { containsWord(haystack, $0.0) }) {
            let start = calendar.startOfDay(for: now)
            return calendar.date(byAdding: .day, value: match.1, to: start)
        }

        guard let detector = try? NSDataDetector(
            types: NSTextCheckingResult.CheckingType.date.rawValue
        ) else { return nil }

        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        let detected = detector
            .matches(in: text, options: [], range: range)
            .compactMap(\.date)
            .filter { $0 > now }
            .min()

        guard let detected else { return nil }
        return calendar.startOfDay(for: detected)
    }

    static func defaultDue(from now: Date, calendar: Calendar = .current) -> Date {
        let start = calendar.startOfDay(for: now)
        return calendar.date(byAdding: .day, value: 3, to: start) ?? start
    }
}

// MARK: - Draft cleanup

private extension Array where Element == MemoryDraft {
    /// Drops near-duplicates and caps how many suggestions the user has to triage.
    func reduced(limit: Int = 5) -> [MemoryDraft] {
        var seen: Set<String> = []
        var result: [MemoryDraft] = []
        for draft in self {
            let key = draft.content.lowercased()
            guard !seen.contains(key) else { continue }
            seen.insert(key)
            result.append(draft)
            if result.count == limit { break }
        }
        return result
    }
}

/// Returns nothing. Used by previews that want the plain manual flow.
struct NoopCaptureExtractor: CaptureExtracting {
    func extract(from text: String, now: Date) async -> CaptureDraft {
        CaptureDraft(summary: text, occurredAt: now)
    }
}

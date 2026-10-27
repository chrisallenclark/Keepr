import Foundation

/// Works out what to call a pasted write-up.
///
/// The title is what makes a profile readable six months on, and it's also the
/// thing nobody wants to type. So this guesses, and the guess is always shown
/// in an editable field rather than applied silently — a wrong title the user
/// can see and fix is fine; one they discover later is not.
///
/// Three strategies, tried in order, because real pasted text falls into three
/// shapes:
///
/// 1. It already has a heading. Recorder apps and note tools usually open with
///    one: "Consultation — Jake Mathers". If the first line reads like a title,
///    that *is* the title and anything we invent would be worse.
/// 2. It doesn't, but it says what kind of conversation it was somewhere near
///    the top. "Consultation · Mar 3" beats a sentence fragment.
/// 3. Neither. Fall back to the opening sentence, trimmed — recognisable even
///    when it isn't pretty.
enum NoteTitler {

    /// Longest a first line can be and still read as a heading rather than
    /// prose. Generous enough for "Initial consultation with Jake at Life Time".
    static let headingLimit = 64

    /// How much of the text to search for a conversation kind. Looking at the
    /// whole thing would match the word "consultation" in a closing sentence
    /// and mislabel a note about something else.
    static let scanLimit = 400

    /// Conversation kinds, most specific first. The first hit wins, so
    /// "consultation" beats the "meeting" two paragraphs later.
    static let kinds: [(label: String, keywords: [String])] = [
        ("Consultation", ["consultation", "consult", "intake", "assessment", "initial session"]),
        ("Check-In", ["check in", "check-in", "checkin", "progress review", "weekly review"]),
        ("Discovery Call", ["discovery call", "discovery"]),
        ("Sales Call", ["sales call", "pitch", "proposal call", "quote"]),
        ("Onboarding", ["onboarding", "kickoff", "kick off", "first session"]),
        ("Meeting", ["meeting", "met with", "sat down"]),
        ("Call", ["phone call", "call with", "spoke with", "called"]),
        ("Coffee", ["coffee", "lunch", "dinner", "breakfast", "drinks"]),
        ("Training Session", ["training session", "session", "workout", "lift"]),
        ("Interview", ["interview"]),
        ("Review", ["review", "recap", "debrief"])
    ]

    /// The suggested title, or an empty string when there's nothing to go on —
    /// callers then fall back to the date alone rather than printing a guess
    /// made from two words.
    static func suggest(from text: String, on date: Date = Date()) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }

        if let heading = heading(in: trimmed) { return heading }
        if let kind = kind(in: trimmed) { return "\(kind) · \(day(date))" }
        if let opening = openingSentence(in: trimmed) { return opening }
        return "Notes · \(day(date))"
    }

    // MARK: - Strategy 1: an existing heading

    /// The first line, when it reads like a title rather than the start of a
    /// paragraph: short, not ending in sentence punctuation, and more than one
    /// word — a single word is usually a stray label like "Summary".
    static func heading(in text: String) -> String? {
        guard let first = text.split(whereSeparator: \.isNewline).first else { return nil }

        var line = first.trimmingCharacters(in: .whitespaces)
        // Markdown headings and bullets are headings with decoration.
        line = line.trimmingCharacters(in: CharacterSet(charactersIn: "#*-•> ").union(.whitespaces))

        guard !line.isEmpty, line.count <= headingLimit else { return nil }
        guard let last = line.last, !".!?,;:".contains(last) else { return nil }

        let words = line.split(whereSeparator: \.isWhitespace)
        guard words.count >= 2 else { return nil }

        // "Summary:" style labels describe the document, not the conversation.
        let bare = line.lowercased()
        let labels = ["summary", "transcript", "notes", "meeting notes", "action items", "key points"]
        guard !labels.contains(bare) else { return nil }

        return line
    }

    // MARK: - Strategy 2: what kind of conversation it was

    static func kind(in text: String) -> String? {
        let haystack = HeuristicCaptureExtractor.matchable(String(text.prefix(scanLimit)))

        return kinds.first { _, keywords in
            keywords.contains { HeuristicCaptureExtractor.containsWord(haystack, $0) }
        }?.label
    }

    // MARK: - Strategy 3: whatever it opens with

    static func openingSentence(in text: String, limit: Int = 52) -> String? {
        guard let sentence = HeuristicCaptureExtractor.sentences(in: text).first else { return nil }

        let cleaned = sentence
            .trimmingCharacters(in: CharacterSet(charactersIn: " .!?"))
            .replacingOccurrences(of: "\n", with: " ")
        guard cleaned.count > 3 else { return nil }
        guard cleaned.count > limit else { return cleaned }

        let cut = cleaned.prefix(limit)
        guard let lastSpace = cut.lastIndex(of: " ") else { return String(cut) + "…" }
        return String(cut[cut.startIndex..<lastSpace]) + "…"
    }

    // MARK: - Helpers

    static func day(_ date: Date) -> String {
        date.formatted(.dateTime.month(.abbreviated).day())
    }

    /// Title for a note that is only screenshots — there's no text to read.
    static func imageOnlyTitle(on date: Date = Date()) -> String {
        "Notes · \(day(date))"
    }
}

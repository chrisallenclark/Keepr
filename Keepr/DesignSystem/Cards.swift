import SwiftUI

// MARK: - Typography

extension Font {

    /// The screen's own name — "Keepr", "People", a person's name on their
    /// profile.
    ///
    /// Serif, which on iPhone means New York: already on the device, so nothing
    /// is bundled and nothing is licensed, and it scales with Dynamic Type like
    /// any other system font. Used only for names of things; never for content,
    /// where it would fight the system UI font every other app uses.
    static let keeprTitle = Font.system(.title, design: .serif).weight(.bold)

    /// The same voice, sized for a navigation bar.
    static let keeprTitleInline = Font.system(.title3, design: .serif).weight(.bold)

    /// The label above a group of rows.
    static let keeprSectionHeading = Font.caption.weight(.semibold)
}

// MARK: - List treatment

extension View {

    /// The app's list treatment: grouped rows on cards, over the warm ground.
    ///
    /// `.insetGrouped` is doing real work here. It already rounds the first and
    /// last row of every section and insets the block from the edge, which is
    /// exactly the card shape the design wants — so the cards cost one line
    /// instead of a pile of corner-radius geometry, and every swipe action,
    /// section index and separator keeps behaving the way people expect.
    func keeprList() -> some View {
        self
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Theme.Palette.ground)
            .listRowSeparatorTint(Theme.Palette.hairline)
    }

    /// A row that sits on a card. Applied per row rather than globally so a
    /// row that wants to be invisible — a header, a chip strip — still can be.
    func keeprRow() -> some View {
        listRowBackground(Theme.Palette.card)
    }

    /// A row that is part of the page rather than part of a card.
    func keeprBareRow() -> some View {
        self
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}

// MARK: - Card

/// A block of content raised off the ground.
///
/// For the places a `List` section can't reach — a grid cell, the About table,
/// a note. Inside a list, `keeprRow()` gets the same look for free.
struct KeeprCard<Content: View>: View {

    private let padding: CGFloat
    private let content: Content

    init(padding: CGFloat = Theme.Spacing.medium, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .background(Theme.Palette.card, in: .rect(cornerRadius: Theme.Radius.card))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.card)
                    .strokeBorder(Theme.Palette.hairline, lineWidth: 1)
            }
    }
}

// MARK: - Section heading

/// The label above a group of rows, with an optional count and an optional
/// action on the right.
///
/// The count is part of the heading rather than a badge on the tab bar: it
/// answers "how much is here" while you're already looking at the thing, which
/// is the only moment it's useful and the only moment it isn't nagging.
struct SectionHeading<Trailing: View>: View {

    private let title: String
    private let count: Int?
    private let trailing: Trailing

    init(_ title: String, count: Int? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.count = count
        self.trailing = trailing()
    }

    var body: some View {
        HStack(spacing: Theme.Spacing.small) {
            Text(title)
                .font(.keeprSectionHeading)
                .textCase(.uppercase)
                .kerning(0.6)
                .foregroundStyle(.secondary)

            if let count, count > 0 {
                Text("\(count)")
                    .font(.caption.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1)
                    .background(Theme.Palette.fill, in: .capsule)
            }

            Spacer(minLength: 0)

            trailing
        }
        .textCase(nil)
        .accessibilityElement(children: .combine)
    }
}

extension SectionHeading where Trailing == EmptyView {
    init(_ title: String, count: Int? = nil) {
        self.init(title, count: count) { EmptyView() }
    }
}

// MARK: - Type badge

/// A relationship type, in its own color.
///
/// The tint is the point: once "green means potential client" is true on the
/// People list, the profile header and the relationship map, the type stops
/// being a word you read and becomes something you recognize.
struct TypeBadge: View {

    let name: String
    var tint: Theme.Tint = .graphite
    var symbolName: String?

    var body: some View {
        Label {
            Text(name)
        } icon: {
            if let symbolName {
                Image(systemName: symbolName)
            }
        }
        .labelStyle(.titleAndIcon)
        .font(.caption.weight(.semibold))
        .foregroundStyle(tint.foreground)
        .padding(.horizontal, Theme.Spacing.small)
        .padding(.vertical, 3)
        .background(tint.background, in: .rect(cornerRadius: 6))
        .lineLimit(1)
    }
}

extension TypeBadge {
    init(tag: RelationshipTag, showsSymbol: Bool = false) {
        self.init(
            name: tag.name,
            tint: tag.tint,
            symbolName: showsSymbol ? tag.symbolName : nil
        )
    }
}

/// The badges under a name. Truncates rather than wrapping into a wall.
struct TypeBadgeRow: View {

    let tags: [RelationshipTag]
    var limit = 2

    var body: some View {
        if !tags.isEmpty {
            HStack(spacing: Theme.Spacing.tight) {
                ForEach(tags.prefix(limit)) { tag in
                    TypeBadge(tag: tag)
                }
                if tags.count > limit {
                    Text("+\(tags.count - limit)")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(tags.map(\.name).joined(separator: ", "))
        }
    }
}

// MARK: - Note block

/// A longer piece of the user's own writing, set apart from the structured
/// fields around it.
struct NoteBlock: View {

    let title: String
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.tight) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Theme.Spacing.medium)
        .background(Theme.Palette.noteBackground, in: .rect(cornerRadius: Theme.Radius.card))
    }
}

// MARK: - Previews

#Preview("Cards") {
    ScrollView {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            Text("Keepr").font(.keeprTitle)

            SectionHeading("Needs Attention", count: 3)

            KeeprCard {
                VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                    Text("Amy Lewis").font(.body.weight(.semibold))
                    HStack {
                        TypeBadge(name: "Current Client", tint: .blue)
                        TypeBadge(name: "Potential Client", tint: .green)
                        TypeBadge(name: "Friend", tint: .purple)
                    }
                }
            }

            HStack {
                ForEach(Theme.Tint.allCases) { tint in
                    TypeBadge(name: tint.title, tint: tint)
                }
            }

            NoteBlock(
                title: "Personal notes",
                text: "Amy mentioned she's planning a trip to Italy this summer. Follow up with restaurant recommendations in Rome."
            )
        }
        .padding()
    }
    .background(Theme.Palette.ground)
}

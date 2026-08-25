import SwiftUI

/// Photo, name, who they are to you. Nothing else — the header answers
/// "who is this?" and hands off.
struct PersonHeader: View {
    let person: Person
    let mode: ContextMode

    var body: some View {
        VStack(spacing: Theme.Spacing.medium) {
            Avatar(person: person, size: .extraLarge)

            VStack(spacing: Theme.Spacing.tight) {
                Text(person.displayName)
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)

                if person.displayName != person.fullName {
                    Text(person.fullName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                if let subtitle = person.subtitle {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }

            let tags = person.headlineTags(for: mode)
            if !tags.isEmpty {
                HStack(spacing: Theme.Spacing.small) {
                    ForEach(tags.prefix(3)) { tag in
                        TypeBadge(tag: tag)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Theme.Spacing.medium)
    }
}

/// Message · Call · Email · Log. Unavailable methods are hidden rather than
/// shown disabled — a dimmed button you can never press is just noise.
struct QuickActions: View {
    let person: Person
    let onContact: (ContactMethod) -> Void
    let onLog: () -> Void

    var body: some View {
        HStack(spacing: Theme.Spacing.small) {
            ForEach(ContactMethod.allCases) { method in
                if CommunicationLauncher.isAvailable(method, for: person) {
                    actionButton(
                        title: method.title,
                        symbolName: method.symbolName,
                        isProminent: false
                    ) {
                        onContact(method)
                    }
                }
            }

            actionButton(title: "Log", symbolName: "plus.circle.fill", isProminent: true) {
                onLog()
            }
        }
        .padding(.horizontal, Theme.Spacing.medium)
    }

    private func actionButton(
        title: String,
        symbolName: String,
        isProminent: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: Theme.Spacing.tight) {
                Image(systemName: symbolName)
                    .font(.system(size: 17, weight: .medium))
                Text(title)
                    .font(.caption2)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .foregroundStyle(isProminent ? Color.white : Color.accentColor)
            .background(
                isProminent ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.quaternary),
                in: .rect(cornerRadius: Theme.Radius.card)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

/// One remembered fact.
///
/// Reads as "label → value" when the fact has a name, and as a sentence when it
/// doesn't. Both shapes live in the same row so the About table stays one list
/// rather than two that happen to sit next to each other.
struct MemoryRow: View {
    let memory: Memory

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.medium) {
            if let label = memory.label, !label.isEmpty {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(width: 124, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                Text(memory.content)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Image(systemName: memory.category.symbolName)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .frame(width: 20)
                    .padding(.top, 2)

                VStack(alignment: .leading, spacing: 2) {
                    Text(memory.content)
                        .font(.subheadline)
                        .foregroundStyle(.primary)

                    if memory.category != .other {
                        Text(memory.category.title)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }

                Spacer(minLength: 0)
            }

            if memory.importance == .high {
                Image(systemName: "star.fill")
                    .font(.caption2)
                    .foregroundStyle(.yellow)
                    .accessibilityLabel("Important")
                    .padding(.top, 2)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            memory.label.map { "\($0), \(memory.content)" } ?? memory.content
        )
    }
}

/// One "field name → value" line in the About table.
///
/// The same two-column shape as a labeled memory, for the facts the app already
/// knows without anyone typing them: how you met, when you last spoke, what's
/// next. Putting them in the same table is the point — from the reader's side
/// there's no difference between a fact Keepr worked out and one they wrote.
struct AboutRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.medium) {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: 124, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)

            Text(value)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(value)")
    }
}

/// The unlabeled facts, as one bulleted block.
///
/// Kept together under a single heading rather than one row each, because a
/// column of bullets with no field names beside them is a list, and a list of
/// one-line facts reads faster stacked than spaced out.
struct KeyFactsRow: View {
    let memories: [Memory]

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.medium) {
            Text("Key facts to remember")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: 124, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: Theme.Spacing.tight) {
                ForEach(memories) { memory in
                    HStack(alignment: .top, spacing: Theme.Spacing.small) {
                        Text("•")
                            .font(.subheadline)
                            .foregroundStyle(.tertiary)
                        Text(memory.content)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "Key facts to remember. " + memories.map(\.content).joined(separator: ". ")
        )
    }
}

/// One linked person: who they are and what they are to this person.
struct ConnectionRow: View {
    let connection: PersonConnection

    var body: some View {
        HStack(spacing: Theme.Spacing.medium) {
            Avatar(person: connection.person, size: .small)

            VStack(alignment: .leading, spacing: 2) {
                Text(connection.person.displayName)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)

                Text(connection.label)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let note = connection.link.note, !note.isEmpty {
                    Text(note)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .lineLimit(2)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

/// One entry in the interaction timeline.
struct InteractionRow: View {
    let interaction: Interaction

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.medium) {
            Image(systemName: interaction.kind.symbolName)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(width: 20)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: Theme.Spacing.tight) {
                    Text(RelativeDate.past(interaction.occurredAt))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                    Text("·")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                    Text(interaction.kind.title)
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }

                Text(interaction.headline)
                    .font(.subheadline)
                    .foregroundStyle(.primary)

                if let detail = interaction.detail {
                    Text(detail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

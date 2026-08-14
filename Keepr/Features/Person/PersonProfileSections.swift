import SwiftUI

/// Photo, name, who they are to you. Nothing else — the header answers
/// "who is this?" and hands off.
struct PersonHeader: View {
    let person: Person
    let mode: ContextMode

    var body: some View {
        VStack(spacing: Theme.Spacing.medium) {
            Avatar(person: person, size: .large)

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
                        TagChip(tag: tag, isProminent: true)
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
struct MemoryRow: View {
    let memory: Memory

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.medium) {
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

            if memory.importance == .high {
                Image(systemName: "star.fill")
                    .font(.caption2)
                    .foregroundStyle(.yellow)
                    .accessibilityLabel("Important")
            }
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

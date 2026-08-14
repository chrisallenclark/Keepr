import SwiftUI

// MARK: - Context switcher

/// The Business ↔ Personal control.
///
/// A stock segmented `Picker` rather than a custom slider: it's the control
/// iPhone users already know for "change the lens on this screen", it inherits
/// Dynamic Type, VoiceOver and both appearances for free, and it stays quiet.
struct ContextSwitcher: View {
    @Binding var mode: ContextMode

    var body: some View {
        Picker("Context", selection: $mode) {
            ForEach(ContextMode.allCases) { mode in
                Text(mode.title).tag(mode)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal)
        .padding(.vertical, Theme.Spacing.small)
        .background(.bar)
        .accessibilityLabel("Context")
        .accessibilityHint("Switches between your business and personal relationships")
        .onChange(of: mode) { _, _ in
            Haptics.selection()
        }
    }
}

extension View {
    /// Pins the context switch below the navigation bar.
    func contextSwitcher(_ mode: Binding<ContextMode>) -> some View {
        safeAreaInset(edge: .top, spacing: 0) {
            ContextSwitcher(mode: mode)
        }
    }
}

// MARK: - Tags

/// A relationship type, shown small and unobtrusive.
struct TagChip: View {
    let name: String
    var symbolName: String?
    var isProminent = false

    var body: some View {
        Label {
            Text(name)
        } icon: {
            if let symbolName {
                Image(systemName: symbolName)
            }
        }
        .labelStyle(.titleAndIcon)
        .font(.caption)
        .fontWeight(isProminent ? .medium : .regular)
        .foregroundStyle(isProminent ? Color.primary : Color.secondary)
        .padding(.horizontal, Theme.Spacing.small)
        .padding(.vertical, 3)
        .background(.quaternary, in: .rect(cornerRadius: Theme.Radius.chip))
    }
}

extension TagChip {
    init(tag: RelationshipTag, isProminent: Bool = false) {
        self.init(name: tag.name, symbolName: tag.symbolName, isProminent: isProminent)
    }
}

/// The row of tags under a name. Truncates rather than wrapping into a wall.
struct TagRow: View {
    let tags: [RelationshipTag]
    var limit = 2

    var body: some View {
        if !tags.isEmpty {
            HStack(spacing: Theme.Spacing.tight) {
                ForEach(tags.prefix(limit)) { tag in
                    TagChip(tag: tag)
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

// MARK: - Previews

#Preview("Components") {
    VStack(alignment: .leading, spacing: 24) {
        ContextSwitcher(mode: .constant(.business))
        HStack {
            TagChip(name: "Current Client", symbolName: "checkmark.seal")
            TagChip(name: "Lead", symbolName: "flame", isProminent: true)
        }
        Spacer()
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}

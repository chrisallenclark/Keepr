import SwiftUI

/// A raised panel that sits on the grouped background rather than filling a row.
///
/// Used where content is a *thing* rather than a list entry — a conversation
/// write-up, a stack of files. The fill is the system's own card color, so this
/// adds no palette to maintain and looks native in both appearances; the border
/// and shadow are deliberately almost invisible, which is what reads as
/// "floating" instead of "boxed".
struct FloatingCard<Content: View>: View {

    var isHighlighted: Bool = false
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(Theme.Spacing.medium)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color(.secondarySystemGroupedBackground),
                in: .rect(cornerRadius: Theme.Radius.card)
            )
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.card)
                    .strokeBorder(
                        isHighlighted ? AnyShapeStyle(.tint.opacity(0.45)) : AnyShapeStyle(.quaternary),
                        lineWidth: isHighlighted ? 1.5 : 0.5
                    )
            }
            .shadow(color: .black.opacity(0.07), radius: 5, y: 2)
    }
}

extension View {

    /// Makes a `List` row behave like a canvas for a `FloatingCard`: no row
    /// fill, no separator, and a gutter so the card's shadow isn't clipped.
    func floatingCardRow() -> some View {
        listRowInsets(
            EdgeInsets(
                top: Theme.Spacing.small,
                leading: Theme.Spacing.medium,
                bottom: Theme.Spacing.small,
                trailing: Theme.Spacing.medium
            )
        )
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }
}

/// The chevron on a card that opens and closes. Separate so every expandable
/// card turns it the same way and at the same speed.
struct DisclosureChevron: View {
    let isExpanded: Bool

    var body: some View {
        Image(systemName: "chevron.down")
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
            .rotationEffect(.degrees(isExpanded ? 0 : -90))
            .animation(.snappy(duration: 0.22), value: isExpanded)
            .accessibilityHidden(true)
    }
}

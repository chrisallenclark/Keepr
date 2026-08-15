import SwiftUI

/// A scrolling row of filter chips with live counts.
///
/// Filtering by two things at once is normally a form: a menu, a second menu,
/// a "done". Here it's two taps on things that are already on screen telling you
/// what they'll give you — "Client 12", "Life Time 8" — which is the difference
/// between a feature people use and one they discover once.
///
/// Tapping the selected chip clears it, so there's no separate reset to hunt for.
struct FilterChipRow: View {

    let title: String
    let facets: [FilterFacet]
    /// `nil` means no filter on this row.
    @Binding var selection: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.tight) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.tertiary)
                .textCase(.uppercase)
                .padding(.horizontal, Theme.Spacing.medium)

            ScrollView(.horizontal) {
                HStack(spacing: Theme.Spacing.small) {
                    chip(
                        FilterFacet(
                            id: "",
                            name: "All",
                            symbolName: "circle.grid.2x2",
                            count: 0
                        ),
                        isSelected: selection == nil,
                        showsCount: false
                    ) {
                        select(nil)
                    }

                    ForEach(facets) { facet in
                        chip(facet, isSelected: selection == facet.id, showsCount: true) {
                            select(selection == facet.id ? nil : facet.id)
                        }
                    }
                }
                .padding(.horizontal, Theme.Spacing.medium)
            }
            .scrollIndicators(.hidden)
        }
        .padding(.vertical, Theme.Spacing.tight)
    }

    private func chip(
        _ facet: FilterFacet,
        isSelected: Bool,
        showsCount: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: Theme.Spacing.tight) {
                Image(systemName: facet.symbolName)
                    .font(.caption2)
                Text(facet.name)
                    .lineLimit(1)
                if showsCount {
                    Text("\(facet.count)")
                        .foregroundStyle(isSelected ? Color.white.opacity(0.8) : Color.secondary)
                }
            }
            .font(.subheadline)
            .fontWeight(isSelected ? .semibold : .regular)
            .foregroundStyle(isSelected ? Color.white : Color.primary)
            .padding(.horizontal, Theme.Spacing.medium)
            .padding(.vertical, Theme.Spacing.small)
            .background(
                isSelected ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.quaternary),
                in: .capsule
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(
            showsCount ? "\(facet.name), \(facet.count) people" : facet.name
        )
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }

    private func select(_ id: String?) {
        withAnimation(.snappy(duration: 0.2)) { selection = id }
        Haptics.selection()
    }
}

#Preview("Filter chips") {
    VStack(alignment: .leading, spacing: 0) {
        FilterChipRow(
            title: "Type",
            facets: [
                .init(id: "Current Client", name: "Current Client", symbolName: "checkmark.seal", count: 12),
                .init(id: "Lead", name: "Lead", symbolName: "flame", count: 3),
                .init(id: "Team", name: "Team", symbolName: "person.3", count: 2)
            ],
            selection: .constant("Current Client")
        )
        FilterChipRow(
            title: "Place",
            facets: [
                .init(id: "a", name: "Life Time", symbolName: "figure.run", count: 8),
                .init(id: "b", name: "Home Studio", symbolName: "house", count: 4),
                .init(id: FilterFacet.unplacedID, name: "No Place", symbolName: "questionmark.circle", count: 5)
            ],
            selection: .constant(nil)
        )
    }
    .background(Color(.systemGroupedBackground))
}

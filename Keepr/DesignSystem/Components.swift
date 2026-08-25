import SwiftUI

// MARK: - Context switcher

/// The Business ↔ Personal control.
///
/// This is the app's organizing principle, so it gets the one piece of custom
/// chrome in the design: a capsule track with the selected side filled. It was a
/// stock segmented `Picker` before, and the reason to leave that behind is that
/// `UISegmentedControl` can be recolored but not reshaped — the capsule is the
/// shape the design is built around, and a rounded rectangle sitting under a
/// screen full of capsules reads as the one control nobody styled.
///
/// What the stock control gave for free is therefore paid for by hand here:
/// the labels use a text style so they scale with Dynamic Type, and each side
/// carries the selected trait so VoiceOver announces "Business, selected".
struct ContextSwitcher: View {

    @Binding var mode: ContextMode
    @Namespace private var pill

    var body: some View {
        HStack(spacing: 0) {
            ForEach(ContextMode.allCases) { option in
                let isSelected = option == mode

                Button {
                    guard !isSelected else { return }
                    withAnimation(.snappy(duration: 0.22)) { mode = option }
                } label: {
                    Text(option.title)
                        .font(.subheadline.weight(isSelected ? .semibold : .regular))
                        .foregroundStyle(isSelected ? Theme.Palette.ground : Color.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Theme.Spacing.small)
                        .contentShape(.capsule)
                        .background {
                            if isSelected {
                                Capsule()
                                    .fill(Color.accentColor)
                                    .matchedGeometryEffect(id: "selection", in: pill)
                            }
                        }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(option.title)
                .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(3)
        .background(Theme.Palette.fill, in: .capsule)
        .padding(.horizontal)
        .padding(.vertical, Theme.Spacing.small)
        .background(Theme.Palette.ground)
        .accessibilityElement(children: .contain)
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

// MARK: - Previews

#Preview("Components") {
    VStack(alignment: .leading, spacing: 24) {
        ContextSwitcher(mode: .constant(.business))
        HStack {
            TypeBadge(name: "Current Client", tint: .blue, symbolName: "checkmark.seal")
            TypeBadge(name: "Lead", tint: .orange, symbolName: "flame")
        }
        Spacer()
    }
    .background(Theme.Palette.ground)
}

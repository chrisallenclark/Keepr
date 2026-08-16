import SwiftUI

/// The A–Z strip down the right edge, like Contacts.
///
/// Tap a letter or drag through them; the list jumps as you go. Only letters
/// that actually have someone behind them are listed, so a drag never lands on
/// an empty section — a scrubber that stops on nothing feels broken even though
/// technically it worked.
struct SectionIndexBar: View {

    let titles: [String]
    let onSelect: (String) -> Void

    /// Tracks the letter under the finger so the haptic fires once per letter,
    /// not once per touch event.
    @State private var activeTitle: String?

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                ForEach(titles, id: \.self) { title in
                    Text(title)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color.accentColor)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(.rect)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        select(at: value.location.y, in: geometry.size.height)
                    }
                    .onEnded { _ in activeTitle = nil }
            )
        }
        .frame(width: 22)
        .padding(.vertical, Theme.Spacing.small)
        .accessibilityHidden(true)
    }

    private func select(at position: CGFloat, in height: CGFloat) {
        guard !titles.isEmpty, height > 0 else { return }

        let step = height / CGFloat(titles.count)
        let index = min(max(Int(position / step), 0), titles.count - 1)
        let title = titles[index]

        guard title != activeTitle else { return }
        activeTitle = title
        onSelect(title)
        Haptics.selection()
    }
}

#Preview {
    HStack {
        Spacer()
        SectionIndexBar(titles: ["A", "B", "C", "K", "M", "R", "S", "T", "W", "#"]) { _ in }
    }
    .padding()
}

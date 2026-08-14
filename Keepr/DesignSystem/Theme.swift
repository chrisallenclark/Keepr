import SwiftUI
import UIKit

/// The small set of visual constants the app agrees on.
///
/// Everything else comes from the system: semantic colors, system typography,
/// standard list styles. There is no custom palette to maintain, which is what
/// keeps the app looking native in both appearances and at every Dynamic Type size.
enum Theme {

    enum Spacing {
        /// Between tightly-related elements — a label and its value.
        static let tight: CGFloat = 4
        /// Between rows of related content.
        static let small: CGFloat = 8
        /// The default gap inside a card or row.
        static let medium: CGFloat = 12
        /// Between distinct blocks in a stack.
        static let large: CGFloat = 20
        /// Between major sections of a screen.
        static let section: CGFloat = 28
    }

    enum Radius {
        static let card: CGFloat = 14
        static let chip: CGFloat = 8
    }
}

// MARK: - Haptics

/// Feedback is used only where it clarifies that something happened —
/// completing a follow-up, saving, switching context. Never decorative.
enum Haptics {

    @MainActor
    static func selection() {
        UISelectionFeedbackGenerator().selectionChanged()
    }

    @MainActor
    static func success() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    @MainActor
    static func light() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

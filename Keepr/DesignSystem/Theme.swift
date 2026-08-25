import SwiftUI
import UIKit

/// The small set of visual constants the app agrees on.
///
/// Most of what the app draws still comes from the system: semantic text colors,
/// system typography, standard list behaviours. What's defined here is the
/// surface treatment — a warm paper ground, cards that sit on it, and the tints
/// relationship types are shown in — because "the same grey as every other app"
/// was the one thing the design didn't want.
///
/// Every color below is a *dynamic* color built from a light and a dark value,
/// so there is one code path and Dark Mode is impossible to forget.
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

    // MARK: - Palette

    /// The surfaces. Text keeps using `.primary` / `.secondary` / `.tertiary`,
    /// which already adapt and already respect the accessibility contrast
    /// settings — there's nothing to gain by restating them here.
    enum Palette {

        /// The paper the app is printed on. Warm off-white in light, near-black
        /// in dark — deliberately a touch lighter than pure black so cards have
        /// something to sit on.
        static let ground = Color.dynamic(light: 0xF7F5F1, dark: 0x0F0F11)

        /// A card raised off the ground.
        static let card = Color.dynamic(light: 0xFFFFFF, dark: 0x1C1C1E)

        /// The line between rows inside a card, and the card's own edge.
        static let hairline = Color.dynamic(light: 0xE6E2DA, dark: 0x303035)

        /// A quiet fill: unselected chips, avatar placeholders, the count pill.
        static let fill = Color.dynamic(light: 0xE9E5DE, dark: 0x2A2A2E)

        /// Bars that need to read as chrome rather than content.
        static let bar = Color.dynamic(light: 0xF2EFEA, dark: 0x1A1A1D)

        /// A tinted block for a longer piece of the user's own writing.
        static let noteBackground = Color.dynamic(light: 0xE9EFF8, dark: 0x1A2333)

        /// Overdue, and anything else that is late rather than wrong. Warmer
        /// than the system's red, because none of this is an error.
        static let overdue = Color.dynamic(light: 0xB65A16, dark: 0xE29455)
    }

    // MARK: - Tints

    /// The colors a relationship type can be shown in.
    ///
    /// A fixed, small palette rather than a color picker: the point of the tint
    /// is that "blue means client" becomes true at a glance across the whole
    /// app, and that only survives if there are few enough of them to learn.
    /// Each is a background/foreground pair chosen together so the text stays
    /// legible on the fill in both appearances.
    enum Tint: String, CaseIterable, Identifiable, Sendable {
        case blue
        case green
        case purple
        case orange
        case teal
        case pink
        case graphite

        var id: String { rawValue }

        var background: Color {
            switch self {
            case .blue:     .dynamic(light: 0xE8EFF9, dark: 0x1B2C46)
            case .green:    .dynamic(light: 0xE3F1E8, dark: 0x16301F)
            case .purple:   .dynamic(light: 0xEFEAFA, dark: 0x262046)
            case .orange:   .dynamic(light: 0xFBEFE2, dark: 0x33220F)
            case .teal:     .dynamic(light: 0xE0F0EF, dark: 0x12302E)
            case .pink:     .dynamic(light: 0xFBE9EE, dark: 0x361B24)
            case .graphite: .dynamic(light: 0xEAEBEE, dark: 0x2A2C31)
            }
        }

        var foreground: Color {
            switch self {
            case .blue:     .dynamic(light: 0x2A548F, dark: 0x8AB2EE)
            case .green:    .dynamic(light: 0x1D6B3E, dark: 0x6DCB93)
            case .purple:   .dynamic(light: 0x5C449A, dark: 0xAE96EE)
            case .orange:   .dynamic(light: 0x8F5417, dark: 0xE0A165)
            case .teal:     .dynamic(light: 0x1A6360, dark: 0x62BFBA)
            case .pink:     .dynamic(light: 0x92384F, dark: 0xE895AB)
            case .graphite: .dynamic(light: 0x4A4E57, dark: 0xAFB4BD)
            }
        }

        var title: String { rawValue.capitalized }

        /// The tint for a stored key, falling back to a **stable** choice
        /// derived from the name.
        ///
        /// The fallback is what makes a type the user invented look deliberate
        /// without asking them to pick a color. It has to be stable across
        /// launches, which is why it hashes the string itself rather than using
        /// `hashValue` — Swift seeds that per-process, so the same tag would
        /// change color every time the app opened.
        static func resolve(key: String?, fallbackSeed: String) -> Tint {
            if let key, let tint = Tint(rawValue: key) { return tint }
            return allCases[Int(stableHash(fallbackSeed) % UInt64(allCases.count))]
        }

        /// FNV-1a. Small, stable, and good enough to spread a few dozen names.
        static func stableHash(_ text: String) -> UInt64 {
            var hash: UInt64 = 0xcbf2_9ce4_8422_2325
            for byte in text.lowercased().utf8 {
                hash ^= UInt64(byte)
                hash = hash &* 0x0000_0100_0000_01B3
            }
            return hash
        }
    }
}

// MARK: - Dynamic colors

extension Color {

    /// Builds one color from a light and a dark hex value.
    ///
    /// Going through `UIColor`'s trait-aware initializer rather than declaring
    /// two `Color`s means the value resolves at draw time, so it is also correct
    /// inside a view that overrides the color scheme.
    static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

extension UIColor {

    /// `0xRRGGBB`, sRGB, opaque.
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
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

import Foundation

/// Launch arguments used to drive the app for automated screenshots.
///
/// Debug-only by construction: in a Release build every check below folds to a
/// constant `false`/`nil`, so none of this exists in a TestFlight or App Store
/// binary. Nothing here can be reached by a real user.
enum LaunchOptions {

    private static var arguments: [String] {
        ProcessInfo.processInfo.arguments
    }

    /// Runs against a throwaway in-memory store preloaded with sample data,
    /// skips onboarding, and starts on a chosen screen.
    static var isDemoMode: Bool {
        #if DEBUG
        arguments.contains("-KeeprDemoMode")
        #else
        false
        #endif
    }

    /// Which screen to open on.
    ///
    /// `profile` opens the first person in People, and `map` opens the first
    /// connected person's relationship map — both because the screen worth
    /// capturing is one level in, and a screenshot of an empty detail view
    /// proves nothing.
    enum Screen: String {
        case today
        case people
        case followUp
        case profile
        case network
        case map
        case more

        var tab: AppTab {
            switch self {
            case .today: .today
            case .people, .profile: .people
            case .followUp: .followUp
            case .network, .map: .network
            case .more: .more
            }
        }
    }

    static var screen: Screen? {
        guard isDemoMode else { return nil }
        return value(for: "-KeeprScreen").flatMap(Screen.init(rawValue:))
    }

    static var contextMode: ContextMode? {
        guard isDemoMode else { return nil }
        return value(for: "-KeeprContext").flatMap(ContextMode.init(rawValue:))
    }

    /// Reads `-Flag value` style arguments.
    private static func value(for flag: String) -> String? {
        guard let index = arguments.firstIndex(of: flag),
              arguments.indices.contains(index + 1) else { return nil }
        return arguments[index + 1]
    }
}

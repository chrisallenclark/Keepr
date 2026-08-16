import SwiftUI

// MARK: - Service injection
//
// Classic `EnvironmentKey` rather than the `@Entry` macro, which is iOS 18+.

private struct ContactStoreKey: EnvironmentKey {
    static let defaultValue: any ContactStoreProviding = StubContactStore()
}

private struct NotificationServiceKey: EnvironmentKey {
    static let defaultValue: any NotificationScheduling = StubNotificationService()
}

private struct CaptureExtractorKey: EnvironmentKey {
    static let defaultValue: any CaptureExtracting = HeuristicCaptureExtractor()
}

extension EnvironmentValues {
    /// Defaults to the stub so previews are safe; the app injects the live one.
    var contactStore: any ContactStoreProviding {
        get { self[ContactStoreKey.self] }
        set { self[ContactStoreKey.self] = newValue }
    }

    var notificationService: any NotificationScheduling {
        get { self[NotificationServiceKey.self] }
        set { self[NotificationServiceKey.self] = newValue }
    }

    var captureExtractor: any CaptureExtracting {
        get { self[CaptureExtractorKey.self] }
        set { self[CaptureExtractorKey.self] = newValue }
    }
}

// MARK: - Stored preferences

/// Every `@AppStorage` key in one place, so there's no chance of two screens
/// disagreeing about a spelling.
enum PreferenceKey {
    static let contextMode = "keepr.contextMode"
    static let hasOnboarded = "keepr.hasOnboarded"
    static let peopleSort = "keepr.peopleSort"
    /// One-time switch of an existing install to alphabetical people.
    static let didDefaultToNameSort = "keepr.didDefaultToNameSort"
    static let remindersEnabled = "keepr.remindersEnabled"
    /// Singular noun for the second axis — "Group", "Business", "App".
    static let groupLabel = "keepr.groupLabel"
    static let hasAskedForNotifications = "keepr.hasAskedForNotifications"
}

/// One instance of each, built once — `keeprServices()` runs on every body
/// evaluation and shouldn't be minting new contact stores.
enum LiveServices {
    static let contactStore = LiveContactStore()
    static let notificationService = LiveNotificationService()
    static let captureExtractor = HeuristicCaptureExtractor()
}

extension View {
    /// Injects the live services. Applied once, at the app entry point.
    func keeprServices() -> some View {
        self
            .environment(\.contactStore, LiveServices.contactStore)
            .environment(\.notificationService, LiveServices.notificationService)
            .environment(\.captureExtractor, LiveServices.captureExtractor)
    }
}

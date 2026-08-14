import SwiftData
import SwiftUI

@main
struct KeeprApp: App {

    @MainActor private static let container = KeeprStore.makeContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .keeprServices()
        }
        .modelContainer(Self.container)
    }
}

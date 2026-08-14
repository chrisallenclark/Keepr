import SwiftData
import SwiftUI

@main
struct KeeprApp: App {

    @MainActor private static let container: ModelContainer = {
        // Screenshot runs get a throwaway store with sample data, so automated
        // captures never touch — or depend on — anything real.
        guard LaunchOptions.isDemoMode else {
            return KeeprStore.makeContainer()
        }
        let container = KeeprStore.makeContainer(inMemory: true)
        SampleData.load(into: container.mainContext)
        return container
    }()

    var body: some Scene {
        WindowGroup {
            RootView()
                .keeprServices()
        }
        .modelContainer(Self.container)
    }
}

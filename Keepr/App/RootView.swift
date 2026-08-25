import SwiftData
import SwiftUI

/// The five places the app can be.
///
/// Four of these are the daily loop — what needs doing, who everyone is, what
/// you promised, and how they connect. The fifth is everything else: Search,
/// the two taxonomies, and Settings. Settings used to hide behind a gear in the
/// corner of Today, which is a fine place to put something nobody should find.
enum AppTab: String, Hashable, CaseIterable {
    case today
    case people
    case followUp
    case network
    case more

    var title: String {
        switch self {
        case .today: "Today"
        case .people: "People"
        case .followUp: "Follow Up"
        case .network: "Network"
        case .more: "More"
        }
    }

    var symbolName: String {
        switch self {
        case .today: "sun.max"
        case .people: "person.2"
        case .followUp: "bell"
        case .network: "point.3.connected.trianglepath.dotted"
        case .more: "ellipsis"
        }
    }
}

struct RootView: View {

    @AppStorage(PreferenceKey.hasOnboarded) private var hasOnboarded = false
    @AppStorage(PreferenceKey.contextMode) private var contextModeRaw = ContextMode.business.rawValue

    @State private var selectedTab: AppTab = .today
    @State private var isShowingOnboarding = false

    private var mode: Binding<ContextMode> {
        Binding(
            get: { ContextMode(rawValue: contextModeRaw) ?? .business },
            set: { contextModeRaw = $0.rawValue }
        )
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            TodayView(mode: mode)
                .tabItem { Label(AppTab.today.title, systemImage: AppTab.today.symbolName) }
                .tag(AppTab.today)

            PeopleView(mode: mode)
                .tabItem { Label(AppTab.people.title, systemImage: AppTab.people.symbolName) }
                .tag(AppTab.people)

            FollowUpsView(mode: mode)
                .tabItem { Label(AppTab.followUp.title, systemImage: AppTab.followUp.symbolName) }
                .tag(AppTab.followUp)

            NetworkView(mode: mode)
                .tabItem { Label(AppTab.network.title, systemImage: AppTab.network.symbolName) }
                .tag(AppTab.network)

            MoreView(mode: mode)
                .tabItem { Label(AppTab.more.title, systemImage: AppTab.more.symbolName) }
                .tag(AppTab.more)
        }
        .onAppear(perform: applyLaunchOptions)
        .fullScreenCover(isPresented: $isShowingOnboarding) {
            OnboardingView(mode: mode) {
                hasOnboarded = true
                isShowingOnboarding = false
            }
            .interactiveDismissDisabled()
        }
    }

    /// Normal launches decide onboarding from stored state. Screenshot runs skip
    /// it and jump straight to the screen being captured.
    private func applyLaunchOptions() {
        guard LaunchOptions.isDemoMode else {
            isShowingOnboarding = !hasOnboarded
            return
        }
        isShowingOnboarding = false
        if let screen = LaunchOptions.screen {
            selectedTab = screen.tab
        }
        if let contextMode = LaunchOptions.contextMode {
            contextModeRaw = contextMode.rawValue
        }
    }
}

#Preview {
    RootView()
        .modelContainer(.preview)
}

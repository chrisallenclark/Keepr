import SwiftData
import SwiftUI

/// The four places the app can be. Settings deliberately isn't one of them —
/// it lives behind a toolbar button on Today.
enum AppTab: String, Hashable, CaseIterable {
    case today
    case people
    case followUp
    case search

    var title: String {
        switch self {
        case .today: "Today"
        case .people: "People"
        case .followUp: "Follow Up"
        case .search: "Search"
        }
    }

    var symbolName: String {
        switch self {
        case .today: "sun.max"
        case .people: "person.2"
        case .followUp: "bell"
        case .search: "magnifyingglass"
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

            SearchView()
                .tabItem { Label(AppTab.search.title, systemImage: AppTab.search.symbolName) }
                .tag(AppTab.search)
        }
        .onAppear { isShowingOnboarding = !hasOnboarded }
        .fullScreenCover(isPresented: $isShowingOnboarding) {
            OnboardingView(mode: mode) {
                hasOnboarded = true
                isShowingOnboarding = false
            }
            .interactiveDismissDisabled()
        }
    }
}

#Preview {
    RootView()
        .modelContainer(.preview)
}

import SwiftData
import SwiftUI

/// Everything that isn't one of the four things you do every day.
///
/// Search lives here rather than on the tab bar because the People screen has
/// its own search field and that's where most searching starts; this is the one
/// that goes wider, across memories, interactions and notes. Settings lives here
/// because a gear tucked into the corner of Today was, in practice, hidden — a
/// tab called More is the first place anyone looks for it.
struct MoreView: View {

    @Binding var mode: ContextMode

    @AppStorage(PreferenceKey.groupLabel) private var groupLabel = GroupVocabulary.default.singular

    @State private var isShowingSearch = false
    @State private var isShowingGroups = false
    @State private var isShowingTypes = false
    @State private var isShowingSettings = false

    private var groupPlural: String { GroupVocabulary.plural(for: groupLabel) }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        isShowingSearch = true
                    } label: {
                        row(
                            "Search Everything",
                            detail: "Names, companies, memories, interactions, notes",
                            symbolName: "magnifyingglass"
                        )
                    }
                } footer: {
                    Text("Search ignores the Business and Personal switch — you search everything you've recorded.")
                }

                Section {
                    Button {
                        isShowingTypes = true
                    } label: {
                        row(
                            "Relationship Types",
                            detail: "What people are to you, and how often they're worth contacting",
                            symbolName: "tag"
                        )
                    }
                    Button {
                        isShowingGroups = true
                    } label: {
                        row(
                            groupPlural,
                            detail: "Where relationships come from — a gym, a business, an app",
                            symbolName: "mappin.and.ellipse"
                        )
                    }
                } header: {
                    SectionHeading("Organize")
                }

                Section {
                    Button {
                        isShowingSettings = true
                    } label: {
                        row(
                            "Settings",
                            detail: "Reminders, contacts access, your data",
                            symbolName: "gearshape"
                        )
                    }
                }
            }
            .keeprList()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("More")
                        .font(.keeprTitleInline)
                        .foregroundStyle(.primary)
                        .accessibilityAddTraits(.isHeader)
                }
            }
            .sheet(isPresented: $isShowingSearch) {
                // SearchView brings its own navigation stack.
                SearchView()
            }
            .sheet(isPresented: $isShowingGroups) {
                NavigationStack { GroupsView(mode: $mode) }
            }
            .sheet(isPresented: $isShowingTypes) {
                NavigationStack { RelationshipTypeEditor(mode: $mode) }
            }
            .sheet(isPresented: $isShowingSettings) {
                SettingsView()
            }
        }
    }

    private func row(_ title: String, detail: String, symbolName: String) -> some View {
        HStack(spacing: Theme.Spacing.medium) {
            Image(systemName: symbolName)
                .font(.body)
                .foregroundStyle(Color.accentColor)
                .frame(width: 26)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body)
                    .foregroundStyle(.primary)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.leading)
            }

            Spacer(minLength: Theme.Spacing.small)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 2)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    MoreView(mode: .constant(.business))
        .modelContainer(.preview)
}

import SwiftUI

/// Choose the SF Symbol that stands for a group or a relationship type.
///
/// The catalogue is a hand-checked subset rather than the whole symbol library.
/// There is no supported way to enumerate SF Symbols at runtime, and a name that
/// doesn't exist renders as a blank square with no error — so every entry here
/// has been verified to ship in iOS 17. A short list is also far quicker to pick
/// from than five thousand icons.
struct SymbolPicker: View {

    @Binding var selection: String

    @Environment(\.dismiss) private var dismiss

    /// Held apart from `selection` so Cancel really cancels.
    @State private var draft: String
    @State private var query = ""

    init(selection: Binding<String>) {
        _selection = selection
        _draft = State(initialValue: selection.wrappedValue)
    }

    private let columns = [GridItem(.adaptive(minimum: 56), spacing: Theme.Spacing.small)]

    private var visibleCategories: [Category] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { return Self.catalogue }

        return Self.catalogue.compactMap { category in
            let matches = category.symbols.filter { $0.contains(trimmed) }
            return matches.isEmpty ? nil : Category(title: category.title, symbols: matches)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, alignment: .leading, spacing: Theme.Spacing.small) {
                    ForEach(visibleCategories) { category in
                        Section {
                            ForEach(category.symbols, id: \.self) { name in
                                cell(name)
                            }
                        } header: {
                            Text(category.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, Theme.Spacing.medium)
                                .padding(.bottom, Theme.Spacing.tight)
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, Theme.Spacing.large)
            }
            .overlay {
                if visibleCategories.isEmpty {
                    ContentUnavailableView.search(text: query)
                }
            }
            .searchable(text: $query, prompt: "Search symbols")
            .navigationTitle("Choose a Symbol")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        selection = draft
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .presentationDragIndicator(.visible)
    }

    private func cell(_ name: String) -> some View {
        let isSelected = name == draft

        return Button {
            draft = name
            Haptics.selection()
        } label: {
            Image(systemName: name)
                .font(.title3)
                .foregroundStyle(isSelected ? Color.white : Color.primary)
                .frame(width: 52, height: 52)
                .background(
                    Circle().fill(isSelected ? Color.accentColor : Color(.tertiarySystemFill))
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Self.readableName(for: name))
        .accessibilityAddTraits(isSelected ? AccessibilityTraits.isSelected : [])
    }
}

// MARK: - Naming

extension SymbolPicker {

    /// VoiceOver reads "person.2" as a filename. Spacing the components out
    /// makes it a phrase.
    static func readableName(for symbolName: String) -> String {
        symbolName.replacingOccurrences(of: ".", with: " ")
    }
}

// MARK: - Catalogue

extension SymbolPicker {

    struct Category: Identifiable {
        let title: String
        let symbols: [String]

        var id: String { title }
    }

    /// Grouped the way someone shopping for an icon thinks, not the way Apple
    /// files them. Anything ambiguous was left out rather than guessed at.
    static let catalogue: [Category] = [
        Category(title: "People", symbols: [
            "person",
            "person.2",
            "person.3",
            "person.crop.circle",
            "person.2.circle",
            "person.badge.plus",
            "figure.2.and.child.holdinghands",
            "figure.and.child.holdinghands",
            "hand.wave",
            "face.smiling",
            "graduationcap",
            "crown"
        ]),
        Category(title: "Places", symbols: [
            "house",
            "building",
            "building.2",
            "building.columns",
            "map",
            "mappin",
            "mappin.and.ellipse",
            "globe",
            "airplane",
            "car",
            "bus",
            "tram",
            "bicycle",
            "beach.umbrella"
        ]),
        Category(title: "Activity", symbols: [
            "figure.run",
            "figure.walk",
            "figure.hiking",
            "figure.yoga",
            "figure.pool.swim",
            "figure.basketball",
            "figure.wave",
            "dumbbell",
            "sportscourt",
            "gamecontroller",
            "music.note",
            "music.mic",
            "guitars",
            "paintbrush",
            "camera",
            "theatermasks",
            "fork.knife"
        ]),
        Category(title: "Work", symbols: [
            "briefcase",
            "laptopcomputer",
            "doc.text",
            "folder",
            "calendar",
            "clock",
            "chart.line.uptrend.xyaxis",
            "chart.bar",
            "chart.pie",
            "dollarsign.circle",
            "creditcard",
            "banknote",
            "checkmark.seal",
            "target",
            "lightbulb",
            "megaphone",
            "wrench.and.screwdriver"
        ]),
        Category(title: "Objects", symbols: [
            "bag",
            "cart",
            "gift",
            "shippingbox",
            "envelope",
            "phone",
            "message",
            "bell",
            "key",
            "lock",
            "book",
            "books.vertical",
            "bookmark",
            "paperclip",
            "pencil",
            "scissors",
            "eyeglasses",
            "bed.double",
            "cup.and.saucer",
            "wineglass",
            "ticket",
            "headphones"
        ]),
        Category(title: "Nature", symbols: [
            "leaf",
            "tree",
            "flame",
            "drop",
            "bolt",
            "sun.max",
            "moon",
            "moon.stars",
            "cloud",
            "snowflake",
            "wind",
            "sparkles",
            "pawprint",
            "dog",
            "cat",
            "bird",
            "fish",
            "mountain.2",
            "water.waves",
            "tent"
        ]),
        Category(title: "Symbols", symbols: [
            "star",
            "heart",
            "flag",
            "tag",
            "circle",
            "square",
            "triangle",
            "diamond",
            "hexagon",
            "seal",
            "shield",
            "sparkle",
            "checkmark.circle",
            "exclamationmark.circle",
            "questionmark.circle",
            "info.circle",
            "rosette",
            "trophy",
            "gearshape",
            "circle.hexagongrid"
        ])
    ]
}

#Preview {
    SymbolPicker(selection: .constant("figure.run"))
        .modelContainer(.preview)
}

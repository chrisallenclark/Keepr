import SwiftUI

/// Choose the SF Symbol that stands for a group or a relationship type.
///
/// The catalogue is a hand-checked subset rather than the whole symbol library.
/// There is no supported way to enumerate SF Symbols at runtime, and a name that
/// doesn't exist renders as a blank square with no error — so every entry here
/// has been verified to ship in iOS 17. A short list is also far quicker to pick
/// from than five thousand icons.
///
/// It's weighted towards what this app is for. Nobody categorizing their
/// business contacts needs a snowflake, a cat and a tent; they need something
/// that reads as *client*, *past client*, *trainer*, *partner*, *meal prep*.
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
            let matches = category.symbols.filter { $0.matches(trimmed) }
            return matches.isEmpty ? nil : Category(title: category.title, symbols: matches)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, alignment: .leading, spacing: Theme.Spacing.small) {
                    ForEach(visibleCategories) { category in
                        Section {
                            ForEach(category.symbols) { symbol in
                                cell(symbol.name)
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
            .searchable(text: $query, prompt: "Search — client, gym, meal, money…")
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

    /// One symbol, plus the words someone would actually type looking for it.
    ///
    /// Searching by filename is useless here: nobody hunting for a client icon
    /// types "checkmark.seal". The keywords are what make this a picker rather
    /// than a list you scroll until something catches your eye.
    struct Symbol: Identifiable, Hashable {
        let name: String
        let keywords: String

        var id: String { name }

        init(_ name: String, _ keywords: String = "") {
            self.name = name
            self.keywords = keywords
        }

        func matches(_ query: String) -> Bool {
            name.contains(query) || keywords.contains(query)
        }
    }

    struct Category: Identifiable {
        let title: String
        let symbols: [Symbol]

        var id: String { title }
    }

    /// Ordered by how often this app's users reach for them, not how Apple files
    /// them. Anything ambiguous was left out rather than guessed at.
    static let catalogue: [Category] = [
        Category(title: "Clients & Deals", symbols: [
            .init("checkmark.seal", "client current active signed confirmed"),
            .init("person.crop.circle.badge.checkmark", "client active confirmed member"),
            .init("sparkles", "potential prospect lead new opportunity"),
            .init("flame", "lead hot warm urgent priority"),
            .init("target", "prospect goal pipeline aim"),
            .init("clock.arrow.circlepath", "past former previous lapsed alumni win back"),
            .init("person.badge.clock", "past former pending waiting"),
            .init("person.badge.plus", "new candidate recruit signup prospect"),
            .init("dollarsign.circle", "money paid revenue invoice paying"),
            .init("creditcard", "payment billing subscription"),
            .init("banknote", "money cash revenue paid"),
            .init("cart", "sale purchase order buyer customer"),
            .init("bag", "sale customer buyer retail"),
            .init("percent", "discount commission rate deal"),
            .init("chart.line.uptrend.xyaxis", "growth investor revenue results progress"),
            .init("signature", "contract signed agreement deal close"),
            .init("megaphone", "marketing promotion outreach campaign"),
            .init("star", "vip best favourite favorite top")
        ]),
        Category(title: "Business & Work", symbols: [
            .init("briefcase", "work business professional job company"),
            .init("building.2", "company office business corporate employer"),
            .init("building.columns", "bank institution finance legal"),
            .init("person.2", "partner colleague coworker connection two"),
            .init("person.2.circle", "colleague team coworker peers"),
            .init("person.3", "team staff crew group employees"),
            .init("network", "connection networking contacts web referral"),
            .init("link", "connection referral linked introduced"),
            .init("arrow.triangle.branch", "referral introduced source pipeline"),
            .init("laptopcomputer", "work remote tech online"),
            .init("doc.text", "contract paperwork proposal document"),
            .init("folder", "project account file client folder"),
            .init("calendar", "schedule booking appointment recurring"),
            .init("clock", "schedule time hourly session"),
            .init("chart.bar", "results numbers performance report"),
            .init("chart.pie", "share split breakdown equity"),
            .init("lightbulb", "advisor idea mentor consultant"),
            .init("graduationcap", "mentor coach teacher student education"),
            .init("crown", "vip executive ceo founder owner boss"),
            .init("trophy", "top best winner champion success"),
            .init("rosette", "award certified qualified accredited"),
            .init("key", "landlord access owner property"),
            .init("wrench.and.screwdriver", "contractor trades service repair vendor"),
            .init("shippingbox", "vendor supplier delivery product"),
            .init("gearshape", "operations service ops technical"),
            .init("globe", "international remote online web"),
            .init("airplane", "travel remote out of town")
        ]),
        Category(title: "Training & Fitness", symbols: [
            .init("dumbbell", "training gym lifting weights personal trainer client"),
            .init("figure.strengthtraining.traditional", "training gym lifting weights trainer"),
            .init("figure.run", "running cardio gym fitness client"),
            .init("figure.walk", "walking steps mobility"),
            .init("figure.yoga", "yoga stretch mobility class"),
            .init("figure.mind.and.body", "wellness recovery mindset coaching"),
            .init("figure.pool.swim", "swim pool aquatics"),
            .init("figure.basketball", "sport basketball league team"),
            .init("sportscourt", "gym court facility club team"),
            .init("stopwatch", "session timing interval conditioning"),
            .init("bolt.heart", "conditioning cardio energy health"),
            .init("waveform.path.ecg", "health progress metrics results"),
            .init("scalemass", "weight progress body measurement"),
            .init("heart", "health wellness personal care")
        ]),
        Category(title: "Food & Meal Prep", symbols: [
            .init("fork.knife", "meal prep food nutrition dinner client"),
            .init("takeoutbag.and.cup.and.straw", "meal prep delivery takeout order food"),
            .init("basket", "grocery shopping meal prep order"),
            .init("carrot", "nutrition food vegetables diet meal"),
            .init("leaf", "nutrition healthy plant diet"),
            .init("cup.and.saucer", "coffee meeting catch up chat"),
            .init("wineglass", "drinks dinner social client entertaining"),
            .init("birthday.cake", "birthday celebration anniversary")
        ]),
        Category(title: "People & Personal", symbols: [
            .init("person", "person individual acquaintance someone"),
            .init("person.crop.circle", "profile contact individual"),
            .init("person.text.rectangle", "contact card details record"),
            .init("hand.wave", "friend hello greeting casual"),
            .init("face.smiling", "friend friendly casual social"),
            .init("hands.sparkles", "thanks gratitude helper supporter"),
            .init("house", "family home household personal"),
            .init("figure.2.and.child.holdinghands", "family kids parents household"),
            .init("figure.and.child.holdinghands", "family parent child"),
            .init("gift", "gift birthday thank you present"),
            .init("moon.stars", "night out evening social bar"),
            .init("music.note", "music band hobby social"),
            .init("ticket", "event concert game invite"),
            .init("camera", "photography creative hobby"),
            .init("book", "reading study book club"),
            .init("car", "commute local drive neighbour"),
            .init("mappin.and.ellipse", "place location venue local"),
            .init("building", "neighbourhood building apartment local")
        ]),
        Category(title: "Marks & Status", symbols: [
            .init("tag", "tag label category general"),
            .init("bookmark", "saved shortlist keep"),
            .init("flag", "flagged important follow up"),
            .init("pin", "pinned priority important"),
            .init("checkmark.circle", "done complete confirmed active"),
            .init("exclamationmark.circle", "urgent attention issue"),
            .init("questionmark.circle", "unknown unsure to decide uncategorized"),
            .init("hourglass", "waiting pending slow stalled"),
            .init("archivebox", "archived inactive dormant cold"),
            .init("bell", "reminder follow up notify"),
            .init("hand.thumbsup", "good positive approved happy"),
            .init("shield", "trusted protected private"),
            .init("lock", "private confidential sensitive"),
            .init("eye", "watching monitor keep an eye"),
            .init("seal", "official verified certified"),
            .init("circle.hexagongrid", "both mixed general other"),
            .init("square.grid.2x2", "group set collection")
        ])
    ]
}

#Preview {
    SymbolPicker(selection: .constant("dumbbell"))
        .modelContainer(.preview)
}

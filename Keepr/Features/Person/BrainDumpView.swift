import SwiftData
import SwiftUI

/// Empty your head about one person, and let the app file it.
///
/// The window after meeting someone is short and rich: you know their kids'
/// names, what they're worried about, what they said they'd do. A day later
/// most of it is gone. Typing it as one long note preserves the words but not
/// the usefulness — nobody re-reads a paragraph six months on.
///
/// So this takes the paragraph and hands back one card per fact, each filed
/// under a category, and asks you to confirm. The confirming is the point:
/// the app never writes to someone's record from a guess.
struct BrainDumpView: View {

    let person: Person

    @Environment(\.captureExtractor) private var extractor
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var text = ""
    @State private var drafts: [MemoryDraft] = []
    @State private var hasSorted = false
    @State private var isSorting = false
    @State private var alsoLogInteraction = false

    @FocusState private var isWriting: Bool

    private var trimmed: String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var keptCount: Int {
        drafts.filter(\.isSelected).count
    }

    var body: some View {
        NavigationStack {
            Group {
                if hasSorted {
                    reviewList
                } else {
                    writingPad
                }
            }
            .navigationTitle(hasSorted ? "Keep What Matters" : "Brain Dump")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(hasSorted ? "Back" : "Cancel") {
                        if hasSorted {
                            withAnimation { hasSorted = false }
                        } else {
                            dismiss()
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if hasSorted {
                        Button("Save \(keptCount)", action: save)
                            .fontWeight(.semibold)
                            .disabled(keptCount == 0)
                    } else {
                        Button("Sort It Out", action: sort)
                            .fontWeight(.semibold)
                            .disabled(trimmed.isEmpty || isSorting)
                    }
                }
            }
        }
        .presentationDragIndicator(.visible)
    }

    // MARK: - Writing

    private var writingPad: some View {
        VStack(alignment: .leading, spacing: 0) {
            TextEditor(text: $text)
                .focused($isWriting)
                .font(.body)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, Theme.Spacing.medium)
                .overlay(alignment: .topLeading) {
                    if text.isEmpty {
                        Text(Self.placeholder)
                            .font(.body)
                            .foregroundStyle(.tertiary)
                            .padding(.horizontal, Theme.Spacing.medium + 5)
                            .padding(.top, 8)
                            .allowsHitTesting(false)
                    }
                }

            Text("Write it however it comes out — or hold the mic on your keyboard and talk. Keepr splits it into separate facts and files them; you decide what's kept.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(Theme.Spacing.medium)
        }
        .onAppear { isWriting = true }
    }

    private static let placeholder = """
        Everything you know about them.

        Their family, what they do, what they're working towards, what they told \
        you, anything you'd want in front of you next time you see them.
        """

    // MARK: - Review

    private var reviewList: some View {
        List {
            Section {
                ForEach($drafts) { $draft in
                    draftRow($draft)
                }
            } header: {
                HStack {
                    Text("\(drafts.count) found")
                    Spacer()
                    Button(keptCount == drafts.count ? "Keep None" : "Keep All") {
                        toggleAll()
                    }
                    .font(.caption.weight(.semibold))
                    .textCase(nil)
                }
            } footer: {
                Text("Tap a category to change it, or the text to edit it. Anything switched off is thrown away.")
            }

            Section {
                Toggle("Also log this as an interaction", isOn: $alsoLogInteraction)
                    .font(.subheadline)
            } footer: {
                Text("Records that you spoke today and restarts how often Keepr expects you to reach out to them.")
            }
        }
        .listStyle(.insetGrouped)
        .overlay {
            if drafts.isEmpty {
                ContentUnavailableView(
                    "Nothing to file",
                    systemImage: "text.badge.xmark",
                    description: Text("Keepr couldn't find separate facts in that. Go back and try a line per thing.")
                )
            }
        }
    }

    private func draftRow(_ draft: Binding<MemoryDraft>) -> some View {
        HStack(alignment: .top, spacing: Theme.Spacing.medium) {
            Button {
                draft.wrappedValue.isSelected.toggle()
                Haptics.selection()
            } label: {
                Image(systemName: draft.wrappedValue.isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(draft.wrappedValue.isSelected ? Color.accentColor : Color.secondary)
            }
            .buttonStyle(.plain)
            .padding(.top, 2)

            VStack(alignment: .leading, spacing: Theme.Spacing.tight) {
                TextField("Fact", text: draft.content, axis: .vertical)
                    .font(.subheadline)
                    .foregroundStyle(draft.wrappedValue.isSelected ? .primary : .secondary)

                HStack(spacing: Theme.Spacing.small) {
                    Menu {
                        ForEach(MemoryCategory.allCases) { category in
                            Button {
                                draft.wrappedValue.category = category
                                Haptics.selection()
                            } label: {
                                Label(category.title, systemImage: category.symbolName)
                            }
                        }
                    } label: {
                        Label(
                            draft.wrappedValue.category.title,
                            systemImage: draft.wrappedValue.category.symbolName
                        )
                        .font(.caption2.weight(.medium))
                        .labelStyle(.titleAndIcon)
                        .padding(.horizontal, Theme.Spacing.small)
                        .padding(.vertical, 3)
                        .background(.quaternary, in: .rect(cornerRadius: Theme.Radius.chip))
                    }

                    Button {
                        draft.wrappedValue.importance = draft.wrappedValue.importance == .high ? .normal : .high
                        Haptics.selection()
                    } label: {
                        Image(systemName: draft.wrappedValue.importance == .high ? "star.fill" : "star")
                            .font(.caption)
                            .foregroundStyle(draft.wrappedValue.importance == .high ? Color.yellow : Color.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Mark as important")
                }
            }
        }
        .padding(.vertical, 2)
    }

    // MARK: - Actions

    private func sort() {
        isSorting = true
        let input = trimmed
        Task {
            let found = await extractor.facts(from: input)
            drafts = found
            hasSorted = true
            isSorting = false
            isWriting = false
            Haptics.success()
        }
    }

    private func toggleAll() {
        let turningOff = keptCount == drafts.count
        for index in drafts.indices {
            drafts[index].isSelected = !turningOff
        }
        Haptics.selection()
    }

    private func save() {
        let now = Date()
        var source: Interaction?

        if alsoLogInteraction {
            let interaction = Interaction(
                kind: .inPerson,
                occurredAt: now,
                summary: "Caught up — notes added",
                person: person
            )
            context.insert(interaction)
            person.lastInteractionAt = now
            Outreach.markReplied(person, at: now)
            source = interaction
        }

        for draft in drafts where draft.isSelected {
            let content = draft.content.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !content.isEmpty else { continue }
            context.insert(
                Memory(
                    content: content,
                    category: draft.category,
                    importance: draft.importance,
                    createdAt: now,
                    person: person,
                    sourceInteraction: source
                )
            )
        }

        person.touch()
        try? context.save()
        Haptics.success()
        dismiss()
    }
}

#Preview {
    BrainDumpPreview()
        .modelContainer(.preview)
}

private struct BrainDumpPreview: View {
    @Query(sort: \Person.createdAt) private var people: [Person]

    var body: some View {
        if let person = people.first {
            BrainDumpView(person: person)
        } else {
            Text("No sample data")
        }
    }
}

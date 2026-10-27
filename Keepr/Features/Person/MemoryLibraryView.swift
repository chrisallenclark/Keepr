import SwiftData
import SwiftUI

/// Everything you know about one person, filed.
///
/// The profile deliberately shows only a few facts; this is where the rest
/// lives. Grouped rather than listed, because forty facts in one column is the
/// same as no facts at all — and dated, because "why is this here?" is the
/// question a two-year-old note has to be able to answer.
struct MemoryLibraryView: View {

    let person: Person

    @Environment(\.modelContext) private var context

    @State private var query = ""
    @State private var isShowingBrainDump = false
    @State private var refileResult: Int?

    private var groups: [MemoryGroup] {
        let all = person.visibleMemories
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return MemoryEngine.grouped(all) }
        return MemoryEngine.grouped(all.filter { $0.content.localizedStandardContains(trimmed) })
    }

    var body: some View {
        List {
            ForEach(groups) { group in
                Section {
                    ForEach(group.memories) { memory in
                        row(memory)
                    }
                } header: {
                    HStack(spacing: Theme.Spacing.small) {
                        Image(systemName: group.category.symbolName)
                        Text(group.category.title)
                        Spacer()
                        if group.category == .other, !refilable.isEmpty {
                            Button("File \(refilable.count)", action: refileOthers)
                                .font(.caption.weight(.semibold))
                                .textCase(nil)
                        }
                        Text("\(group.count)")
                            .foregroundStyle(.tertiary)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $query, prompt: "Search what you know")
        .navigationTitle("What You Know")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    isShowingBrainDump = true
                } label: {
                    Label("Brain Dump", systemImage: "square.and.pencil")
                }
            }
        }
        .sheet(isPresented: $isShowingBrainDump) {
            BrainDumpView(person: person)
        }
        .alert("Filed", isPresented: refileAlert) {
            Button("OK", role: .cancel) { refileResult = nil }
        } message: {
            Text(refileResult == 1
                 ? "One fact moved out of Other."
                 : "\(refileResult ?? 0) facts moved out of Other.")
        }
        .overlay {
            if groups.isEmpty {
                emptyState
            }
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        if query.isEmpty {
            ContentUnavailableView {
                Label("Nothing recorded yet", systemImage: "brain")
            } description: {
                Text("Everything you learn about \(person.displayName) can live here.")
            } actions: {
                Button("Brain Dump") { isShowingBrainDump = true }
                    .buttonStyle(.borderedProminent)
            }
        } else {
            ContentUnavailableView.search(text: query)
        }
    }

    private func row(_ memory: Memory) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(alignment: .top, spacing: Theme.Spacing.small) {
                Text(memory.content)
                    .font(.subheadline)
                Spacer(minLength: 0)
                if memory.importance == .high {
                    Image(systemName: "star.fill")
                        .font(.caption2)
                        .foregroundStyle(.yellow)
                        .accessibilityLabel("Important")
                }
            }

            // Where it came from, so an old note can explain itself.
            Text(provenance(for: memory))
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 2)
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                delete(memory)
            } label: {
                Label("Delete", systemImage: "trash")
            }
            Button {
                archive(memory)
            } label: {
                Label("Archive", systemImage: "archivebox")
            }
            .tint(.gray)
        }
        .swipeActions(edge: .leading) {
            Button {
                toggleImportance(memory)
            } label: {
                Label("Important", systemImage: "star")
            }
            .tint(.yellow)
        }
        .contextMenu {
            Menu("Move to") {
                ForEach(MemoryCategory.allCases) { category in
                    Button {
                        move(memory, to: category)
                    } label: {
                        // Title plus one line of "what belongs here", because
                        // Goals and Progress are indistinguishable from their
                        // names alone.
                        Text(category.title)
                        Text(category.explanation)
                        Image(systemName: category.symbolName)
                    }
                }
            }
        }
    }

    private func provenance(for memory: Memory) -> String {
        if let source = memory.sourceInteraction {
            return "From \(source.headline.lowercased()) · \(RelativeDate.past(memory.createdAt))"
        }
        return "Added \(RelativeDate.past(memory.createdAt).lowercased())"
    }

    // MARK: - Re-filing

    /// Facts in Other that today's rules would now place somewhere.
    private var refilable: [(memory: Memory, category: MemoryCategory)] {
        MemoryEngine.refilable(person.memoryList) { content in
            HeuristicCaptureExtractor.category(of: content)
        }
    }

    private var refileAlert: Binding<Bool> {
        Binding(get: { refileResult != nil }, set: { if !$0 { refileResult = nil } })
    }

    private func refileOthers() {
        let proposals = refilable
        guard !proposals.isEmpty else { return }

        withAnimation {
            for proposal in proposals {
                proposal.memory.category = proposal.category
                proposal.memory.updatedAt = Date()
            }
        }
        try? context.save()
        refileResult = proposals.count
        Haptics.success()
    }

    // MARK: - Actions

    private func move(_ memory: Memory, to category: MemoryCategory) {
        withAnimation {
            memory.category = category
            memory.updatedAt = Date()
        }
        try? context.save()
        Haptics.selection()
    }

    private func toggleImportance(_ memory: Memory) {
        withAnimation {
            memory.importance = memory.importance == .high ? .normal : .high
            memory.updatedAt = Date()
        }
        try? context.save()
        Haptics.light()
    }

    private func archive(_ memory: Memory) {
        withAnimation {
            memory.isArchived = true
            memory.updatedAt = Date()
        }
        try? context.save()
    }

    private func delete(_ memory: Memory) {
        withAnimation { context.delete(memory) }
        try? context.save()
    }
}

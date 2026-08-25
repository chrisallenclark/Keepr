import SwiftData
import SwiftUI

/// One person in the middle, everyone they're linked to around them.
///
/// The map answers a question the profile can't: not "who is Amy connected to"
/// but "how does this corner of my world hang together" — who introduced whom,
/// which client came from which referral. Tapping anyone re-centres on them, so
/// you walk outward a hop at a time instead of trying to read the whole graph.
///
/// Every connection is also listed underneath. That isn't a fallback for a
/// picture that didn't work — a diagram can't be read aloud, can't be swiped,
/// and gets tight at large text sizes, and the list is how VoiceOver, Dynamic
/// Type and anyone who just wants to tap a name all get through the screen.
struct RelationshipMapView: View {

    let person: Person
    @Binding var mode: ContextMode

    /// Where the map stops being a picture — see `NetworkLayout.maxVisible`.
    private var connections: [PersonConnection] { person.connections }
    private var mapped: [PersonConnection] { Array(connections.prefix(NetworkLayout.maxVisible)) }
    private var placements: [NetworkPlacement] { NetworkLayout.place(count: connections.count) }
    private var overflow: Int { NetworkLayout.overflow(count: connections.count) }

    var body: some View {
        List {
            if connections.isEmpty {
                Section {
                    ContentUnavailableView {
                        Label("No connections yet", systemImage: "person.2.slash")
                    } description: {
                        Text("Link \(person.displayName) to someone — who introduced you, who they work with, who they referred.")
                    }
                    .keeprBareRow()
                }
            } else {
                Section {
                    RelationshipMap(
                        person: person,
                        connections: mapped,
                        placements: placements,
                        mode: $mode
                    )
                    .listRowInsets(EdgeInsets())
                    .keeprBareRow()
                }

                Section {
                    ForEach(connections) { connection in
                        NavigationLink {
                            RelationshipMapView(person: connection.person, mode: $mode)
                        } label: {
                            ConnectionRow(connection: connection)
                        }
                    }
                } header: {
                    SectionHeading(
                        "\(person.firstNameForHeading)'s Connections",
                        count: connections.count
                    )
                } footer: {
                    if overflow > 0 {
                        Text("The map shows the first \(NetworkLayout.maxVisible). All \(connections.count) are listed here.")
                    }
                }
            }

            Section {
                NavigationLink {
                    PersonProfileView(person: person, mode: $mode)
                } label: {
                    Label("Open \(person.displayName)'s profile", systemImage: "person.crop.circle")
                        .font(.subheadline)
                }
            }
        }
        .keeprList()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(person.displayName)
                    .font(.keeprTitleInline)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .accessibilityAddTraits(.isHeader)
            }
        }
    }
}

// MARK: - The map itself

/// The drawing: edges behind, people on top.
///
/// Laid out from `NetworkLayout`'s unit offsets, so this view only decides how
/// much room there is and never how anything is arranged.
private struct RelationshipMap: View {

    let person: Person
    let connections: [PersonConnection]
    let placements: [NetworkPlacement]
    @Binding var mode: ContextMode

    /// Enough room for an avatar, a name on two lines and a badge.
    private let nodeWidth: CGFloat = 98
    private let nodeHeight: CGFloat = 104

    private var hasOuterRing: Bool { placements.contains { $0.ring == 1 } }

    private var mapHeight: CGFloat { hasOuterRing ? 460 : 380 }

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            let centre = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(
                (size.width - nodeWidth) / 2,
                (size.height - nodeHeight) / 2
            )

            ZStack {
                edges(centre: centre, radius: radius)

                ForEach(placements) { placement in
                    if let connection = connections[safe: placement.index] {
                        let point = position(placement, centre: centre, radius: radius)

                        edgeLabel(connection.label, centre: centre, node: point)

                        NavigationLink {
                            RelationshipMapView(person: connection.person, mode: $mode)
                        } label: {
                            NodeView(person: connection.person, isCentre: false)
                        }
                        .buttonStyle(.plain)
                        .frame(width: nodeWidth)
                        .position(point)
                    }
                }

                NodeView(person: person, isCentre: true)
                    .frame(width: nodeWidth)
                    .position(centre)
            }
        }
        .frame(height: mapHeight)
        .padding(.vertical, Theme.Spacing.small)
        // The diagram repeats what the list below says, and a shape read out
        // node by node is worse than the list either way.
        .accessibilityHidden(true)
    }

    private func position(_ placement: NetworkPlacement, centre: CGPoint, radius: CGFloat) -> CGPoint {
        CGPoint(
            x: centre.x + placement.offset.x * radius,
            y: centre.y + placement.offset.y * radius
        )
    }

    private func edges(centre: CGPoint, radius: CGFloat) -> some View {
        Canvas { canvasContext, _ in
            for placement in placements {
                let point = position(placement, centre: centre, radius: radius)
                var path = Path()
                path.move(to: centre)
                path.addLine(to: point)
                canvasContext.stroke(
                    path,
                    with: .color(Theme.Palette.hairline),
                    lineWidth: 1.5
                )
            }
        }
    }

    /// The label sits on the line, on a patch of the ground colour, so the edge
    /// appears to run behind the words rather than through them.
    private func edgeLabel(_ text: String, centre: CGPoint, node: CGPoint) -> some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(Theme.Palette.ground, in: .capsule)
            .position(
                x: centre.x + (node.x - centre.x) * 0.5,
                y: centre.y + (node.y - centre.y) * 0.5
            )
    }
}

// MARK: - Node

private struct NodeView: View {

    let person: Person
    let isCentre: Bool

    var body: some View {
        VStack(spacing: Theme.Spacing.tight) {
            Avatar(person: person, size: isCentre ? .large : .medium)
                .overlay {
                    if isCentre {
                        Circle().strokeBorder(Color.accentColor, lineWidth: 2)
                    }
                }

            Text(person.displayName)
                .font(isCentre ? .subheadline.weight(.bold) : .caption.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(2)
                .multilineTextAlignment(.center)

            if let tag = person.tagList.min(by: { $0.sortOrder < $1.sortOrder }) {
                Text(tag.name)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(tag.tint.foreground)
                    .lineLimit(1)
            }
        }
    }
}

// MARK: - Safe indexing

private extension Array {
    /// The layout and the connection list are built from the same count, but
    /// reading a node out of an array by an index computed elsewhere is exactly
    /// the kind of thing that crashes once, in front of someone.
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

#Preview {
    NavigationStack {
        RelationshipMapView(
            person: Person(givenName: "Amy", familyName: "Lewis"),
            mode: .constant(.business)
        )
    }
    .modelContainer(.preview)
}

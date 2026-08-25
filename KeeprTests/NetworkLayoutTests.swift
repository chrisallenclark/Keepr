import CoreGraphics
import Foundation
import Testing

@testable import Keepr

/// The relationship map is the one screen whose correctness is arithmetic, so
/// the arithmetic is pinned here rather than judged by eye in a simulator.
@Suite("NetworkLayout")
struct NetworkLayoutTests {

    /// Floating-point geometry never lands exactly on the number you wrote.
    private func isClose(_ lhs: CGFloat, _ rhs: CGFloat, within tolerance: CGFloat = 0.0001) -> Bool {
        abs(lhs - rhs) < tolerance
    }

    private func magnitude(_ point: CGPoint) -> CGFloat {
        sqrt(point.x * point.x + point.y * point.y)
    }

    // MARK: - Shape

    @Test("Nobody to place produces nothing to draw")
    func emptyGraph() {
        #expect(NetworkLayout.place(count: 0).isEmpty)
    }

    @Test("Every connection gets exactly one placement, in the order given")
    func placementsMatchConnections() {
        for count in 1...NetworkLayout.maxVisible {
            let placements = NetworkLayout.place(count: count)
            #expect(placements.count == count)
            #expect(placements.map(\.index) == Array(0..<count))
        }
    }

    @Test("The first connection sits straight up, so the map reads the same every time")
    func firstNodeIsAtTheTop() {
        let first = NetworkLayout.place(count: 4)[0]
        #expect(isClose(first.offset.x, 0))
        #expect(isClose(first.offset.y, -1))
    }

    @Test("Four connections land at the compass points")
    func fourNodesFormACross() {
        let offsets = NetworkLayout.place(count: 4).map(\.offset)

        #expect(isClose(offsets[0].x, 0) && isClose(offsets[0].y, -1))  // top
        #expect(isClose(offsets[1].x, 1) && isClose(offsets[1].y, 0))   // right
        #expect(isClose(offsets[2].x, 0) && isClose(offsets[2].y, 1))   // bottom
        #expect(isClose(offsets[3].x, -1) && isClose(offsets[3].y, 0))  // left
    }

    @Test("A single ring sits at full radius, so the view can scale by one number")
    func singleRingIsUnitLength() {
        for count in 1...NetworkLayout.innerRingCapacity {
            for placement in NetworkLayout.place(count: count) {
                #expect(placement.ring == 0)
                #expect(isClose(magnitude(placement.offset), 1))
            }
        }
    }

    @Test("Nodes on a ring are evenly spaced")
    func nodesAreEvenlySpaced() {
        let placements = NetworkLayout.place(count: 5)
        let angles = placements.map { atan2($0.offset.y, $0.offset.x) }
        let step = 2 * CGFloat.pi / 5

        for (earlier, later) in zip(angles, angles.dropFirst()) {
            // Normalize, since the angle wraps through π on the way round.
            var delta = later - earlier
            while delta < 0 { delta += 2 * .pi }
            #expect(isClose(delta, step, within: 0.001))
        }
    }

    // MARK: - Two rings

    @Test("A crowded graph splits into two rings rather than crowding one")
    func splitsIntoTwoRings() {
        let placements = NetworkLayout.place(count: 10)
        let rings = Set(placements.map(\.ring))

        #expect(rings == [0, 1])
        #expect(placements.filter { $0.ring == 0 }.count == 5)
        #expect(placements.filter { $0.ring == 1 }.count == 5)
    }

    @Test("The split is even, never six crowded nodes and one orbiter")
    func ringsAreBalanced() {
        for count in (NetworkLayout.innerRingCapacity + 1)...NetworkLayout.maxVisible {
            let sizes = NetworkLayout.distribute(count: count)
            #expect(sizes.reduce(0, +) == count)
            #expect(sizes.count == 2)
            // The outer ring has more circumference, so it may hold more —
            // but never fewer than the inner one.
            #expect(sizes[1] >= sizes[0])
        }
    }

    @Test("The inner ring sits inside the outer one")
    func innerRingIsCloser() {
        let placements = NetworkLayout.place(count: 10)
        let inner = placements.filter { $0.ring == 0 }.map { magnitude($0.offset) }
        let outer = placements.filter { $0.ring == 1 }.map { magnitude($0.offset) }

        #expect(inner.allSatisfy { isClose($0, NetworkLayout.innerRingFactor) })
        #expect(outer.allSatisfy { isClose($0, 1) })
        #expect((inner.max() ?? 0) < (outer.min() ?? 0))
    }

    @Test("The outer ring is rotated, so an outer node never hides behind an inner one")
    func outerRingIsOffset() {
        let placements = NetworkLayout.place(count: 12)
        let innerAngles = placements.filter { $0.ring == 0 }.map { atan2($0.offset.y, $0.offset.x) }
        let outerAngles = placements.filter { $0.ring == 1 }.map { atan2($0.offset.y, $0.offset.x) }

        for outer in outerAngles {
            for inner in innerAngles {
                #expect(!isClose(outer, inner, within: 0.05))
            }
        }
    }

    // MARK: - Overflow

    @Test("A very large network is capped, and says how many it left out")
    func capsAndReportsOverflow() {
        let placements = NetworkLayout.place(count: 30)

        #expect(placements.count == NetworkLayout.maxVisible)
        #expect(NetworkLayout.overflow(count: 30) == 30 - NetworkLayout.maxVisible)
        #expect(NetworkLayout.overflow(count: 3) == 0)
    }

    @Test("The same graph lays out identically every time")
    func layoutIsDeterministic() {
        #expect(NetworkLayout.place(count: 7) == NetworkLayout.place(count: 7))
    }
}

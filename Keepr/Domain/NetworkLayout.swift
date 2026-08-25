import CoreGraphics
import Foundation

/// Where one person sits on the relationship map, relative to the person in the
/// middle.
///
/// `offset` is in **unit space**: the outermost ring has magnitude 1, so the
/// view multiplies by whatever radius it actually has room for and doesn't have
/// to know anything about geometry.
struct NetworkPlacement: Equatable, Identifiable, Sendable {

    /// Index into the connection list this placement is for.
    let index: Int
    /// 0 is the inner ring. Only ever 0 or 1.
    let ring: Int
    /// Direction and distance from the centre, magnitude ≤ 1.
    let offset: CGPoint

    var id: Int { index }
}

/// Arranges a person's connections around them.
///
/// Pure and deterministic, like the rest of `Domain/` — the map is the one
/// screen where "does it look right" is a question about arithmetic, and
/// arithmetic is much easier to check without a simulator in the way.
///
/// The shape is a ring rather than a force-directed graph on purpose. A physics
/// layout looks impressive on a poster and is unreadable on a phone: it
/// wanders between launches, so the person who was on the left last time isn't
/// this time, and it needs the whole graph in view to settle. A ring is the same
/// every time you open it, which is what makes the map memorable rather than
/// merely pretty.
enum NetworkLayout {

    /// Past this, a ring's labels start colliding at phone width.
    static let innerRingCapacity = 6

    /// Beyond this the map stops being a picture and becomes a bad list, so the
    /// remainder is shown as one instead.
    static let maxVisible = 14

    /// How far in the inner ring sits when there are two.
    static let innerRingFactor: CGFloat = 0.55

    /// The first node sits straight up. Everything else is measured from there,
    /// so a person's map looks the same every time it's opened.
    static let startAngle: CGFloat = -.pi / 2

    /// Placements for `count` connections, in the order they were given.
    static func place(count: Int) -> [NetworkPlacement] {
        guard count > 0 else { return [] }

        let visible = min(count, maxVisible)
        let ringSizes = distribute(count: visible)

        var placements: [NetworkPlacement] = []
        var index = 0

        for (ring, size) in ringSizes.enumerated() where size > 0 {
            let radius = ringSizes.count == 1 ? 1 : (ring == 0 ? innerRingFactor : 1)
            let step = 2 * CGFloat.pi / CGFloat(size)

            // Half a step of rotation on the outer ring, so an outer node never
            // hides directly behind an inner one and their two edges never
            // become one line.
            let rotation = ring == 0 ? 0 : step / 2

            for position in 0..<size {
                let angle = startAngle + rotation + step * CGFloat(position)
                placements.append(
                    NetworkPlacement(
                        index: index,
                        ring: ring,
                        offset: CGPoint(x: cos(angle) * radius, y: sin(angle) * radius)
                    )
                )
                index += 1
            }
        }

        return placements
    }

    /// How many nodes go on each ring.
    ///
    /// Splits roughly in half rather than filling the inner ring first: six
    /// crowded nodes with one lonely orbiter outside them reads as a bug, and
    /// two even rings read as a diagram.
    static func distribute(count: Int) -> [Int] {
        guard count > innerRingCapacity else { return [count] }
        let inner = min(innerRingCapacity, count / 2)
        return [inner, count - inner]
    }

    /// How many connections didn't fit on the map.
    static func overflow(count: Int) -> Int {
        max(0, count - maxVisible)
    }
}

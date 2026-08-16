import Foundation
import Testing

@testable import Keepr

/// Interaction kinds are stored as raw strings, so the list can grow — but a
/// raw value that changes silently reclassifies interactions people already
/// logged. These tests pin the wire format and check the list stays usable.
@Suite("Interaction kinds")
struct InteractionKindTests {

    @Test("Raw values are stable, so already-logged interactions keep their kind")
    func rawValuesAreStable() {
        let expected: Set<String> = [
            "inPerson", "call", "video", "text", "dm",
            "email", "meeting", "meal", "event", "other"
        ]
        #expect(Set(InteractionKind.allCases.map(\.rawValue)) == expected)
    }

    @Test("An unknown raw value doesn't exist, so decoding falls back rather than crashing")
    func unknownRawValuesAreNil() {
        #expect(InteractionKind(rawValue: "carrierPigeon") == nil)
    }

    @Test("Every kind is presentable")
    func everyKindHasWordsAndAnIcon() {
        for kind in InteractionKind.allCases {
            #expect(!kind.title.isEmpty)
            #expect(!kind.symbolName.isEmpty)
        }
    }

    @Test("Kinds cover how people actually reach each other, without becoming a wall")
    func theListIsBroadButBounded() {
        // Both halves matter. Too few and someone's normal week has no home;
        // too many and picking one costs more than the precision is worth.
        #expect(InteractionKind.allCases.count >= 8)
        #expect(InteractionKind.allCases.count <= 12)

        let titles = Set(InteractionKind.allCases.map(\.title))
        for expected in ["Video Call", "DM", "Coffee or Meal", "Event"] {
            #expect(titles.contains(expected), "\(expected) is a normal way business gets done")
        }
    }

    @Test("Names are distinct, so the picker never shows the same word twice")
    func titlesAreUnique() {
        #expect(Set(InteractionKind.allCases.map(\.title)).count == InteractionKind.allCases.count)
    }
}

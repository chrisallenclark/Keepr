import Foundation
import SwiftData

/// A file kept on someone's profile — a photo of their consultation results, a
/// signed form, a PDF of their plan.
///
/// The bytes live in `.externalStorage`, so the SwiftData store itself stays
/// small and a profile row never loads a 4 MB photo just to show its name.
/// `thumbnailData` is the opposite trade: small enough to keep inline so a list
/// of twenty files scrolls without touching the big blobs at all.
///
/// Files are the most sensitive thing in the app — a body-composition scan is
/// health information about someone who isn't the user. Nothing here is ever
/// logged, and nothing leaves the device.
@Model
final class PersonFile {

    var id: UUID = UUID()

    /// What the user calls it. Defaults to something useful rather than
    /// "IMG_4821" — a date-stamped name is what makes a stack of scans
    /// readable a year later.
    var title: String = ""

    var kindRaw: String = PersonFileKind.image.rawValue

    /// Lowercase, no dot. Used to write a real filename when sharing.
    var fileExtension: String = "jpg"

    /// The file itself. Optional because CloudKit requires it, and because a
    /// failed import should leave a row the user can delete rather than a crash.
    @Attribute(.externalStorage) var data: Data?

    /// A small JPEG preview, kept inline so lists don't fault in the original.
    var thumbnailData: Data?

    var byteCount: Int = 0

    /// When the thing in the file happened — the day of the consultation, not
    /// the day it was imported. Editable, because photos get added later.
    var capturedAt: Date = Date()
    var createdAt: Date = Date()

    /// One line of context. "Week 1 InBody — 24% body fat."
    var note: String?

    var person: Person?

    /// The interaction this came out of, when it came from one.
    var sourceInteraction: Interaction?

    init(
        title: String,
        kind: PersonFileKind = .image,
        fileExtension: String = "jpg",
        data: Data? = nil,
        thumbnailData: Data? = nil,
        byteCount: Int = 0,
        capturedAt: Date = Date(),
        createdAt: Date = Date(),
        note: String? = nil,
        person: Person? = nil,
        sourceInteraction: Interaction? = nil
    ) {
        self.id = UUID()
        self.title = title
        self.kindRaw = kind.rawValue
        self.fileExtension = fileExtension
        self.data = data
        self.thumbnailData = thumbnailData
        self.byteCount = byteCount
        self.capturedAt = capturedAt
        self.createdAt = createdAt
        self.note = note
        self.person = person
        self.sourceInteraction = sourceInteraction
    }
}

extension PersonFile {

    var kind: PersonFileKind {
        get { PersonFileKind(rawValue: kindRaw) ?? .other }
        set { kindRaw = newValue.rawValue }
    }

    /// "2.4 MB". Shown so a profile full of scans doesn't quietly eat storage.
    var sizeLabel: String {
        ByteCountFormatter.string(fromByteCount: Int64(byteCount), countStyle: .file)
    }

    /// A real filename for the share sheet, so what lands in Files or Mail is
    /// named after the person and the date rather than "File".
    var suggestedFileName: String {
        let safe = title
            .components(separatedBy: CharacterSet.alphanumerics.union(.whitespaces).inverted)
            .joined()
            .trimmingCharacters(in: .whitespaces)
        let base = safe.isEmpty ? "File" : safe
        return fileExtension.isEmpty ? base : "\(base).\(fileExtension)"
    }

    var isImage: Bool { kind == .image }
}

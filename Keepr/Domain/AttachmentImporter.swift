import Foundation
import PDFKit
import UIKit
import UniformTypeIdentifiers

/// Everything a new attachment needs, worked out before anything is saved.
struct AttachmentPayload: Sendable {
    var data: Data
    var thumbnail: Data?
    var kind: AttachmentKind
    var fileExtension: String

    var byteCount: Int { data.count }
}

/// Turns a picked photo or file into something worth storing.
///
/// The one real decision in here is that photos are re-encoded rather than kept
/// as they arrived. A phone photo of a body-composition printout is 3–5 MB, and
/// a trainer with fifty clients and four check-ins each would be carrying a
/// gigabyte of storage to read numbers that are perfectly legible at 2200px.
/// Downscaling takes that to roughly 400 KB with no loss that matters for the
/// job — reading what the sheet says.
///
/// Documents and PDFs are stored byte-for-byte: a signed form has to stay the
/// file it was.
enum AttachmentImporter {

    /// Long edge, in pixels, that imported photos are fitted inside.
    /// Chosen so a sheet of printed numbers stays readable when zoomed.
    static let maxPixelSize: CGFloat = 2200

    /// Long edge of the inline preview kept on the record.
    static let thumbnailPixelSize: CGFloat = 400

    static let jpegQuality: CGFloat = 0.82

    // MARK: - Sizing

    /// The size an image of `size` should be drawn at to fit inside
    /// `maxPixelSize` on its long edge, preserving aspect ratio.
    ///
    /// Images already inside the budget are left alone — upscaling a small
    /// photo adds bytes and no detail.
    static func fittedSize(for size: CGSize, maxPixelSize: CGFloat = maxPixelSize) -> CGSize {
        let longEdge = max(size.width, size.height)
        guard longEdge > maxPixelSize, longEdge > 0 else { return size }

        let scale = maxPixelSize / longEdge
        return CGSize(
            width: max(1, (size.width * scale).rounded()),
            height: max(1, (size.height * scale).rounded())
        )
    }

    // MARK: - Photos

    /// Prepares a photo picked from the library or taken with the camera.
    /// Returns nil when the data isn't a decodable image, so a failed import
    /// says so instead of saving an empty row.
    static func photo(from data: Data) -> AttachmentPayload? {
        guard let image = UIImage(data: data) else { return nil }
        return photo(from: image)
    }

    static func photo(from image: UIImage) -> AttachmentPayload? {
        let target = fittedSize(for: image.size)
        let resized = target == image.size ? image : redraw(image, at: target)

        guard let jpeg = resized.jpegData(compressionQuality: jpegQuality) else { return nil }

        return AttachmentPayload(
            data: jpeg,
            thumbnail: thumbnail(from: resized),
            kind: .image,
            fileExtension: "jpg"
        )
    }

    // MARK: - Files

    /// Prepares a file picked out of Files. Kept verbatim; a PDF additionally
    /// gets a thumbnail of its first page, so a profile shows the plan rather
    /// than a generic document icon.
    static func file(from data: Data, fileExtension: String) -> AttachmentPayload {
        let normalized = fileExtension
            .trimmingCharacters(in: CharacterSet(charactersIn: " ."))
            .lowercased()
        let kind = AttachmentKind(fileExtension: normalized)

        // An image arriving through the file picker still benefits from the
        // photo path — it's the same bytes doing the same job.
        if kind == .image, let payload = photo(from: data) {
            return payload
        }

        return AttachmentPayload(
            data: data,
            thumbnail: kind == .pdf ? pdfThumbnail(from: data) : nil,
            kind: kind,
            fileExtension: normalized.isEmpty ? "dat" : normalized
        )
    }

    // MARK: - Titles

    /// What a file is called before the user renames it.
    ///
    /// Never the camera's filename: "IMG_4821" tells you nothing a year later,
    /// while "Photo · Mar 3" is the thing you'd actually scan a list for.
    static func defaultTitle(
        for kind: AttachmentKind,
        on date: Date = Date(),
        existingTitles: [String] = []
    ) -> String {
        let day = date.formatted(.dateTime.month(.abbreviated).day())
        let base = "\(kind.title) · \(day)"

        guard existingTitles.contains(base) else { return base }

        // A second scan on the same day gets a number rather than a duplicate
        // name, since the whole point is telling two of them apart.
        var index = 2
        while existingTitles.contains("\(base) (\(index))") { index += 1 }
        return "\(base) (\(index))"
    }

    // MARK: - Drawing

    private static func redraw(_ image: UIImage, at size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        // Points, not pixels: the sizes computed above are already in pixels.
        format.scale = 1
        format.opaque = true

        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    private static func thumbnail(from image: UIImage) -> Data? {
        let target = fittedSize(for: image.size, maxPixelSize: thumbnailPixelSize)
        let small = target == image.size ? image : redraw(image, at: target)
        return small.jpegData(compressionQuality: 0.7)
    }

    private static func pdfThumbnail(from data: Data) -> Data? {
        guard let page = PDFDocument(data: data)?.page(at: 0) else { return nil }

        let bounds = page.bounds(for: .mediaBox)
        let target = fittedSize(for: bounds.size, maxPixelSize: thumbnailPixelSize)
        guard target.width > 0, target.height > 0 else { return nil }

        return page
            .thumbnail(of: target, for: .mediaBox)
            .jpegData(compressionQuality: 0.7)
    }
}

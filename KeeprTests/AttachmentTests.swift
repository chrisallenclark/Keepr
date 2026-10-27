import Foundation
import SwiftData
import Testing
import UIKit

@testable import Keepr

/// Files on a profile are the most private thing the app holds and the most
/// expensive thing it stores, so both halves are tested: the bytes have to come
/// down to a sane size, and nothing may go missing when a person is deleted.
@Suite("Attachments")
@MainActor
struct AttachmentTests {

    // MARK: - Sizing

    @Test("A big photo is scaled to fit the budget on its long edge")
    func largePhotosAreScaledDown() {
        let fitted = AttachmentImporter.fittedSize(
            for: CGSize(width: 4032, height: 3024),
            maxPixelSize: 2200
        )

        #expect(fitted.width == 2200)
        #expect(fitted.height == 1650, "aspect ratio has to survive the resize")
    }

    @Test("A portrait photo is measured on its height")
    func portraitUsesTheLongEdge() {
        let fitted = AttachmentImporter.fittedSize(
            for: CGSize(width: 3024, height: 4032),
            maxPixelSize: 2200
        )

        #expect(fitted.height == 2200)
        #expect(fitted.width == 1650)
    }

    @Test("A photo already inside the budget is left alone")
    func smallPhotosAreNotUpscaled() {
        let original = CGSize(width: 800, height: 600)

        #expect(AttachmentImporter.fittedSize(for: original, maxPixelSize: 2200) == original)
    }

    @Test("A zero-sized image doesn't divide by zero")
    func degenerateSizeIsSafe() {
        let fitted = AttachmentImporter.fittedSize(for: .zero, maxPixelSize: 2200)

        #expect(fitted == .zero)
    }

    // MARK: - Importing

    /// A real JPEG, drawn rather than fabricated, so the decode path is exercised.
    private func jpeg(width: CGFloat, height: CGFloat) -> Data {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let image = UIGraphicsImageRenderer(
            size: CGSize(width: width, height: height),
            format: format
        ).image { context in
            UIColor.darkGray.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return image.jpegData(compressionQuality: 1)!
    }

    @Test("Importing a photo produces bytes, a thumbnail and a real extension")
    func photoImportIsComplete() throws {
        let payload = try #require(AttachmentImporter.photo(from: jpeg(width: 3000, height: 2000)))

        #expect(payload.kind == .image)
        #expect(payload.fileExtension == "jpg")
        #expect(payload.byteCount > 0)
        #expect(payload.thumbnail != nil, "lists must never fault in the original")
    }

    @Test("A consultation photo comes out far smaller than it went in")
    func photosAreCompressed() throws {
        let original = jpeg(width: 4032, height: 3024)
        let payload = try #require(AttachmentImporter.photo(from: original))

        #expect(payload.byteCount < original.count)

        let decoded = try #require(UIImage(data: payload.data))
        #expect(max(decoded.size.width, decoded.size.height) <= AttachmentImporter.maxPixelSize)
    }

    @Test("The thumbnail is much smaller than the file it previews")
    func thumbnailIsSmall() throws {
        let payload = try #require(AttachmentImporter.photo(from: jpeg(width: 3000, height: 3000)))
        let thumbnail = try #require(payload.thumbnail)

        #expect(thumbnail.count < payload.byteCount)

        let decoded = try #require(UIImage(data: thumbnail))
        #expect(max(decoded.size.width, decoded.size.height) <= AttachmentImporter.thumbnailPixelSize)
    }

    @Test("Something that isn't an image fails instead of saving an empty row")
    func undecodableDataIsRejected() {
        #expect(AttachmentImporter.photo(from: Data("not a photo".utf8)) == nil)
    }

    @Test("A document keeps its own bytes untouched")
    func documentsAreStoredVerbatim() {
        let contents = Data("signed waiver".utf8)
        let payload = AttachmentImporter.file(from: contents, fileExtension: "txt")

        #expect(payload.kind == .document)
        #expect(payload.data == contents, "a signed form has to stay the file it was")
        #expect(payload.fileExtension == "txt")
    }

    @Test("An image picked out of Files still goes down the photo path")
    func imagesFromFilesAreCompressed() {
        let original = jpeg(width: 4000, height: 3000)
        let payload = AttachmentImporter.file(from: original, fileExtension: "JPEG")

        #expect(payload.kind == .image)
        #expect(payload.byteCount < original.count)
    }

    @Test("A file with no extension still saves")
    func missingExtensionIsHandled() {
        let payload = AttachmentImporter.file(from: Data([0x00, 0x01]), fileExtension: "")

        #expect(payload.kind == .other)
        #expect(payload.fileExtension == "dat")
    }

    @Test("Extensions are read case-insensitively, dot or no dot")
    func extensionParsingIsForgiving() {
        #expect(AttachmentKind(fileExtension: "PDF") == .pdf)
        #expect(AttachmentKind(fileExtension: "heic") == .image)
        #expect(AttachmentImporter.file(from: Data([0x25]), fileExtension: ".TXT").fileExtension == "txt")
    }

    // MARK: - Naming

    @Test("A new file is named after what it is and when, not IMG_4821")
    func defaultTitleIsReadable() {
        let title = AttachmentImporter.defaultTitle(for: .image, on: TestDates.now)

        #expect(title.hasPrefix("Photo · "))
        #expect(!title.isEmpty)
    }

    @Test("Two files added the same day get told apart")
    func duplicateTitlesAreNumbered() {
        let first = AttachmentImporter.defaultTitle(for: .image, on: TestDates.now)
        let second = AttachmentImporter.defaultTitle(
            for: .image,
            on: TestDates.now,
            existingTitles: [first]
        )
        let third = AttachmentImporter.defaultTitle(
            for: .image,
            on: TestDates.now,
            existingTitles: [first, second]
        )

        #expect(second == "\(first) (2)")
        #expect(third == "\(first) (3)")
    }

    @Test("A shared file gets a clean filename")
    func sharedFileNameIsSafe() throws {
        let store = try TestStore()
        let attachment = Make.attachment(
            store.context,
            title: "InBody / Week 1: 24% BF",
            fileExtension: "pdf"
        )

        let name = attachment.suggestedFileName
        #expect(name.hasSuffix(".pdf"))
        #expect(!name.contains("/"), "a slash in a filename is a path, not a name")
        #expect(!name.contains(":"))
    }

    @Test("A file with an unusable name still gets one")
    func unnameableFileFallsBack() throws {
        let store = try TestStore()
        let attachment = Make.attachment(store.context, title: "///", fileExtension: "jpg")

        #expect(attachment.suggestedFileName == "Attachment.jpg")
    }

    // MARK: - On the record

    @Test("Files come back newest first")
    func filesAreNewestFirst() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = Make.attachment(store.context, title: "Week 1", capturedAt: TestDates.days(-60), person: person)
        _ = Make.attachment(store.context, title: "Week 12", capturedAt: TestDates.days(-1), person: person)
        _ = Make.attachment(store.context, title: "Week 6", capturedAt: TestDates.days(-30), person: person)

        #expect(person.attachmentList.map(\.title) == ["Week 12", "Week 6", "Week 1"])
    }

    @Test("Deleting someone takes their files with them")
    func filesCascadeOnDelete() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        _ = Make.attachment(store.context, title: "Results", person: person)
        try store.context.save()

        store.context.delete(person)
        try store.context.save()

        let remaining = try store.context.fetch(FetchDescriptor<Attachment>())
        #expect(remaining.isEmpty, "a deleted person must not leave their scans behind")
    }

    @Test("A file survives the interaction it came from being deleted")
    func filesOutliveTheirInteraction() throws {
        let store = try TestStore()
        let person = Make.person(store.context)
        let consultation = Make.interaction(store.context, kind: .meeting, person: person)
        let attachment = Make.attachment(store.context, title: "Results", person: person)
        attachment.sourceInteraction = consultation
        try store.context.save()

        store.context.delete(consultation)
        try store.context.save()

        #expect(person.attachmentList.count == 1)
        #expect(person.attachmentList.first?.sourceInteraction == nil)
    }

    @Test("Size is reported in units a person reads")
    func sizeIsHumanReadable() throws {
        let store = try TestStore()
        let attachment = Make.attachment(store.context, title: "Scan", byteCount: 418_000)

        #expect(attachment.sizeLabel.isEmpty == false)
        #expect(attachment.sizeLabel.contains("418") || attachment.sizeLabel.contains("KB"))
    }

    @Test("Every file-kind symbol is a real SF Symbol")
    func symbolsExist() {
        for kind in AttachmentKind.allCases {
            #expect(UIImage(systemName: kind.symbolName) != nil, "\(kind.symbolName) does not exist")
        }
    }
}

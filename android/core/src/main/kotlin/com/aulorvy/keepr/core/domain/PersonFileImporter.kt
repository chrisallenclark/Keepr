package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.KeeprCalendar
import com.aulorvy.keepr.core.model.PersonFileKind
import com.aulorvy.keepr.core.services.trimmingScalars
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

// Translated from Keepr/Domain/PersonFileImporter.swift. The decisions — sizes,
// kinds, extensions, titles — live here; decoding and drawing pixels is
// platform work behind [ImageCodec].

/** `CGSize`, in pixels. */
data class PixelSize(val width: Double, val height: Double) {
    companion object {
        val ZERO = PixelSize(0.0, 0.0)
    }
}

/** A decoded image the platform can redraw and encode. */
interface RasterImage {
    val size: PixelSize
}

/**
 * The pixel work `UIImage`, `UIGraphicsImageRenderer` and PDFKit do on iOS.
 * Android implements it with `BitmapFactory`/`Bitmap` and `PdfRenderer`; tests
 * use a fake.
 */
interface ImageCodec {
    /** `UIImage(data:)`: null when the bytes aren't a decodable image. */
    fun decode(data: ByteArray): RasterImage?

    /** Draws [image] at exactly [size] pixels, opaque, at scale 1. */
    fun redraw(image: RasterImage, size: PixelSize): RasterImage

    /** `jpegData(compressionQuality:)` */
    fun jpegData(image: RasterImage, quality: Double): ByteArray?

    /** The first page's media box, or null when [data] isn't a readable PDF. */
    fun pdfFirstPageSize(data: ByteArray): PixelSize?

    /** The first page rendered at [size] (`PDFPage.thumbnail(of:for: .mediaBox)`). */
    fun pdfFirstPage(data: ByteArray, size: PixelSize): RasterImage?
}

/** Everything a new file needs, worked out before anything is saved. */
class PersonFilePayload(
    val data: ByteArray,
    val thumbnail: ByteArray?,
    val kind: PersonFileKind,
    val fileExtension: String,
) {
    val byteCount: Int get() = data.size
}

/**
 * Turns a picked photo or file into something worth storing.
 *
 * Photos are re-encoded rather than kept as they arrived: a phone photo of a
 * body-composition printout is 3–5 MB, and fifty clients with four check-ins
 * each would be a gigabyte to read numbers perfectly legible at 2200px.
 * Downscaling takes that to roughly 400 KB with no loss that matters.
 *
 * Documents and PDFs are stored byte-for-byte: a signed form has to stay the
 * file it was.
 */
object PersonFileImporter {

    /** Long edge, in pixels, imported photos are fitted inside — printed numbers stay readable zoomed. */
    const val maxPixelSize: Double = 2200.0

    /** Long edge of the inline preview kept on the record. */
    const val thumbnailPixelSize: Double = 400.0

    const val jpegQuality: Double = 0.82

    // MARK: - Sizing

    /**
     * The size an image of [size] should be drawn at to fit inside
     * [maxPixelSize] on its long edge, preserving aspect ratio. Images already
     * inside the budget are left alone — upscaling adds bytes and no detail.
     */
    fun fittedSize(size: PixelSize, maxPixelSize: Double = this.maxPixelSize): PixelSize {
        val longEdge = max(size.width, size.height)
        if (!(longEdge > maxPixelSize && longEdge > 0)) return size

        val scale = maxPixelSize / longEdge
        return PixelSize(
            width = max(1.0, roundedHalfAwayFromZero(size.width * scale)),
            height = max(1.0, roundedHalfAwayFromZero(size.height * scale)),
        )
    }

    // MARK: - Photos

    /**
     * Prepares a photo picked from the library or taken with the camera.
     * Null when the data isn't a decodable image, so a failed import says so
     * instead of saving an empty row.
     */
    fun photo(data: ByteArray, codec: ImageCodec): PersonFilePayload? {
        val image = codec.decode(data) ?: return null
        return photo(image, codec)
    }

    fun photo(image: RasterImage, codec: ImageCodec): PersonFilePayload? {
        val target = fittedSize(image.size)
        val resized = if (target == image.size) image else codec.redraw(image, target)

        val jpeg = codec.jpegData(resized, jpegQuality) ?: return null

        return PersonFilePayload(
            data = jpeg,
            thumbnail = thumbnail(resized, codec),
            kind = PersonFileKind.IMAGE,
            fileExtension = "jpg",
        )
    }

    // MARK: - Files

    /**
     * Prepares a file picked out of Files. Kept verbatim; a PDF also gets a
     * thumbnail of its first page, so a profile shows the plan rather than a
     * generic document icon.
     */
    fun file(data: ByteArray, fileExtension: String, codec: ImageCodec): PersonFilePayload {
        val normalized = fileExtension.trimmingScalars(" .").lowercase()
        val kind = PersonFileKind.forExtension(normalized)

        // An image arriving through the file picker still benefits from the
        // photo path — it's the same bytes doing the same job.
        if (kind == PersonFileKind.IMAGE) {
            photo(data, codec)?.let { return it }
        }

        return PersonFilePayload(
            data = data,
            thumbnail = if (kind == PersonFileKind.PDF) pdfThumbnail(data, codec) else null,
            kind = kind,
            fileExtension = normalized.ifEmpty { "dat" },
        )
    }

    // MARK: - Titles

    /**
     * What a file is called before the user renames it. Never the camera's
     * filename: "IMG_4821" tells you nothing a year later, "Photo · Mar 3" is
     * the thing you'd scan a list for.
     */
    fun defaultTitle(
        kind: PersonFileKind,
        date: Instant = Instant.now(),
        existingTitles: List<String> = emptyList(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): String {
        val base = "${kind.title} · ${calendar.monthDay(date)}"

        if (base !in existingTitles) return base

        // A second scan on the same day gets a number rather than a duplicate
        // name, since the whole point is telling two of them apart.
        var index = 2
        while ("$base ($index)" in existingTitles) index += 1
        return "$base ($index)"
    }

    // MARK: - Drawing

    private fun thumbnail(image: RasterImage, codec: ImageCodec): ByteArray? {
        val target = fittedSize(image.size, thumbnailPixelSize)
        val small = if (target == image.size) image else codec.redraw(image, target)
        return codec.jpegData(small, 0.7)
    }

    private fun pdfThumbnail(data: ByteArray, codec: ImageCodec): ByteArray? {
        val bounds = codec.pdfFirstPageSize(data) ?: return null
        val target = fittedSize(bounds, thumbnailPixelSize)
        if (!(target.width > 0 && target.height > 0)) return null

        val page = codec.pdfFirstPage(data, target) ?: return null
        return codec.jpegData(page, 0.7)
    }

    /** Swift's `rounded()`: to nearest, halves away from zero (Kotlin's `round` is half-even). */
    private fun roundedHalfAwayFromZero(value: Double): Double {
        val truncated = if (value < 0) -floor(-value) else floor(value)
        return if (abs(value - truncated) >= 0.5) truncated + (if (value < 0) -1.0 else 1.0) else truncated
    }
}

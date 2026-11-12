package com.aulorvy.keepr.ui.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.aulorvy.keepr.core.domain.ImageCodec
import com.aulorvy.keepr.core.domain.PersonFileImporter
import com.aulorvy.keepr.core.domain.PixelSize
import com.aulorvy.keepr.core.domain.RasterImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [ImageCodec] on Android's own image stack: `BitmapFactory` decodes,
 * `Bitmap.compress` encodes, and `PdfRenderer` draws a PDF's first page —
 * the work UIKit and PDFKit do on iOS.
 *
 * Run it off the main thread; a 12 MP photo takes a moment to decode.
 */
class AndroidImageCodec(
    /** Somewhere private to write a PDF while `PdfRenderer` reads it (it needs a file, not bytes). */
    private val scratchDirectory: File,
) : ImageCodec {

    class Image(val bitmap: Bitmap) : RasterImage {
        override val size: PixelSize get() = PixelSize(bitmap.width.toDouble(), bitmap.height.toDouble())
    }

    override fun decode(data: ByteArray): RasterImage? {
        // Read the size first, then decode no larger than the import budget
        // needs: a 48 MP photo decoded at full size is ~190 MB of pixels.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= PersonFileImporter.maxPixelSize) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching { BitmapFactory.decodeByteArray(data, 0, data.size, options) }.getOrNull() ?: return null
        return Image(uprighted(decoded, data))
    }

    override fun redraw(image: RasterImage, size: PixelSize): RasterImage {
        val source = (image as Image).bitmap
        val width = max(1, size.width.roundToInt())
        val height = max(1, size.height.roundToInt())
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        // Opaque, as iOS's renderer draws it: transparency becomes white
        // rather than JPEG's black.
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return Image(target)
    }

    override fun jpegData(image: RasterImage, quality: Double): ByteArray? {
        var bitmap = (image as Image).bitmap
        if (bitmap.hasAlpha()) {
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(flattened).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            bitmap = flattened
        }
        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, (quality * 100).roundToInt().coerceIn(0, 100), out)
        return if (ok) out.toByteArray() else null
    }

    override fun pdfFirstPageSize(data: ByteArray): PixelSize? = withPdf(data) { renderer ->
        if (renderer.pageCount < 1) return@withPdf null
        renderer.openPage(0).use { page -> PixelSize(page.width.toDouble(), page.height.toDouble()) }
    }

    override fun pdfFirstPage(data: ByteArray, size: PixelSize): RasterImage? = withPdf(data) { renderer ->
        if (renderer.pageCount < 1) return@withPdf null
        renderer.openPage(0).use { page ->
            Image(PdfPages.render(page, max(1, size.width.roundToInt()), max(1, size.height.roundToInt())))
        }
    }

    private fun <T> withPdf(data: ByteArray, block: (PdfRenderer) -> T?): T? {
        scratchDirectory.mkdirs()
        val file = File.createTempFile("thumb", ".pdf", scratchDirectory)
        return try {
            file.writeBytes(data)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> block(renderer) }
            }
        } catch (error: Exception) {
            // Not a PDF, encrypted, or damaged: no thumbnail, the kind icon stands in.
            null
        } finally {
            file.delete()
        }
    }

    /** Camera JPEGs are often stored sideways with an EXIF note saying so; draw them the right way up. */
    private fun uprighted(bitmap: Bitmap, data: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/** Drawing PDF pages, shared by thumbnails and the viewer. */
object PdfPages {
    /** One page at exactly [width]×[height] pixels, on white (PdfRenderer leaves the background transparent). */
    fun render(page: PdfRenderer.Page, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}

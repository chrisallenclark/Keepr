package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.ImageCodec
import com.chrisallenclark.keepr.core.domain.PixelSize
import com.chrisallenclark.keepr.core.domain.RasterImage
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * The test stand-in for the platform's image work, on the JDK's own ImageIO,
 * so the photo tests decode and encode real JPEGs as the iOS ones do with
 * UIKit. No PDF renderer ships with the JDK, so PDFs read as unreadable.
 */
class JvmImageCodec : ImageCodec {

    class Image(val buffered: BufferedImage) : RasterImage {
        override val size: PixelSize get() = PixelSize(buffered.width.toDouble(), buffered.height.toDouble())
    }

    override fun decode(data: ByteArray): RasterImage? =
        runCatching { ImageIO.read(ByteArrayInputStream(data)) }.getOrNull()?.let { Image(it) }

    override fun redraw(image: RasterImage, size: PixelSize): RasterImage {
        val source = (image as Image).buffered
        val target = BufferedImage(size.width.toInt(), size.height.toInt(), BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(source, 0, 0, target.width, target.height, null)
        graphics.dispose()
        return Image(target)
    }

    override fun jpegData(image: RasterImage, quality: Double): ByteArray? {
        val source = (image as Image).buffered
        val rgb = if (source.type == BufferedImage.TYPE_INT_RGB) source else {
            BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB).also {
                val g = it.createGraphics()
                g.drawImage(source, 0, 0, null)
                g.dispose()
            }
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality.toFloat()
            }
            writer.write(null, IIOImage(rgb, null, null), params)
        }
        writer.dispose()
        return out.toByteArray()
    }

    override fun pdfFirstPageSize(data: ByteArray): PixelSize? = null

    override fun pdfFirstPage(data: ByteArray, size: PixelSize): RasterImage? = null

    companion object {
        /** A real JPEG, drawn rather than fabricated, so the decode path is exercised. */
        fun jpeg(width: Int, height: Int): ByteArray {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.color = Color.DARK_GRAY
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
            return JvmImageCodec().jpegData(Image(image), 1.0)!!
        }
    }
}

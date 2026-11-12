package com.aulorvy.keepr.ui.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.core.model.PersonFileKind
import com.aulorvy.keepr.ui.components.EmptyState
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.KeeprButton
import com.aulorvy.keepr.ui.components.KeeprButtonStyle
import com.aulorvy.keepr.ui.components.KeeprTopBar
import com.aulorvy.keepr.ui.components.ToolbarButton
import com.aulorvy.keepr.ui.components.ToolbarTextButton
import com.aulorvy.keepr.ui.theme.KeeprTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the viewer has in hand once the bytes are read. */
private sealed interface Loaded {
    data object Loading : Loaded
    data object Missing : Loaded
    class Picture(val bitmap: ImageBitmap) : Loaded
    class Pdf(val document: PdfDocumentHandle) : Loaded
    data object Other : Loaded
}

/**
 * Quick Look, for one file: a photo full screen with pinch to zoom, a PDF
 * page by page, anything else as a card with "Open in Another App". Share
 * is always in the corner, as it is in Quick Look.
 */
@Composable
fun FileViewer(file: PersonFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = KeeprTheme.colors
    var message by remember { mutableStateOf<String?>(null) }

    val loaded by produceState<Loaded>(Loaded.Loading, file.id) {
        value = withContext(Dispatchers.IO) {
            val bytes = runCatching { file.data }.getOrNull()
            when {
                bytes == null -> Loaded.Missing
                file.kind == PersonFileKind.IMAGE -> decodeForViewing(bytes)?.let { Loaded.Picture(it.asImageBitmap()) } ?: Loaded.Other
                file.kind == PersonFileKind.PDF -> PdfDocumentHandle.open(File(context.cacheDir, "preview"), bytes)?.let { Loaded.Pdf(it) } ?: Loaded.Other
                else -> Loaded.Other
            }
        }
    }
    val current = loaded
    DisposableEffect(current) {
        onDispose { (current as? Loaded.Pdf)?.document?.close() }
    }

    fun handOff(open: Boolean) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) { PersonFileSharing.export(context, file) }
            val ok = uri != null && if (open) PersonFileSharing.openExternally(context, uri, file) else PersonFileSharing.share(context, uri, file)
            if (!ok) message = if (uri == null) "That file is missing its contents." else "No app on this phone can open this kind of file."
        }
    }

    val isPicture = current is Loaded.Picture
    val background = if (isPicture) Color.Black else colors.groupedBackground

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(background)) {
            KeeprTopBar(
                title = file.title,
                background = background,
                leading = { ToolbarTextButton("Done", onDismiss) },
                trailing = { ToolbarButton("square.and.arrow.up", "Share", { handOff(open = false) }) },
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (current) {
                    Loaded.Loading -> Unit
                    Loaded.Missing -> EmptyState(
                        "Can't open this file",
                        file.kind.symbolName,
                        description = "That file is missing its contents.",
                    )
                    is Loaded.Picture -> ZoomableImage(current.bitmap, file.title)
                    is Loaded.Pdf -> PdfPageList(current.document)
                    Loaded.Other -> EmptyState(
                        file.title,
                        file.kind.symbolName,
                        description = "${file.fileExtension.uppercase()} · ${file.sizeLabel}",
                    ) {
                        KeeprButton("Open in Another App", { handOff(open = true) }, style = KeeprButtonStyle.Prominent)
                    }
                }
            }
            message?.let {
                KText(
                    it,
                    style = KeeprTheme.typography.footnote,
                    color = if (isPicture) Color.White else colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

/** A photo that fits the screen, pinches to zoom, pans when zoomed, and double-taps back. */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap, title: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bitmap,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** Every page, top to bottom, drawn at the width of the screen as it scrolls in. */
@Composable
private fun PdfPageList(document: PdfDocumentHandle) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidthPx = with(density) { (maxWidth - 32.dp).toPx() }.roundToInt().coerceIn(1, 2048)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(document.pageCount) { index ->
                val (width, height) = document.pageSizes[index]
                val ratio = if (height > 0) width.toFloat() / height.toFloat() else 0.77f
                val page by produceState<ImageBitmap?>(null, index, pageWidthPx) {
                    value = document.render(index, pageWidthPx)?.asImageBitmap()
                }
                val label = "Page ${index + 1} of ${document.pageCount}"
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .shadow(2.dp, RoundedCornerShape(2.dp))
                        .background(Color.White),
                ) {
                    page?.let { bitmap ->
                        Image(
                            bitmap,
                            contentDescription = label,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** A photo decoded no larger than a screen needs to show it sharply when zoomed. */
private fun decodeForViewing(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 3000) sample *= 2
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/**
 * An open PDF. `PdfRenderer` reads from a file and draws one page at a time,
 * so the bytes go to a private cache file for as long as the viewer is open,
 * and pages render one after another behind a lock.
 */
class PdfDocumentHandle private constructor(
    private val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val lock = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Each page's size in points, read once on open. */
    val pageSizes: List<Pair<Int, Int>> = (0 until pageCount).map { index ->
        renderer.openPage(index).use { page -> page.width to page.height }
    }

    /** Page [index] at [widthPx] wide, keeping its proportions. Null once closed. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            runCatching {
                renderer.openPage(index).use { page ->
                    val height = max(1, (widthPx.toFloat() * page.height / max(1, page.width)).roundToInt())
                    // Bound memory for very tall pages.
                    val safeHeight = min(height, 4096)
                    PdfPages.render(page, widthPx, safeHeight)
                }
            }.getOrNull()
        }
    }

    override fun close() {
        // Closing waits for an in-flight render rather than pulling the file from under it.
        Thread {
            kotlinx.coroutines.runBlocking {
                lock.withLock {
                    if (closed) return@withLock
                    closed = true
                    runCatching { renderer.close() }
                    runCatching { descriptor.close() }
                    file.delete()
                }
            }
        }.start()
    }

    companion object {
        /** Opens [bytes] as a PDF, or null when they aren't one. Call off the main thread. */
        fun open(directory: File, bytes: ByteArray): PdfDocumentHandle? {
            directory.mkdirs()
            val file = File.createTempFile("view", ".pdf", directory)
            return try {
                file.writeBytes(bytes)
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    PdfDocumentHandle(file, descriptor, PdfRenderer(descriptor))
                } catch (error: Exception) {
                    descriptor.close()
                    throw error
                }
            } catch (error: Exception) {
                file.delete()
                null
            }
        }
    }
}

package com.rajat.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import com.rajat.pdfviewer.util.CacheManager
import com.rajat.pdfviewer.util.CacheStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Rajat on 11,July,2020
 */

open class PdfRendererCore(
    private val context: Context,
    private val fileDescriptor: ParcelFileDescriptor,
    private val cacheIdentifier: String,
    private val cacheStrategy: CacheStrategy
) {

    private var isRendererOpen = false
    private val cacheManager = CacheManager(context, cacheIdentifier, cacheStrategy)

    constructor(context: Context, file: File, cacheStrategy: CacheStrategy) : this(
        context = context,
        fileDescriptor = getFileDescriptor(file),
        cacheIdentifier = getCacheIdentifierFromFile(file),
        cacheStrategy = cacheStrategy
    )

    private val openPages = ConcurrentHashMap<Int, PdfRenderer.Page>()
    private var pdfRenderer: PdfRenderer =
        PdfRenderer(fileDescriptor).also { isRendererOpen = true }

    companion object {
        private fun sanitizeFilePath(filePath: String): String {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val path = Paths.get(filePath)
                    if (Files.exists(path)) filePath else ""
                } else filePath
            } catch (e: Exception) {
                ""
            }
        }

        internal fun getFileDescriptor(file: File): ParcelFileDescriptor {
            val safeFile = File(sanitizeFilePath(file.path))
            return ParcelFileDescriptor.open(safeFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        internal fun getCacheIdentifierFromFile(file: File): String {
            return file.name.toString()
        }
    }

    init {
        isRendererOpen = true
    }

    internal fun getBitmapFromCache(pageNo: Int): Bitmap? = cacheManager.getBitmapFromCache(pageNo)

    private fun addBitmapToMemoryCache(pageNo: Int, bitmap: Bitmap) =
        cacheManager.addBitmapToCache(pageNo, bitmap)

    fun pageExistInCache(pageNo: Int): Boolean = cacheManager.pageExistsInCache(pageNo)

    fun getPageCount(): Int {
        synchronized(this) {
            if (!isRendererOpen) return 0
            return pdfRenderer.pageCount
        }
    }

    private val renderJobs = ConcurrentHashMap<Int, Job>()

    fun renderPage(
        pageNo: Int,
        bitmap: Bitmap,
        onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)? = null
    ) {
        val startTime = System.nanoTime() // ⏱ Start timing

        if (pageNo >= getPageCount()) {
            onBitmapReady?.invoke(false, pageNo, null)
            return
        }

        // Check cache first
        val cachedBitmap = getBitmapFromCache(pageNo)
        if (cachedBitmap != null) {
            CoroutineScope(Dispatchers.Main).launch {
                onBitmapReady?.invoke(
                    true, pageNo, cachedBitmap
                )
            }
            return
        }

        renderJobs[pageNo]?.cancel() // Cancel any previous render job
        CoroutineScope(Dispatchers.IO).launch {

            var success = false
            var renderedBitmap: Bitmap? = null

            synchronized(this@PdfRendererCore) {
                if (!isRendererOpen) return@launch

                val pdfPage = openPageSafely(pageNo) ?: return@launch

                try {
                    if (!isRendererOpen || openPages[pageNo] == null) {
                        Log.e("PdfRendererCore", "Page $pageNo is already closed.")
                        return@launch
                    }


                    // ✅ Optimize rendering with scaled resolution when prefetching
                    val scaleFactor =
                        if (Thread.currentThread().name.contains("Prefetch")) 0.5f else 1.0f
                    val width = (bitmap.width * scaleFactor).toInt()
                    val height = (bitmap.height * scaleFactor).toInt()

                    val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    pdfPage.render(
                        tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    addBitmapToMemoryCache(pageNo, tempBitmap)
                    success = true
                    renderedBitmap = tempBitmap
                } catch (e: Exception) {
                    Log.e("PdfRendererCore", "Error rendering page $pageNo: ${e.message}", e)
                }
            }

            val renderTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms

            withContext(Dispatchers.Main) {
                onBitmapReady?.invoke(success, pageNo, renderedBitmap)
            }
        }
    }

    private suspend fun <T> withPdfPage(pageNo: Int, block: (PdfRenderer.Page) -> T): T? =
        withContext(Dispatchers.IO) {
            synchronized(this@PdfRendererCore) {
                pdfRenderer.openPage(pageNo).use { page ->
                    return@withContext block(page)
                }
            }
        }

    private val pageDimensionCache = mutableMapOf<Int, Size>()

    fun getPageDimensionsAsync(pageNo: Int, callback: (Size) -> Unit) {
        pageDimensionCache[pageNo]?.let {
            callback(it)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val size = withPdfPage(pageNo) { page ->
                Size(page.width, page.height).also { pageSize ->
                    pageDimensionCache[pageNo] = pageSize
                }
            } ?: Size(1, 1) // Fallback to a default minimal size

            withContext(Dispatchers.Main) {
                callback(size)
            }
        }
    }

    private fun openPageSafely(pageNo: Int): PdfRenderer.Page? {
        synchronized(this) {
            if (!isRendererOpen) {
                return null
            }

            openPages[pageNo]?.let {
                return it
            }

            return try {
                val page = pdfRenderer.openPage(pageNo)
                openPages[pageNo] = page

                // ✅ Keep last 5 pages open instead of just 3
                if (openPages.size > 5) {
                    val oldestPage = openPages.keys.minOrNull()
                    oldestPage?.let { oldPage ->
                        openPages.remove(oldPage)?.close()
                    }
                }
                page
            } catch (e: Exception) {
                Log.e("PDF_OPEN_TRACKER", "Error opening page $pageNo: ${e.message}", e)
                null
            }
        }
    }

    private fun closeAllOpenPages() {
        synchronized(this) {
            val iterator = openPages.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                try {
                    entry.value.close()
                } catch (e: IllegalStateException) {
                    Log.e("PDFRendererCore", "Page ${entry.key} was already closed", e)
                } finally {
                    iterator.remove()
                }
            }
        }
    }

    fun closePdfRender() {
        synchronized(this) {
            Log.d("PdfRendererCore", "Closing PdfRenderer and releasing resources.")

            closeAllOpenPages()
            if (isRendererOpen) {
                try {
                    pdfRenderer.close()
                } catch (e: Exception) {
                    Log.e("PdfRendererCore", "Error closing PdfRenderer: ${e.message}", e)
                } finally {
                    isRendererOpen = false
                }
            }

            try {
                fileDescriptor.close()
            } catch (e: Exception) {
                Log.e("PdfRendererCore", "Error closing file descriptor: ${e.message}", e)
            }
        }
    }


}
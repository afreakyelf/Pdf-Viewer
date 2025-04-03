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
import com.rajat.pdfviewer.util.CommonUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

open class PdfRendererCore(
    private val context: Context,
    private val fileDescriptor: ParcelFileDescriptor,
    private val cacheIdentifier: String,
    private val cacheStrategy: CacheStrategy
) {

    private var isRendererOpen = false
    private val cacheManager = CacheManager(context, cacheIdentifier, cacheStrategy)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val renderLock = Mutex()
    private val pageCount = AtomicInteger(-1)

    companion object {
        var enableDebugMetrics = false
        var prefetchDistance = 2

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

        internal fun getCacheIdentifierFromFile(file: File): String = file.name.toString()
    }

    private var totalPagesRendered = 0
    private var totalRenderTime = 0L
    private var slowestRenderTime = 0L
    private var slowestPage: Int? = null

    private val openPages = ConcurrentHashMap<Int, PdfRenderer.Page>()
    private var pdfRenderer: PdfRenderer = PdfRenderer(fileDescriptor).also {
        isRendererOpen = true
        pageCount.set(it.pageCount)
    }

    init {
        isRendererOpen = true
    }

    internal fun getBitmapFromCache(pageNo: Int): Bitmap? = cacheManager.getBitmapFromCache(pageNo)

    private fun addBitmapToMemoryCache(pageNo: Int, bitmap: Bitmap) =
        cacheManager.addBitmapToCache(pageNo, bitmap)

    fun pageExistInCache(pageNo: Int): Boolean = cacheManager.pageExistsInCache(pageNo)

    fun getPageCount(): Int = if (!isRendererOpen) 0 else pageCount.get()

    private val renderJobs = ConcurrentHashMap<Int, Job>()

    fun renderPage(
        pageNo: Int,
        size: Size,
        onBitmapReady: ((pageNo: Int, bitmap: Bitmap?) -> Unit)? = null
    ) {
        val startTime = System.nanoTime()

        if (pageNo >= getPageCount()) {
            onBitmapReady?.invoke(pageNo, null)
            return
        }

        getBitmapFromCache(pageNo)?.let { cachedBitmap ->
            scope.launch(Dispatchers.Main) {
                onBitmapReady?.invoke(pageNo, cachedBitmap)
                if (enableDebugMetrics) {
                    Log.d("PdfRendererCore", "Page $pageNo loaded from cache")
                }
            }
            return
        }

        if (renderJobs[pageNo]?.isActive == true) return

        renderJobs[pageNo]?.cancel()
        renderJobs[pageNo] = scope.launch {
            var renderedBitmap: Bitmap? = null

            renderLock.withLock {
                if (!isRendererOpen) return@launch
                val pdfPage = openPageSafely(pageNo) ?: return@launch

                try {
                    val aspectRatio = pdfPage.width.toFloat() / pdfPage.height
                    val height = size.height
                    val width = (height * aspectRatio).toInt()

                    val tempBitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
                    pdfPage.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    addBitmapToMemoryCache(pageNo, tempBitmap)
                    renderedBitmap = tempBitmap

                } catch (e: Exception) {
                    Log.e("PdfRendererCore", "Error rendering page $pageNo: ${e.message}", e)
                }
            }

            val renderTime = (System.nanoTime() - startTime) / 1_000_000

            if (enableDebugMetrics) {
                Log.d("PdfRendererCore_Metrics", "Page $pageNo rendered in ${renderTime}ms")
                if (renderTime > 500) {
                    Log.w("PdfRendererCore_Metrics", "⚠️ Slow render: Page $pageNo took ${renderTime}ms")
                }
            }

            updateAggregateMetrics(pageNo, renderTime)

            withContext(Dispatchers.Main) {
                onBitmapReady?.invoke(pageNo, renderedBitmap)
            }
        }
    }

    suspend fun renderPageAsync(pageNo: Int, width: Int, height: Int): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            renderPage(pageNo, Size(width, maxOf(1, height))) { _, renderedBitmap ->
                continuation.resume(renderedBitmap, null)
            }
        }
    }

    private fun updateAggregateMetrics(page: Int, duration: Long) {
        totalPagesRendered++
        totalRenderTime += duration
        if (duration > slowestRenderTime) {
            slowestRenderTime = duration
            slowestPage = page
        }
    }

    fun prefetchPagesAround(currentPage: Int, width: Int, height: Int, direction: Int = 0) {
        val range = when (direction) {
            1 -> (currentPage + 1)..(currentPage + prefetchDistance)
            -1 -> (currentPage - prefetchDistance)..<currentPage
            else -> (currentPage - prefetchDistance)..(currentPage + prefetchDistance)
        }

        range
            .filter { it in 0 until getPageCount() && !pageExistInCache(it) }
            .forEach { pageNo ->
                if (renderJobs[pageNo]?.isActive != true) {
                    renderJobs[pageNo]?.cancel()
                    renderJobs[pageNo] = scope.launch {
                        renderPage(pageNo, Size(width, maxOf(1, height))) { _, _ ->
                        }
                    }
                }
            }
    }

    private suspend fun <T> withPdfPage(pageNo: Int, block: (PdfRenderer.Page) -> T): T? =
        withContext(Dispatchers.IO) {
            renderLock.withLock {
                if (!isRendererOpen) return@withContext null
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    closeAllOpenPages()
                }
                try {
                    pdfRenderer.openPage(pageNo).use { page ->
                        block(page)
                    }
                } catch (e: Exception) {
                    Log.e("PdfRendererCore", "withPdfPage error: ${e.message}", e)
                    null
                }
            }
        }

    private val pageDimensionCache = mutableMapOf<Int, Size>()

    fun getPageDimensionsAsync(pageNo: Int, callback: (Size) -> Unit) {
        pageDimensionCache[pageNo]?.let {
            callback(it)
            return
        }

        scope.launch {
            val size = withPdfPage(pageNo) { page ->
                Size(page.width, page.height).also { pageDimensionCache[pageNo] = it }
            } ?: Size(1, 1)

            withContext(Dispatchers.Main) {
                callback(size)
            }
        }
    }

    private fun openPageSafely(pageNo: Int): PdfRenderer.Page? {
        if (!isRendererOpen) return null
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            closeAllOpenPages()
        }

        openPages[pageNo]?.let { return it }

        return try {
            val page = pdfRenderer.openPage(pageNo)
            openPages[pageNo] = page
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && openPages.size > 5) {
                val oldest = openPages.keys.minOrNull()
                oldest?.let { openPages.remove(it)?.close() }
            }
            page
        } catch (e: Exception) {
            Log.e("PDF_OPEN_TRACKER", "Error opening page $pageNo: ${e.message}", e)
            null
        }
    }

    private fun closeAllOpenPages() {
        val iterator = openPages.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try {
                entry.value.close()
            } catch (e: IllegalStateException) {
                Log.e("PdfRendererCore", "Page ${entry.key} was already closed", e)
            } finally {
                iterator.remove()
            }
        }
    }

    fun closePdfRender() {
        Log.d("PdfRendererCore", "Closing PdfRenderer and releasing resources.")
        scope.coroutineContext.cancelChildren()
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
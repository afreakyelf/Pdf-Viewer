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
import kotlin.math.abs

class PdfRendererCore private constructor(
    private val fileDescriptor: ParcelFileDescriptor,
    private val cacheManager: CacheManager,
    private val pdfRenderer: PdfRenderer
) {

    private var isRendererOpen = true
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val renderLock = Mutex()
    private val pageCount = AtomicInteger(pdfRenderer.pageCount)

    private var totalPagesRendered = 0
    private var totalRenderTime = 0L
    private var slowestRenderTime = 0L
    private var slowestPage: Int? = null

    private val openPages = ConcurrentHashMap<Int, PdfRenderer.Page>()
    private val renderJobs = ConcurrentHashMap<Int, Job>()
    private val pageDimensionCache = mutableMapOf<Int, Size>()
    private var prefetchJob: Job? = null

    companion object {
        var enableDebugMetrics: Boolean = true
        const val prefetchDistance: Int = 2

        suspend fun create(
            context: Context,
            fileDescriptor: ParcelFileDescriptor,
            cacheIdentifier: String,
            cacheStrategy: CacheStrategy
        ): PdfRendererCore = withContext(Dispatchers.IO) {
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val manager = CacheManager(context, cacheIdentifier, cacheStrategy).apply { initialize() }
            val core = PdfRendererCore(fileDescriptor, manager, pdfRenderer)
            core.preloadPageDimensions()
            return@withContext core
        }

        fun getFileDescriptor(file: File): ParcelFileDescriptor {
            val safeFile = File(sanitizeFilePath(file.path))
            return ParcelFileDescriptor.open(safeFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        fun getCacheIdentifierFromFile(file: File): String = file.name.toString()

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

        private const val LOG_TAG = "PdfRendererCore"
        private const val METRICS_TAG = "PdfRendererCore_Metrics"
    }

    fun getPageCount(): Int = pageCount.get().takeIf { isRendererOpen } ?: 0

    suspend fun getBitmapFromCache(pageNo: Int): Bitmap? = cacheManager.getBitmapFromCache(pageNo)

    private suspend fun addBitmapToMemoryCache(pageNo: Int, bitmap: Bitmap) = cacheManager.addBitmapToCache(pageNo, bitmap)

    private suspend fun pageExistInCache(pageNo: Int): Boolean = cacheManager.pageExistsInCache(pageNo)

    fun renderPage(
        pageNo: Int,
        bitmap: Bitmap,
        onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)? = null
    ) {
        val startTime = System.nanoTime()

        if (pageNo < 0 || pageNo >= getPageCount()) {
            Log.w(METRICS_TAG, "⚠️ Skipped invalid render for page $pageNo")
            onBitmapReady?.invoke(false, pageNo, null)
            return
        }

        scope.launch {
            val cachedBitmap = cacheManager.getBitmapFromCache(pageNo)
            if (cachedBitmap != null) {
                withContext(Dispatchers.Main) {
                    onBitmapReady?.invoke(true, pageNo, cachedBitmap)
                    Log.d(LOG_TAG, "Page $pageNo loaded from cache")
                }
                return@launch
            }

            if (renderJobs[pageNo]?.isActive == true) return@launch
            renderJobs[pageNo]?.cancel()
            renderJobs[pageNo] = launch {
                var success = false
                var renderedBitmap: Bitmap? = null

                renderLock.withLock {
                    if (!isRendererOpen) return@withLock
                    val pdfPage = openPageSafely(pageNo).takeIf { isRendererOpen } ?: return@withLock

                    try {
                        pdfPage.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        addBitmapToMemoryCache(pageNo, bitmap)
                        success = true
                        renderedBitmap = bitmap
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Error rendering page $pageNo: ${e.message}", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    onBitmapReady?.invoke(success, pageNo, renderedBitmap)
                }
            }
        }
    }

    suspend fun renderPageAsync(pageNo: Int, width: Int, height: Int): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
            renderPage(pageNo, bitmap) { success, _, renderedBitmap ->
                if (success) continuation.resume(renderedBitmap ?: bitmap, null)
                else {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                    continuation.resume(null, null)
                }
            }
        }

    fun preloadPageDimensions() {
        scope.launch {
            for (pageNo in 0 until getPageCount()) {
                if (!pageDimensionCache.containsKey(pageNo)) {
                    withPdfPage(pageNo) { page ->
                        pageDimensionCache[pageNo] = Size(page.width, page.height)
                    }
                }
            }
        }
    }

    fun schedulePrefetch(currentPage: Int, width: Int, height: Int, direction: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(100)
            prefetchPagesAround(currentPage, width, height, direction)
        }
    }

    private suspend fun prefetchPagesAround(currentPage: Int, fallbackWidth: Int, fallbackHeight: Int, direction: Int) {
        val range = when (direction) {
            1 -> (currentPage + 1)..(currentPage + prefetchDistance)
            -1 -> (currentPage - prefetchDistance)..<currentPage
            else -> (currentPage - prefetchDistance)..(currentPage + prefetchDistance)
        }
        val sortedPages = range
            .filter { it in 0 until getPageCount() }
            .filter { !pageExistInCache(it) }
            .sortedBy { abs(it - currentPage) } // prefer pages close to current page

        sortedPages.forEach { pageNo ->
            if (renderJobs[pageNo]?.isActive != true) {
                renderJobs[pageNo]?.cancel()
                renderJobs[pageNo] = scope.launch {
                    val size = withPdfPage(pageNo) { page ->
                        Size(page.width, page.height)
                    } ?: Size(fallbackWidth, fallbackHeight)

                    val aspectRatio = size.width.toFloat() / size.height.toFloat()
                    val height = (fallbackWidth / aspectRatio).toInt()

                    val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(fallbackWidth, maxOf(1, height))
                    renderPage(pageNo, bitmap) { success, _, _ ->
                        if (!success) CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                    }
                }
            }
        }
    }

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

    fun averageRenderTime(): Long = if (totalPagesRendered == 0) 0 else totalRenderTime / totalPagesRendered

    fun slowestPageInfo(): Pair<Int, Long>? = slowestPage?.let { it to slowestRenderTime }

    private suspend fun <T> withPdfPage(pageNo: Int, block: (PdfRenderer.Page) -> T): T? =
        withContext(Dispatchers.IO) {
            renderLock.withLock {
                if (!isRendererOpen) return@withContext null
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    closeAllOpenPages()
                }
                try {
                    pdfRenderer.openPage(pageNo).use(block)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "withPdfPage error: ${e.message}", e)
                    null
                }
            }
        }

    private fun openPageSafely(pageNo: Int): PdfRenderer.Page? {
        if (!isRendererOpen) return null
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) closeAllOpenPages()

        openPages[pageNo]?.let { return it }

        return try {
            val page = pdfRenderer.openPage(pageNo)
            openPages[pageNo] = page
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && openPages.size > 5) {
                openPages.keys.minOrNull()?.let { openPages.remove(it)?.close() }
            }
            page
        } catch (e: Exception) {
            Log.e("PDF_OPEN_TRACKER", "Error opening page $pageNo: ${e.message}", e)
            null
        }
    }

    fun cancelRender(pageNo: Int) {
        renderJobs[pageNo]?.cancel()
        renderJobs.remove(pageNo)
    }

    fun cancelPrefetch() {
        prefetchJob?.cancel()
    }

    private fun closeAllOpenPages() {
        val iterator = openPages.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try {
                entry.value.close()
            } catch (e: IllegalStateException) {
                Log.e(LOG_TAG, "Page ${entry.key} was already closed", e)
            } finally {
                iterator.remove()
            }
        }
    }

    fun closePdfRender() {
        if (!isRendererOpen) return

        Log.d(LOG_TAG, "Closing PdfRenderer and releasing resources.")

        scope.coroutineContext.cancelChildren()
        closeAllOpenPages()

        runCatching { pdfRenderer.close() }
            .onFailure { Log.e(LOG_TAG, "Error closing PdfRenderer: ${it.message}", it) }

        runCatching { fileDescriptor.close() }
            .onFailure { Log.e(LOG_TAG, "Error closing file descriptor: ${it.message}", it) }

        isRendererOpen = false
    }
}
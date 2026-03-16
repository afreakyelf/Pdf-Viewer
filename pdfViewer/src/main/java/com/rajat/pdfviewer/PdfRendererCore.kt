package com.rajat.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import com.rajat.pdfviewer.util.CacheHelper
import com.rajat.pdfviewer.util.CacheManager
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.CommonUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class PdfRendererCore private constructor(
    private val fileDescriptor: ParcelFileDescriptor,
    private val cacheManager: CacheManager,
    private val pdfRenderer: PdfRenderer
) {
    companion object {
        var enableDebugMetrics: Boolean = true
        const val prefetchDistance: Int = 2
        /**
         * Creates a [PdfRendererCore] instance from an already-opened [fileDescriptor].
         *
         * **Ownership contract**: on success the returned [PdfRendererCore] owns both the
         * [fileDescriptor] and the native [PdfRenderer], closing them when [closePdfRender]
         * is called. On failure (i.e. this function throws) all partially-initialised
         * resources — including the native [PdfRenderer] — are closed before the exception
         * propagates. The [fileDescriptor] is *not* closed on failure; the caller is
         * responsible for closing it in its error path.
         */
        suspend fun create(
            context: Context,
            fileDescriptor: ParcelFileDescriptor,
            cacheIdentifier: String,
            cacheStrategy: CacheStrategy
        ): PdfRendererCore = withContext(Dispatchers.IO) {
            val pdfRenderer = PdfRenderer(fileDescriptor)
            try {
                val manager = CacheManager(context, cacheIdentifier, cacheStrategy).apply { initialize() }
                val core = PdfRendererCore(fileDescriptor, manager, pdfRenderer)
                core.preloadPageDimensions()
                return@withContext core
            } catch (e: Exception) {
                runCatching { pdfRenderer.close() }
                    .onFailure { Log.e(LOG_TAG, "Error closing PdfRenderer during cleanup: ${it.message}", it) }
                throw e
            }
        }

        fun getFileDescriptor(file: File): ParcelFileDescriptor {
            val safeFile = File(sanitizeFilePath(file.path))
            return ParcelFileDescriptor.open(safeFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        @Deprecated(
            message = "Use CacheHelper.getCacheKey(file.absolutePath) directly.",
            replaceWith = ReplaceWith(
                "CacheHelper.getCacheKey(file.absolutePath)",
                "com.rajat.pdfviewer.util.CacheHelper"
            )
        )
        fun getCacheIdentifierFromFile(file: File): String = CacheHelper.getCacheKey(file.absolutePath)

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

    private var isRendererOpen = true
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val renderLock = Mutex()
    private val pageCount = AtomicInteger(pdfRenderer.pageCount)

    private var totalPagesRendered = 0
    private var totalRenderTime = 0L
    private var slowestRenderTime = 0L
    private var slowestPage: Int? = null

    // Keep render job/callback bookkeeping atomic without relying on API 24
    // ConcurrentHashMap helpers such as remove(key, value) / compute(...).
    private val renderStateLock = Any()
    private val renderJobs = ConcurrentHashMap<Int, Job>()
    private val renderCallbacks = ConcurrentHashMap<Int, CopyOnWriteArrayList<(Boolean, Int, Bitmap?) -> Unit>>()
    private val pageDimensionCache = mutableMapOf<Int, Size>()
    private var prefetchJob: Job? = null

    fun getPageCount(): Int = pageCount.get().takeIf { isRendererOpen } ?: 0

    suspend fun getBitmapFromCache(pageNo: Int): Bitmap? = cacheManager.getBitmapFromCache(pageNo)

    private suspend fun addBitmapToMemoryCache(pageNo: Int, bitmap: Bitmap) = cacheManager.addBitmapToCache(pageNo, bitmap)

    private suspend fun pageExistInCache(pageNo: Int): Boolean = cacheManager.pageExistsInCache(pageNo)

    fun shouldPrefetch(): Boolean = cacheManager.shouldPrefetch()

    fun renderPage(
        pageNo: Int,
        bitmap: Bitmap,
        onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)? = null
    ) {
        renderPageInternal(pageNo, bitmap, replaceActive = true, onBitmapReady = onBitmapReady)
    }

    private fun prefetchPageIfIdle(
        pageNo: Int,
        bitmap: Bitmap,
        onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)? = null
    ) {
        renderPageInternal(pageNo, bitmap, replaceActive = false, onBitmapReady = onBitmapReady)
    }

    private fun renderPageInternal(
        pageNo: Int,
        bitmap: Bitmap,
        replaceActive: Boolean,
        onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)? = null
    ) {
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

            val renderDecision = synchronized(renderStateLock) {
                val activeRenderExists = renderJobs[pageNo]?.isActive == true
                when {
                    !replaceActive && activeRenderExists -> RenderDecision.Skip
                    replaceActive && activeRenderExists -> {
                        enqueueRenderCallbackLocked(pageNo, onBitmapReady)
                        RenderDecision.JoinExisting
                    }

                    else -> {
                        enqueueRenderCallbackLocked(pageNo, onBitmapReady)
                        val renderJob = launch(start = CoroutineStart.LAZY) {
                            var success = false
                            var renderedBitmap: Bitmap? = null

                            try {
                                // Reopen the page for each render so revisits do not depend on
                                // long-lived PdfRenderer.Page instances remaining valid.
                                val rendered = withPdfPage(pageNo) { pdfPage ->
                                    pdfPage.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )
                                }

                                if (rendered != null) {
                                    addBitmapToMemoryCache(pageNo, bitmap)
                                    success = true
                                    renderedBitmap = bitmap
                                }
                            } catch (e: Exception) {
                                Log.e(LOG_TAG, "Error rendering page $pageNo: ${e.message}", e)
                            } finally {
                                clearRenderJob(pageNo, coroutineContext[Job] as? Job)
                                deliverRenderCallbacks(pageNo, success, renderedBitmap)
                            }
                        }
                        renderJobs[pageNo] = renderJob
                        RenderDecision.Start(renderJob)
                    }
                }
            }

            when (renderDecision) {
                RenderDecision.Skip -> {
                    withContext(Dispatchers.Main) {
                        onBitmapReady?.invoke(false, pageNo, null)
                    }
                }

                RenderDecision.JoinExisting -> Unit
                is RenderDecision.Start -> renderDecision.job.start()
            }

            return@launch
        }
    }

    private fun enqueueRenderCallback(
        pageNo: Int,
        onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)?
    ) {
        if (onBitmapReady == null) return
        synchronized(renderStateLock) {
            enqueueRenderCallbackLocked(pageNo, onBitmapReady)
        }
    }

    private fun enqueueRenderCallbackLocked(
        pageNo: Int,
        onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)?
    ) {
        if (onBitmapReady == null) return
        val callbacks = renderCallbacks[pageNo] ?: CopyOnWriteArrayList<(Boolean, Int, Bitmap?) -> Unit>().also {
            renderCallbacks[pageNo] = it
        }
        callbacks.add(onBitmapReady)
    }

    private fun clearRenderJob(pageNo: Int, completedJob: Job?) {
        synchronized(renderStateLock) {
            if (renderJobs[pageNo] === completedJob) {
                renderJobs.remove(pageNo)
            }
        }
    }

    private suspend fun deliverRenderCallbacks(pageNo: Int, success: Boolean, bitmap: Bitmap?) {
        val callbacks = synchronized(renderStateLock) {
            renderCallbacks.remove(pageNo)
        } ?: return
        withContext(NonCancellable + Dispatchers.Main) {
            callbacks.forEach { callback ->
                callback(success, pageNo, bitmap)
            }
        }
    }

    private sealed interface RenderDecision {
        data object Skip : RenderDecision
        data object JoinExisting : RenderDecision
        data class Start(val job: Job) : RenderDecision
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
        if (!cacheManager.shouldPrefetch()) return
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
                val size = withPdfPage(pageNo) { page ->
                    Size(page.width, page.height)
                } ?: Size(fallbackWidth, fallbackHeight)

                val aspectRatio = size.width.toFloat() / size.height.toFloat()
                val height = (fallbackWidth / aspectRatio).toInt()

                val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(fallbackWidth, maxOf(1, height))
                prefetchPageIfIdle(pageNo, bitmap) { success, _, _ ->
                    if (!success) CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
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
                coroutineContext.ensureActive()
                if (!isRendererOpen) return@withContext null
                try {
                    pdfRenderer.openPage(pageNo).use(block)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "withPdfPage error: ${e.message}", e)
                    null
                }
            }
        }

    fun cancelRender(pageNo: Int) {
        renderJobs.remove(pageNo)?.cancel()
        renderCallbacks.remove(pageNo)
    }

    fun cancelPrefetch() {
        prefetchJob?.cancel()
    }

    fun closePdfRender() {
        if (!isRendererOpen) return

        Log.d(LOG_TAG, "Closing PdfRenderer and releasing resources.")

        scope.coroutineContext.cancelChildren()

        runBlocking {
            renderLock.withLock {
                if (!isRendererOpen) return@withLock

                // Flip the guard before closing native objects so any pending work bails out.
                isRendererOpen = false

                runCatching { pdfRenderer.close() }
                    .onFailure { Log.e(LOG_TAG, "Error closing PdfRenderer: ${it.message}", it) }

                runCatching { fileDescriptor.close() }
                    .onFailure { Log.e(LOG_TAG, "Error closing file descriptor: ${it.message}", it) }
            }
        }
    }
}

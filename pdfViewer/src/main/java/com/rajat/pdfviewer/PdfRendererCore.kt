    package com.rajat.pdfviewer
    import android.content.Context
    import android.graphics.Bitmap
    import android.graphics.Bitmap.CompressFormat
    import android.graphics.Color
    import android.graphics.pdf.PdfRenderer
    import android.os.Build
    import android.os.ParcelFileDescriptor
    import android.util.Log
    import android.util.Size
    import com.rajat.pdfviewer.util.CacheManager
    import com.rajat.pdfviewer.util.CacheManager.Companion.CACHE_PATH
    import com.rajat.pdfviewer.util.CommonUtils
    import com.rajat.pdfviewer.util.CommonUtils.Companion.calculateDynamicPrefetchCount
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import java.io.File
    import java.io.FileOutputStream
    import java.nio.file.Files
    import java.nio.file.Paths
    import java.util.concurrent.ConcurrentHashMap

    /**
     * Created by Rajat on 11,July,2020
     */

    internal class PdfRendererCore(
        private val context: Context,
        fileDescriptor: ParcelFileDescriptor
    ) {

        private var isRendererOpen = false

        constructor(context: Context, file: File) : this(
            context = context,
            fileDescriptor = getFileDescriptor(file)
        )

        private val openPages = ConcurrentHashMap<Int, PdfRenderer.Page>()
        private var pdfRenderer: PdfRenderer? = null
        private val cacheManager = CacheManager(context)

        companion object {

            private fun sanitizeFilePath(filePath: String): String {
                return try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val path = Paths.get(filePath)
                        if (Files.exists(path)) {
                            filePath
                        } else {
                            "" // Return a default safe path or handle the error
                        }
                    } else {
                        filePath
                    }
                } catch (e: Exception) {
                    "" // Handle the exception and return a safe default path
                }
            }

            internal fun getFileDescriptor(file: File): ParcelFileDescriptor {
                val safeFile = File(sanitizeFilePath(file.path))
                return ParcelFileDescriptor.open(safeFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }


        init {
            pdfRenderer = PdfRenderer(fileDescriptor).also { isRendererOpen = true }
            cacheManager.initCache()
        }

        internal fun getBitmapFromCache(pageNo: Int): Bitmap? =
            cacheManager.getBitmapFromCache(pageNo)

        private fun addBitmapToMemoryCache(pageNo: Int, bitmap: Bitmap) =
            cacheManager.addBitmapToCache(pageNo, bitmap)

        private fun writeBitmapToCache(pageNo: Int, bitmap: Bitmap, shouldCache: Boolean = true) {
            if (!shouldCache) return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val savePath = File(File(context.cacheDir, CACHE_PATH), pageNo.toString())
                    FileOutputStream(savePath).use { fos ->
                        bitmap.compress(CompressFormat.JPEG, 75, fos) // Compress as JPEG
                    }
                } catch (e: Exception) {
                    Log.e("PdfRendererCore", "Error writing bitmap to cache: ${e.message}")
                }
            }
        }
        fun pageExistInCache(pageNo: Int): Boolean =
            cacheManager.pageExistsInCache(pageNo)

        fun prefetchPages(currentPage: Int, width: Int, height: Int) {
            val dynamicPrefetchCount = calculateDynamicPrefetchCount(context, pdfRenderer!!)
            (currentPage - dynamicPrefetchCount..currentPage + dynamicPrefetchCount)
                .filter { it in 0 until pdfRenderer!!.pageCount && !pageExistInCache(it) }
                .forEach { pageNo ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
                        renderPage(pageNo, bitmap) { success, _, _ ->
                            if (success) writeBitmapToCache(pageNo, bitmap)
                            else CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                        }
                    }
                }
        }
        private fun openPdfFile(fileDescriptor: ParcelFileDescriptor) {
            pdfRenderer = PdfRenderer(fileDescriptor)
        }

        fun getPageCount(): Int {
            synchronized(this) {
                if (!isRendererOpen) return 0
                return pdfRenderer?.pageCount ?: 0
            }
        }

        fun renderPage(pageNo: Int, bitmap: Bitmap, onBitmapReady: ((success: Boolean, pageNo: Int, bitmap: Bitmap?) -> Unit)? = null) {
            if (pageNo >= getPageCount()) {
                onBitmapReady?.invoke(false, pageNo, null)
                return
            }
            val cachedBitmap = getBitmapFromCache(pageNo)
            if (cachedBitmap != null) {
                CoroutineScope(Dispatchers.Main).launch { onBitmapReady?.invoke(true, pageNo, cachedBitmap) }
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                synchronized(this@PdfRendererCore) {
                    if (!isRendererOpen) return@launch
                    openPageSafely(pageNo)?.use { pdfPage ->
                        try {
                            bitmap.eraseColor(Color.WHITE) // Clear the bitmap with white color
                            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            addBitmapToMemoryCache(pageNo, bitmap)
                            CoroutineScope(Dispatchers.IO).launch { writeBitmapToCache(pageNo, bitmap) }
                            CoroutineScope(Dispatchers.Main).launch { onBitmapReady?.invoke(true, pageNo, bitmap) }
                        } catch (e: Exception) {
                            CoroutineScope(Dispatchers.Main).launch { onBitmapReady?.invoke(false, pageNo, null) }
                        }
                    }
                }
            }
        }

        private suspend fun <T> withPdfPage(pageNo: Int, block: (PdfRenderer.Page) -> T): T? =
            withContext(Dispatchers.IO) {
                synchronized(this@PdfRendererCore) {
                    pdfRenderer?.openPage(pageNo)?.use { page ->
                        return@withContext block(page)
                    }
                }
                null
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
                if (!isRendererOpen) return null
                closeAllOpenPages()
                return pdfRenderer?.openPage(pageNo)?.also { page ->
                    openPages[pageNo] = page
                }
            }
        }

        private fun closeAllOpenPages() {
            synchronized(this) {
                openPages.values.forEach { page ->
                    try {
                        page.close()
                    } catch (e: IllegalStateException) {
                        Log.e("PDFRendererCore","Page was already closed")
                    }
                }
                openPages.clear() // Clear the map after closing all pages.
            }
        }

        fun closePdfRender() {
            synchronized(this) {
                closeAllOpenPages()
                if (isRendererOpen) {
                    pdfRenderer?.close()
                    isRendererOpen = false
                }
                cacheManager.clearCache()
            }
        }

    }

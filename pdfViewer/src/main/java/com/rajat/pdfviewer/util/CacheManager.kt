package com.rajat.pdfviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.rajat.pdfviewer.util.CacheHelper.handleCacheStrategy
import com.rajat.pdfviewer.util.CommonUtils.Companion.MAX_CACHED_PDFS
import com.rajat.pdfviewer.util.FileUtils.cachedFileNameWithFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CacheManager(
    private val context: Context,
    private val currentOpenedFileName: String,
    private val cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
) {
    private val memoryCache: LruCache<Int, Bitmap> = createMemoryCache()
    private var cacheDir = File(context.cacheDir, "${CACHE_PATH}/$currentOpenedFileName")
    /**
     * Guards all [memoryCache] reads and writes so that compound check-then-update sequences
     * (e.g. in [getBitmapFromCacheIfAdequate]) are serialized across coroutines.
     */
    private val memoryCacheMutex = Mutex()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (cacheStrategy == CacheStrategy.DISABLE_CACHE) return@withContext

        cacheDir = File(context.cacheDir, "$CACHE_PATH/$currentOpenedFileName")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        handleCacheStrategy(
            "CacheManager",
            cacheDir,
            cacheStrategy,
            currentOpenedFileName,
            MAX_CACHED_PDFS
        )
    }

    private fun createMemoryCache(): LruCache<Int, Bitmap> {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 6
        return object : LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    suspend fun getBitmapFromCache(pageNo: Int): Bitmap? = withContext(Dispatchers.IO) {
        memoryCacheMutex.withLock { memoryCache.get(pageNo) }?.let { return@withContext it }
        if (cacheStrategy == CacheStrategy.DISABLE_CACHE) return@withContext null

        decodeBitmapFromDiskCache(pageNo)?.also {
            memoryCacheMutex.withLock { memoryCache.put(pageNo, it) }
        }
    }

    /**
     * Returns the cached [Bitmap] for [pageNo] if it exists **and** its dimensions are at least
     * [minWidth] × [minHeight]; otherwise returns `null`. All [memoryCache] reads and writes are
     * guarded by [memoryCacheMutex] to prevent concurrent modification. For disk-only entries a
     * bounds-only decode is done first so that no pixel data is allocated when the cached
     * resolution is too small for the current zoom level. In the unlikely case that two coroutines
     * race to decode the same disk entry, both produce identical content so the last writer wins
     * without data corruption.
     */
    suspend fun getBitmapFromCacheIfAdequate(pageNo: Int, minWidth: Int, minHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            // Memory cache: perform the entire check under the lock so that the size-check and
            // return are truly atomic, eliminating any window where the bitmap could be evicted
            // between the get() and the .width access. A boolean tracks whether a (too-small)
            // entry was found to avoid a spurious disk-cache lookup when the page is already
            // cached at a lower resolution.
            var foundInMemoryCache = false
            val memoryCached = memoryCacheMutex.withLock {
                memoryCache.get(pageNo)?.also { foundInMemoryCache = true }
                    ?.takeIf { it.width >= minWidth && it.height >= minHeight }
            }
            if (foundInMemoryCache) return@withContext memoryCached  // null if too small, bitmap if adequate
            if (cacheStrategy == CacheStrategy.DISABLE_CACHE) return@withContext null

            // Disk cache: check bounds first to avoid a full decode for undersized entries.
            ensureActive()
            val file = File(cacheDir, cachedFileNameWithFormat(pageNo))
            if (!file.exists()) return@withContext null

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth < minWidth || opts.outHeight < minHeight) return@withContext null

            // Dimensions are adequate — do the full decode.
            ensureActive()
            decodeBitmapFromDiskCache(pageNo)?.also {
                memoryCacheMutex.withLock { memoryCache.put(pageNo, it) }
            }
        }

    private fun decodeBitmapFromDiskCache(pageNo: Int): Bitmap? {
        val file = File(cacheDir, cachedFileNameWithFormat(pageNo))
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    suspend fun addBitmapToCache(pageNo: Int, bitmap: Bitmap) {
        memoryCacheMutex.withLock { memoryCache.put(pageNo, bitmap) }
        if (cacheStrategy != CacheStrategy.DISABLE_CACHE) {
            writeBitmapToCache(pageNo, bitmap)
        }
    }

    private suspend fun writeBitmapToCache(pageNo: Int, bitmap: Bitmap) = withContext(Dispatchers.IO) {

        runCatching {

            cacheDir.mkdirs()
            val savePath = File(cacheDir, cachedFileNameWithFormat(pageNo))
            savePath.parentFile?.mkdirs()
            FileOutputStream(savePath).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }.onFailure {
            Log.e("CacheManager", "Error writing bitmap to cache (Page $pageNo)", it)
        }
    }

    suspend fun pageExistsInCache(pageNo: Int): Boolean = withContext(Dispatchers.IO) {
        if (cacheStrategy == CacheStrategy.DISABLE_CACHE) return@withContext false
        File(cacheDir, cachedFileNameWithFormat(pageNo)).exists()
    }

    companion object {
        const val CACHE_PATH = "___pdf___cache___"

        suspend fun clearCacheDir(context: Context) {
            withContext(Dispatchers.IO) {
                val cacheDir = File(context.cacheDir, CACHE_PATH)
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
            }
        }
    }
}

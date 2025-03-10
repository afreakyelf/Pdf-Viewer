package com.rajat.pdfviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.rajat.pdfviewer.util.CacheHelper.handleCacheStrategy
import com.rajat.pdfviewer.util.CommonUtils.Companion.MAX_CACHED_PDFS
import com.rajat.pdfviewer.util.FileUtils.cachedFileNameWithFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class CacheManager(
    context: Context,
    currentOpenedFileName: String,
    cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
) {
    private val memoryCache: LruCache<Int, Bitmap> = createMemoryCache()
    private val cacheDir = File(context.cacheDir, "${CACHE_PATH}/$currentOpenedFileName")

    init {
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

    fun getBitmapFromCache(pageNo: Int): Bitmap? {
        return memoryCache.get(pageNo) ?: decodeBitmapFromDiskCache(pageNo)?.also {
            memoryCache.put(pageNo, it)
        }
    }

    private fun decodeBitmapFromDiskCache(pageNo: Int): Bitmap? {
        val file = File(cacheDir, cachedFileNameWithFormat(pageNo))
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun addBitmapToCache(pageNo: Int, bitmap: Bitmap) {
        memoryCache.put(pageNo, bitmap)
        writeBitmapToCache(pageNo, bitmap)
    }

    private fun writeBitmapToCache(pageNo: Int, bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                val savePath = File(cacheDir, cachedFileNameWithFormat(pageNo))

                savePath.parentFile?.let {
                    if (!it.exists()) {
                        it.mkdirs()
                    }
                }

                FileOutputStream(savePath).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
            } catch (e: Exception) {
                Log.e("CacheManager", "Error writing bitmap to cache", e)
            }
        }
    }

    fun pageExistsInCache(pageNo: Int): Boolean =
        File(cacheDir, cachedFileNameWithFormat(pageNo)).exists()

    companion object {
        const val CACHE_PATH = "___pdf___cache___"
    }
}

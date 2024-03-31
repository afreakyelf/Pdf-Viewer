package com.rajat.pdfviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.rajat.pdfviewer.PdfRendererCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class CacheManager(private val context: Context) {
    private val memoryCache: LruCache<Int, Bitmap> = createMemoryCache()
    private val cacheDir = File(context.cacheDir, CACHE_PATH)

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun createMemoryCache(): LruCache<Int, Bitmap> {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() // Use 1/8th of available memory for cache
        val cacheSize = maxMemory / 8
        return object : LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun initCache() {
        val cacheDir = File(context.cacheDir, Companion.CACHE_PATH)
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        cacheDir.mkdirs()
    }

    fun getBitmapFromCache(pageNo: Int): Bitmap? =
        memoryCache.get(pageNo) ?: decodeBitmapFromDiskCache(pageNo)

    private fun decodeBitmapFromDiskCache(pageNo: Int): Bitmap? {
        val file = File(cacheDir, pageNo.toString())
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun addBitmapToCache(pageNo: Int, bitmap: Bitmap) {
        memoryCache.put(pageNo, bitmap)
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(cacheDir, pageNo.toString())
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos)
            }
        }
    }

    fun pageExistsInCache(pageNo: Int): Boolean =
        File(cacheDir, pageNo.toString()).exists()

    fun clearCache() {
        memoryCache.evictAll()
        cacheDir.deleteRecursively()
    }

    companion object {
        const val CACHE_PATH = "___pdf___cache___"
    }
}

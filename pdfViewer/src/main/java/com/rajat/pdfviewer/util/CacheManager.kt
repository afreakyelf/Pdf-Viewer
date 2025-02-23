package com.rajat.pdfviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
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
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 6  // Increased cache size
        return object : LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun initCache() {
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
        writeBitmapToCache(pageNo, bitmap)
    }

    fun writeBitmapToCache(pageNo: Int, bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure cache directory exists
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                val savePath = File(cacheDir, pageNo.toString())

                // Ensure parent directory exists
                savePath.parentFile?.let {
                    if (!it.exists()) {
                        it.mkdirs()
                    }
                }

                FileOutputStream(savePath).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos)
                }
            } catch (e: Exception) {
                Log.e("CacheManager", "Error writing bitmap to cache", e)
            }
        }
    }

    fun pageExistsInCache(pageNo: Int): Boolean =
        File(cacheDir, pageNo.toString()).exists()

    fun clearCache() {
        memoryCache.evictAll()
        cacheDir.deleteRecursively()

        // Ensure cache directory is re-created after clearing
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    companion object {
        const val CACHE_PATH = "___pdf___cache___"
    }
}

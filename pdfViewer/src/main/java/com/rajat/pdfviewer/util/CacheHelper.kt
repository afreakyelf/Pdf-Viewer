package com.rajat.pdfviewer.util

import android.util.Log
import java.io.File
import kotlin.math.max

object CacheHelper {

    // **Apply Cache Strategy**
    fun handleCacheStrategy(
        origin: String,
        cacheDir: File,
        cacheStrategy: CacheStrategy,
        newFileName: String,
        maxCachedPdfs: Int
    ) {
        Log.d(
            "CacheHelper",
            "[$origin] Cache Strategy: $cacheStrategy | Directory: $cacheDir | File: $newFileName"
        )
        when (cacheStrategy) {
            CacheStrategy.MINIMIZE_CACHE -> {
                clearAllPreviousCache(origin, cacheDir, newFileName)
            }
            CacheStrategy.MAXIMIZE_PERFORMANCE -> {
                updateCacheAccessTime(cacheDir)
                enforceCacheLimit(origin, cacheDir, maxCachedPdfs)
            }
        }
    }

    // **Clear all old files, keeping only the latest (for MINIMIZE_CACHE)**
    private fun clearAllPreviousCache(origin: String, cacheDir: File, keepFileName: String) {
        val cachedFiles = cacheDir.parentFile?.listFiles() ?: return

        // If the file is not in cache, remove all previous files
        cachedFiles.forEach { file ->
            Log.d("CacheHelper", "From $origin : Deleting old cached file: ${file.absolutePath}")
            if (file.name != keepFileName) {
                file.deleteRecursively()
            }
        }
    }


    // **Enforce LRU-based limit but only remove the oldest file if needed**
    private fun enforceCacheLimit(
        origin: String,
        cacheDir: File,
        maxCachedPdfs: Int
    ) {
        val cachedFolders =
            cacheDir.parentFile?.listFiles()?.filter { it.isDirectory } ?: return

        if (cachedFolders.size >= max(maxCachedPdfs,1)) {
            cachedFolders.minByOrNull { it.lastModified() }?.let {
                Log.d("CacheHelper", "[$origin] Evicting old cached folder: ${it.absolutePath}")
                it.deleteRecursively()
            }
        }
    }

    // **Update access time**
    private fun updateCacheAccessTime(cacheDir: File) {
        cacheDir.setLastModified(System.currentTimeMillis())
    }
}

package com.rajat.pdfviewer.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object CacheHelper {

    suspend fun handleCacheStrategy(
        origin: String,
        cacheDir: File,
        cacheStrategy: CacheStrategy,
        maxCachedPdfs: Int
    ) = withContext(Dispatchers.IO) {
        val cachePolicy = CachePolicy.from(cacheStrategy, maxCachedPdfs)
        logDebug("[$origin] Cache Strategy: $cacheStrategy | Directory: $cacheDir")
        applyDocumentRetention(origin, cacheDir, cachePolicy)
    }

    internal fun applyDocumentRetention(origin: String, cacheDir: File, cachePolicy: CachePolicy) {
        if (cachePolicy.maxRetainedDocuments <= 0) return

        cacheDir.mkdirs()
        updateCacheAccessTime(cacheDir)

        val cacheRoot = cacheDir.parentFile ?: return
        val cachedFolders = cacheRoot.listFiles()?.filter { it.isDirectory } ?: return
        val overflowCount = cachedFolders.size - cachePolicy.maxRetainedDocuments
        if (overflowCount <= 0) return

        cachedFolders
            .filter { it.name != cacheDir.name }
            .sortedBy { it.lastModified() }
            .take(overflowCount)
            .forEach { file ->
                logDebug("[$origin] Evicting cached folder: ${file.absolutePath}")
                file.deleteRecursively()
            }
    }

    internal fun cleanupTransientDocument(file: File) {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
            val parent = file.parentFile ?: return
            if (parent.exists() && parent.listFiles().isNullOrEmpty()) {
                parent.deleteRecursively()
            }
        }
    }

    private fun updateCacheAccessTime(cacheDir: File) {
        cacheDir.setLastModified(System.currentTimeMillis())
    }

    private fun logDebug(message: String) {
        runCatching {
            Log.d("CacheHelper", message)
        }
    }

    fun getCacheKey(source: String): String {
        val prefix = if (source.startsWith("http")) "url_" else "file_"
        val hash = sha256(source)
        return prefix + hash
    }

    fun getRemoteDocumentCacheKey(url: String): String {
        return getCacheKey(url) + ".pdf"
    }

    // Persistent strategies intentionally share one remote document key so they can
    // reuse the same retained file across sessions. DISABLE_CACHE gets a session key
    // instead so its transient cleanup never deletes another strategy's retained copy.
    fun getRemoteDocumentCacheKey(
        url: String,
        cacheStrategy: CacheStrategy,
        sessionToken: String = UUID.randomUUID().toString()
    ): String {
        val cachePolicy = CachePolicy.from(cacheStrategy)
        if (cachePolicy.persistRemoteFile) {
            return getRemoteDocumentCacheKey(url)
        }
        return "session_${sha256("$url#$sessionToken")}.pdf"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

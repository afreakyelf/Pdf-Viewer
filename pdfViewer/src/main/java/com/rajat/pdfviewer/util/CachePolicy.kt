package com.rajat.pdfviewer.util

import com.rajat.pdfviewer.util.CommonUtils.Companion.MAX_CACHED_PDFS

internal data class CachePolicy(
    val reuseRemoteFile: Boolean,
    val persistRemoteFile: Boolean,
    val maxRetainedDocuments: Int,
    val useMemoryBitmapCache: Boolean,
    val useDiskBitmapCache: Boolean,
    val enablePrefetch: Boolean
) {
    companion object {
        fun from(
            strategy: CacheStrategy,
            maxRetainedDocuments: Int = MAX_CACHED_PDFS
        ): CachePolicy = when (strategy) {
            CacheStrategy.MAXIMIZE_PERFORMANCE -> CachePolicy(
                reuseRemoteFile = true,
                persistRemoteFile = true,
                maxRetainedDocuments = maxRetainedDocuments,
                useMemoryBitmapCache = true,
                useDiskBitmapCache = true,
                enablePrefetch = true
            )

            CacheStrategy.MINIMIZE_CACHE -> CachePolicy(
                reuseRemoteFile = true,
                persistRemoteFile = true,
                maxRetainedDocuments = 1,
                useMemoryBitmapCache = true,
                useDiskBitmapCache = false,
                enablePrefetch = true
            )

            CacheStrategy.DISABLE_CACHE -> CachePolicy(
                reuseRemoteFile = false,
                persistRemoteFile = false,
                maxRetainedDocuments = 0,
                useMemoryBitmapCache = true,
                useDiskBitmapCache = false,
                enablePrefetch = false
            )
        }
    }
}

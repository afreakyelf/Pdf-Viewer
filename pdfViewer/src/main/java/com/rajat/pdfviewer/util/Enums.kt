package com.rajat.pdfviewer.util

sealed class DownloadStatus {
    object Started : DownloadStatus()
    object Success : DownloadStatus()
    object Failure : DownloadStatus()
    data class Progress(val progress: Int) : DownloadStatus()
}

enum class saveTo {
    DOWNLOADS,
    ASK_EVERYTIME
}

enum class CacheStrategy {
    MINIMIZE_CACHE,  // Keep only the current document cache on disk and reuse pages in memory.
    MAXIMIZE_PERFORMANCE, // Retain recent document/page cache for fastest reopen and scrolling.
    DISABLE_CACHE // Disable persistent cache and prefetch while keeping visible rendering correct.
}

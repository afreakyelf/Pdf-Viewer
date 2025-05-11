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
    MINIMIZE_CACHE,  // Keep only one file at a time
    MAXIMIZE_PERFORMANCE, // Store up to 5 PDFs using LRU eviction
    DISABLE_CACHE // Disable caching
}

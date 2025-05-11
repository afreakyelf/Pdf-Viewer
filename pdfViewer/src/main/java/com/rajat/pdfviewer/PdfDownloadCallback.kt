package com.rajat.pdfviewer

import android.content.Context
import java.io.File

internal class PdfDownloadCallback(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onProgress: (Int, Long, Long) -> Unit,
    private val onSuccess: (File) -> Unit,
    private val onError: (Throwable) -> Unit
) : PdfDownloader.StatusListener {

    override fun getContext(): Context = context

    override fun onDownloadStart() {
        onStart()
    }

    override fun onDownloadProgress(currentBytes: Long, totalBytes: Long) {
        val progress = (currentBytes.toFloat() / totalBytes.toFloat() * 100F).toInt().coerceAtMost(100)
        onProgress(progress, currentBytes, totalBytes)
    }

    override fun onDownloadSuccess(downloadedFile: File) {
        onSuccess(downloadedFile)
    }

    override fun onDownloadError(error: Throwable) {
        error.printStackTrace()
        onError(error)
    }
}

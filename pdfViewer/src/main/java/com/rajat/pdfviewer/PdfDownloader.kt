package com.rajat.pdfviewer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

class PdfDownloader(
    private val coroutineScope: CoroutineScope,
    private val headers: HeaderData,
    private val url: String,
    private val listener: StatusListener
) {

    interface StatusListener {
        fun getContext(): Context
        fun onDownloadStart()
        fun onDownloadProgress(currentBytes: Long, totalBytes: Long)
        fun onDownloadSuccess(absolutePath: String)
        fun onError(error: Throwable)
    }

    private var lastDownloadedFile: String? = null

    init {
        coroutineScope.launch { checkAndDownload(url) }
    }

    private fun getCachedFileName(url: String): String {
        return url.hashCode().toString() + ".pdf"
    }

    private fun clearPdfCache(exceptFileName: String? = null) {
        val cacheDir = listener.getContext().cacheDir
        val pdfFiles = cacheDir.listFiles { _, name -> name.endsWith(".pdf") && name != exceptFileName }
        pdfFiles?.forEach { it.delete() }
    }

    private suspend fun checkAndDownload(downloadUrl: String) {
        val cachedFileName = getCachedFileName(downloadUrl)

        if (lastDownloadedFile != cachedFileName) {
            clearPdfCache(cachedFileName) // Clear previous cache if a new file is being accessed
        }

        val cachedFile = File(listener.getContext().cacheDir, cachedFileName)

        if (cachedFile.exists()) {
            listener.onDownloadSuccess(cachedFile.absolutePath)
        } else {
            download(downloadUrl, cachedFileName)
        }

        lastDownloadedFile = cachedFileName // Update the last downloaded file
    }

    private suspend fun download(downloadUrl: String, cachedFileName: String) {
        withContext(Dispatchers.IO) {
            try {
                listener.onDownloadStart()
                val outputFile = File(listener.getContext().cacheDir, cachedFileName)
                outputFile.delete()
                val urlConnection = URL(downloadUrl).openConnection().apply {
                    // Apply headers to the URL connection
                    headers.headers.forEach { (key, value) ->
                        setRequestProperty(key, value)
                    }
                }
                val totalLength = urlConnection.contentLength
                BufferedInputStream(urlConnection.getInputStream()).use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        val data = ByteArray(8192)
                        var totalBytesRead = 0L
                        var bytesRead: Int

                        while (inputStream.read(data).also { bytesRead = it } != -1) {
                            outputStream.write(data, 0, bytesRead)
                            totalBytesRead += bytesRead
                            withContext(Dispatchers.Main) {
                                listener.onDownloadProgress(totalBytesRead, totalLength.toLong())
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    listener.onDownloadSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onError(e)
                }
            }
        }
    }
}

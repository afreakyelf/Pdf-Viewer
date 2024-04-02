package com.rajat.pdfviewer

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

    companion object {
        private const val MAX_RETRIES = 3 // Maximum number of retries
        private const val RETRY_DELAY = 2000L // Delay between retries in milliseconds
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
        var retries = 0
        while (retries < MAX_RETRIES) {
            withContext(Dispatchers.IO) {
                var tempFile: File? = null
                try {
                    listener.onDownloadStart()
                    val cacheDir = listener.getContext().cacheDir
                    tempFile = File.createTempFile("download_", ".tmp", cacheDir)
                    val urlConnection = URL(downloadUrl).openConnection().apply {
                        headers.headers.forEach { (key, value) -> setRequestProperty(key, value) }
                    }
                    val totalLength: Long = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            urlConnection.contentLengthLong
                        } else {
                            urlConnection.contentLength.toLong()
                        }
                    } catch (e: NoSuchMethodError) {
                        urlConnection.contentLength.toLong()
                    }
                    BufferedInputStream(urlConnection.getInputStream()).use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            val data = ByteArray(8192)
                            var totalBytesRead = 0L
                            var bytesRead: Int
                            while (inputStream.read(data).also { bytesRead = it } != -1) {
                                outputStream.write(data, 0, bytesRead)
                                totalBytesRead += bytesRead
                                withContext(Dispatchers.Main) {
                                    listener.onDownloadProgress(totalBytesRead, totalLength)
                                }
                            }
                            outputStream.flush() // Ensure all data is written to the file
                        }
                    }
                    if (tempFile.length() == totalLength) {
                        val outputFile = File(cacheDir, cachedFileName)
                        tempFile.renameTo(outputFile)
                        withContext(Dispatchers.Main) { listener.onDownloadSuccess(outputFile.absolutePath) }
                        retries = MAX_RETRIES
                        return@withContext
                    } else {
                        throw IOException("Incomplete download")
                    }
                } catch (e: IOException) {
                    tempFile?.delete()
                    Log.e("PdfDownloader", "Download incomplete or failed: $downloadUrl", e)
                    withContext(Dispatchers.Main) { listener.onError(e) }
                    retries++
                    if (retries < MAX_RETRIES) {
                        Log.d("PdfDownloader", "Retrying download: $downloadUrl. Attempt $retries")
                        delay(RETRY_DELAY) // Backoff before retrying
                    } else {
                        withContext(Dispatchers.Main) {
                            listener.onError(IOException("Failed to download after $MAX_RETRIES attempts: $downloadUrl", e))
                        }
                    }
                }

            }
        }
    }

}

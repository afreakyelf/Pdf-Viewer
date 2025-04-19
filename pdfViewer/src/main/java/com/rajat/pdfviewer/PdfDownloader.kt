package com.rajat.pdfviewer

import android.content.Context
import android.util.Log
import com.rajat.pdfviewer.util.CacheHelper
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.CommonUtils.Companion.MAX_CACHED_PDFS
import com.rajat.pdfviewer.util.FileUtils.getCachedFileName
import com.rajat.pdfviewer.util.FileUtils.isValidPdf
import com.rajat.pdfviewer.util.FileUtils.writeFile
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.IOException

class PdfDownloader(
    private val coroutineScope: CoroutineScope,
    private val headers: HeaderData,
    private val url: String,
    private val cacheStrategy: CacheStrategy,
    private val listener: StatusListener,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    interface StatusListener {
        fun getContext(): Context
        fun onDownloadStart()
        fun onDownloadProgress(currentBytes: Long, totalBytes: Long)
        fun onDownloadSuccess(downloadedFile: File)
        fun onError(error: Throwable)
    }

    fun start() {
        coroutineScope.launch(Dispatchers.Main) {
            listener.onDownloadStart()
        }
        coroutineScope.launch(Dispatchers.IO) {
            checkAndDownload(url)
        }
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY = 2000L

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

    private suspend fun checkAndDownload(downloadUrl: String) {
        val cachedFileName = getCachedFileName(downloadUrl)
        val cacheDir = File(listener.getContext().cacheDir, "___pdf___cache___/$cachedFileName")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val pdfFile = File(cacheDir, cachedFileName)

        CacheHelper.handleCacheStrategy("Downloader", cacheDir, cacheStrategy, cachedFileName, MAX_CACHED_PDFS)

        if (pdfFile.exists() && isValidPdf(pdfFile)) {
            withContext(Dispatchers.Main) {
                listener.onDownloadSuccess(pdfFile)
            }
        } else {
            retryDownload(downloadUrl, pdfFile)
        }
    }

    private suspend fun retryDownload(downloadUrl: String, pdfFile: File) {
        Log.d("PdfDownloader", "Retrying download for: $downloadUrl")
        withContext(Dispatchers.Main) {
            listener.onDownloadStart()
        }
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                downloadFile(downloadUrl, pdfFile)
                return
            } catch (e: IOException) {
                if (isInvalidFileError(e)) {
                    listener.onError(e)
                    return
                }

                attempt++
                Log.e("PdfDownloader", "Attempt $attempt failed: $downloadUrl", e)

                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY)
                } else {
                    withContext(Dispatchers.Main) {
                        listener.onError(
                            IOException("Failed to download after $MAX_RETRIES attempts: $downloadUrl", e)
                        )
                    }
                }
            }
        }
    }

    private fun isInvalidFileError(error: IOException): Boolean {
        val message = error.message ?: return false
        return message.contains("Invalid content type", ignoreCase = true) ||
                message.contains("Downloaded file is not a valid PDF", ignoreCase = true)
    }

    private suspend fun downloadFile(downloadUrl: String, pdfFile: File) = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("download_", ".tmp", pdfFile.parentFile)

        try {
            if (pdfFile.exists() && !isValidPdf(pdfFile)) {
                pdfFile.delete()
            }

            val response = makeNetworkRequest(downloadUrl)
            validateResponse(response)

            response.body?.use { body ->
                body.byteStream().use { inputStream ->
                    writeFile(inputStream, tempFile, body.contentLength()) { progress ->
                        coroutineScope.launch(Dispatchers.Main) {
                            listener.onDownloadProgress(progress, body.contentLength())
                        }
                    }
                }
            } ?: throw IOException("Empty response body received for PDF")

            val renamed = tempFile.renameTo(pdfFile)
            if (!renamed) {
                tempFile.delete()
                throw IOException("Failed to rename temp file to final PDF path")
            }

            if (!isValidPdf(pdfFile)) {
                pdfFile.delete()
                throw IOException("Downloaded file is not a valid PDF")
            }

            Log.d("PdfDownloader", "Downloaded PDF to: ${pdfFile.absolutePath}")

            coroutineScope.launch(Dispatchers.Main) {
                listener.onDownloadSuccess(pdfFile)
            }
        } catch (e: Exception) {
            tempFile.delete() // Clean up temp on failure
            throw e
        }
    }

    private fun makeNetworkRequest(downloadUrl: String): Response {
        val requestBuilder = Request.Builder().url(downloadUrl)
        headers.headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        return httpClient.newCall(requestBuilder.build()).execute()
    }

    private fun validateResponse(response: Response) {
        if (!response.isSuccessful) {
            throw IOException("Failed to download PDF, HTTP Status: ${response.code}")
        }

        val contentType = response.header("Content-Type", "")
        if (!contentType.isNullOrEmpty() && !contentType.contains(
                "application/pdf",
                ignoreCase = true
            )
        ) {
            throw IOException("Invalid content type received: $contentType. Expected a PDF file.")
        }
    }
}
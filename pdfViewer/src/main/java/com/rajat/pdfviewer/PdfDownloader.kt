package com.rajat.pdfviewer

import android.content.Context
import android.util.Log
import com.rajat.pdfviewer.util.CacheHelper
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.CommonUtils.Companion.MAX_CACHED_PDFS
import com.rajat.pdfviewer.util.FileUtils.getCachedFileName
import com.rajat.pdfviewer.util.FileUtils.isValidPdf
import com.rajat.pdfviewer.util.FileUtils.writeFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

class PdfDownloader(
    private val coroutineScope: CoroutineScope,
    private val headers: HeaderData,
    private val url: String,
    private val cacheStrategy: CacheStrategy,
    private val listener: StatusListener
) {

    interface StatusListener {
        fun getContext(): Context
        fun onDownloadStart()
        fun onDownloadProgress(currentBytes: Long, totalBytes: Long)
        fun onDownloadSuccess(downloadedFile: File)
        fun onError(error: Throwable)
    }

    init {
        coroutineScope.launch { checkAndDownload(url) }
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY = 2000L
    }

    private suspend fun checkAndDownload(downloadUrl: String) {
        val cachedFileName = getCachedFileName(downloadUrl)
        val cacheDir = File(listener.getContext().cacheDir, "___pdf___cache___/$cachedFileName") // Folder for PDF + Rendered Pages

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val pdfFile = File(cacheDir, cachedFileName)

        // Use centralized cache logic
        CacheHelper.handleCacheStrategy("Downloader", cacheDir, cacheStrategy, cachedFileName, MAX_CACHED_PDFS)

        if (pdfFile.exists() && isValidPdf(pdfFile)) {
            withContext(Dispatchers.Main) {
                listener.onDownloadSuccess(pdfFile)
            }
        } else {
            retryDownload(downloadUrl, pdfFile)
        }
    }


    private suspend fun retryDownload(downloadUrl: String, cachedFileName: File) {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                downloadFile(downloadUrl, cachedFileName)
                return // Exit loop on success
            } catch (e: IOException) {
                if (isInvalidFileError(e)) {
                    listener.onError(e)
                    return // Exit immediately for invalid files (do not retry)
                }

                attempt++
                Log.e("PdfDownloader", "Attempt $attempt failed: $downloadUrl", e)

                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY) // Wait before retrying
                } else {
                    listener.onError(
                        IOException(
                            "Failed to download after $MAX_RETRIES attempts: $downloadUrl",
                            e
                        )
                    )
                }
            }
        }
    }

    private fun isInvalidFileError(error: IOException): Boolean {
        val message = error.message ?: return false
        return message.contains("Invalid content type") || message.contains("Downloaded file is not a valid PDF")
    }

    private suspend fun downloadFile(downloadUrl: String, pdfFile: File) {
        withContext(Dispatchers.IO) {
            listener.getContext().cacheDir
            val tempFile = File.createTempFile("download_", ".tmp", pdfFile.parentFile)

            if (pdfFile.exists() && !isValidPdf(pdfFile)) {
                pdfFile.delete()
            }

            val response = makeNetworkRequest(downloadUrl)

            validateResponse(response)

            response.body?.byteStream()?.use { inputStream ->
                writeFile(inputStream, tempFile, response.body!!.contentLength()) { progress ->
                    // Ensure progress updates happen on the Main Thread
                    coroutineScope.launch(Dispatchers.Main) {
                        listener.onDownloadProgress(progress, response.body!!.contentLength())
                    }
                }
            }

            tempFile.renameTo(pdfFile)

            if (!isValidPdf(pdfFile)) {
                pdfFile.delete()
                throw IOException("Downloaded file is not a valid PDF")
            } else {
                Log.d("PdfDownloader", "Downloaded PDF to: ${pdfFile.absolutePath}")
            }

            coroutineScope.launch(Dispatchers.Main) {
                listener.onDownloadSuccess(pdfFile)
            }
        }
    }

    private fun makeNetworkRequest(downloadUrl: String): Response {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()

        val requestBuilder = Request.Builder().url(downloadUrl)
        headers.headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        return client.newCall(requestBuilder.build()).execute()
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
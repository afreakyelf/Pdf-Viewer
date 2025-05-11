package com.rajat.pdfviewer.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object FileUtils {
    private const val TAG = "PdfValidator"

    @Throws(IOException::class)
    suspend fun fileFromAsset(context: Context, assetName: String): File = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, assetName)
        if (assetName.contains("/")) {
            outFile.parentFile?.mkdirs()
        }
        copy(context.assets.open(assetName), outFile)
        outFile
    }

    @Throws(IOException::class)
    fun copy(inputStream: InputStream, output: File) {
        inputStream.use { input ->
            FileOutputStream(output).use { outputStream ->
                val buffer = ByteArray(1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
            }
        }
    }

    suspend fun uriToFile(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Failed to open URI: $uri")
        val tempFile = File.createTempFile("pdf_temp", ".pdf", context.cacheDir)
        inputStream.use { it.copyTo(tempFile.outputStream()) }
        tempFile
    }

    suspend fun createPdfDocumentUri(contentResolver: ContentResolver, fileName: String): Uri =
        withContext(Dispatchers.IO) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
            }

            contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                ?: throw IOException("Failed to create new MediaStore record.")
        }


    fun getCachedFileName(url: String): String {
        return CacheHelper.getCacheKey(url) + ".pdf"
    }

    fun clearPdfCache(context: Context, exceptFileName: String? = null) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles { _, name -> name.endsWith(".pdf") && name != exceptFileName }
            ?.forEach { it.delete() }
    }

    fun writeFile(inputStream: InputStream, file: File, totalLength: Long, onProgress: (Long) -> Unit) {
        FileOutputStream(file).use { outputStream ->
            val data = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int
            while (inputStream.read(data).also { bytesRead = it } != -1) {
                outputStream.write(data, 0, bytesRead)
                totalBytesRead += bytesRead
                try {
                    onProgress(totalBytesRead)
                } catch (e: Exception) {
                    Log.w(TAG, "Progress callback failed: ${e.message}", e)
                }
            }
            outputStream.flush()
        }
    }

    suspend fun isValidPdf(file: File?): Boolean = withContext(Dispatchers.IO) {
        if (file == null || !file.exists() || file.length() < 4) {
            Log.e(TAG, "Validation failed: File is null, does not exist, or is too small.")
            return@withContext false
        }

        return@withContext try {
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(1024)
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    Log.e(TAG, "Validation failed: Unable to read file content.")
                    return@withContext false
                }

                val pdfContent = String(buffer, Charsets.US_ASCII)
                val pdfIndex = pdfContent.indexOf("%PDF")
                if (pdfIndex == -1) {
                    Log.e(TAG, "Validation failed: `%PDF` signature not found in first 1024 bytes.")
                    return@withContext false
                }

                Log.d(TAG, "PDF signature found at byte offset: $pdfIndex")

                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount <= 0) {
                            Log.e(TAG, "Validation failed: PDF has no pages.")
                            return@withContext false
                        }
                        Log.d(TAG, "Validation successful: PDF is valid with ${renderer.pageCount} pages.")
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed: ${e.message}", e)
            false
        }
    }

    fun cachedFileNameWithFormat(name: Any, format: String = ".jpg") = "$name$format"
}
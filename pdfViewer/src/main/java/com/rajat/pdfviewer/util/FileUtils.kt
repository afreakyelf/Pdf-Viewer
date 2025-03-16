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
import java.io.*

object FileUtils {
    private const val TAG = "PdfValidator"

    @Throws(IOException::class)
    fun fileFromAsset(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        if (assetName.contains("/")) {
            outFile.parentFile?.mkdirs()
        }
        copy(context.assets.open(assetName), outFile)
        return outFile
    }

    @Throws(IOException::class)
    fun copy(inputStream: InputStream, output: File?) {
        val outputStream = FileOutputStream(output)
        try {
            var read = 0
            val bytes = ByteArray(1024)
            while (inputStream.read(bytes).also { read = it } != -1) {
                outputStream.write(bytes, 0, read)
            }
        } finally {
            try {
                inputStream.close()
            } finally {
                outputStream.close()
            }
        }
    }

    fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("pdf_temp", ".pdf", context.cacheDir)
        tempFile.outputStream().use { fileOut ->
            inputStream?.copyTo(fileOut)
        }
        return tempFile
    }

    fun createPdfDocumentUri(contentResolver: ContentResolver, fileName: String): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }
        }
        return contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            ?: throw IOException("Failed to create new MediaStore record.")
    }

    fun getCachedFileName(url: String): String {
        return url.hashCode().toString() + ".pdf"
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
                onProgress(totalBytesRead)
            }
            outputStream.flush()
        }
    }

    fun isValidPdf(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() < 4) {
            Log.e(TAG, "Validation failed: File is null, does not exist, or is too small.")
            return false
        }

        return try {
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(1024) // Read first 1024 bytes or less
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    Log.e(TAG, "Validation failed: Unable to read file content.")
                    return false
                }

                val pdfContent = String(buffer, Charsets.US_ASCII)
                val pdfIndex = pdfContent.indexOf("%PDF") // Look for `%PDF` anywhere in first 1024 bytes
                if (pdfIndex == -1) {
                    Log.e(TAG, "Validation failed: `%PDF` signature not found in first 1024 bytes.")
                    return false
                }

                Log.d(TAG, "PDF signature found at byte offset: $pdfIndex")

                // Check if PdfRenderer can open it
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount <= 0) {
                            Log.e(TAG, "Validation failed: PDF has no pages.")
                            return false
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
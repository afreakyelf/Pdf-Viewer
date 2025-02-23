package com.rajat.pdfviewer.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object FileUtils {
    @Throws(IOException::class)
    fun fileFromAsset(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, "$assetName")
        if (assetName.contains("/")) {
            outFile.parentFile.mkdirs()
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


    fun isValidPdf(file: File): Boolean {
        return try {
            FileInputStream(file).use { inputStream ->
                val signature = ByteArray(4)
                if (inputStream.read(signature) != 4) return false
                val pdfHeader = String(signature, Charsets.US_ASCII)
                pdfHeader.startsWith("%PDF")
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Error checking PDF validity: ${e.message}")
            false
        }
    }
}
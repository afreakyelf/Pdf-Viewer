package com.rajat.pdfviewer

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.rajat.pdfviewer.util.saveTo
import java.io.File

abstract class BasePdfViewerTest {

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val samplePdf = "quote.pdf"
    protected val sampleUrl = "https://css4.pub/2015/usenix/example.pdf"

    protected fun copyAssetPdfToCache(assetName: String): File {
        val file = File(context.cacheDir, assetName)
        context.assets.open(assetName).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    protected fun launchPdfFromUrl(
        url: String = sampleUrl,
        title: String = "Remote PDF",
        enableDownload: Boolean = true
    ): ActivityScenario<PdfViewerActivity> {
        val intent = PdfViewerActivity.launchPdfFromUrl(
            context = context,
            pdfUrl = url,
            pdfTitle = title,
            saveTo = saveTo.DOWNLOADS,
            enableDownload = enableDownload
        )
        return ActivityScenario.launch(intent)
    }

    protected fun launchPdfFromAssets(
        assetName: String = samplePdf,
        title: String = "PDF From Asset",
        enableZoom: Boolean = true
    ): ActivityScenario<PdfViewerActivity> {
        val file = copyAssetPdfToCache(assetName)
        val intent = PdfViewerActivity.launchPdfFromPath(
            context = context,
            path = file.absolutePath,
            pdfTitle = title,
            saveTo = saveTo.DOWNLOADS,
            fromAssets = false,
            enableZoom = enableZoom
        )
        return ActivityScenario.launch(intent)
    }
}

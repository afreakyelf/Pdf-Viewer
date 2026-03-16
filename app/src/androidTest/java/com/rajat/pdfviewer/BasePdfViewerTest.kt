package com.rajat.pdfviewer

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.rajat.pdfviewer.util.CacheManager
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.saveTo
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
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
        enableDownload: Boolean = true,
        cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
    ): ActivityScenario<PdfViewerActivity> {
        val intent = PdfViewerActivity.launchPdfFromUrl(
            context = context,
            pdfUrl = url,
            pdfTitle = title,
            saveTo = saveTo.DOWNLOADS,
            enableDownload = enableDownload,
            cacheStrategy = cacheStrategy
        )
        return ActivityScenario.launch(intent)
    }

    protected fun launchPdfFromAssets(
        assetName: String = samplePdf,
        title: String = "PDF From Asset",
        enableZoom: Boolean = true,
        cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
    ): ActivityScenario<PdfViewerActivity> {
        val file = copyAssetPdfToCache(assetName)
        return launchPdfFromFile(
            file = file,
            title = title,
            enableZoom = enableZoom,
            cacheStrategy = cacheStrategy
        )
    }

    protected fun launchPdfFromFile(
        file: File,
        title: String = "PDF From File",
        enableZoom: Boolean = true,
        cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
    ): ActivityScenario<PdfViewerActivity> {
        val intent = PdfViewerActivity.launchPdfFromPath(
            context = context,
            path = file.absolutePath,
            pdfTitle = title,
            saveTo = saveTo.DOWNLOADS,
            fromAssets = false,
            enableZoom = enableZoom,
            cacheStrategy = cacheStrategy
        )
        return ActivityScenario.launch(intent)
    }

    protected fun clearPdfCacheRoot() {
        File(context.cacheDir, CacheManager.CACHE_PATH).deleteRecursively()
    }

    // Hermetic remote-cache tests use a local HTTP server so they don't depend on public hosts.
    protected fun startPdfServer(assetName: String = samplePdf): MockWebServer {
        val pdfBytes = context.assets.open(assetName).use { it.readBytes() }
        return MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/pdf")
                        .setBody(Buffer().write(pdfBytes))
                }
            }
            start()
        }
    }

    protected fun waitForTestCondition(
        timeoutMs: Long = 10_000,
        intervalMs: Long = 100,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            Thread.sleep(intervalMs)
        }
        check(condition()) { "Condition not met within ${timeoutMs}ms" }
    }
}

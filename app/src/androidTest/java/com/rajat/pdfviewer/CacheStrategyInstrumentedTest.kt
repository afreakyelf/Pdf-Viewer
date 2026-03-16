package com.rajat.pdfviewer

import androidx.test.core.app.ActivityScenario
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rajat.pdfviewer.util.CacheHelper
import com.rajat.pdfviewer.util.CacheManager
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.sample.pdfviewer.EmbeddedUrlPdfTestActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@LargeTest
class CacheStrategyInstrumentedTest : BasePdfViewerTest() {

    @Before
    fun setUp() {
        clearPdfCacheRoot()
    }

    @After
    fun tearDown() {
        clearPdfCacheRoot()
    }

    @Test
    fun local_pdf_maximize_performance_survives_revisit_and_writes_disk_cache() {
        val localFile = copyAssetPdfToCache(samplePdf)
        val cacheDir = localDocumentCacheDir(localFile)

        launchPdfFromFile(
            file = localFile,
            title = "Local MAX",
            cacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
        ).use { scenario ->
            assertLocalRenderFlow(scenario)
            waitForTestCondition(timeoutMs = 10_000) { cacheDir.listJpgFiles().isNotEmpty() }
        }
    }

    @Test
    fun local_pdf_minimize_cache_survives_revisit_without_disk_bitmap_cache() {
        val localFile = copyAssetPdfToCache(samplePdf)
        val cacheDir = localDocumentCacheDir(localFile)

        launchPdfFromFile(
            file = localFile,
            title = "Local MIN",
            cacheStrategy = CacheStrategy.MINIMIZE_CACHE
        ).use { scenario ->
            assertLocalRenderFlow(scenario)
            assertTrue("Current local document folder should exist", cacheDir.exists())
            assertTrue("MINIMIZE_CACHE should avoid disk page bitmap cache", cacheDir.listJpgFiles().isEmpty())
        }
    }

    @Test
    fun local_pdf_disable_cache_survives_revisit_without_disk_bitmap_cache() {
        val localFile = copyAssetPdfToCache(samplePdf)
        val cacheDir = localDocumentCacheDir(localFile)

        launchPdfFromFile(
            file = localFile,
            title = "Local DISABLE",
            cacheStrategy = CacheStrategy.DISABLE_CACHE
        ).use { scenario ->
            assertLocalRenderFlow(scenario)
            assertTrue("DISABLE_CACHE should avoid disk page bitmap cache", cacheDir.listJpgFiles().isEmpty())
        }
    }

    @Test
    fun remote_pdf_is_reused_when_strategy_allows_it() {
        startPdfServer().use { server ->
            val url = server.url("/remote.pdf").toString()
            assertRemoteReuse(CacheStrategy.MAXIMIZE_PERFORMANCE, url, server)
        }
        clearPdfCacheRoot()
        startPdfServer().use { server ->
            val url = server.url("/remote.pdf").toString()
            assertRemoteReuse(CacheStrategy.MINIMIZE_CACHE, url, server)
        }
    }

    @Test
    fun remote_pdf_is_not_reused_when_cache_is_disabled() {
        startPdfServer().use { server ->
            val url = server.url("/remote.pdf").toString()
            val firstPath = openRemoteAndGetDownloadedPath(CacheStrategy.DISABLE_CACHE, url)
            waitForTestCondition { !File(firstPath).exists() }
            assertEquals(1, server.requestCount)

            val secondPath = openRemoteAndGetDownloadedPath(CacheStrategy.DISABLE_CACHE, url)
            waitForTestCondition { !File(secondPath).exists() }

            assertEquals(
                "DISABLE_CACHE should redownload on the second open because it does not reuse persistent remote cache",
                2,
                server.requestCount
            )
            assertNotEquals(
                "DISABLE_CACHE should isolate each remote session so transient cleanup cannot delete persistent cache for the same URL",
                firstPath,
                secondPath
            )
        }
    }

    @Test
    fun embedded_disable_cache_cleans_up_remote_file_on_activity_destroy() {
        var downloadedPath: String? = null
        startPdfServer().use { server ->
            val url = server.url("/remote.pdf").toString()
            ActivityScenario.launch<EmbeddedUrlPdfTestActivity>(
                EmbeddedUrlPdfTestActivity.intent(
                    context = context,
                    url = url,
                    cacheStrategy = CacheStrategy.DISABLE_CACHE
                )
            ).use { scenario ->
                waitForTestCondition(timeoutMs = 20_000) {
                    scenario.onActivity { activity ->
                        downloadedPath = activity.downloadedPath
                    }
                    downloadedPath != null
                }
                scenario.onActivity { activity ->
                    assertTrue("Expected embedded DISABLE view to render content", activity.renderSucceeded)
                }
            }

            waitForTestCondition {
                val path = requireNotNull(downloadedPath)
                !File(path).exists()
            }
        }
    }

    @Test
    fun disable_cache_session_does_not_delete_persistent_remote_cache() {
        startPdfServer().use { server ->
            val url = server.url("/remote.pdf").toString()
            val persistentPath = openRemoteAndGetDownloadedPath(CacheStrategy.MAXIMIZE_PERFORMANCE, url)
            val persistentFile = File(persistentPath)
            assertTrue("Expected persistent cache file to exist after MAX session closes", persistentFile.exists())
            assertEquals(1, server.requestCount)

            var transientPath: String? = null
            ActivityScenario.launch<EmbeddedUrlPdfTestActivity>(
                EmbeddedUrlPdfTestActivity.intent(
                    context = context,
                    url = url,
                    cacheStrategy = CacheStrategy.DISABLE_CACHE
                )
            ).use { scenario ->
                waitForTestCondition(timeoutMs = 20_000) {
                    scenario.onActivity { activity ->
                        transientPath = activity.downloadedPath
                    }
                    transientPath != null
                }
            }

            waitForTestCondition {
                val path = requireNotNull(transientPath)
                !File(path).exists()
            }

            assertEquals(
                "The DISABLE session should hit the local test server without invalidating the earlier persistent cache",
                2,
                server.requestCount
            )
            assertNotEquals(
                "Transient DISABLE session should not share the same remote file path as persistent strategies",
                persistentPath,
                transientPath
            )
            assertTrue(
                "Persistent cache should survive an unrelated DISABLE session for the same URL",
                persistentFile.exists()
            )
        }
    }

    @Test
    fun reopening_under_lower_persistence_purges_stale_disk_bitmaps() {
        val localFile = copyAssetPdfToCache(samplePdf)
        val cacheDir = localDocumentCacheDir(localFile)

        launchPdfFromFile(
            file = localFile,
            title = "Downgrade MAX",
            cacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
        ).use { scenario ->
            assertLocalRenderFlow(scenario)
            waitForTestCondition(timeoutMs = 10_000) { cacheDir.listJpgFiles().isNotEmpty() }
        }

        launchPdfFromFile(
            file = localFile,
            title = "Downgrade MIN",
            cacheStrategy = CacheStrategy.MINIMIZE_CACHE
        ).use { scenario ->
            assertLocalRenderFlow(scenario)
            waitForTestCondition(timeoutMs = 10_000) { cacheDir.listJpgFiles().isEmpty() }
        }

        launchPdfFromFile(
            file = localFile,
            title = "Downgrade MAX Again",
            cacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
        ).use { scenario ->
            assertLocalRenderFlow(scenario)
            waitForTestCondition(timeoutMs = 10_000) { cacheDir.listJpgFiles().isNotEmpty() }
        }

        launchPdfFromFile(
            file = localFile,
            title = "Downgrade DISABLE",
            cacheStrategy = CacheStrategy.DISABLE_CACHE
        ).use { scenario ->
            assertLocalRenderFlow(scenario)
            waitForTestCondition(timeoutMs = 10_000) { cacheDir.listJpgFiles().isEmpty() }
        }
    }

    private fun assertRemoteReuse(strategy: CacheStrategy, url: String, server: okhttp3.mockwebserver.MockWebServer) {
        val firstPath = openRemoteAndGetDownloadedPath(strategy, url)
        val firstFile = File(firstPath)
        assertTrue("Expected downloaded PDF to persist after closing for $strategy", firstFile.exists())
        val firstModified = firstFile.lastModified()
        assertEquals(1, server.requestCount)

        waitForTestCondition(timeoutMs = 2_000) { firstFile.exists() }

        val secondPath = openRemoteAndGetDownloadedPath(strategy, url)
        val secondFile = File(secondPath)

        assertEquals("Expected same cached file path for $strategy", firstPath, secondPath)
        assertTrue("Expected cached PDF to exist for $strategy", secondFile.exists())
        assertEquals(
            "Expected existing cached PDF to be reused instead of redownloaded for $strategy",
            firstModified,
            secondFile.lastModified()
        )
        assertEquals(
            "Expected no second network request when remote reuse is enabled for $strategy",
            1,
            server.requestCount
        )
    }

    private fun openRemoteAndGetDownloadedPath(strategy: CacheStrategy, url: String): String {
        var downloadedPath: String? = null
        launchPdfFromUrl(
            url = url,
            title = "Remote $strategy",
            cacheStrategy = strategy
        ).use { scenario ->
            waitForTestCondition(timeoutMs = 20_000) {
                scenario.onActivity {
                    downloadedPath = it.downloadedFilePath
                }
                downloadedPath != null
            }
            scenario.onActivity { activity ->
                val pdfView = activity.findViewById<PdfRendererView>(R.id.pdfView)
                assertTrue("Expected rendered content for $strategy", pdfView.totalPageCount > 0)
            }
        }
        return requireNotNull(downloadedPath)
    }

    private fun assertLocalRenderFlow(scenario: androidx.test.core.app.ActivityScenario<PdfViewerActivity>) {
        waitForTestCondition(timeoutMs = 10_000) {
            var rendered = false
            scenario.onActivity { activity ->
                val pdfView = activity.findViewById<PdfRendererView>(R.id.pdfView)
                rendered = pdfView.totalPageCount > 0 && pdfView.recyclerView.adapter != null && pdfView.recyclerView.childCount > 0
            }
            rendered
        }

        var lastPage = 0
        scenario.onActivity { activity ->
            val pdfView = activity.findViewById<PdfRendererView>(R.id.pdfView)
            assertTrue("Expected asset PDF to have multiple pages", pdfView.totalPageCount > 1)
            lastPage = pdfView.totalPageCount - 1
        }

        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<PdfRendererView>(R.id.pdfView).recyclerView
            recyclerView.scrollToPosition(lastPage)
        }
        waitForVisiblePage(scenario, lastPage)

        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<PdfRendererView>(R.id.pdfView).recyclerView
            recyclerView.scrollToPosition(0)
        }
        waitForVisiblePage(scenario, 0)

        scenario.onActivity { activity ->
            val pdfView = activity.findViewById<PdfRendererView>(R.id.pdfView)
            assertNotNull("RecyclerView adapter should stay attached", pdfView.recyclerView.adapter)
            assertTrue("Expected rendered content to remain attached", pdfView.recyclerView.childCount > 0)
        }
    }

    private fun waitForVisiblePage(
        scenario: androidx.test.core.app.ActivityScenario<PdfViewerActivity>,
        expectedPage: Int
    ) {
        waitForTestCondition(timeoutMs = 10_000) {
            var firstVisible = RecyclerView.NO_POSITION
            var lastVisible = RecyclerView.NO_POSITION
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<PdfRendererView>(R.id.pdfView).recyclerView
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                lastVisible = layoutManager?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
            }
            firstVisible != RecyclerView.NO_POSITION &&
                lastVisible != RecyclerView.NO_POSITION &&
                expectedPage in firstVisible..lastVisible
        }
    }

    private fun localDocumentCacheDir(file: File): File {
        return File(
            context.cacheDir,
            "${CacheManager.CACHE_PATH}/${CacheHelper.getCacheKey(file.absolutePath)}"
        )
    }

    private fun File.listJpgFiles(): List<File> {
        if (!exists()) return emptyList()
        return walkTopDown()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .toList()
    }
}

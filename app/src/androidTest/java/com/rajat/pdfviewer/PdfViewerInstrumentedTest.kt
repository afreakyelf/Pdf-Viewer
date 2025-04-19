package com.rajat.pdfviewer

import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rajat.pdfviewer.util.saveTo
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PdfViewerInstrumentedTest : BasePdfViewerTest() {

    @Test
    fun test_pdf_renders_successfully_from_assets() {
        launchPdfFromAssets().use {
            onView(withId(R.id.recyclerView))
                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(2))
            onView(withId(R.id.pageNumber)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_jump_to_page_from_assets() {
        launchPdfFromAssets(title = "Jump Test").use { scenario ->
            scenario.onActivity {
                it.findViewById<PdfRendererView>(R.id.pdfView).jumpToPage(3)
            }

            onView(withId(R.id.recyclerView))
                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(3))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_download_button_is_visible_when_loading_from_url() {
        launchPdfFromUrl(enableDownload = true).use {
            onView(withId(R.id.download)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_error_dialog_shown_for_invalid_path() {
        val intent = PdfViewerActivity.launchPdfFromPath(
            context, "/invalid/path/file.pdf", "Invalid PDF", saveTo.DOWNLOADS
        )

        ActivityScenario.launch<PdfViewerActivity>(intent).use {
            onView(withId(android.R.id.message)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_pdf_renders_successfully_from_url() {
        launchPdfFromUrl().use {
            onView(withId(R.id.recyclerView))
                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(1))
            onView(withId(R.id.pageNumber)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_download_button_is_not_visible_when_disabled() {
        launchPdfFromUrl(enableDownload = false).use { scenario ->
            scenario.onActivity {
                assertTrue("Download button should be hidden", !it.isDownloadButtonVisible())
            }
        }
    }

    @Test
    fun test_pdf_does_not_crash_on_orientation_change() {
        launchPdfFromAssets().use {
            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
            it.recreate()
            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_toolbar_title_matches_intent_value() {
        val customTitle = "Testing Title"
        val intent = PdfViewerActivity.launchPdfFromUrl(
            context = context,
            pdfUrl = sampleUrl,
            pdfTitle = customTitle,
            saveTo = saveTo.DOWNLOADS
        )

        ActivityScenario.launch<PdfViewerActivity>(intent).use {
            onView(withId(R.id.toolbar_title)).check(matches(withText(customTitle)))
        }
    }

    @Test
    fun test_zoom_is_disabled_when_flag_false() {
        launchPdfFromAssets(enableZoom = false).use { scenario ->
            scenario.onActivity {
                val zoomEnabled = it.findViewById<PdfRendererView>(R.id.pdfView).getZoomEnabled()
                assertTrue("Zoom should be disabled", !zoomEnabled)
            }
        }
    }

    @Test
    fun test_total_page_count_is_correct() {
        launchPdfFromAssets().use { scenario ->
            scenario.onActivity {
                val totalPages = it.findViewById<PdfRendererView>(R.id.pdfView).totalPageCount
                assertTrue("Expected at least 1 page", totalPages >= 1)
            }
        }
    }

    @Test
    fun test_file_loaded_triggers_onPdfLoadSuccess() {
        launchPdfFromAssets().use { scenario ->
            scenario.onActivity { activity ->
                val pdfView = activity.findViewById<PdfRendererView>(R.id.pdfView)
                runBlocking {
                    Thread.sleep(5000)
                    val bitmaps = pdfView.getLoadedBitmaps()
                    assertTrue("Expected PDF to load at least 1 page", bitmaps.isNotEmpty())
                }
            }
        }
    }


    @Test
    fun test_toolbar_color_matches_theme() {
        launchPdfFromAssets().use { scenario ->
            scenario.onActivity {
                val toolbar = it.findViewById<View>(R.id.my_toolbar)
                val expectedColor = ContextCompat.getColor(context, R.color.colorPrimary)
                val actualColor = (toolbar.background as? ColorDrawable)?.color
                assertEquals("Toolbar color mismatch", expectedColor, actualColor)
            }
        }
    }

    @Test
    fun test_error_retry_button_is_shown_on_download_failure() {
        launchPdfFromUrl(url = "https://invalid.pdf.url/404.pdf",
            title = "Fail PDF",).use {
            Thread.sleep(5000)
            onView(withText(R.string.pdf_viewer_retry)).check(matches(isDisplayed()))
        }
    }

//    @Test
//    fun test_pdf_renders_successfully_from_content_uri() {
//        val testUri = TestFileUtils.getContentUriFromAsset("sample.pdf", context)
//        val intent = PdfViewerActivity.launchPdfFromPath(
//            context = context,
//            path = testUri.toString(),
//            pdfTitle = "Content URI",
//            saveTo = saveTo.DOWNLOADS,
//            fromAssets = false
//        )
//
//        ActivityScenario.launch<PdfViewerActivity>(intent).use {
//            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
//        }
//    }

    @Test
    fun test_jump_to_invalid_page_does_not_crash() {
        launchPdfFromAssets().use { scenario ->
            scenario.onActivity {
                val view = it.findViewById<PdfRendererView>(R.id.pdfView)
                val totalPages = view.totalPageCount
                view.jumpToPage(totalPages + 10) // should not crash
            }

            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test_zoom_disabled_blocks_zooming() {
        launchPdfFromAssets(enableZoom = false).use { scenario ->
            scenario.onActivity {
                val zoomEnabled = it.findViewById<PdfRendererView>(R.id.pdfView).getZoomEnabled()
                assertTrue("Zoom should be disabled", !zoomEnabled)
            }
        }
    }

    @Test
    fun test_retry_after_failed_download_restores_viewer() {
        val badIntent = PdfViewerActivity.launchPdfFromUrl(
            context = context,
            pdfUrl = "https://invalid.example.com/fail.pdf",
            pdfTitle = "Retry Test",
            saveTo = saveTo.DOWNLOADS
        )

        ActivityScenario.launch<PdfViewerActivity>(badIntent).use {
            Thread.sleep(5000)

            // Simulate download failure
            onView(withText(R.string.pdf_viewer_retry)).check(matches(isDisplayed()))

            // Click retry (mock network success if needed)
            onView(withText(R.string.pdf_viewer_retry)).perform(click())

            Thread.sleep(1000)

            onView(withId(R.id.progressBar)).check(matches(isDisplayed()))
        }
    }
}

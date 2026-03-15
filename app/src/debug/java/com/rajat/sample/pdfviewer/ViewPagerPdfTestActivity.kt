package com.rajat.sample.pdfviewer

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.rajat.pdfviewer.PdfRendererView
import java.io.File

class ViewPagerPdfTestActivity : AppCompatActivity() {

    lateinit var viewPager: ViewPager
        private set

    lateinit var pdfView: PdfRendererView
        private set

    @Volatile
    var renderSucceeded = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pdfView = PdfRendererView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            statusListener = object : PdfRendererView.StatusCallBack {
                override fun onPdfRenderSuccess() {
                    renderSucceeded = true
                }
            }
            initWithFile(copyAssetPdfToCache("quote.pdf"))
        }

        val pages = listOf(
            pdfView,
            createLabelPage("Second page"),
            createLabelPage("Third page")
        )

        viewPager = ViewPager(this).apply {
            id = View.generateViewId()
            adapter = object : PagerAdapter() {
                override fun getCount(): Int = pages.size

                override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`

                override fun instantiateItem(container: ViewGroup, position: Int): Any {
                    val view = pages[position]
                    (view.parent as? ViewGroup)?.removeView(view)
                    container.addView(view)
                    return view
                }

                override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                    container.removeView(`object` as View)
                }
            }
        }

        setContentView(viewPager)
    }

    fun hasAttachedPdfContent(): Boolean {
        if (!this::pdfView.isInitialized || !::viewPager.isInitialized) return false
        val recyclerView = runCatching { pdfView.recyclerView }.getOrNull() ?: return false
        return pdfView.childCount > 0 && recyclerView.adapter != null
    }

    private fun createLabelPage(label: String): TextView =
        TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            text = label
        }

    private fun copyAssetPdfToCache(assetName: String): File {
        val file = File(cacheDir, assetName)
        assets.open(assetName).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}

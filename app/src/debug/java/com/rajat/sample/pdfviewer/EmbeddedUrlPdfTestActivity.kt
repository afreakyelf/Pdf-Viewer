package com.rajat.sample.pdfviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.HeaderData
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.util.CacheStrategy

// Debug-only host activity for instrumentation tests that need to exercise
// PdfRendererView.initWithUrl(...) without PdfViewerActivity's manual teardown.
class EmbeddedUrlPdfTestActivity : AppCompatActivity() {

    lateinit var pdfView: PdfRendererView
        private set

    @Volatile
    var downloadedPath: String? = null
        private set

    @Volatile
    var renderSucceeded = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
        val strategyOrdinal = intent.getIntExtra(
            EXTRA_CACHE_STRATEGY,
            CacheStrategy.DISABLE_CACHE.ordinal
        )
        val cacheStrategy = CacheStrategy.entries.getOrElse(strategyOrdinal) {
            CacheStrategy.DISABLE_CACHE
        }

        pdfView = PdfRendererView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            statusListener = object : PdfRendererView.StatusCallBack {
                override fun onPdfLoadSuccess(absolutePath: String) {
                    downloadedPath = absolutePath
                }

                override fun onPdfRenderSuccess() {
                    renderSucceeded = true
                }
            }
            initWithUrl(
                url = url,
                headers = HeaderData(),
                lifecycleCoroutineScope = lifecycleScope,
                lifecycle = lifecycle,
                cacheStrategy = cacheStrategy
            )
        }

        setContentView(pdfView)
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_CACHE_STRATEGY = "extra_cache_strategy"
        private const val DEFAULT_URL = "https://css4.pub/2015/usenix/example.pdf"

        fun intent(
            context: Context,
            url: String = DEFAULT_URL,
            cacheStrategy: CacheStrategy = CacheStrategy.DISABLE_CACHE
        ): Intent {
            return Intent(context, EmbeddedUrlPdfTestActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_CACHE_STRATEGY, cacheStrategy.ordinal)
            }
        }
    }
}

package com.rajat.sample.pdfviewer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.PdfViewerActivity
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.ToolbarTitleBehavior
import com.rajat.pdfviewer.util.saveTo
import com.rajat.sample.pdfviewer.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // View Binding
    private lateinit var binding: ActivityMainBinding

    // Sample PDF URLs
    private val largePdf = "https://css4.pub/2015/usenix/example.pdf"
    private val largePdf1 = "https://research.nhm.org/pdfs/10840/10840.pdf"
    private val localPdf = "http://192.168.0.72:8001/pw.pdf"
    private val newsletterPdf = "https://css4.pub/2017/newsletter/drylab.pdf"
    private val textbookPdf = "https://css4.pub/2015/textbook/somatosensory.pdf"

    private val pdfList = listOf(largePdf, largePdf1, newsletterPdf, textbookPdf)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Inflate layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set Default ActionBar title
        supportActionBar?.title = "PDF Viewer"

        // Setup Click Listeners
        setupListeners()
    }

    /**
     * Sets up click listeners for the UI buttons.
     */
    private fun setupListeners() {
        binding.onlinePdf.setOnClickListener {
            setupPdfStatusListener()
            launchPdfFromUrl(largePdf1)
        }

        binding.pickPdfButton.setOnClickListener {
            launchFilePicker()
        }

        binding.fromAssets.setOnClickListener {
            launchPdfFromAssets("password_protected.pdf")
        }

        binding.showInView.setOnClickListener {
            setupPdfStatusListener()
            binding.pdfView.initWithUrl(
                url = textbookPdf,
                lifecycleCoroutineScope = lifecycleScope,
                lifecycle = lifecycle,
                cacheStrategy = CacheStrategy.MINIMIZE_CACHE
            )
            binding.pdfView.jumpToPage(3)
        }

        binding.openInCompose.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
    }

    /**
     * Sets up the PDF status listener for monitoring PDF rendering progress.
     */
    private fun setupPdfStatusListener() {
        binding.pdfView.statusListener = object : PdfRendererView.StatusCallBack {
            override fun onPdfLoadStart() {
                Log.i("PDF Status", "Loading started")
            }

            override fun onPdfLoadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long?) {
                Log.i("PDF Status", "Download progress: $progress%")
            }

            override fun onPdfLoadSuccess(absolutePath: String) {
                Log.i("PDF Status", "Load successful: $absolutePath")
                binding.pdfView.post {
                    binding.pdfView.recyclerView.scrollToPosition(1)
                }
            }

            override fun onError(error: Throwable) {
                Log.e("PDF Status", "Error loading PDF: ${error.message}")
            }

            override fun onPageChanged(currentPage: Int, totalPage: Int) {
                Log.i("PDF Status", "Page changed: $currentPage / $totalPage")
            }
        }
    }

    /**
     * Launches a PDF file from a URL.
     */
    private fun launchPdfFromUrl(url: String) {
        Toast.makeText(this@MainActivity, "Opening PDF: $url", Toast.LENGTH_SHORT).show()
        startActivity(
            PdfViewerActivity.launchPdfFromUrl(
                context = this,
                pdfUrl = url,
                pdfTitle = "PDF Title",
                saveTo = saveTo.DOWNLOADS,
                enableDownload = true,
                toolbarTitleBehavior = ToolbarTitleBehavior.SINGLE_LINE_SCROLLABLE,
                cacheStrategy = CacheStrategy.MINIMIZE_CACHE
            )
        )
    }

    /**
     * Launches a file picker for selecting PDFs.
     */
    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        filePicker.launch(intent)
    }

    /**
     * Handles the result of the file picker.
     */
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                launchPdfFromUri(uri.toString())
            }
        }
    }

    /**
     * Launches a PDF file from a local URI.
     */
    private fun launchPdfFromUri(uri: String) {
        startActivity(
            PdfViewerActivity.launchPdfFromPath(
                context = this,
                path = uri,
                pdfTitle = "Title",
                saveTo = saveTo.ASK_EVERYTIME,
                fromAssets = false
            )
        )
    }

    /**
     * Launches a PDF file from assets.
     */
    private fun launchPdfFromAssets(uri: String) {
        startActivity(
            PdfViewerActivity.launchPdfFromPath(
                context = this,
                path = uri,
                pdfTitle = "Title",
                saveTo = saveTo.ASK_EVERYTIME,
                fromAssets = true
            )
        )
    }
}
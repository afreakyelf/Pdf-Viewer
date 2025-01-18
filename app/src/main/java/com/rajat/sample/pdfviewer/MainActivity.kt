package com.rajat.sample.pdfviewer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.PdfViewerActivity
import com.rajat.pdfviewer.util.saveTo
import com.rajat.sample.pdfviewer.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var download_file_url = "https://css4.pub/2015/usenix/example.pdf"
    private var large_pdf = "https://research.nhm.org/pdfs/10840/10840.pdf"
    private var download_file_url1 = "https://css4.pub/2017/newsletter/drylab.pdf"
    private var download_file_url2 = "https://css4.pub/2015/textbook/somatosensory.pdf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.onlinePdf.setOnClickListener {
            binding.pdfView.statusListener = object : PdfRendererView.StatusCallBack {
                override fun onPdfLoadStart() {
                    Log.i("statusCallBack","onPdfLoadStart")
                }
                override fun onPdfLoadProgress(
                    progress: Int,
                    downloadedBytes: Long,
                    totalBytes: Long?
                ) {
                    //Download is in progress
                }

                override fun onPdfLoadSuccess(absolutePath: String) {
                    Log.i("statusCallBack","onPdfLoadSuccess")
//                    binding.pdfView.recyclerView.scrollToPosition(1)
                    binding.pdfView.post {
                        binding.pdfView.recyclerView.scrollToPosition(
                            1
                        )
                    }

                }

                override fun onError(error: Throwable) {
                    Log.i("statusCallBack","onError")
                }

                override fun onPageChanged(currentPage: Int, totalPage: Int) {
                    //Page change. Not require
                }
            }

            launchPdfFromUrl(large_pdf)
        }

        binding.pickPdfButton.setOnClickListener {
            launchFilePicker()
        }

        binding.fromAssets.setOnClickListener {
            launchPdfFromAssets("quote.pdf")
        }

        binding.showInView.setOnClickListener {
            binding.pdfView.statusListener = object : PdfRendererView.StatusCallBack {
                override fun onPdfLoadStart() {
                    Log.i("statusCallBack","onPdfLoadStart")
                }
                override fun onPdfLoadProgress(
                    progress: Int,
                    downloadedBytes: Long,
                    totalBytes: Long?
                ) {
                    //Download is in progress
                }

                override fun onPdfLoadSuccess(absolutePath: String) {
                    Log.i("statusCallBack","onPdfLoadSuccess")
                }

                override fun onError(error: Throwable) {
                    Log.i("statusCallBack","onError")
                }

                override fun onPageChanged(currentPage: Int, totalPage: Int) {
                    //Page change. Not require
                }
            }
            binding.pdfView.initWithUrl(
                url = download_file_url2,
                lifecycleCoroutineScope = lifecycleScope,
                lifecycle = lifecycle
            )
            binding.pdfView.jumpToPage(3)
            //second version of initWithUrl version, using functional programming
            binding.pdfView.proWithUrl(url = download_file_url2,
                lifecycleCoroutineScope = lifecycleScope,
                lifecycle = lifecycle,
                onDownloadProgress = {_,_,_->
                    //doing any thing
                },
                onDownloadPdfSuccess = {_->
                    //doing any thing
                },
                onDownloadPdfFailed = {e->
                    //doing any thing
                },
                onDownloadStarted = {
                    //doing any thing
                },
                onPageChanged = {_,_->}
                )
        }

        binding.openInCompose.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
    }

    private fun launchPdfFromUrl(url: String) {

//        Headers can be passed like this, be default header will be empty.
//        val url = "http://10.0.2.2:5000/download_pdf" // Use 10.0.2.2 for Android emulator to access localhost
//        val headers = mapOf("Authorization" to "123456789")

        startActivity(
            PdfViewerActivity.launchPdfFromUrl(
                context = this,
                pdfUrl = url,
                pdfTitle = "PDF Title",
                saveTo = saveTo.ASK_EVERYTIME,
                enableDownload = true
            )
        )
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedFileUri = result.data?.data
            selectedFileUri?.let { uri ->
                launchPdfFromUri(uri.toString())
            }
        }
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        filePicker.launch(intent)
    }

    private fun launchPdfFromUri(uri: String) {
        startActivity(
            PdfViewerActivity.launchPdfFromPath(
                context = this, path = uri,
                pdfTitle = "Title", saveTo = saveTo.ASK_EVERYTIME,  fromAssets = false)
        )
    }

    private fun launchPdfFromAssets(uri: String) {
        startActivity(
            PdfViewerActivity.launchPdfFromPath(
                context = this, path = uri,
                pdfTitle = "Title", saveTo = saveTo.ASK_EVERYTIME,  fromAssets = true)
        )
    }

}

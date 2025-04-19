package com.rajat.pdfviewer

import android.Manifest.permission
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.Window
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.databinding.ActivityPdfViewerBinding
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.EdgeToEdgeHelper
import com.rajat.pdfviewer.util.FileUtils.createPdfDocumentUri
import com.rajat.pdfviewer.util.FileUtils.fileFromAsset
import com.rajat.pdfviewer.util.FileUtils.uriToFile
import com.rajat.pdfviewer.util.NetworkUtil.checkInternetConnection
import com.rajat.pdfviewer.util.ThemeValidator
import com.rajat.pdfviewer.util.ToolbarStyle
import com.rajat.pdfviewer.util.ToolbarTitleBehavior
import com.rajat.pdfviewer.util.ViewerStrings
import com.rajat.pdfviewer.util.ViewerStrings.Companion.getMessageForError
import com.rajat.pdfviewer.util.ViewerStyle
import com.rajat.pdfviewer.util.saveTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Created by Rajat on 11,July,2020
 */

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var file_not_downloaded_yet: String
    private lateinit var file_saved_to_downloads: String
    private lateinit var file_saved_successfully: String
    private lateinit var error_no_internet_connection: String
    private lateinit var permission_required: String
    private lateinit var permission_required_title: String
    private lateinit var error_pdf_corrupted: String
    private lateinit var pdf_viewer_retry: String
    private lateinit var pdf_viewer_grant: String
    private lateinit var pdf_viewer_cancel: String
    private lateinit var pdf_viewer_error: String
    private var menuItem: MenuItem? = null
    private var fileUrl: String? = null
    private lateinit var headers: HeaderData
    private lateinit var binding: ActivityPdfViewerBinding
    private val viewModel: PdfViewerViewModel by viewModels()
    var downloadedFilePath: String? = null
    private var isDownloadButtonEnabled = false
    private lateinit var cacheStrategy: CacheStrategy

    companion object {
        const val FILE_URL = "pdf_file_url"
        const val FILE_TITLE = "pdf_file_title"
        const val ENABLE_FILE_DOWNLOAD = "enable_download"
        const val FROM_ASSETS = "from_assests"
        const val TITLE_BEHAVIOR = "title_behavior"
        const val ENABLE_ZOOM = "enable_zoom"
        var enableDownload = false
        var isPDFFromPath = false
        var isFromAssets = false
        var SAVE_TO_DOWNLOADS = true
        var isZoomEnabled = true
        const val CACHE_STRATEGY = "cache_strategy"

        fun launchPdfFromUrl(
            context: Context?,
            pdfUrl: String?,
            pdfTitle: String?,
            saveTo: saveTo,
            enableDownload: Boolean = true,
            enableZoom: Boolean = true,
            headers: Map<String, String> = emptyMap(),
            toolbarTitleBehavior: ToolbarTitleBehavior? = null,
            cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
        ): Intent {
            val intent = Intent(context, PdfViewerActivity::class.java)
            intent.putExtra(FILE_URL, pdfUrl)
            intent.putExtra(FILE_TITLE, pdfTitle)
            intent.putExtra(ENABLE_FILE_DOWNLOAD, enableDownload)
            intent.putExtra("headers", HeaderData(headers))
            intent.putExtra(ENABLE_ZOOM, enableZoom)
            toolbarTitleBehavior?.let {
                intent.putExtra(TITLE_BEHAVIOR, it.ordinal)
            }
            intent.putExtra(CACHE_STRATEGY, cacheStrategy.ordinal)
            isPDFFromPath = false
            SAVE_TO_DOWNLOADS = saveTo == com.rajat.pdfviewer.util.saveTo.DOWNLOADS
            return intent
        }

        fun launchPdfFromPath(
            context: Context?,
            path: String?,
            pdfTitle: String?,
            saveTo: saveTo,
            fromAssets: Boolean = false,
            enableZoom: Boolean = true,
            toolbarTitleBehavior: ToolbarTitleBehavior? = null,
            cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
        ): Intent {
            val intent = Intent(context, PdfViewerActivity::class.java)
            intent.putExtra(FILE_URL, path)
            intent.putExtra(FILE_TITLE, pdfTitle)
            intent.putExtra(ENABLE_FILE_DOWNLOAD, false)
            intent.putExtra(FROM_ASSETS, fromAssets)
            toolbarTitleBehavior?.let {
                intent.putExtra(TITLE_BEHAVIOR, it.ordinal)
            }
            intent.putExtra(ENABLE_ZOOM, enableZoom)
            intent.putExtra(CACHE_STRATEGY, cacheStrategy.ordinal)
            isPDFFromPath = true
            SAVE_TO_DOWNLOADS = saveTo == com.rajat.pdfviewer.util.saveTo.DOWNLOADS
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PdfView_SelectedTheme)
        ThemeValidator.validatePdfViewerTheme(this)
        super.onCreate(savedInstanceState)

        // Inflate layout once (previously done twice)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply edge-to-edge window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            applyEdgeToEdge(window)
        }

        // Setup Toolbar
        configureToolbar()

        // Apply theme attributes (background & progress bar styles)
        applyThemeAttributes()

        // Retrieve intent extras
        extractIntentExtras()

        // Initialize the PDF viewer
        init()
    }

    private fun configureToolbar() {
        val toolbarStyle = ToolbarStyle.from(this, intent)
        val toolbarTitle = intent.getStringExtra(FILE_TITLE) ?: "PDF"

        try {
            // Check if system ActionBar exists (theme includes windowActionBar)
            supportActionBar?.hide() // Hide it (avoids double toolbar)
        } catch (e: IllegalStateException) {
            // Do nothing — if it crashes here, we’ll fallback safely below
            Log.w("PdfViewer", "supportActionBar check failed: ${e.message}")
        }

        // Use our custom toolbar always
        binding.myToolbar.visibility = VISIBLE
        try {
            setSupportActionBar(binding.myToolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)
        } catch (e: IllegalStateException) {
            // fallback — don't set toolbar, maybe layout-only mode
            Log.e("PdfViewer", "Can't setSupportActionBar(): ${e.message}")
        }

        toolbarStyle.applyTo(binding.myToolbar, binding.toolbarTitle)
        binding.toolbarTitle.text = toolbarTitle
    }

    private fun applyEdgeToEdge(window: Window) {
        val isDarkMode = EdgeToEdgeHelper.isDarkModeEnabled(resources.configuration.uiMode)
        val toolbarColor = ToolbarStyle.from(this, intent).toolbarColor

        // Must be called from ComponentActivity
        enableEdgeToEdge(
            statusBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(toolbarColor)
            } else {
                SystemBarStyle.light(toolbarColor, toolbarColor)
            }
        )

        // apply insets via helper
        EdgeToEdgeHelper.applyInsets(window, binding.root, isDarkMode)
    }

    private fun applyThemeAttributes() {
        ViewerStyle.from(this).applyTo(binding)
    }

    private fun extractIntentExtras() {
        enableDownload = intent.getBooleanExtra(ENABLE_FILE_DOWNLOAD, false)
        isFromAssets = intent.getBooleanExtra(FROM_ASSETS, false)

        headers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("headers", HeaderData::class.java)
        } else {
            intent.getParcelableExtra("headers")
        } ?: HeaderData(emptyMap())

        isZoomEnabled = intent.getBooleanExtra(ENABLE_ZOOM, true)

        val strategyOrdinal =
            intent.getIntExtra(CACHE_STRATEGY, CacheStrategy.MAXIMIZE_PERFORMANCE.ordinal)
        cacheStrategy = CacheStrategy.entries.getOrElse(strategyOrdinal) {
            CacheStrategy.MAXIMIZE_PERFORMANCE
        }

        // Apply themed strings with fallback
        ViewerStrings.from(this).also { strings ->
            error_pdf_corrupted = strings.errorPdfCorrupted
            error_no_internet_connection = strings.errorNoInternet
            file_saved_successfully = strings.fileSavedSuccessfully
            file_saved_to_downloads = strings.fileSavedToDownloads
            file_not_downloaded_yet = strings.fileNotDownloadedYet
            permission_required = strings.permissionRequired
            permission_required_title = strings.permissionRequiredTitle
            pdf_viewer_error = strings.genericError
            pdf_viewer_retry = strings.retry
            pdf_viewer_cancel = strings.cancel
            pdf_viewer_grant = strings.grant
        }
    }

    private fun init() {
        binding.pdfView.statusListener = object : PdfRendererView.StatusCallBack {
            override fun onPdfLoadStart() {
                true.showProgressBar()
                updateDownloadButtonState(false)
            }

            override fun onPdfLoadProgress(
                progress: Int, downloadedBytes: Long, totalBytes: Long?
            ) {
                //Download is in progress
                true.showProgressBar()
            }

            override fun onPdfLoadSuccess(absolutePath: String) {
                runOnUiThread {
                    false.showProgressBar()
                    downloadedFilePath = absolutePath
                    if (menuItem == null) {
                        isDownloadButtonEnabled = true // ✅ Store state so it applies later
                    } else {
                        updateDownloadButtonState(true)
                    }
                }
            }

            override fun onError(error: Throwable) {
                runOnUiThread {
                    false.showProgressBar()
                    val strings = ViewerStrings.from(this@PdfViewerActivity)
                    val errorMessage = strings.getMessageForError(error)
                    showErrorDialog(errorMessage, isRetryable(error))
                }
            }

            override fun onPageChanged(currentPage: Int, totalPage: Int) {
                //Page change. Not require
            }
        }

        intent.extras?.getString(FILE_URL)?.let { fileUrl ->
            this.fileUrl = fileUrl
            if (isPDFFromPath) {
                lifecycleScope.launch {
                    initPdfViewerWithPath(fileUrl)
                }
            } else if (checkInternetConnection(this)) {
                loadFileFromNetwork(fileUrl)
            } else {
                Toast.makeText(this, error_no_internet_connection, Toast.LENGTH_SHORT).show()
            }
        }

    }


    private fun isRetryable(error: Throwable): Boolean {
        return error is UnknownHostException || error is SocketTimeoutException || error.message?.contains(
            "Failed to download"
        ) == true || error.message?.contains("Incomplete download") == true
    }

    private fun showErrorDialog(message: String, shouldRetry: Boolean) {
        val strings = ViewerStrings.from(this)
        val builder = AlertDialog.Builder(this)
            .setTitle(strings.errorDialogTitle)
            .setMessage(message)
        if (shouldRetry) {
            builder.setPositiveButton(pdf_viewer_retry) { _, _ -> loadFileFromNetwork(fileUrl) }
        }
        builder.setNegativeButton(pdf_viewer_cancel, null).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        menuItem = menu.findItem(R.id.download)
        menuItem?.isVisible = enableDownload

        // Apply download icon tint from theme
        val toolbarStyle = ToolbarStyle.from(this, intent)
        menuItem?.icon?.mutate()?.let {
            val wrappedIcon = DrawableCompat.wrap(it)
            DrawableCompat.setTint(wrappedIcon, toolbarStyle.downloadIconTint)
            menuItem?.icon = wrappedIcon
        }

        updateDownloadButtonState(isDownloadButtonEnabled)
        return true
    }

    @TestOnly
    fun isDownloadButtonVisible(): Boolean = menuItem?.isVisible == true

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection.
        return when (item.itemId) {
            R.id.download -> {
                checkAndStartDownload()
                true
            }

            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadFileFromNetwork(fileUrl: String?) {
        initPdfViewer(
            fileUrl
        )
    }

    private fun initPdfViewer(fileUrl: String?) {
        if (TextUtils.isEmpty(fileUrl)) onPdfError("")
        //Initiating PDf Viewer with URL
        try {
            binding.pdfView.setZoomEnabled(isZoomEnabled)
            binding.pdfView.initWithUrl(
                fileUrl!!,
                headers,
                lifecycleScope,
                lifecycle = lifecycle,
                cacheStrategy = cacheStrategy
            )
        } catch (e: Exception) {
            onPdfError(e.toString())
        }
    }

    private suspend fun initPdfViewerWithPath(filePath: String?) {
        if (TextUtils.isEmpty(filePath)) {
            onPdfError("")
            return
        }
        try {
            val file = if (filePath!!.startsWith("content://")) {
                uriToFile(applicationContext, Uri.parse(filePath))
            } else if (isFromAssets) {
                fileFromAsset(this, filePath)
            } else {
                File(filePath)
            }
            binding.pdfView.setZoomEnabled(isZoomEnabled)
            binding.pdfView.initWithFile(file, cacheStrategy)
        } catch (e: Exception) {
            onPdfError(e.toString())
        }
    }

    private fun onPdfError(e: String) {
        Log.e("Pdf render error", e)
        AlertDialog.Builder(this).setTitle(pdf_viewer_error).setMessage(error_pdf_corrupted)
            .setPositiveButton(pdf_viewer_retry) { dialog, which ->
                runOnUiThread {
                    init()
                }
            }.setNegativeButton(pdf_viewer_cancel, null).show()
    }

    private fun Boolean.showProgressBar() {
        binding.progressBar.visibility = if (this) VISIBLE else GONE
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startDownload()
        } else {
            // Show an AlertDialog here
            AlertDialog.Builder(this).setTitle(permission_required_title)
                .setMessage(permission_required)
                .setPositiveButton(pdf_viewer_grant) { dialog: DialogInterface, which: Int ->
                    // Request the permission again
                    requestStoragePermission()
                }.setNegativeButton(pdf_viewer_cancel, null).show()
        }
    }

    private fun requestStoragePermission() {
        requestPermissionLauncher.launch(permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun checkAndStartDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // For OS versions below Android 11, use the old method
            if (ContextCompat.checkSelfPermission(
                    this, permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startDownload()
            } else {
                // Request the permission
                requestPermissionLauncher.launch(permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // For Android 13 and above, use scoped storage or MediaStore APIs
            startDownload()
        }
    }

    fun startDownload() {
        val fileName = intent.getStringExtra(FILE_TITLE) ?: "downloaded_file.pdf"
        downloadedFilePath?.let { filePath ->
            if (SAVE_TO_DOWNLOADS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveFileToPublicDirectoryScopedStorage(filePath, fileName)
                } else {
                    saveFileToPublicDirectoryLegacy(filePath, fileName)
                }
            } else {
                promptUserForLocation(fileName)
            }
        } ?: Toast.makeText(this, file_not_downloaded_yet, Toast.LENGTH_SHORT).show()
    }

    private fun promptUserForLocation(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        createFileLauncher.launch(intent)
    }

    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val file = File(downloadedFilePath ?: return@launch)
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                file.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@PdfViewerActivity, file_saved_successfully, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("PdfViewerActivity", "Error saving PDF: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@PdfViewerActivity, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

    private fun saveFileToPublicDirectoryScopedStorage(filePath: String, fileName: String) {
        lifecycleScope.launch {
            val uri = createPdfDocumentUri(contentResolver, fileName)
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    File(filePath).inputStream().copyTo(outputStream)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PdfViewerActivity, file_saved_to_downloads, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveFileToPublicDirectoryLegacy(filePath: String, fileName: String) {
        val destinationFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName
        )
        File(filePath).copyTo(destinationFile, overwrite = true)
        Toast.makeText(this, file_saved_to_downloads, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.pdfView.closePdfRender()
    }

    private fun updateDownloadButtonState(isEnabled: Boolean) {
        isDownloadButtonEnabled = isEnabled

        menuItem?.let { item ->
            item.isEnabled = isEnabled
            item.icon?.alpha = if (isEnabled) 255 else 100 // Adjust opacity for disabled state
        }
    }

}
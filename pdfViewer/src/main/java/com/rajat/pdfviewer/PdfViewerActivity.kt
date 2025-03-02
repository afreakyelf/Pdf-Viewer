package com.rajat.pdfviewer

import android.Manifest.permission
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.TextAppearanceSpan
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.rajat.pdfviewer.databinding.ActivityPdfViewerBinding
import com.rajat.pdfviewer.util.FileUtils.createPdfDocumentUri
import com.rajat.pdfviewer.util.FileUtils.fileFromAsset
import com.rajat.pdfviewer.util.FileUtils.uriToFile
import com.rajat.pdfviewer.util.NetworkUtil.checkInternetConnection
import com.rajat.pdfviewer.util.ToolbarTitleBehavior
import com.rajat.pdfviewer.util.saveTo
import java.io.File
import java.io.FileNotFoundException
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
    private var downloadedFilePath: String? = null
    private var isDownloadButtonEnabled = false

    companion object {
        const val FILE_URL = "pdf_file_url"
        const val FILE_TITLE = "pdf_file_title"
        const val ENABLE_FILE_DOWNLOAD = "enable_download"
        const val FROM_ASSETS = "from_assests"
        const val TITLE_BEHAVIOR = "title_behavior"
        var enableDownload = false
        var isPDFFromPath = false
        var isFromAssets = false
        var SAVE_TO_DOWNLOADS = true

        fun launchPdfFromUrl(
            context: Context?,
            pdfUrl: String?,
            pdfTitle: String?,
            saveTo: saveTo,
            enableDownload: Boolean = true,
            headers: Map<String, String> = emptyMap(),
            toolbarTitleBehavior: ToolbarTitleBehavior? = null,
        ): Intent {
            val intent = Intent(context, PdfViewerActivity::class.java)
            intent.putExtra(FILE_URL, pdfUrl)
            intent.putExtra(FILE_TITLE, pdfTitle)
            intent.putExtra(ENABLE_FILE_DOWNLOAD, enableDownload)
            intent.putExtra("headers", HeaderData(headers))
            toolbarTitleBehavior?.let {
                intent.putExtra(TITLE_BEHAVIOR, it.ordinal)
            }
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
            toolbarTitleBehavior: ToolbarTitleBehavior? = null,
            ): Intent {
            val intent = Intent(context, PdfViewerActivity::class.java)
            intent.putExtra(FILE_URL, path)
            intent.putExtra(FILE_TITLE, pdfTitle)
            intent.putExtra(ENABLE_FILE_DOWNLOAD, false)
            intent.putExtra(FROM_ASSETS, fromAssets)
            toolbarTitleBehavior?.let {
                intent.putExtra(TITLE_BEHAVIOR, it.ordinal)
            }
            isPDFFromPath = true
            SAVE_TO_DOWNLOADS = saveTo == com.rajat.pdfviewer.util.saveTo.DOWNLOADS
            return intent
        }
    }

    private fun configureToolbar() {
        val typedArray = theme.obtainStyledAttributes(R.styleable.PdfRendererView_toolbar)
        try {
            val showToolbar =
                typedArray.getBoolean(R.styleable.PdfRendererView_toolbar_pdfView_showToolbar, true)
            val backIcon =
                typedArray.getDrawable(R.styleable.PdfRendererView_toolbar_pdfView_backIcon)
            val titleTextStyle = typedArray.getResourceId(
                R.styleable.PdfRendererView_toolbar_pdfView_titleTextStyle,
                -1
            )

            // Retrieve action bar tint with a fallback to Material3 colorPrimary
            val toolbarColor = typedArray.getColor(
                R.styleable.PdfRendererView_toolbar_pdfView_toolbarColor,
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorPrimary,
                    Color.BLUE
                )
            )

            // Adjust toolbar color in dark mode
            val isDarkMode = (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val adjustedToolbarColor = if (isDarkMode) {
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    Color.DKGRAY
                )
            } else {
                toolbarColor
            }

            binding.myToolbar.setBackgroundColor(adjustedToolbarColor)

            // Retrieve behavior from Intent or XML
            val intentBehaviorIndex = intent.extras?.getInt(TITLE_BEHAVIOR, -1) ?: -1
            val behavior: ToolbarTitleBehavior = if (intentBehaviorIndex != -1) {
                ToolbarTitleBehavior.entries[intentBehaviorIndex]
            } else {
                val xmlBehaviorIndex = typedArray.getInt(R.styleable.PdfRendererView_toolbar_pdfView_titleBehavior, 3)
                ToolbarTitleBehavior.fromXmlValue(xmlBehaviorIndex)
            }

            // Apply title behavior using a separate TextView
            binding.toolbarTitle.apply {
                setSingleLine(behavior.isSingleLine)
                maxLines = behavior.maxLines
                ellipsize = behavior.ellipsize

                if (behavior.ellipsize == TextUtils.TruncateAt.MARQUEE) {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            }

            // Apply toolbar visibility and other settings
            binding.myToolbar.visibility = if (showToolbar) VISIBLE else GONE
            backIcon?.let { binding.myToolbar.navigationIcon = it }

            // Apply title text appearance safely
            if (titleTextStyle != -1) {
                val spannable = SpannableString(binding.toolbarTitle.text)
                val textAppearance = TextAppearanceSpan(this, titleTextStyle)
                spannable.setSpan(
                    textAppearance,
                    0,
                    spannable.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.toolbarTitle.text = spannable
            }

            // Apply action bar tint using backgroundTintList for better theming
            binding.myToolbar.backgroundTintList = ColorStateList.valueOf(toolbarColor)

        } finally {
            typedArray.recycle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Ensures backward compatibility

        // Inflate layout once (previously done twice)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable Edge-to-Edge support
        enableEdgeToEdgeMode()

        // Setup Toolbar
        setUpToolbar(intent.getStringExtra(FILE_TITLE) ?: "PDF")
        configureToolbar()

        // Apply theme attributes (background & progress bar styles)
        applyThemeAttributes()

        // Retrieve intent extras
        extractIntentExtras()

        // Initialize the PDF viewer
        init()
    }

    private fun enableEdgeToEdgeMode() {
        // Retrieve primary color and background from the current theme
        val typedArray = theme.obtainStyledAttributes(intArrayOf(
            android.R.attr.colorPrimary,
            android.R.attr.colorBackground
        ))

        val primaryColor = typedArray.getColor(0, 0xFF6200EE.toInt())  // Default fallback: Indigo
        val backgroundColor = typedArray.getColor(1, 0xFFFFFFFF.toInt()) // Default: White
        typedArray.recycle()

        // Enable edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        // Apply scrim colors dynamically
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = backgroundColor, // Use theme's background color
                darkScrim = primaryColor      // Use theme's primary color
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = backgroundColor,
                darkScrim = primaryColor
            )
        )

        // Handle system insets to avoid content overlap
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }
    }


    private fun applyThemeAttributes() {
        val typedArray = theme.obtainStyledAttributes(R.styleable.PdfRendererView)
        try {
            // Set background color
            val backgroundColor = typedArray.getColor(
                R.styleable.PdfRendererView_pdfView_backgroundColor,
                ContextCompat.getColor(applicationContext, android.R.color.white)
            )

            // Set progress bar style
            val progressBarStyleResId =
                typedArray.getResourceId(R.styleable.PdfRendererView_pdfView_progressBar, -1)
            if (progressBarStyleResId != -1) {
                binding.progressBar.indeterminateDrawable =
                    ContextCompat.getDrawable(this, progressBarStyleResId)
            }
        } finally {
            typedArray.recycle()
        }
    }

    private fun extractIntentExtras() {
        enableDownload = intent.getBooleanExtra(ENABLE_FILE_DOWNLOAD, false)
        isFromAssets = intent.getBooleanExtra(FROM_ASSETS, false)

        headers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("headers", HeaderData::class.java)
        } else {
            intent.getParcelableExtra("headers")
        } ?: HeaderData(emptyMap())

        // Load string resources from XML attributes
        val typedArray = obtainStyledAttributes(R.styleable.PdfRendererView_Strings)
        error_pdf_corrupted =
            typedArray.getString(R.styleable.PdfRendererView_Strings_error_pdf_corrupted)
                ?: getString(R.string.error_pdf_corrupted)
        error_no_internet_connection =
            typedArray.getString(R.styleable.PdfRendererView_Strings_error_no_internet_connection)
                ?: getString(R.string.error_no_internet_connection)
        file_saved_successfully =
            typedArray.getString(R.styleable.PdfRendererView_Strings_file_saved_successfully)
                ?: getString(R.string.file_saved_successfully)
        file_saved_to_downloads =
            typedArray.getString(R.styleable.PdfRendererView_Strings_file_saved_to_downloads)
                ?: getString(R.string.file_saved_to_downloads)
        file_not_downloaded_yet =
            typedArray.getString(R.styleable.PdfRendererView_Strings_file_not_downloaded_yet)
                ?: getString(R.string.file_not_downloaded_yet)
        permission_required =
            typedArray.getString(R.styleable.PdfRendererView_Strings_permission_required)
                ?: getString(R.string.permission_required)
        permission_required_title =
            typedArray.getString(R.styleable.PdfRendererView_Strings_permission_required_title)
                ?: getString(R.string.permission_required_title)
        pdf_viewer_error =
            typedArray.getString(R.styleable.PdfRendererView_Strings_pdf_viewer_error)
                ?: getString(R.string.pdf_viewer_error)
        pdf_viewer_retry =
            typedArray.getString(R.styleable.PdfRendererView_Strings_pdf_viewer_retry)
                ?: getString(R.string.pdf_viewer_retry)
        pdf_viewer_cancel =
            typedArray.getString(R.styleable.PdfRendererView_Strings_pdf_viewer_cancel)
                ?: getString(R.string.pdf_viewer_cancel)
        pdf_viewer_grant =
            typedArray.getString(R.styleable.PdfRendererView_Strings_pdf_viewer_grant)
                ?: getString(R.string.pdf_viewer_grant)

        typedArray.recycle()
    }


    private fun init() {
        binding.pdfView.statusListener = object : PdfRendererView.StatusCallBack {
            override fun onPdfLoadStart() {
                runOnUiThread {
                    true.showProgressBar()
                    updateDownloadButtonState(false)
                }
            }

            override fun onPdfLoadProgress(
                progress: Int,
                downloadedBytes: Long,
                totalBytes: Long?
            ) {
                //Download is in progress
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

                    val errorMessage = when {
                        error is UnknownHostException -> error_no_internet_connection
                        error is SocketTimeoutException -> "Network timeout! Please check your connection."
                        error is FileNotFoundException -> "File not found on the server."
                        error.message?.contains("Invalid content type received") == true ->
                            "The server returned a non-PDF file. Please check the URL."

                        error.message?.contains("Downloaded file is not a valid PDF") == true ->
                            "The file appears to be corrupted or is not a valid PDF."

                        error.message?.contains("Incomplete download") == true ->
                            "The download was incomplete. Please check your internet connection and try again."

                        error.message?.contains("Failed to download after") == true ->
                            "Failed to download the PDF after multiple attempts. Please check your internet connection."

                        else -> "An unexpected error occurred: ${error.localizedMessage}"
                    }

                    Log.e("PdfViewer", "Error: $errorMessage", error)

                    showErrorDialog(errorMessage, isRetryable(error))
                }
            }

            override fun onPageChanged(currentPage: Int, totalPage: Int) {
                //Page change. Not require
            }
        }

        if (intent.extras!!.containsKey(FILE_URL)) {
            fileUrl = intent.extras!!.getString(FILE_URL)
            if (isPDFFromPath) {
                initPdfViewerWithPath(this.fileUrl)
            } else {
                if (checkInternetConnection(this)) {
                    loadFileFromNetwork(this.fileUrl)
                } else {
                    Toast.makeText(
                        this,
                        error_no_internet_connection,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun isRetryable(error: Throwable): Boolean {
        return error is UnknownHostException ||
                error is SocketTimeoutException ||
                error.message?.contains("Failed to download") == true ||
                error.message?.contains("Incomplete download") == true
    }

    private fun showErrorDialog(message: String, shouldRetry: Boolean) {
        val builder = AlertDialog.Builder(this)
            .setTitle("Error Loading PDF")
            .setMessage(message)

        if (shouldRetry) {
            builder.setPositiveButton(pdf_viewer_retry) { _, _ -> loadFileFromNetwork(fileUrl) }
        }

        builder.setNegativeButton(pdf_viewer_cancel, null)
            .show()
    }

    private fun setUpToolbar(toolbarTitle: String) {
        binding.toolbarTitle.text = toolbarTitle

        setSupportActionBar(binding.myToolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        menuItem = menu.findItem(R.id.download)

        val typedArray = theme.obtainStyledAttributes(R.styleable.PdfRendererView_toolbar)
        try {
            val downloadIconTint = typedArray.getColor(
                R.styleable.PdfRendererView_toolbar_pdfView_downloadIconTint,
                ContextCompat.getColor(applicationContext, android.R.color.white)
            )
            // Apply tint if it's specified and the icon exists
            menuItem?.icon?.let { icon ->
                val wrappedIcon = DrawableCompat.wrap(icon).mutate()
                DrawableCompat.setTint(wrappedIcon, downloadIconTint)
                menuItem?.icon = wrappedIcon
            }
        } finally {
            typedArray.recycle()
        }

        updateDownloadButtonState(isDownloadButtonEnabled)

        menuItem?.isVisible = enableDownload
        return true
    }

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
            binding.pdfView.initWithUrl(
                fileUrl!!,
                headers,
                lifecycleScope,
                lifecycle = lifecycle
            )
        } catch (e: Exception) {
            onPdfError(e.toString())
        }
    }

    private fun initPdfViewerWithPath(filePath: String?) {
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
            binding.pdfView.initWithFile(file)
        } catch (e: Exception) {
            onPdfError(e.toString())
        }
    }

    private fun onPdfError(e: String) {
        Log.e("Pdf render error", e)
        AlertDialog.Builder(this)
            .setTitle(pdf_viewer_error)
            .setMessage(error_pdf_corrupted)
            .setPositiveButton(pdf_viewer_retry) { dialog, which ->
                runOnUiThread {
                    init()
                }
            }
            .setNegativeButton(pdf_viewer_cancel, null)
            .show()
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
            AlertDialog.Builder(this)
                .setTitle(permission_required_title)
                .setMessage(permission_required)
                .setPositiveButton(pdf_viewer_grant) { dialog: DialogInterface, which: Int ->
                    // Request the permission again
                    requestStoragePermission()
                }
                .setNegativeButton(pdf_viewer_cancel, null)
                .show()
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

    private fun startDownload() {
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
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        downloadedFilePath?.let { filePath ->
                            File(filePath).inputStream().copyTo(outputStream)
                        }
                    }
                    Toast.makeText(this, file_saved_successfully, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun saveFileToPublicDirectoryScopedStorage(filePath: String, fileName: String) {
        val contentResolver = applicationContext.contentResolver
        val uri = createPdfDocumentUri(contentResolver, fileName)
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            File(filePath).inputStream().copyTo(outputStream)
        }
        Toast.makeText(this, file_saved_to_downloads, Toast.LENGTH_SHORT).show()
    }

    private fun saveFileToPublicDirectoryLegacy(filePath: String, fileName: String) {
        val destinationFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
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
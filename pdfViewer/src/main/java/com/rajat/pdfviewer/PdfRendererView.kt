package com.rajat.pdfviewer

import android.app.Activity
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleObserver
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.rajat.pdfviewer.util.CacheManager
import com.rajat.pdfviewer.util.CacheStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.io.File

/**
 * Created by Rajat on 11,July,2020
 */

class PdfRendererView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), LifecycleObserver {

    // region Core rendering
    private lateinit var pdfRendererCore: PdfRendererCore
    private lateinit var pdfViewAdapter: PdfViewAdapter
    private var pdfRendererCoreInitialised = false
    private var cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
    // endregion

    // region UI
    lateinit var recyclerView: PinchZoomRecyclerView
    private lateinit var pageNo: TextView
    private var divider: Drawable? = null
    private var pageMargin: Rect = Rect(0, 0, 0, 0)
    // endregion

    // region State
    private var positionToUseForState: Int = NO_POSITION
    private var restoredScrollPosition: Int = NO_POSITION
    private var lastDy: Int = 0
    private var pendingJumpPage: Int? = null
    // endregion

    // region Flags
    private var showDivider = true
    private var isZoomEnabled = true
    private var enableLoadingForPages = false
    private var disableScreenshots = false
    // endregion

    // region Lifecycle + Async
    private var postInitializationAction: (() -> Unit)? = null
    private var viewJob = SupervisorJob()
    private val viewScope = CoroutineScope(viewJob + Dispatchers.IO)
    // endregion

    var zoomListener: ZoomListener? = null
    var statusListener: StatusCallBack? = null

    //region Public APIs
    fun isZoomedIn(): Boolean = this::recyclerView.isInitialized && recyclerView.isZoomedIn()
    fun getZoomScale(): Float = if (this::recyclerView.isInitialized) recyclerView.getZoomScale() else 1f

    val totalPageCount: Int
        get() {
            return pdfRendererCore.getPageCount()
        }

    /**
     * Clears the cache directory of the application.
     * @param context The application context.
     */
    suspend fun PdfRendererView.clearCache(context: Context) {
        CacheManager.clearCacheDir(context)
    }
    //endregion

    init {
        getAttrs(attrs, defStyleAttr)
    }

    /**
     * Initializes the PDF view with a remote URL. Downloads and renders the PDF.
     *
     * @param url The URL of the PDF file.
     * @param headers Optional HTTP headers.
     * @param lifecycleCoroutineScope Scope for managing coroutines.
     * @param lifecycle Lifecycle to observe for cleanup.
     * @param cacheStrategy Cache strategy to apply.
     */
    fun initWithUrl(
        url: String,
        headers: HeaderData = HeaderData(),
        lifecycleCoroutineScope: LifecycleCoroutineScope,
        lifecycle: Lifecycle,
        cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
    ) {
        lifecycle.addObserver(this) // Register as LifecycleObserver
        this.cacheStrategy = cacheStrategy
        PdfDownloader(
            lifecycleCoroutineScope,
            headers,
            url,
            cacheStrategy,
            PdfDownloadCallback(
                context,
                onStart = { statusListener?.onPdfLoadStart() },
                onProgress = { progress, current, total ->
                    statusListener?.onPdfLoadProgress(progress, current, total)
                },
                onSuccess = {
                    try {
                        initWithFile(it, cacheStrategy)
                        statusListener?.onPdfLoadSuccess(it.absolutePath)
                    } catch (e: Exception) {
                        statusListener?.onError(e)
                    }
                },
                onError = { statusListener?.onError(it) }
            )).start()
    }

    /**
     * Initializes the PDF view with a local [File].
     *
     * @param file The PDF file to render.
     * @param cacheStrategy Cache strategy to apply.
     */
    fun initWithFile(file: File, cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE) {
        this.cacheStrategy = cacheStrategy
        val cacheIdentifier = file.name

        // Notify loading started
        statusListener?.onPdfRenderStart()
        viewScope.launch {
            try {
                val fileDescriptor = PdfRendererCore.getFileDescriptor(file)
                val renderer =
                    PdfRendererCore.create(context, fileDescriptor, cacheIdentifier, cacheStrategy)
                withContext(Dispatchers.Main) {
                    initializeRenderer(renderer)
                    statusListener?.onPdfLoadSuccess(file.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusListener?.onError(e)
                }
            }
        }
    }

    /**
     * Initializes the PDF view with a content [Uri]. Useful for opening from storage provider.
     *
     * @param uri The Uri to the PDF file.
     */
    fun initWithUri(uri: Uri) {
        val cacheIdentifier = uri.toString().hashCode().toString()

        statusListener?.onPdfRenderStart()

        viewScope.launch {
            try {
                val fileDescriptor =
                    context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                val renderer =
                    PdfRendererCore.create(context, fileDescriptor, cacheIdentifier, cacheStrategy)
                withContext(Dispatchers.Main) {
                    initializeRenderer(renderer)
                    statusListener?.onPdfLoadSuccess("uri:$uri")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusListener?.onError(e)
                }
            }
        }
    }

    private fun initializeRenderer(renderer: PdfRendererCore) {
        // If re-initializing, clear old views & adapter
        if (pdfRendererCoreInitialised) {
            viewJob.cancel()
            removeAllViews()
            if (this::recyclerView.isInitialized) {
                recyclerView.adapter = null
            }
        }

        PdfRendererCore.enableDebugMetrics = true
        pdfRendererCore = renderer
        pdfRendererCoreInitialised = true

        // Inflate layout first — ensures RecyclerView references are valid
        val v = LayoutInflater.from(context).inflate(R.layout.pdf_rendererview, this, false)
        addView(v)

        // Now that layout is added, find RecyclerView and other views
        recyclerView = findViewById(R.id.recyclerView)
        pageNo = findViewById(R.id.pageNumber)

        // Now it's safe to create the adapter and assign it
        pdfViewAdapter = PdfViewAdapter(
            context,
            pdfRendererCore,
            this,
            pageMargin,
            enableLoadingForPages
        )

        recyclerView.apply {
            adapter = pdfViewAdapter
            itemAnimator = DefaultItemAnimator()
            if (showDivider) {
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                    divider?.let { setDrawable(it) }
                }.let { addItemDecoration(it) }
            }
            setZoomEnabled(isZoomEnabled)
        }

        recyclerView.addOnScrollListener(
            PdfPageScrollListener(
                pageNoTextView = pageNo,
                totalPageCount = { totalPageCount },
                updatePage = { updatePageNumberDisplay(it) },
                schedulePrefetch = { page ->
                    pdfRendererCore.schedulePrefetch(page, recyclerView.width, recyclerView.height, 0)
                }
            )
        )

        recyclerView.postDelayed({
            if (restoredScrollPosition != NO_POSITION) {
                recyclerView.scrollToPosition(restoredScrollPosition)
                restoredScrollPosition = NO_POSITION  // Reset after applying
            }
        }, 500) // Adjust delay as needed

        recyclerView.setOnZoomChangeListener { isZoomedIn, scale ->
            zoomListener?.onZoomChanged(isZoomedIn, scale)
        }

        recyclerView.post {
            postInitializationAction?.invoke()
            statusListener?.onPdfRenderSuccess()
            postInitializationAction = null
        }

        pendingJumpPage?.let { page ->
            jumpToPage(page)
            pendingJumpPage = null
        }

        // Start preloading cache into memory immediately after setting up adapter and RecyclerView
        preloadCacheIntoMemory()
    }

    /**
     * Scrolls the RecyclerView to the specified PDF page.
     *
     * @param pageNumber The page number to scroll to (0-based).
     * @param smoothScroll Whether to use smooth scrolling.
     * @param delayMillis Optional delay before scrolling (default 150ms).
     */
    fun jumpToPage(pageNumber: Int, smoothScroll: Boolean = true, delayMillis: Long = 150L) {
        if (pageNumber !in 0 until totalPageCount) return
        if (!::recyclerView.isInitialized) {
            pendingJumpPage = pageNumber
            return
        }

        recyclerView.postDelayed({
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@postDelayed
            val adapter = recyclerView.adapter ?: return@postDelayed
            if (adapter.itemCount == 0) return@postDelayed

            if (smoothScroll) {
                layoutManager.smoothScrollToPosition(recyclerView, RecyclerView.State(), pageNumber)
            } else {
                layoutManager.scrollToPositionWithOffset(pageNumber, 0)
            }

            recyclerView.post {
                forceUpdatePageNumber()
            }
        }, delayMillis)
    }

    private fun forceUpdatePageNumber() {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val firstCompletelyVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        val lastCompletelyVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
        val positionToUse = lastCompletelyVisiblePosition.takeIf { it != NO_POSITION }
            ?: lastVisiblePosition.takeIf { it != NO_POSITION }
            ?: firstCompletelyVisiblePosition.takeIf { it != NO_POSITION }
            ?: firstVisiblePosition
        positionToUseForState = positionToUse
        updatePageNumberDisplay(positionToUse)
    }

    private fun updatePageNumberDisplay(position: Int) {
        if (position != NO_POSITION) {
            pageNo.text = context.getString(R.string.pdfView_page_no, position + 1, totalPageCount)
            pageNo.visibility = VISIBLE
            if (position == 0) {
                pageNo.postDelayed({ pageNo.visibility = GONE }, 3000)
            }
            statusListener?.onPageChanged(position + 1, totalPageCount)
        }
    }

    private fun getAttrs(attrs: AttributeSet?, defStyle: Int) {
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.PdfRendererView, defStyle, 0)
        setTypeArray(typedArray)
    }

    private fun setTypeArray(typedArray: TypedArray) {
        showDivider = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_showDivider, true)
        divider = typedArray.getDrawable(R.styleable.PdfRendererView_pdfView_divider)
        enableLoadingForPages =
            typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_enableLoadingForPages, false)
        disableScreenshots =
            typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_disableScreenshots, false)
        isZoomEnabled = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_enableZoom, true)

        // Fetch all margin values efficiently
        val marginDim =
            typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_margin, 0)
        pageMargin.set(
            typedArray.getDimensionPixelSize(
                R.styleable.PdfRendererView_pdfView_page_marginLeft,
                marginDim
            ),
            typedArray.getDimensionPixelSize(
                R.styleable.PdfRendererView_pdfView_page_marginTop,
                marginDim
            ),
            typedArray.getDimensionPixelSize(
                R.styleable.PdfRendererView_pdfView_page_marginRight,
                marginDim
            ),
            typedArray.getDimensionPixelSize(
                R.styleable.PdfRendererView_pdfView_page_marginBottom,
                marginDim
            )
        )

        applyScreenshotSecurity()
        typedArray.recycle()
    }

    private fun applyScreenshotSecurity() {
        if (disableScreenshots) {
            // Disables taking screenshots and screen recording
            (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Closes the current PDF rendering session and frees associated resources.
     */
    fun closePdfRender() {
        if (pdfRendererCoreInitialised) {
            pdfRendererCore.closePdfRender()
            pdfRendererCoreInitialised = false
        }
    }

    private suspend fun getBitmapByPage(page: Int): Bitmap? {
        return pdfRendererCore.getBitmapFromCache(page)
    }

    /**
     * Returns all the pages that have been rendered and are cached.
     *
     * @return List of Bitmap pages.
     */
    suspend fun getLoadedBitmaps(): List<Bitmap> {
        return (0..<totalPageCount).mapNotNull { page ->
            getBitmapByPage(page)
        }
    }

    /**
     * Enables or disables zoom functionality on the PDF pages.
     *
     * @param zoomEnabled true to enable zoom, false to disable.
     */
    fun setZoomEnabled(zoomEnabled: Boolean) {
        isZoomEnabled = zoomEnabled
    }

    @TestOnly
    fun getZoomEnabled(): Boolean {
        return isZoomEnabled
    }

    private fun preloadCacheIntoMemory() {
        viewScope.launch {
            pdfRendererCore.let { renderer ->
                (0 until renderer.getPageCount()).forEach { pageNo ->
                    renderer.getBitmapFromCache(pageNo)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clear adapter to release ViewHolders
        if (this::recyclerView.isInitialized) {
            recyclerView.adapter = null
        }
        closePdfRender()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = Bundle()
        savedState.putParcelable("superState", superState)
        if (this::recyclerView.isInitialized) {
            savedState.putInt("scrollPosition", positionToUseForState)
        }
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var savedState = state
        if (savedState is Bundle) {
            val superState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedState.getParcelable("superState", Parcelable::class.java)
            } else {
                savedState.getParcelable("superState")
            }
            super.onRestoreInstanceState(superState)
            restoredScrollPosition = savedState.getInt("scrollPosition", positionToUseForState)
        } else {
            super.onRestoreInstanceState(savedState)
        }
    }

    /**
     * Returns the current scroll direction of the view.
     *
     * @return 1 = scrolling down, -1 = up, 0 = idle.
     */
    fun getScrollDirection(): Int = when {
        lastDy > 0 -> 1   // down/forward
        lastDy < 0 -> -1  // up/backward
        else -> 0         // idle
    }

    // region Interfaces
    interface StatusCallBack {
        fun onPdfLoadStart() {}
        fun onPdfLoadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long?) {}
        fun onPdfLoadSuccess(absolutePath: String) {}
        fun onError(error: Throwable) {}
        fun onPageChanged(currentPage: Int, totalPage: Int) {}
        fun onPdfRenderStart() {}
        fun onPdfRenderSuccess() {}
    }

    interface ZoomListener {
        fun onZoomChanged(isZoomedIn: Boolean, scale: Float)
    }
}

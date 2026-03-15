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
import android.os.ParcelFileDescriptor
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
import com.rajat.pdfviewer.util.CacheHelper
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
    private var restoredScrollOffset: Int = 0
    private var lastDy: Int = 0
    private var pendingJumpPage: Int? = null
    // endregion

    // region Flags
    private var showDivider = true
    private var isZoomEnabled = true
    private var maxZoomScale = DEFAULT_MAX_ZOOM
    private var enableLoadingForPages = false
    private var disableScreenshots = false
    // endregion

    // region Lifecycle + Async
    private var postInitializationAction: (() -> Unit)? = null
    private var viewJob = SupervisorJob()
    private var viewScope = CoroutineScope(viewJob + Dispatchers.IO)
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
        val cacheIdentifier = CacheHelper.getCacheKey(file.absolutePath)

        // Notify loading started
        statusListener?.onPdfRenderStart()
        viewScope.launch {
            var fileDescriptor: ParcelFileDescriptor? = null
            try {
                fileDescriptor = PdfRendererCore.getFileDescriptor(file)
                val renderer =
                    PdfRendererCore.create(context, fileDescriptor, cacheIdentifier, cacheStrategy)
                fileDescriptor = null // ownership transferred to PdfRendererCore
                withContext(Dispatchers.Main) {
                    initializeRenderer(renderer)
                    statusListener?.onPdfLoadSuccess(file.absolutePath)
                }
            } catch (e: Exception) {
                fileDescriptor?.close()
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
        val cacheIdentifier = CacheHelper.getCacheKey(uri.toString())

        statusListener?.onPdfRenderStart()

        viewScope.launch {
            var fileDescriptor: ParcelFileDescriptor? = null
            try {
                fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalArgumentException("Failed to open file descriptor — verify URI is valid and app has read permission")
                val renderer =
                    PdfRendererCore.create(context, fileDescriptor, cacheIdentifier, cacheStrategy)
                fileDescriptor = null // ownership transferred to PdfRendererCore
                withContext(Dispatchers.Main) {
                    initializeRenderer(renderer)
                    statusListener?.onPdfLoadSuccess("uri:$uri")
                }
            } catch (e: Exception) {
                fileDescriptor?.close()
                withContext(Dispatchers.Main) {
                    statusListener?.onError(e)
                }
            }
        }
    }

    private fun initializeRenderer(renderer: PdfRendererCore) {
        // If re-initializing, clear old views & adapter
        if (pdfRendererCoreInitialised) {
            resetViewScope()
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
            setMaxZoomScale(maxZoomScale)
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
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                    restoredScrollPosition,
                    restoredScrollOffset
                )
                restoredScrollPosition = NO_POSITION
                restoredScrollOffset = 0
            }
        }, 500) // Adjust delay as needed

        recyclerView.setOnZoomChangeListener { isZoomedIn, scale ->
            zoomListener?.onZoomChanged(isZoomedIn, scale)
        }

        recyclerView.setOnZoomSettledListener {
            // Re-bind visible items so pages are re-rendered at the new zoom resolution.
            if (pdfRendererCoreInitialised) {
                pdfViewAdapter.rebindVisiblePages(recyclerView)
            }
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
     * Scrolls down by one viewport height if the current page extends below the visible area,
     * otherwise jumps to the next page.
     *
     * This is especially useful in landscape mode where a page may be taller than the screen,
     * allowing the user to scroll through the current page before advancing to the next one.
     *
     * Must be called on the main thread, after the PDF has been loaded and the view is attached.
     */
    fun scrollToNextPage() {
        if (!::recyclerView.isInitialized) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePosition == NO_POSITION) return
        val firstView = layoutManager.findViewByPosition(firstVisiblePosition) ?: return
        // PinchZoomRecyclerView scales via canvas; layout coords are unscaled.
        // Derive the viewport height in unscaled layout-px so both the boundary
        // check and the scroll delta operate in the same coordinate space.
        val scale = recyclerView.getZoomScale()
        val unscaledViewportHeight = (recyclerView.height / scale).toInt()
        val remaining = layoutManager.getDecoratedBottom(firstView) - unscaledViewportHeight
        if (remaining > 0) {
            // Clamp to the remaining distance so we never scroll past the page bottom.
            val delta = minOf(unscaledViewportHeight, remaining)
            recyclerView.smoothScrollBy(0, delta)
        } else {
            jumpToPage(firstVisiblePosition + 1)
        }
    }

    /**
     * Scrolls up by one viewport height if there is content above the current scroll position,
     * otherwise jumps to the previous page.
     *
     * This is especially useful in landscape mode where a page may be taller than the screen,
     * allowing the user to scroll back through the current page before going to the previous one.
     *
     * Must be called on the main thread, after the PDF has been loaded and the view is attached.
     */
    fun scrollToPreviousPage() {
        if (!::recyclerView.isInitialized) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePosition == NO_POSITION) return
        val firstView = layoutManager.findViewByPosition(firstVisiblePosition) ?: return
        // getDecoratedTop is negative when content is scrolled above the viewport.
        // Derive an unscaled viewport height so the scroll delta matches the
        // zoom-adjusted coordinate space used by the layout manager.
        val scale = recyclerView.getZoomScale()
        val unscaledViewportHeight = (recyclerView.height / scale).toInt()
        val hiddenAbove = -layoutManager.getDecoratedTop(firstView)
        if (hiddenAbove > 0) {
            // Clamp to the hidden distance so we never scroll past the page top.
            val delta = minOf(unscaledViewportHeight, hiddenAbove)
            recyclerView.smoothScrollBy(0, -delta)
        } else {
            jumpToPage(firstVisiblePosition - 1)
        }
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

    private fun captureCurrentScrollState(): Pair<Int, Int>? {
        if (!this::recyclerView.isInitialized) return null
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePosition == NO_POSITION) return null
        val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition) ?: return null
        return firstVisiblePosition to layoutManager.getDecoratedTop(firstVisibleView)
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
        maxZoomScale = typedArray.getFloat(R.styleable.PdfRendererView_pdfView_maxZoom, DEFAULT_MAX_ZOOM)
            .coerceIn(1f, MAX_ALLOWED_ZOOM)

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
        resetViewScope()
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
        if (this::recyclerView.isInitialized) {
            recyclerView.setZoomEnabled(zoomEnabled)
        }
    }

    fun setMaxZoomScale(maxZoomScale: Float) {
        this.maxZoomScale = maxZoomScale.coerceIn(1f, MAX_ALLOWED_ZOOM)
        if (this::recyclerView.isInitialized) {
            recyclerView.setMaxZoomScale(this.maxZoomScale)
        }
    }

    fun getMaxZoomScale(): Float = maxZoomScale

    @TestOnly
    fun getZoomEnabled(): Boolean {
        return isZoomEnabled
    }

    /**
     * Zooms in on the current content.
     */
    fun zoomIn() {
        if (this::recyclerView.isInitialized) {
            recyclerView.zoomIn()
        }
    }

    /**
     * Zooms out on the current content.
     */
    fun zoomOut() {
        if (this::recyclerView.isInitialized) {
            recyclerView.zoomOut()
        }
    }

    /**
     * Resets the zoom level to 1.0.
     */
    fun resetZoom() {
        if (this::recyclerView.isInitialized) {
            recyclerView.resetZoom()
        }
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

    override fun canScrollVertically(direction: Int): Boolean {
        return if (this::recyclerView.isInitialized) {
            recyclerView.canScrollVertically(direction)
        } else {
            super.canScrollVertically(direction)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Temporary detaches happen inside ViewPager/ViewPager2 tabs.
        // Keep the renderer and adapter intact so the view can display again when reattached.
    }

    private fun resetViewScope() {
        viewJob.cancel()
        viewJob = SupervisorJob()
        viewScope = CoroutineScope(viewJob + Dispatchers.IO)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = Bundle()
        savedState.putParcelable("superState", superState)
        val scrollState = captureCurrentScrollState()
        if (scrollState != null) {
            val (position, offset) = scrollState
            savedState.putInt(KEY_SCROLL_POSITION, position)
            savedState.putInt(KEY_SCROLL_OFFSET, offset)
        } else if (positionToUseForState != NO_POSITION) {
            savedState.putInt(KEY_SCROLL_POSITION, positionToUseForState)
            savedState.putInt(KEY_SCROLL_OFFSET, 0)
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
            restoredScrollPosition = savedState.getInt(KEY_SCROLL_POSITION, positionToUseForState)
            restoredScrollOffset = savedState.getInt(KEY_SCROLL_OFFSET, 0)
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

    companion object {
        private const val DEFAULT_MAX_ZOOM = 3.0f
        private const val MAX_ALLOWED_ZOOM = 5.0f
        private const val KEY_SCROLL_POSITION = "scrollPosition"
        private const val KEY_SCROLL_OFFSET = "scrollOffset"
    }
}

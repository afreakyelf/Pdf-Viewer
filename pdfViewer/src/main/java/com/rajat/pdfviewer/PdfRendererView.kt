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
import android.os.Handler
import android.os.Looper
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
import com.rajat.pdfviewer.util.CacheStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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
    lateinit var recyclerView: PinchZoomRecyclerView
    private lateinit var pageNo: TextView
    private lateinit var pdfRendererCore: PdfRendererCore
    private lateinit var pdfViewAdapter: PdfViewAdapter
    private var showDivider = true
    private var isZoomEnabled = true
    private var divider: Drawable? = null
    private var runnable = Runnable {}
    private var enableLoadingForPages: Boolean = false
    private var pdfRendererCoreInitialised = false
    private var pageMargin: Rect = Rect(0,0,0,0)
    var statusListener: StatusCallBack? = null
    private var positionToUseForState: Int = 0
    private var restoredScrollPosition: Int = NO_POSITION
    private var disableScreenshots: Boolean = false
    private var postInitializationAction: (() -> Unit)? = null
    private var cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isZoomedIn(): Boolean = this::recyclerView.isInitialized && recyclerView.isZoomedIn()

    fun getZoomScale(): Float = if (this::recyclerView.isInitialized) recyclerView.getZoomScale() else 1f

    val totalPageCount: Int
        get() {
            return pdfRendererCore.getPageCount()
        }

    init {
        getAttrs(attrs, defStyleAttr)
    }

    interface StatusCallBack {
        fun onPdfLoadStart() {}
        fun onPdfLoadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long?) {}
        fun onPdfLoadSuccess(absolutePath: String) {}
        fun onError(error: Throwable) {}
        fun onPageChanged(currentPage: Int, totalPage: Int) {}
    }

    interface ZoomListener {
        fun onZoomChanged(isZoomedIn: Boolean, scale: Float)
    }

    var zoomListener: ZoomListener? = null

    // Load PDF from network URL
    fun initWithUrl(
        url: String,
        headers: HeaderData = HeaderData(),
        lifecycleCoroutineScope: LifecycleCoroutineScope,
        lifecycle: Lifecycle,
        cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE
    ) {
        lifecycle.addObserver(this) // Register as LifecycleObserver
        this.cacheStrategy = cacheStrategy
        PdfDownloader(lifecycleCoroutineScope,headers,url,cacheStrategy, object : PdfDownloader.StatusListener {
            override fun getContext(): Context = context
            override fun onDownloadStart() {
                statusListener?.onPdfLoadStart()
            }
            override fun onDownloadProgress(currentBytes: Long, totalBytes: Long) {
                var progress = (currentBytes.toFloat() / totalBytes.toFloat() * 100F).toInt()
                if (progress >= 100)
                    progress = 100
                statusListener?.onPdfLoadProgress(progress, currentBytes, totalBytes)
            }
            override fun onDownloadSuccess(downloadedFile: File) {
                try {
                    initWithFile(File(downloadedFile.absolutePath), cacheStrategy)
                    statusListener?.onPdfLoadSuccess(downloadedFile.absolutePath)
                } catch (e: Exception) {
                    statusListener?.onError(e)
                }
            }
            override fun onError(error: Throwable) {
                error.printStackTrace()
                statusListener?.onError(error)
            }
        }).start()
    }

    // Load PDF directly from File
    fun initWithFile(file: File, cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE) {
        this.cacheStrategy = cacheStrategy
        val cacheIdentifier = "${file.name}_${file.parent?.hashCode()}_${file.length()}"

        // Notify loading started
        statusListener?.onPdfLoadStart()
        coroutineScope.launch {
            try {
                val fileDescriptor = PdfRendererCore.getFileDescriptor(file)
                val renderer = PdfRendererCore.create(context, fileDescriptor, cacheIdentifier, cacheStrategy)
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


    // Load PDF directly from Uri
    fun initWithUri(uri: Uri) {
        val cacheIdentifier = uri.toString().hashCode().toString()

        statusListener?.onPdfLoadStart()
        coroutineScope.launch {
            try {
                val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
                val renderer = PdfRendererCore.create(context, fileDescriptor, cacheIdentifier, cacheStrategy)
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
        // Proceed with safeFile
        PdfRendererCore.enableDebugMetrics = true
        pdfRendererCore = renderer
        pdfRendererCoreInitialised = true
        pdfViewAdapter = PdfViewAdapter(context,pdfRendererCore, pageMargin, enableLoadingForPages)
        val v = LayoutInflater.from(context).inflate(R.layout.pdf_rendererview, this, false)
        addView(v)
        recyclerView = findViewById(R.id.recyclerView)
        pageNo = findViewById(R.id.pageNumber)
        recyclerView.apply {
            adapter = pdfViewAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            itemAnimator = DefaultItemAnimator()
            if (showDivider) {
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                    divider?.let { setDrawable(it) }
                }.let { addItemDecoration(it) }
            }
            setZoomEnabled(isZoomEnabled)
            addOnScrollListener(scrollListener)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (restoredScrollPosition != NO_POSITION) {
                recyclerView.scrollToPosition(restoredScrollPosition)
                restoredScrollPosition = NO_POSITION  // Reset after applying
            }
        }, 500) // Adjust delay as needed

        recyclerView.setOnZoomChangeListener { isZoomedIn, scale ->
            zoomListener?.onZoomChanged(isZoomedIn, scale)
        }

        runnable = Runnable {
            pageNo.visibility = GONE
        }

        recyclerView.post {
            postInitializationAction?.invoke()
            postInitializationAction = null
        }

        // Start preloading cache into memory immediately after setting up adapter and RecyclerView
        preloadCacheIntoMemory()
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        private var lastDisplayedPage = NO_POSITION
        private var lastScrollDirection = 0 // 1 = Down, -1 = Up

        private val hideRunnable = Runnable {
            if (pageNo.visibility == VISIBLE) {
                pageNo.visibility = GONE
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager

            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val firstCompletelyVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            val lastCompletelyVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()

            // Determine scroll direction
            val scrollDirection = when {
                dy > 0 -> 1  // Scrolling Down
                dy < 0 -> -1 // Scrolling Up
                else -> lastScrollDirection
            }
            lastScrollDirection = scrollDirection

            // Determine the most dominant page to display
            val pageToShow = when (scrollDirection) {
                1 -> lastCompletelyVisiblePosition.takeIf { it != NO_POSITION }
                    ?: lastVisiblePosition.takeIf { it != NO_POSITION }
                    ?: firstVisiblePosition // Scrolling Down - Prefer the last fully visible, then partially visible
                -1 -> firstCompletelyVisiblePosition.takeIf { it != NO_POSITION }
                    ?: firstVisiblePosition.takeIf { it != NO_POSITION }
                    ?: lastVisiblePosition // Scrolling Up - Prefer the first fully visible, then partially visible
                else -> firstVisiblePosition // Default case
            }

            // Ensure updates happen when the page actually changes
            if (pageToShow != lastDisplayedPage) {
                updatePageNumberDisplay(pageToShow)
                lastDisplayedPage = pageToShow
            }
        }

        fun updatePageNumberDisplay(position: Int) {
            if (position != NO_POSITION) {
                pageNo.text = context.getString(R.string.pdfView_page_no, position + 1, totalPageCount)
                pageNo.visibility = VISIBLE

                // Remove any existing hide delays before scheduling a new one
                pageNo.removeCallbacks(hideRunnable)
                pageNo.postDelayed(hideRunnable, 3000)

                statusListener?.onPageChanged(position + 1, totalPageCount)
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                pageNo.removeCallbacks(hideRunnable)
                pageNo.postDelayed(hideRunnable, 3000)
            } else {
                pageNo.removeCallbacks(hideRunnable)
            }
        }
    }

    fun jumpToPage(pageNumber: Int) {
        val action = {
            if (pageNumber in 0 until totalPageCount) {
                recyclerView.post {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPositionWithOffset(pageNumber, 0) // Ensures full visibility

                    recyclerView.post { forceUpdatePageNumber() } // No delay needed
                }
            }
        }
        if (this::pdfRendererCore.isInitialized) {
            action()
        } else {
            postInitializationAction = action
        }
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
        enableLoadingForPages = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_enableLoadingForPages, false)
        disableScreenshots = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_disableScreenshots, false)
        isZoomEnabled = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_enableZoom, true)

        // Fetch all margin values efficiently
        val marginDim = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_margin, 0)
        pageMargin.set(
            typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginLeft, marginDim),
            typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginTop, marginDim),
            typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginRight, marginDim),
            typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginBottom, marginDim)
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

    fun closePdfRender() {
        if (pdfRendererCoreInitialised) {
            pdfRendererCore.closePdfRender()
            pdfRendererCoreInitialised = false
        }
    }

    private suspend fun getBitmapByPage(page: Int): Bitmap? {
        return pdfRendererCore.getBitmapFromCache(page)
    }

    suspend fun getLoadedBitmaps(): List<Bitmap> {
        return (0..<totalPageCount).mapNotNull { page ->
            getBitmapByPage(page)
        }
    }

    fun setZoomEnabled(zoomEnabled: Boolean) {
        isZoomEnabled = zoomEnabled
    }

    @TestOnly
    fun getZoomEnabled(): Boolean {
        return isZoomEnabled
    }

    private fun preloadCacheIntoMemory() {
        coroutineScope.launch {
            pdfRendererCore.let { renderer ->
                (0 until renderer.getPageCount()).forEach { pageNo ->
                    renderer.getBitmapFromCache(pageNo)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closePdfRender()
        coroutineScope.coroutineContext.cancelChildren()
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
                savedState.getParcelable("superState",Parcelable::class.java)
            } else {
                savedState.getParcelable("superState")
            }
            super.onRestoreInstanceState(superState)
            restoredScrollPosition = savedState.getInt("scrollPosition", positionToUseForState)
        } else {
            super.onRestoreInstanceState(savedState)
        }
    }

}

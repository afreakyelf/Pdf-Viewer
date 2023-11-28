package com.rajat.pdfviewer

import android.app.Activity
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
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
import com.rajat.pdfviewer.util.PdfEngine
import java.io.File

/**
 * Created by Rajat on 11,July,2020
 */

class PdfRendererView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), LifecycleObserver {
    lateinit var recyclerView: RecyclerView
    private lateinit var pageNo: TextView
    private lateinit var pdfRendererCore: PdfRendererCore
    private lateinit var pdfViewAdapter: PdfViewAdapter
    private var engine = PdfEngine.INTERNAL
    private var showDivider = true
    private var divider: Drawable? = null
    private var runnable = Runnable {}
    private var enableLoadingForPages: Boolean = false
    private var pdfRendererCoreInitialised = false
    private var pageMargin: Rect = Rect(0,0,0,0)
    var statusListener: StatusCallBack? = null
    private var positionToUseForState: Int = 0
    private var restoredScrollPosition: Int = NO_POSITION
    private var disableScreenshots: Boolean = false

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

    fun initWithUrl(
        url: String,
        headers: HeaderData = HeaderData(),
        lifecycleCoroutineScope: LifecycleCoroutineScope,
        lifecycle: Lifecycle
    ) {
        lifecycle.addObserver(this) // Register as LifecycleObserver
        PdfDownloader(lifecycleCoroutineScope,headers,url, object : PdfDownloader.StatusListener {
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
            override fun onDownloadSuccess(absolutePath: String) {
                initWithFile(File(absolutePath))
                statusListener?.onPdfLoadSuccess(absolutePath)
            }
            override fun onError(error: Throwable) {
                error.printStackTrace()
                statusListener?.onError(error)
            }
        })
    }

    fun initWithFile(file: File) {
        init(file)
    }

    override fun onSaveInstanceState(): Parcelable? {
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

    private fun init(file: File) {
        pdfRendererCore = PdfRendererCore(context, file)
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
            addOnScrollListener(scrollListener)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (restoredScrollPosition != NO_POSITION) {
                recyclerView.scrollToPosition(restoredScrollPosition)
                restoredScrollPosition = NO_POSITION  // Reset after applying
            }
        }, 300) // Adjust delay as needed

        runnable = Runnable {
            pageNo.visibility = View.GONE
        }
    }


    private val scrollListener = object : RecyclerView.OnScrollListener() {
        private var lastFirstVisiblePosition = NO_POSITION
        private var lastCompletelyVisiblePosition = NO_POSITION

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager

            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val firstCompletelyVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            val isPositionChanged = firstVisiblePosition != lastFirstVisiblePosition ||
                    firstCompletelyVisiblePosition != lastCompletelyVisiblePosition
            if (isPositionChanged) {
                val positionToUse = if (firstCompletelyVisiblePosition != NO_POSITION) {
                    firstCompletelyVisiblePosition
                } else {
                    firstVisiblePosition
                }
                positionToUseForState = positionToUse
                updatePageNumberDisplay(positionToUse)
                lastFirstVisiblePosition = firstVisiblePosition
                lastCompletelyVisiblePosition = firstCompletelyVisiblePosition
            }else{
                positionToUseForState = firstVisiblePosition
            }
        }

        private fun updatePageNumberDisplay(position: Int) {
            if (position != NO_POSITION) {
                pageNo.text = context.getString(R.string.pdfView_page_no, position + 1, totalPageCount)
                pageNo.visibility = View.VISIBLE
                if (position == 0) {
                    pageNo.postDelayed({ pageNo.visibility = View.GONE }, 3000)
                }
                statusListener?.onPageChanged(position, totalPageCount)
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                pageNo.postDelayed(runnable, 3000)
            } else {
                pageNo.removeCallbacks(runnable)
            }
        }
    }

    private fun getAttrs(attrs: AttributeSet?, defStyle: Int) {
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.PdfRendererView, defStyle, 0)
        setTypeArray(typedArray)
    }

    private fun setTypeArray(typedArray: TypedArray) {
        val engineValue =
            typedArray.getInt(R.styleable.PdfRendererView_pdfView_engine, PdfEngine.INTERNAL.value)
        engine = PdfEngine.values().first { it.value == engineValue }
        showDivider = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_showDivider, true)
        divider = typedArray.getDrawable(R.styleable.PdfRendererView_pdfView_divider)
        enableLoadingForPages = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_enableLoadingForPages, enableLoadingForPages)
        val marginDim = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_margin, 0)
        pageMargin = Rect(marginDim, marginDim, marginDim, marginDim).apply {
            top = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginTop, top)
            left = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginLeft, left)
            right = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginRight, right)
            bottom = typedArray.getDimensionPixelSize(R.styleable.PdfRendererView_pdfView_page_marginBottom, bottom)
        }
        disableScreenshots = typedArray.getBoolean(R.styleable.PdfRendererView_pdfView_disableScreenshots, false)
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

}
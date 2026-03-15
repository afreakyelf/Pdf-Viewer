package com.rajat.pdfviewer

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rajat.pdfviewer.databinding.ListItemPdfPageBinding
import com.rajat.pdfviewer.util.CommonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PdfViewAdapter(
    private val context: Context,
    private val renderer: PdfRendererCore,
    private val parentView: PdfRendererView,
    private val pageSpacing: Rect,
    private val enableLoadingForPages: Boolean
) : RecyclerView.Adapter<PdfViewAdapter.PdfPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder =
        PdfPageViewHolder(
            ListItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun getItemCount(): Int = renderer.getPageCount()

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: PdfPageViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelJobs()
    }

    /**
     * Re-binds all currently visible pages so they are re-rendered at the current zoom scale.
     * Call this after a zoom gesture completes to replace low-resolution cached bitmaps with
     * higher-resolution ones.
     */
    fun rebindVisiblePages(recyclerView: RecyclerView) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first >= 0 && last >= first) {
            notifyItemRangeChanged(first, last - first + 1)
        }
    }

    inner class PdfPageViewHolder(private val itemBinding: ListItemPdfPageBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        private var currentBoundPage: Int = -1
        private var hasRealBitmap: Boolean = false
        private var hasRetried: Boolean = false
        /** Monotonically increasing counter; incremented on every [bind] call so that
         *  stale render callbacks (from cancelled / replaced jobs) can be detected and
         *  ignored instead of triggering retries with outdated dimensions. */
        private var bindGeneration: Int = 0
        private val fallbackHandler = Handler(Looper.getMainLooper())
        private var scope = MainScope()

        private val DEBUG_LOGS_ENABLED = false

        fun bind(position: Int) {
            cancelJobs()
            currentBoundPage = position
            hasRealBitmap = false
            hasRetried = false
            bindGeneration++
            scope = MainScope()

            val zoomScale = parentView.getZoomScale()
            val baseWidth = itemBinding.pageView.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels
            val displayWidth = (baseWidth * zoomScale).toInt()

            itemBinding.pageView.setImageBitmap(null)

            itemBinding.root.layoutParams = itemBinding.root.layoutParams.apply {
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility =
                if (enableLoadingForPages) View.VISIBLE else View.GONE

            scope.launch {
                val cached = withContext(Dispatchers.IO) {
                    // Use the size-aware call to avoid a full disk decode when the cached
                    // resolution is smaller than what the current zoom level requires.
                    // minHeight = 1: height is not checked here because the render height is
                    // computed asynchronously inside getPageDimensionsAsync and is not yet known;
                    // for any PDF page the aspect ratio is fixed, so width alone determines
                    // whether the cached resolution is sufficient for the requested zoom.
                    renderer.getBitmapFromCacheIfAdequate(position, displayWidth, 1)
                }

                // The cached bitmap meets or exceeds the required zoom resolution.
                if (cached != null && currentBoundPage == position) {
                    if (DEBUG_LOGS_ENABLED) Log.d("PdfViewAdapter", "✅ Loaded page $position from cache")
                    itemBinding.pageView.setImageBitmap(cached)
                    hasRealBitmap = true
                    applyFadeInAnimation(itemBinding.pageView)
                    itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                    return@launch
                }

                renderer.getPageDimensionsAsync(position) { size ->
                    if (currentBoundPage != position) return@getPageDimensionsAsync

                    val aspectRatio = size.width.toFloat() / size.height.toFloat()
                    val layoutHeight = (baseWidth / aspectRatio).toInt()
                    itemBinding.updateLayoutParams(layoutHeight)

                    val renderHeight = (displayWidth / aspectRatio).toInt()
                    renderAndApplyBitmap(position, displayWidth, renderHeight)
                }
            }

            startPersistentFallbackRender(position)
        }

        private fun renderAndApplyBitmap(page: Int, width: Int, height: Int) {
            val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, maxOf(1, height))
            val expectedGeneration = bindGeneration

            renderer.renderPage(page, bitmap) { success, pageNo, rendered ->
                // *** All bitmap-lifecycle and UI decisions are made here, synchronously on the
                // Main thread (renderPage guarantees this callback fires on Dispatchers.Main).
                // We do NOT delegate to scope.launch so that bitmap recycling is never skipped
                // if the ViewHolder's coroutine scope has been cancelled. ***

                // Track ownership: set to true when the bitmap is shown to the user or already
                // recycled, so the terminal cleanup block doesn't double-recycle it.
                var bitmapConsumed = false

                // When the renderer returned a different (cached) bitmap, recycle the
                // now-unused pool-allocated one immediately.
                if (rendered != null && rendered !== bitmap) {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                    bitmapConsumed = true
                }

                // Stale callback guard: if bind() was called again since we started this
                // render (e.g., zoom rebind for the same page), this callback belongs to an
                // older generation. Just recycle — do NOT retry, because retrying with the
                // old dimensions would cancel the newer high-res render that replaced us.
                if (expectedGeneration != bindGeneration) {
                    if (!bitmapConsumed) {
                        CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                    }
                    return@renderPage
                }

                if (success && currentBoundPage == pageNo) {
                    if (DEBUG_LOGS_ENABLED) Log.d("PdfViewAdapter", "✅ Render complete for page $pageNo")
                    itemBinding.pageView.setImageBitmap(rendered ?: bitmap)
                    hasRealBitmap = true
                    applyFadeInAnimation(itemBinding.pageView)
                    itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                    bitmapConsumed = true

                    val fallbackHeight = itemBinding.pageView.height.takeIf { it > 0 }
                        ?: context.resources.displayMetrics.heightPixels
                    renderer.schedulePrefetch(pageNo, width, fallbackHeight, parentView.getScrollDirection())
                } else {
                    if (DEBUG_LOGS_ENABLED) Log.w("PdfViewAdapter", "🚫 Skipping render for page $pageNo — ViewHolder now bound to $currentBoundPage")
                    // Only retry once per bind cycle to prevent repeated retries when the
                    // renderer is temporarily busy (e.g. active render for the same page).
                    if (currentBoundPage == page && !hasRetried) {
                        hasRetried = true
                        retryRenderOnce(page, width, height)
                    }
                }

                // Terminal cleanup: recycle the pool bitmap if it was neither shown nor
                // already recycled above (covers all remaining failure / page-mismatch paths).
                if (!bitmapConsumed) {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                }
            }
        }

        private fun retryRenderOnce(page: Int, width: Int, height: Int) {
            val retryBitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
            val expectedGeneration = bindGeneration
            renderer.renderPage(page, retryBitmap) { success, retryPageNo, rendered ->
                // Synchronous on Main thread — track whether the bitmap was consumed so we
                // can recycle it in every failure path regardless of which branch is taken.
                var bitmapConsumed = false
                if (rendered != null && rendered !== retryBitmap) {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(retryBitmap)
                    bitmapConsumed = true
                }
                // Stale callback: bind() was called again since this retry was issued.
                if (expectedGeneration != bindGeneration) {
                    if (!bitmapConsumed) {
                        CommonUtils.Companion.BitmapPool.recycleBitmap(retryBitmap)
                    }
                    return@renderPage
                }
                if (success && retryPageNo == currentBoundPage && !hasRealBitmap) {
                    if (DEBUG_LOGS_ENABLED) Log.d("PdfViewAdapter", "🔁 Retry success for page $retryPageNo")
                    itemBinding.pageView.setImageBitmap(rendered ?: retryBitmap)
                    hasRealBitmap = true
                    applyFadeInAnimation(itemBinding.pageView)
                    itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                    bitmapConsumed = true
                }
                if (!bitmapConsumed) {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(retryBitmap)
                }
            }
        }

        private fun startPersistentFallbackRender(
            page: Int,
            retries: Int = 10,
            delayMs: Long = 200L
        ) {
            var attempt = 0

            lateinit var task: Runnable
            task = object : Runnable {
                override fun run() {
                    if (currentBoundPage != page || hasRealBitmap) return

                    scope.launch {
                        val cached = withContext(Dispatchers.IO) {
                            renderer.getBitmapFromCache(page)
                        }

                        if (cached != null && currentBoundPage == page) {
                            if (DEBUG_LOGS_ENABLED) Log.d("PdfViewAdapter", "🕒 Fallback applied for page $page on attempt $attempt")
                            itemBinding.pageView.setImageBitmap(cached)
                            hasRealBitmap = true
                            applyFadeInAnimation(itemBinding.pageView)
                            itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                        } else {
                            attempt++
                            if (attempt < retries) {
                                fallbackHandler.postDelayed(task, delayMs)
                            }
                        }
                    }
                }
            }

            fallbackHandler.postDelayed(task, delayMs)
        }

        private fun ListItemPdfPageBinding.updateLayoutParams(height: Int) {
            root.layoutParams = root.layoutParams.apply {
                this.height = height
                (this as? ViewGroup.MarginLayoutParams)?.setMargins(
                    pageSpacing.left, pageSpacing.top, pageSpacing.right, pageSpacing.bottom
                )
            }
        }

        fun cancelJobs() {
            scope.cancel()
        }

        private fun applyFadeInAnimation(view: View) {
            view.startAnimation(AlphaAnimation(0F, 1F).apply {
                interpolator = LinearInterpolator()
                duration = 300
            })
        }
    }
}
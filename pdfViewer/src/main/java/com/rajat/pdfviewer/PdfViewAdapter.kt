package com.rajat.pdfviewer

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.rajat.pdfviewer.databinding.ListItemPdfPageBinding
import com.rajat.pdfviewer.util.CommonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

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

    override fun onViewAttachedToWindow(holder: PdfPageViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.renderIfMissing()
    }

    override fun onViewDetachedFromWindow(holder: PdfPageViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.handleDetach()
    }

    inner class PdfPageViewHolder(private val itemBinding: ListItemPdfPageBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        private var currentBoundPage: Int = -1
        private var displayedBitmap: android.graphics.Bitmap? = null
        private var hasRetried: Boolean = false
        private var hasTriggeredFallbackRender: Boolean = false
        private var bindGeneration: Int = 0
        private val fallbackHandler = Handler(Looper.getMainLooper())
        private var scope = MainScope()

        fun bind(position: Int) {
            cancelJobs()
            currentBoundPage = position
            clearDisplayedBitmapReference()
            hasRetried = false
            hasTriggeredFallbackRender = false
            bindGeneration++
            scope = MainScope()

            val displayWidth = itemBinding.pageView.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels

            itemBinding.pageView.setImageDrawable(null)

            itemBinding.root.layoutParams = itemBinding.root.layoutParams.apply {
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility =
                if (enableLoadingForPages) View.VISIBLE else View.GONE

            scope.launch {
                val cached = withContext(Dispatchers.IO) {
                    renderer.getBitmapFromCache(position)
                }

                if (cached != null && currentBoundPage == position && applyCachedBitmap(cached, displayWidth)) {
                    return@launch
                }

                renderer.getPageDimensionsAsync(position) { size ->
                    if (currentBoundPage != position) return@getPageDimensionsAsync

                    val aspectRatio = size.width.toFloat() / size.height.toFloat()
                    val height = (displayWidth / aspectRatio).toInt()
                    itemBinding.updateLayoutParams(height)

                    renderAndApplyBitmap(position, displayWidth, height)
                }
            }

            startPersistentFallbackRender(position)
        }

        private fun renderAndApplyBitmap(page: Int, width: Int, height: Int) {
            val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, maxOf(1, height))
            val expectedGeneration = bindGeneration

            renderer.renderPage(page, bitmap) { success, pageNo, rendered ->
                var bitmapConsumed = false

                if (rendered != null && rendered !== bitmap) {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                    bitmapConsumed = true
                }

                if (success && rendered === bitmap) {
                    // PdfRendererCore caches the same bitmap instance before invoking us.
                    // Once render succeeds, the cache owns this bitmap even if this holder
                    // has gone stale, so it must not be returned to the reuse pool.
                    bitmapConsumed = true
                }

                if (expectedGeneration != bindGeneration) {
                    if (!bitmapConsumed) {
                        CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                    }
                    return@renderPage
                }

                if (success && currentBoundPage == pageNo) {
                    if (applyBitmapToView(rendered ?: bitmap, width)) {
                        bitmapConsumed = true
                    }

                    val fallbackHeight = itemBinding.pageView.height.takeIf { it > 0 }
                        ?: context.resources.displayMetrics.heightPixels

                    renderer.schedulePrefetch(
                        currentPage = pageNo,
                        width = width,
                        height = fallbackHeight,
                        direction = parentView.getScrollDirection()
                    )
                } else {
                    if (currentBoundPage == page && !hasRetried) {
                        hasRetried = true
                        retryRenderOnce(page, width, height)
                    }
                }

                if (!bitmapConsumed) {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                }
            }
        }

        private fun retryRenderOnce(page: Int, width: Int, height: Int) {
            val retryBitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
            val expectedGeneration = bindGeneration
            renderer.renderPage(page, retryBitmap) { success, retryPageNo, rendered ->
                var bitmapConsumed = false

                if (rendered != null && rendered !== retryBitmap) {
                    CommonUtils.Companion.BitmapPool.recycleBitmap(retryBitmap)
                    bitmapConsumed = true
                }

                if (success && rendered === retryBitmap) {
                    // Successful renders are already stored in cache by PdfRendererCore.
                    bitmapConsumed = true
                }

                if (expectedGeneration != bindGeneration) {
                    if (!bitmapConsumed) {
                        CommonUtils.Companion.BitmapPool.recycleBitmap(retryBitmap)
                    }
                    return@renderPage
                }

                if (success && retryPageNo == currentBoundPage && !hasLiveBitmap()) {
                    if (applyBitmapToView(rendered ?: retryBitmap, width)) {
                        bitmapConsumed = true
                    }
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
                    if (currentBoundPage != page || hasLiveBitmap()) return

                    scope.launch {
                        val cached = withContext(Dispatchers.IO) {
                            renderer.getBitmapFromCache(page)
                        }

                        if (cached != null && currentBoundPage == page && applyCachedBitmap(
                                cached,
                                itemBinding.pageView.width.takeIf { it > 0 }
                                    ?: itemView.width.takeIf { it > 0 }
                                    ?: context.resources.displayMetrics.widthPixels
                            )
                        ) {
                        } else {
                            if (!hasTriggeredFallbackRender && currentBoundPage == page && !hasLiveBitmap()) {
                                hasTriggeredFallbackRender = true
                                triggerFallbackRender(page)
                            }
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

        private fun triggerFallbackRender(page: Int) {
            val displayWidth = itemBinding.pageView.width.takeIf { it > 0 }
                ?: itemView.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels

            renderer.getPageDimensionsAsync(page) { size ->
                if (currentBoundPage != page || hasLiveBitmap()) return@getPageDimensionsAsync

                val aspectRatio = size.width.toFloat() / size.height.toFloat()
                val height = (displayWidth / aspectRatio).toInt()
                itemBinding.updateLayoutParams(height)
                renderAndApplyBitmap(page, displayWidth, height)
            }
        }

        private fun applyCachedBitmap(bitmap: android.graphics.Bitmap, displayWidth: Int): Boolean {
            return applyBitmapToView(bitmap, displayWidth)
        }

        private fun applyBitmapToView(bitmap: android.graphics.Bitmap, displayWidth: Int): Boolean {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val height = (displayWidth / aspectRatio).toInt()
            itemBinding.updateLayoutParams(height)
            itemBinding.pageView.setImageBitmap(bitmap)
            displayedBitmap = bitmap
            applyFadeInAnimation(itemBinding.pageView)
            itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
            return true
        }

        private fun ListItemPdfPageBinding.updateLayoutParams(height: Int) {
            root.layoutParams = root.layoutParams.apply {
                this.height = height
                (this as? ViewGroup.MarginLayoutParams)?.setMargins(
                    pageSpacing.left, pageSpacing.top, pageSpacing.right, pageSpacing.bottom
                )
            }
            root.requestLayout()
            pageView.requestLayout()
            runCatching { parentView.recyclerView.requestLayout() }
        }

        fun cancelJobs() {
            cancelPendingRenderIfNeeded()
            scope.cancel()
            fallbackHandler.removeCallbacksAndMessages(null)
        }

        fun renderIfMissing() {
            val page = currentBoundPage
            if (page == RecyclerView.NO_POSITION) {
                return
            }
            hasRetried = false
            hasTriggeredFallbackRender = false
            scope.cancel()
            scope = MainScope()

            val displayWidth = itemBinding.pageView.width.takeIf { it > 0 }
                ?: itemView.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels

            renderer.getPageDimensionsAsync(page) { size ->
                if (currentBoundPage != page) return@getPageDimensionsAsync

                val aspectRatio = size.width.toFloat() / size.height.toFloat()
                val height = (displayWidth / aspectRatio).toInt()
                val hasBitmap = hasLiveBitmap()
                val layoutHeight = itemBinding.root.layoutParams?.height ?: 0
                val measuredHeight = itemBinding.root.height
                val hasUsableHeight = measuredHeight > 0
                val heightMismatch = abs(layoutHeight - height) > 1 || (hasUsableHeight && abs(measuredHeight - height) > 1)

                if (!hasBitmap || !hasUsableHeight || heightMismatch) {
                    itemBinding.updateLayoutParams(height)
                }

                if (!hasBitmap) {
                    renderAndApplyBitmap(page, displayWidth, height)
                    startPersistentFallbackRender(page, retries = 3, delayMs = 150L)
                } else {
                    itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                }
            }
        }

        fun handleDetach() {
            cancelPendingRenderIfNeeded()
            scope.cancel()
            fallbackHandler.removeCallbacksAndMessages(null)
        }

        private fun cancelPendingRenderIfNeeded() {
            val page = currentBoundPage
            if (page == RecyclerView.NO_POSITION || page < 0 || hasLiveBitmap()) {
                return
            }
            renderer.cancelRender(page)
        }

        private fun clearDisplayedBitmapReference() {
            displayedBitmap = null
        }

        private fun hasLiveBitmap(): Boolean {
            val bitmap = displayedBitmap ?: return false
            return !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0
        }

        private fun applyFadeInAnimation(view: View) {
            view.startAnimation(AlphaAnimation(0F, 1F).apply {
                interpolator = LinearInterpolator()
                duration = 300
            })
        }
    }
}

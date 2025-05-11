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

    inner class PdfPageViewHolder(private val itemBinding: ListItemPdfPageBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        private var currentBoundPage: Int = -1
        private var hasRealBitmap: Boolean = false
        private val fallbackHandler = Handler(Looper.getMainLooper())
        private var scope = MainScope()

        private val DEBUG_LOGS_ENABLED = false

        fun bind(position: Int) {
            cancelJobs()
            currentBoundPage = position
            hasRealBitmap = false
            scope = MainScope()

            val displayWidth = itemBinding.pageView.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels

            itemBinding.pageView.setImageBitmap(null)

            itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility =
                if (enableLoadingForPages) View.VISIBLE else View.GONE

            scope.launch {
                val cached = withContext(Dispatchers.IO) {
                    renderer.getBitmapFromCache(position)
                }

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
                    val height = (displayWidth / aspectRatio).toInt()
                    itemBinding.updateLayoutParams(height)

                    renderAndApplyBitmap(position, displayWidth, height)
                }
            }

            startPersistentFallbackRender(position)
        }

        private fun renderAndApplyBitmap(page: Int, width: Int, height: Int) {
            val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, maxOf(1, height))

            renderer.renderPage(page, bitmap) { success, pageNo, rendered ->
                scope.launch {
                    if (success && currentBoundPage == pageNo) {
                        if (DEBUG_LOGS_ENABLED) Log.d("PdfViewAdapter", "✅ Render complete for page $pageNo")
                        itemBinding.pageView.setImageBitmap(rendered ?: bitmap)
                        hasRealBitmap = true
                        applyFadeInAnimation(itemBinding.pageView)
                        itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE

                        val fallbackHeight = itemBinding.pageView.height.takeIf { it > 0 }
                            ?: context.resources.displayMetrics.heightPixels

                        renderer.schedulePrefetch(
                            currentPage = pageNo,
                            width = width,
                            height = fallbackHeight,
                            direction = parentView.getScrollDirection()
                        )
                    } else {
                        if (DEBUG_LOGS_ENABLED) Log.w("PdfViewAdapter", "🚫 Skipping render for page $pageNo — ViewHolder now bound to $currentBoundPage")
                        CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                        retryRenderOnce(page, width, height)
                    }
                }
            }
        }

        private fun retryRenderOnce(page: Int, width: Int, height: Int) {
            val retryBitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
            renderer.renderPage(page, retryBitmap) { success, retryPageNo, rendered ->
                scope.launch {
                    if (success && retryPageNo == currentBoundPage && !hasRealBitmap) {
                        if (DEBUG_LOGS_ENABLED) Log.d("PdfViewAdapter", "🔁 Retry success for page $retryPageNo")
                        itemBinding.pageView.setImageBitmap(rendered ?: retryBitmap)
                        hasRealBitmap = true
                        applyFadeInAnimation(itemBinding.pageView)
                        itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                    } else {
                        CommonUtils.Companion.BitmapPool.recycleBitmap(retryBitmap)
                    }
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
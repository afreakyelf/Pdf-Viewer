package com.rajat.pdfviewer

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.rajat.pdfviewer.databinding.ListItemPdfPageBinding
import com.rajat.pdfviewer.util.CommonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by Rajat on 11,July,2020
 */

internal class PdfViewAdapter(
    private val context: Context,
    private val renderer: PdfRendererCore,
    private val pageSpacing: Rect,
    private val enableLoadingForPages: Boolean
) : RecyclerView.Adapter<PdfViewAdapter.PdfPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder =
        PdfPageViewHolder(ListItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = renderer.getPageCount()

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class PdfPageViewHolder(private val itemBinding: ListItemPdfPageBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(position: Int) {
            with(itemBinding) {
                pageLoadingLayout.pdfViewPageLoadingProgress.visibility = if (enableLoadingForPages) View.VISIBLE else View.GONE

                // Before we trigger rendering, explicitly ensure that cached bitmaps are used
                renderer.getBitmapFromCache(position)?.let { cachedBitmap ->
                    pageView.setImageBitmap(cachedBitmap)
                    pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                    applyFadeInAnimation(pageView)
                    return
                }

                renderer.getPageDimensionsAsync(position) { size ->
                    val width = pageView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
                    val aspectRatio = size.width.toFloat() / size.height.toFloat()
                    val height = (width / aspectRatio).toInt()

                    updateLayoutParams(height)

                    val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, maxOf(1, height))
                    renderer.renderPage(position, bitmap) { success, pageNo, renderedBitmap ->
                        if (success && pageNo == position) {
                            CoroutineScope(Dispatchers.Main).launch {
                                pageView.setImageBitmap(renderedBitmap ?: bitmap)
                                applyFadeInAnimation(pageView)
                                pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE

                                // Prefetch here
                                renderer.prefetchPagesAround(
                                    currentPage = position,
                                    width = pageView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels,
                                    height = pageView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
                                )

                            }
                        } else {
                            CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
                        }
                    }
                }
            }
        }

        private fun ListItemPdfPageBinding.updateLayoutParams(height: Int) {
            root.layoutParams = root.layoutParams.apply {
                this.height = height
                (this as? ViewGroup.MarginLayoutParams)?.setMargins(
                    pageSpacing.left, pageSpacing.top, pageSpacing.right, pageSpacing.bottom
                )
            }
        }

        private fun applyFadeInAnimation(view: View) {
            view.startAnimation(AlphaAnimation(0F, 1F).apply {
                interpolator = LinearInterpolator()
                duration = 300
            })
        }

        private fun handleLoadingForPage(position: Int) {
            itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility =
                if (enableLoadingForPages && !renderer.pageExistInCache(position)) View.VISIBLE else View.GONE
        }
    }
}


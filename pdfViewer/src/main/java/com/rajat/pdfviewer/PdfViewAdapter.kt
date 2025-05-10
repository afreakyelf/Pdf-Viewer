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
import kotlinx.coroutines.withContext

internal class PdfViewAdapter(
    private val context: Context,
    private val renderer: PdfRendererCore,
    private val parentView: PdfRendererView,
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
            val width = itemBinding.pageView.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels

            CoroutineScope(Dispatchers.Main).launch {
                itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility =
                    if (enableLoadingForPages) View.VISIBLE else View.GONE

                val cached = withContext(Dispatchers.IO) {
                    renderer.getBitmapFromCache(position)
                }

                if (cached != null) {
                    itemBinding.pageView.setImageBitmap(cached)
                    itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
                    applyFadeInAnimation(itemBinding.pageView)
                    return@launch
                }

                renderer.getPageDimensionsAsync(position) { size ->
                    val aspectRatio = size.width.toFloat() / size.height.toFloat()
                    val height = (width / aspectRatio).toInt()

                    itemBinding.updateLayoutParams(height)

                    val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, maxOf(1, height))
                    renderer.renderPage(position, bitmap) { success, pageNo, renderedBitmap ->
                        if (success && pageNo == position) {
                            CoroutineScope(Dispatchers.Main).launch {
                                itemBinding.pageView.setImageBitmap(renderedBitmap ?: bitmap)
                                applyFadeInAnimation(itemBinding.pageView)
                                itemBinding.pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE

                                // adaptive directional prefetch
                                val direction = parentView.getScrollDirection()
                                val fallbackHeight = itemBinding.pageView.height.takeIf { it > 0 }
                                    ?: context.resources.displayMetrics.heightPixels

                                renderer.schedulePrefetch(
                                    currentPage = position,
                                    width = width,
                                    height = fallbackHeight,
                                    direction = direction
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
    }
}

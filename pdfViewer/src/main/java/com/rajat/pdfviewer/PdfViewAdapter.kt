package com.rajat.pdfviewer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.rajat.pdfviewer.databinding.ListItemPdfPageBinding
import com.rajat.pdfviewer.util.CommonUtils
import com.rajat.pdfviewer.util.hide
import com.rajat.pdfviewer.util.show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * Created by Rajat on 11,July,2020
 */

//internal class PdfViewAdapter1(
//    private val context: Context,
//    private val renderer: PdfRendererCore,
//    private val pageSpacing: Rect,
//    private val enableLoadingForPages: Boolean
//) :
//    RecyclerView.Adapter<PdfViewAdapter1.PdfPageViewHolder>() {
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
//        return PdfPageViewHolder(
//            ListItemPdfPageBinding.inflate(
//                LayoutInflater.from(parent.context),
//                parent,
//                false
//            )
//        )
//    }
//
//    override fun getItemCount(): Int {
//        return renderer.getPageCount()
//    }
//
//    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
//        holder.bind(position)
//    }
//
//    inner class PdfPageViewHolder(private val itemBinding: ListItemPdfPageBinding) : RecyclerView.ViewHolder(itemBinding.root) {
//        fun bind(position: Int) {
//            with(itemBinding) {
//                handleLoadingForPage(position)
//                if (pageView.width == 0 || pageView.height == 0) {
//                    pageView.post { bind(position) }  // Postpone if layout not ready
//                    return
//                }
//                val width = pageView.width
//                val height = calculateBitmapHeight(width, position)
//                val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
//
//                val itemHeight = calculateBitmapHeight(itemBinding.root.width, position)
//                val layoutParams = itemBinding.root.layoutParams as ViewGroup.MarginLayoutParams
//                Log.i("Item height","$width-$height-$itemHeight-${layoutParams.height}")
//
//                layoutParams.height = itemHeight
//                layoutParams.setMargins(
//                    pageSpacing.left,
//                    pageSpacing.top,
//                    pageSpacing.right,
//                    pageSpacing.bottom
//                )
//                itemBinding.root.layoutParams = layoutParams
//                Log.d("PdfViewAdapter", "BEFORE    Bitmap Width: $width, Device Width: ${context.resources.displayMetrics.widthPixels}")
//
//                renderer.renderPage(position, bitmap) { success, pageNo, renderedBitmap ->
//                    if (success && pageNo == position) {
//                        CoroutineScope(Dispatchers.Main).launch {
//                            itemBinding.pageView.scaleType = ImageView.ScaleType.FIT_CENTER
//                            renderedBitmap?.let {
//                                Log.d("PdfViewAdapter", "renderedBitmap Width: ${it.width}, Bitmap Height: ${it.height}")
//                            }
//                            bitmap?.let {
//                                Log.d("PdfViewAdapter", "Bitmap Width: ${it.width}, Bitmap Height: ${it.height}")
//                            }
//
//                            itemBinding.pageView.apply {
//                                setImageBitmap(renderedBitmap ?: bitmap)
//                            }
//                            applyFadeInAnimation(pageView)
//                            pageLoadingLayout.pdfViewPageLoadingProgress.hide()
//                        }
//                        // Prefetch pages after rendering the current page
//                        renderer.prefetchPages(position, width, height)
//                    } else {
//                        CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
//                    }
//                }
//            }
//        }
//
//        private fun calculateBitmapHeight(width: Int, position: Int): Int {
//            // Get the actual dimensions of the PDF page
//            val pageDimensions = renderer.getPageDimensions(position)
//            // Calculate the aspect ratio of the PDF page
//            val aspectRatio = pageDimensions.width.toFloat() / pageDimensions.height.toFloat()
//            // Calculate the height based on the width of the ImageView and the aspect ratio
//            return (width / aspectRatio).toInt()
//        }
//
//        private fun applyFadeInAnimation(view: View) {
//            view.animation = AlphaAnimation(0F, 1F).apply {
//                interpolator = LinearInterpolator()
//                duration = 300
//                start()
//            }
//        }
//
//        private fun handleLoadingForPage(position: Int) {
//            with(itemBinding) {
//                if (!enableLoadingForPages || renderer.pageExistInCache(position)) {
//                    pageLoadingLayout.pdfViewPageLoadingProgress.hide()
//                } else {
//                    pageLoadingLayout.pdfViewPageLoadingProgress.show()
//                }
//            }
//        }
//    }
//
//}
//
//internal class PdfViewAdapter2(
//    private val context: Context,
//    private val renderer: PdfRendererCore,
//    private val pageSpacing: Rect,
//    private val enableLoadingForPages: Boolean
//) : RecyclerView.Adapter<PdfViewAdapter2.PdfPageViewHolder>() {
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder =
//        PdfPageViewHolder(ListItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
//
//    override fun getItemCount(): Int = renderer.getPageCount()
//
//    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
//        holder.bind(position)
//    }
//
//    inner class PdfPageViewHolder(private val itemBinding: ListItemPdfPageBinding) : RecyclerView.ViewHolder(itemBinding.root) {
//        fun bind(position: Int) {
//            with(itemBinding) {
//                handleLoadingForPage(position)
//                if (pageView.width == 0 || pageView.height == 0) {
//                    pageView.post { bind(position) }  // Postpone if layout not ready
//                    return
//                }
//
//                val pageDimensions = renderer.getPageDimensions(position)
//                val aspectRatio = pageDimensions.width.toFloat() / pageDimensions.height.toFloat()
//                val width = pageView.width
//                val height = (width / aspectRatio).toInt()
//
//                val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
//                updateLayoutParams(height)
//
//                renderer.renderPage(position, bitmap) { success, pageNo, renderedBitmap ->
//                    if (success && pageNo == position) {
//                        CoroutineScope(Dispatchers.Main).launch {
//                            itemBinding.pageView.setImageBitmap(renderedBitmap ?: bitmap)
//                            applyFadeInAnimation(pageView)
//                            pageLoadingLayout.pdfViewPageLoadingProgress.hide()
//                        }
//                    } else {
//                        CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
//                    }
//                }
//            }
//        }
//
//        private fun ListItemPdfPageBinding.updateLayoutParams(height: Int) {
//            root.layoutParams = root.layoutParams.apply {
//                this.height = height
//                if (this is ViewGroup.MarginLayoutParams) {
//                    setMargins(pageSpacing.left, pageSpacing.top, pageSpacing.right, pageSpacing.bottom)
//                }
//            }
//        }
//
//        private fun applyFadeInAnimation(view: View) {
//            view.startAnimation(AlphaAnimation(0F, 1F).apply {
//                interpolator = LinearInterpolator()
//                duration = 300
//            })
//        }
//
//        private fun handleLoadingForPage(position: Int) {
//            with(itemBinding) {
//                pageLoadingLayout.pdfViewPageLoadingProgress.visibility =
//                    if (enableLoadingForPages && !renderer.pageExistInCache(position)) View.VISIBLE else View.GONE
//            }
//        }
//    }
//}

//
//internal class PdfViewAdapter3(
//    private val context: Context,
//    private val renderer: PdfRendererCore,
//    private val pageSpacing: Rect,
//    private val enableLoadingForPages: Boolean
//) : RecyclerView.Adapter<PdfViewAdapter3.PdfPageViewHolder>() {
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder =
//        PdfPageViewHolder(ListItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
//
//    override fun getItemCount(): Int = renderer.getPageCount()
//
//    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
//        holder.bind(position)
//    }
//
//    inner class PdfPageViewHolder(private val itemBinding: ListItemPdfPageBinding) : RecyclerView.ViewHolder(itemBinding.root) {
//        fun bind(position: Int) {
//            with(itemBinding) {
//                // Load a placeholder or progress bar while the actual content is being prepared.
//                pageLoadingLayout.pdfViewPageLoadingProgress.visibility = if (enableLoadingForPages) View.VISIBLE else View.GONE
//
//                // Calculate the required bitmap dimensions only once and reuse if already calculated.
//                val dimensions = renderer.getPageDimensionsAsync(position) { width, height ->
//                    val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
//                    updateLayoutParams(height)
//
//                    renderer.renderPage(position, bitmap) { success, pageNo, renderedBitmap ->
//                        if (success && pageNo == position) {
//                            CoroutineScope(Dispatchers.Main).launch {
//                                pageView.setImageBitmap(renderedBitmap ?: bitmap)
//                                applyFadeInAnimation(pageView)
//                                pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
//                            }
//                        } else {
//                            CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
//                        }
//                    }
//                }
//            }
//        }
//
//        private fun ListItemPdfPageBinding.updateLayoutParams(height: Int) {
//            root.layoutParams = root.layoutParams.apply {
//                this.height = height
//                if (this is ViewGroup.MarginLayoutParams) {
//                    setMargins(pageSpacing.left, pageSpacing.top, pageSpacing.right, pageSpacing.bottom)
//                }
//            }
//        }
//
//        private fun applyFadeInAnimation(view: View) {
//            view.startAnimation(AlphaAnimation(0F, 1F).apply {
//                interpolator = LinearInterpolator()
//                duration = 300
//            })
//        }
//    }
//}


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
//        fun bind(position: Int) {
////            with(itemBinding) {
////                handleLoadingForPage(position)
////                if (pageView.width == 0 || pageView.height == 0) {
////                    pageView.post { bind(position) }  // Delay the binding if the layout isn't ready.
////                    return
////                }
////
////                val pageDimensions = renderer.getPageDimensions(position)
////                val aspectRatio = pageDimensions.width.toFloat() / pageDimensions.height.toFloat()
////                val width = pageView.width
////                val height = (width / aspectRatio).toInt()
////
////                // Use cached or dynamically calculated height here as per your original logic.
////                val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
////                updateLayoutParams(height)
////
////                renderer.renderPage(position, bitmap) { success, pageNo, renderedBitmap ->
////                    if (success && pageNo == position) {
////                        CoroutineScope(Dispatchers.Main).launch {
////                            itemBinding.pageView.setImageBitmap(renderedBitmap ?: bitmap)
////                            applyFadeInAnimation(pageView)
////                            pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
////                        }
////                    } else {
////                        CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
////                    }
////                }
////            }
//
//            with(itemBinding) {
//                // Show a placeholder or loading indicator
//                pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.VISIBLE
//
//                // Fetch dimensions asynchronously
//                renderer.getPageDimensionsAsync(position) { size ->
//                    val aspectRatio = size.width.toFloat() / size.height.toFloat()
//                    val width = pageView.width
//                    val height = (width / aspectRatio).toInt()
//
//                    // Update layout params based on the actual page size
//                    updateLayoutParams(height)
//
//                    // Now load the actual page
//                    val bitmap = CommonUtils.Companion.BitmapPool.getBitmap(width, height)
//                    renderer.renderPage(position, bitmap) { success, pageNo, renderedBitmap ->
//                        if (success && pageNo == position) {
//                            pageView.setImageBitmap(renderedBitmap ?: bitmap)
//                            applyFadeInAnimation(pageView)
//                            pageLoadingLayout.pdfViewPageLoadingProgress.visibility = View.GONE
//                        } else {
//                            CommonUtils.Companion.BitmapPool.recycleBitmap(bitmap)
//                        }
//                    }
//                }
//            }
//
//        }
//
//        private fun ListItemPdfPageBinding.updateLayoutParams(height: Int) {
//            root.layoutParams.height = height
//            (root.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
//                pageSpacing.left, pageSpacing.top, pageSpacing.right, pageSpacing.bottom
//            )
//        }


        fun bind(position: Int) {
            with(itemBinding) {
                pageLoadingLayout.pdfViewPageLoadingProgress.visibility = if (enableLoadingForPages) View.VISIBLE else View.GONE

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


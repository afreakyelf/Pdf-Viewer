package com.rajat.pdfviewer

import android.view.View
import android.graphics.Rect
import android.view.ViewGroup
import android.graphics.Bitmap
import android.view.LayoutInflater
import com.rajat.pdfviewer.util.hide
import com.rajat.pdfviewer.util.show
import android.view.animation.AlphaAnimation
import androidx.core.view.updateLayoutParams
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.list_item_pdf_page.view.*
import kotlinx.android.synthetic.main.pdf_view_page_loading_layout.view.*

/**
 * Created by Rajat on 11,July,2020
 */

internal class PdfViewAdapter(
    private val renderer: PdfRendererCore,
    private val pageSpacing: Rect,
    private val enableLoadingForPages: Boolean
) :
    RecyclerView.Adapter<PdfViewAdapter.PdfPageViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_pdf_page, parent, false)
        return PdfPageViewHolder(v)
    }

    override fun getItemCount(): Int {
        return renderer.getPageCount()
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class PdfPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(position: Int) {
            with(itemView) {
                handleLoadingForPage(position)

                pageView.setImageBitmap(null)
                renderer.renderPage(position) { bitmap: Bitmap?, pageNo: Int ->
                    if (pageNo != position)
                        return@renderPage
                    bitmap?.let {
                        container_view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            height =
                                (container_view.width.toFloat() / ((bitmap.width.toFloat() / bitmap.height.toFloat()))).toInt()
                            this.topMargin = pageSpacing.top
                            this.leftMargin = pageSpacing.left
                            this.rightMargin = pageSpacing.right
                            this.bottomMargin = pageSpacing.bottom
                        }
                        pageView.setImageBitmap(bitmap)
                        pageView.animation = AlphaAnimation(0F, 1F).apply {
                            interpolator = LinearInterpolator()
                            duration = 300
                        }

                        pdf_view_page_loading_progress.hide()
                    }
                }
            }
        }

        private fun View.handleLoadingForPage(position: Int) {
            if (!enableLoadingForPages) {
                pdf_view_page_loading_progress.hide()
                return
            }

            if (renderer.pageExistInCache(position)) {
                pdf_view_page_loading_progress.hide()
            } else {
                pdf_view_page_loading_progress.show()
            }
        }
    }
}
package com.rajat.pdfviewer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.list_item_pdf_page.view.*

/**
 * Created by Rajat on 11,July,2020
 */

internal class PdfViewAdapter(private val renderer: PdfRendererCore) :
    RecyclerView.Adapter<PdfViewAdapter.PdfPageViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_pdf_page, parent, false)
        return PdfPageViewHolder(v)
    }

    override fun getItemCount(): Int {
        return renderer.getPageCount()
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind()
    }

    inner class PdfPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            with(itemView) {
                pageView.setImageBitmap(null)
                renderer.renderPage(adapterPosition) { bitmap: Bitmap?, pageNo: Int ->
                    if (pageNo != adapterPosition)
                        return@renderPage
                    bitmap?.let {
                        pageView.layoutParams = pageView.layoutParams.apply {
                            height =
                                (pageView.width.toFloat() / ((bitmap.width.toFloat() / bitmap.height.toFloat()))).toInt()
                        }
                        pageView.setImageBitmap(bitmap)
                        pageView.animation = AlphaAnimation(0F, 1F).apply {
                            interpolator = LinearInterpolator()
                            duration = 300
                        }
                    }
                }
            }
        }
    }
}
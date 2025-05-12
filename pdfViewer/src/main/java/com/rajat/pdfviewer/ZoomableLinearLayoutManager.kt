package com.rajat.pdfviewer

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ZoomableLinearLayoutManager(
    context: Context,
    private val scaleFactorProvider: () -> Float
) : LinearLayoutManager(context, VERTICAL, false) {

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        val scaleFactor = scaleFactorProvider()
        val adjustedDy = (dy / scaleFactor).toInt()
        return super.scrollVerticallyBy(adjustedDy, recycler, state)
    }
}
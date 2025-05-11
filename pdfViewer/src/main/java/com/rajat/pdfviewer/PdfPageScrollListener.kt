package com.rajat.pdfviewer

import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class PdfPageScrollListener(
    private val pageNoTextView: TextView,
    private val totalPageCount: () -> Int,
    private val updatePage: (Int) -> Unit,
    private val schedulePrefetch: (Int) -> Unit
) : RecyclerView.OnScrollListener() {

    private var lastDisplayedPage = RecyclerView.NO_POSITION
    private var lastScrollDirection = 0
    private val hideRunnable = Runnable {
        if (pageNoTextView.isVisible) pageNoTextView.visibility = TextView.GONE
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val firstComplete = layoutManager.findFirstCompletelyVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val lastComplete = layoutManager.findLastCompletelyVisibleItemPosition()

        val direction = when {
            dy > 0 -> 1
            dy < 0 -> -1
            else -> lastScrollDirection
        }
        lastScrollDirection = direction

        val pageToShow = when (direction) {
            1 -> lastComplete.takeIf { it != RecyclerView.NO_POSITION }
                ?: lastVisible.takeIf { it != RecyclerView.NO_POSITION }
                ?: firstVisible
            -1 -> firstComplete.takeIf { it != RecyclerView.NO_POSITION }
                ?: firstVisible.takeIf { it != RecyclerView.NO_POSITION }
                ?: lastVisible
            else -> firstVisible
        }

        if (pageToShow != lastDisplayedPage && pageToShow != RecyclerView.NO_POSITION) {
            updatePage(pageToShow)
            pageNoTextView.text = pageNoTextView.context.getString(
                R.string.pdfView_page_no, pageToShow + 1, totalPageCount()
            )
            pageNoTextView.visibility = TextView.VISIBLE

            pageNoTextView.removeCallbacks(hideRunnable)
            pageNoTextView.postDelayed(hideRunnable, 3000)

            lastDisplayedPage = pageToShow
        }
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            pageNoTextView.removeCallbacks(hideRunnable)
            pageNoTextView.postDelayed(hideRunnable, 3000)

            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val first = layoutManager.findFirstVisibleItemPosition()
            val last = layoutManager.findLastVisibleItemPosition()
            val middle = (first + last) / 2
            schedulePrefetch(middle)
        } else {
            pageNoTextView.removeCallbacks(hideRunnable)
        }
    }
}

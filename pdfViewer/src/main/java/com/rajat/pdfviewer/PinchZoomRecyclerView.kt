package com.rajat.pdfviewer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PinchZoomRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    // Touch tracking for gesture state
    private var activePointerId = INVALID_POINTER_ID
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // Zoom and pan state
    private var scaleFactor = 1f
    private var isZoomEnabled = true
    private var maxZoom = MAX_ZOOM
    private var zoomDuration = ZOOM_DURATION
    private var isZoomingInProgress = false

    // Panning offsets and touch memory
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var posX = 0f
    private var posY = 0f

    init {
        setWillNotDraw(false)
    }

    fun setZoomEnabled(enabled: Boolean) {
        isZoomEnabled = enabled
    }

    /**
     * Handles touch interactions — zoom, pan, and scroll.
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isZoomEnabled) return super.onTouchEvent(ev)

        gestureDetector.onTouchEvent(ev)
        scaleDetector.onTouchEvent(ev)

        if (isZoomingInProgress) {
            return true // Block RecyclerView scroll during zoom
        }

        val superHandled = super.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && scaleFactor > 1f) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = ev.getX(pointerIndex)
                        val y = ev.getY(pointerIndex)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        posX += dx
                        posY += dy
                        clampPosition()
                        invalidate()

                        lastTouchX = x
                        lastTouchY = y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = ev.getX(newPointerIndex)
                    lastTouchY = ev.getY(newPointerIndex)
                    activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
            }
        }

        return superHandled || scaleFactor > 1f
    }

    /**
     * Intercepts vertical scroll only during pinch-to-zoom.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return if (isZoomingInProgress) true else super.onInterceptTouchEvent(ev)
    }

    /**
     * Transforms canvas for zoom + pan before drawing children.
     */
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(posX, posY)
        canvas.scale(scaleFactor, scaleFactor)
        super.onDraw(canvas)
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(posX, posY)
        canvas.scale(scaleFactor, scaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    /**
     * Allow vertical scroll only when zoomed in.
     */
    override fun canScrollVertically(direction: Int): Boolean {
        return scaleFactor > 1f && super.canScrollVertically(direction)
    }

    /**
     * Corrects scrollbar offset for zoom state.
     */
    override fun computeVerticalScrollOffset(): Int {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return 0
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val firstView = layoutManager.findViewByPosition(firstVisible) ?: return 0

        val scrolledPast = -layoutManager.getDecoratedTop(firstView)
        val itemHeight = firstView.height.takeIf { it > 0 } ?: height
        val offset = (firstVisible * itemHeight + scrolledPast)

        return (offset * scaleFactor).toInt()
    }

    /**
     * Corrects scrollbar range for zoom state.
     */
    override fun computeVerticalScrollRange(): Int {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return height
        val itemCount = adapter?.itemCount ?: return height

        val visibleHeights = (0 until layoutManager.childCount).mapNotNull {
            layoutManager.getChildAt(it)?.height
        }

        val averageHeight = visibleHeights.average().takeIf { it > 0 } ?: height.toDouble()
        return (averageHeight * itemCount * scaleFactor).toInt()
    }

    /**
     * Handles pinch-to-zoom scaling with focal-point centering.
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isZoomingInProgress = true
            suppressLayout(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactorChange = detector.scaleFactor
            if (scaleFactorChange in 0.98f..1.02f) return true // ignore micro-changes

            val newScale = (scaleFactor * scaleFactorChange).coerceIn(1f, maxZoom)
            if (newScale != scaleFactor) {
                val focusXInContent = (detector.focusX - posX) / scaleFactor
                val focusYInContent = (detector.focusY - posY) / scaleFactor

                scaleFactor = newScale

                posX = detector.focusX - focusXInContent * scaleFactor
                posY = detector.focusY - focusYInContent * scaleFactor

                clampPosition()
                invalidate()
                awakenScrollBars()
            }

            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isZoomingInProgress = false
            suppressLayout(false)
        }


    }

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        return if (scaleFactor > 1f) {
            // Only allow vertical fling when zoomed in
            super.fling(0, velocityY)
        } else {
            super.fling(velocityX, velocityY)
        }
    }


    /**
     * Clamps the panning translation to avoid over-scrolling beyond the content bounds.
     */
    private fun clampPosition() {
        val contentWidth = width * scaleFactor
        val contentHeight = height * scaleFactor

        val maxPosX = if (contentWidth > width) contentWidth - width else 0f
        val maxPosY = if (contentHeight > height) contentHeight - height else 0f

        posX = posX.coerceIn(-maxPosX, maxPosX)
        posY = posY.coerceIn(-maxPosY, maxPosY)
    }

    /**
     * GestureListener handles double-tap zoom.
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!isZoomEnabled) return false

            // Cycle through zoom levels based on current scale.
            val targetScale = when {
                scaleFactor < 1.5f -> 1.5f
                scaleFactor < maxZoom -> maxZoom
                else -> 1f
            }
            zoomTo(targetScale, e.x, e.y, zoomDuration)
            return true
        }

        /**
         * Animates a zoom operation centered on the provided focus point.
         */
        private fun zoomTo(targetScale: Float, focusX: Float, focusY: Float, duration: Long) {
            val startScale = scaleFactor
            val focusXInContent = (focusX - posX) / scaleFactor
            val focusYInContent = (focusY - posY) / scaleFactor

            ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    val scale = startScale + (targetScale - startScale) * fraction
                    scaleFactor = scale

                    posX = focusX - focusXInContent * scaleFactor
                    posY = focusY - focusYInContent * scaleFactor

                    clampPosition()
                    invalidate()
                    awakenScrollBars()
                }
                start()
            }
        }
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
        private const val MAX_ZOOM = 3.0f
        private const val ZOOM_DURATION = 300L
    }
}

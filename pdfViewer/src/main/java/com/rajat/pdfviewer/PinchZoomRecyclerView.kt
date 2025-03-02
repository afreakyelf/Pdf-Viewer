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

    // Active pointer for panning
    private var activePointerId = INVALID_POINTER_ID

    // Gesture detectors for scaling and double tap
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // State values for zooming and panning
    private var scaleFactor = 1f
    private var isZoomEnabled = true
    private var maxZoom = MAX_ZOOM
    private var zoomDuration = ZOOM_DURATION

    // View dimensions and translation values
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var posX = 0f
    private var posY = 0f

    fun setZoomEnabled(enabled: Boolean) {
        isZoomEnabled = enabled
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = measuredWidth.toFloat()
        viewHeight = measuredHeight.toFloat()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isZoomEnabled) return super.onTouchEvent(ev)

        // Let the default handling occur first.
        val superHandled = super.onTouchEvent(ev)

        // Process our custom gesture detectors.
        gestureDetector.onTouchEvent(ev)
        scaleDetector.onTouchEvent(ev)

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
            MotionEvent.ACTION_SCROLL -> {
                val dy = ev.getAxisValue(MotionEvent.AXIS_VSCROLL) * scaleFactor
                posY += dy
                clampPosition()
                invalidate()
            }
        }
        return superHandled || scaleFactor > 1f
    }

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

    override fun canScrollVertically(direction: Int): Boolean {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return false
        return when (direction) {
            1 -> layoutManager.findLastVisibleItemPosition() < (adapter?.itemCount ?: 0) - 1 // Check if last item is visible
            -1 -> layoutManager.findFirstVisibleItemPosition() > 0 // Check if first item is visible
            else -> false
        }
    }

    override fun computeVerticalScrollRange(): Int {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return height
        val itemCount = adapter?.itemCount ?: 0

        // Get the last visible item position
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        val lastVisibleView = layoutManager.findViewByPosition(lastVisibleItem)

        return if (lastVisibleView != null) {
            val lastItemBottom = lastVisibleView.bottom
            val totalHeight = lastItemBottom + (height * (itemCount - lastVisibleItem - 1))

            (totalHeight * scaleFactor).toInt()
        } else {
            (height * itemCount * scaleFactor).toInt()
        }
    }

    override fun computeVerticalScrollOffset(): Int {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return 0
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItem) ?: return 0
        val offset = -firstVisibleView.top  // Distance scrolled from the top of the first item

        // Consider zooming effect
        return (offset * scaleFactor).toInt()
    }


    /**
     * ScaleListener ensures that the zoom is centered around the gesture’s focal point.
     */
        private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isZoomEnabled) return false // Prevent scaling

                val newScale = (scaleFactor * detector.scaleFactor).coerceIn(1f, maxZoom)
                if (newScale != scaleFactor) {
                    val scaleDelta = newScale / scaleFactor

                    // Get the gesture's focal point
                    val focusX = detector.focusX
                    val focusY = detector.focusY

                    // Adjust position so that the gesture's focal point remains fixed
                    posX -= (focusX - posX) * (scaleDelta - 1)
                    posY -= (focusY - posY) * (scaleDelta - 1)

                    scaleFactor = newScale
                    clampPosition()
                    invalidate()
                }
                return true
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
            val startPosX = posX
            val startPosY = posY

            ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    val scale = startScale + (targetScale - startScale) * fraction
                    val scaleDelta = scale / startScale

                    // Update translation to keep the focus point fixed.
                    posX = startPosX - (focusX - startPosX) * (scaleDelta - 1)
                    posY = startPosY - (focusY - startPosY) * (scaleDelta - 1)
                    scaleFactor = scale

                    clampPosition()
                    invalidate()
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
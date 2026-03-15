package com.rajat.pdfviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

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
    private var ignoreScaleAfterPointerUp = false
    private var pinchStartScale = 1f
    private var pinchStartSpan = 1f
    private var pinchStartFocusX = 0f
    private var pinchStartFocusY = 0f
    private var pinchContentFocusX = 0f
    private var pinchContentFocusY = 0f
    private var multiPageScrollResidualY = 0f
    private var blockPanUntilNextDown = false

    private var zoomChangeListener: ((Boolean, Float) -> Unit)? = null
    private var zoomEndListener: ((Float) -> Unit)? = null


    init {
        setWillNotDraw(false)
        layoutManager = ZoomableLinearLayoutManager(context) { getZoomScale() }
    }

    fun setZoomEnabled(enabled: Boolean) {
        isZoomEnabled = enabled
    }

    fun isZoomedIn(): Boolean = scaleFactor > 1f

    fun getZoomScale(): Float = scaleFactor

    fun getMaxZoomScale(): Float = maxZoom

    fun setMaxZoomScale(maxZoomScale: Float) {
        maxZoom = maxZoomScale.coerceIn(1f, HARD_MAX_ZOOM)
        if (scaleFactor > maxZoom) {
            scaleFactor = maxZoom
            clampPosition()
            invalidate()
            zoomChangeListener?.invoke(isZoomedIn(), scaleFactor)
        }
    }

    fun setOnZoomChangeListener(listener: (isZoomedIn: Boolean, scale: Float) -> Unit) {
        zoomChangeListener = listener
    }

    fun setOnZoomEndListener(listener: (scale: Float) -> Unit) {
        zoomEndListener = listener
    }

    /**
     * Handles touch interactions — zoom, pan, and scroll.
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isZoomEnabled) return super.onTouchEvent(ev)

        if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            ignoreScaleAfterPointerUp = true
        }

        gestureDetector.onTouchEvent(ev)
        scaleDetector.onTouchEvent(ev)

        if (isZoomingInProgress) {
            return true // Block RecyclerView scroll during zoom
        }

        val superHandled = if (scaleFactor <= 1f) super.onTouchEvent(ev) else false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isVerticallyScrollable() || scaleFactor > 1f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                multiPageScrollResidualY = 0f
                blockPanUntilNextDown = false
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)
            }

            MotionEvent.ACTION_MOVE -> {
                if (isVerticallyScrollable() || scaleFactor > 1f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (!scaleDetector.isInProgress && scaleFactor > 1f) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = ev.getX(pointerIndex)
                        val y = ev.getY(pointerIndex)
                        if (blockPanUntilNextDown) {
                            lastTouchX = x
                            lastTouchY = y
                            return superHandled || scaleFactor > 1f
                        }
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        posX += dx
                        if (isSinglePage()) {
                            posY += dy
                        } else {
                            applyMultiPageVisualOffsetDelta(-dy)
                        }
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
                blockPanUntilNextDown = true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                activePointerId = INVALID_POINTER_ID
                ignoreScaleAfterPointerUp = false
                multiPageScrollResidualY = 0f
                blockPanUntilNextDown = false
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                activePointerId = INVALID_POINTER_ID
                ignoreScaleAfterPointerUp = false
                multiPageScrollResidualY = 0f
                blockPanUntilNextDown = false
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
        canvas.withTranslation(posX, posY) {
            scale(scaleFactor, scaleFactor)
            super.onDraw(this)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withTranslation(posX, posY) {
            scale(scaleFactor, scaleFactor)
            super.dispatchDraw(this)
        }
    }

    /**
     * Report vertical scrollability whenever the underlying document can move.
     * Nested-scroll parents such as Compose bottom sheets rely on this signal to
     * decide whether the PDF view should consume drag gestures.
     */
    override fun canScrollVertically(direction: Int): Boolean {
        if (scaleFactor > 1f) {
            if (!isSinglePage()) {
                val hasBottomOverflowRoom = posY > -(height * (scaleFactor - 1f)).coerceAtLeast(0f)
                return when {
                    direction > 0 -> super.canScrollVertically(direction) || hasBottomOverflowRoom
                    direction < 0 -> super.canScrollVertically(direction) || posY < 0f
                    else -> super.canScrollVertically(1) || super.canScrollVertically(-1) || posY != 0f
                }
            }
            return super.canScrollVertically(direction)
        }
        return !isSinglePage() && super.canScrollVertically(direction)
    }

    /**
     * Corrects scrollbar offset for zoom state.
     */
    override fun computeVerticalScrollOffset(): Int {
        return (getBaseScrollOffset() * scaleFactor - posY).roundToInt()
    }

    /**
     * Corrects scrollbar range for zoom state.
     */
    override fun computeVerticalScrollRange(): Int {
        return (super.computeVerticalScrollRange() * scaleFactor).roundToInt()
    }

    /**
     * Handles pinch-to-zoom scaling with focal-point centering.
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isZoomingInProgress = true
            ignoreScaleAfterPointerUp = false
            pinchStartScale = scaleFactor
            pinchStartSpan = detector.currentSpan.takeIf { it > 0f } ?: 1f
            pinchStartFocusX = detector.focusX
            pinchStartFocusY = detector.focusY
            multiPageScrollResidualY = 0f
            pinchContentFocusX = (pinchStartFocusX - posX) / scaleFactor
            pinchContentFocusY = if (isSinglePage()) {
                (pinchStartFocusY - posY) / scaleFactor
            } else {
                (getCurrentMultiPageVisualOffset() + pinchStartFocusY) / scaleFactor
            }
            suppressLayout(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (ignoreScaleAfterPointerUp) return true
            val spanRatio = detector.currentSpan / pinchStartSpan
            if (spanRatio in 0.98f..1.02f) return true // ignore micro-changes

            val newScale = (pinchStartScale * spanRatio).coerceIn(1f, maxZoom)
            if (newScale != scaleFactor) {
                scaleFactor = newScale

                posX = pinchStartFocusX - pinchContentFocusX * scaleFactor
                if (isSinglePage()) {
                    posY = pinchStartFocusY - pinchContentFocusY * scaleFactor
                } else {
                    val desiredVisualOffset = pinchContentFocusY * scaleFactor - pinchStartFocusY
                    val visualDelta = desiredVisualOffset - getCurrentMultiPageVisualOffset()
                    applyMultiPageVisualOffsetDelta(visualDelta)
                }

                clampPosition()
                invalidate()
                awakenScrollBars()

                zoomChangeListener?.invoke(isZoomedIn(), scaleFactor)
            }

            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isZoomingInProgress = false
            ignoreScaleAfterPointerUp = false
            suppressLayout(false)
            zoomEndListener?.invoke(scaleFactor)
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
        // horizontal clamp (unchanged)
        val contentWidth = width * scaleFactor
        posX = posX.coerceIn(-(contentWidth - width).coerceAtLeast(0f), 0f)

        if (!isSinglePage()) {
            val maxBottomOverflow = (height * (scaleFactor - 1f)).coerceAtLeast(0f)
            posY = posY.coerceIn(-maxBottomOverflow, 0f)
            return
        }
        val contentHeight = getSinglePageContentHeight()
        val maxPosY = (contentHeight - height).coerceAtLeast(0f)
        posY = posY.coerceIn(-maxPosY, maxPosY)
    }

    private fun getSinglePageContentHeight(): Float {
        val childHeight = getChildAt(0)?.height ?: height
        return childHeight * scaleFactor
    }

    private fun isVerticallyScrollable(): Boolean {
        if (isSinglePage()) return false
        return super.canScrollVertically(1) || super.canScrollVertically(-1)
    }

    private fun getCurrentMultiPageVisualOffset(): Float {
        return getBaseScrollOffset() * scaleFactor - posY
    }

    private fun applyMultiPageVisualOffsetDelta(delta: Float) {
        if (isSinglePage() || delta == 0f) return

        var remainingDelta = delta

        // If we are using bottom overflow and the gesture moves back upward, consume
        // the overflow first before scrolling the list.
        if (posY < 0f && remainingDelta < 0f) {
            val overflowToConsume = minOf(-posY, -remainingDelta)
            posY += overflowToConsume
            remainingDelta += overflowToConsume
        }

        val desiredScrollDelta = remainingDelta + multiPageScrollResidualY
        val requestedScroll = desiredScrollDelta.toInt()
        if (requestedScroll != 0) {
            val beforeOffset = getBaseScrollOffset()
            scrollBy(0, requestedScroll)
            val consumedScroll = (getBaseScrollOffset() - beforeOffset) * scaleFactor
            multiPageScrollResidualY = desiredScrollDelta - consumedScroll
        } else {
            multiPageScrollResidualY = desiredScrollDelta
        }

        remainingDelta = 0f

        // Any remaining positive delta means the list hit the bottom; store only
        // that residual as overflow so the last page can still be fully explored.
        if (multiPageScrollResidualY > 0f) {
            posY -= multiPageScrollResidualY
            multiPageScrollResidualY = 0f
        } else if (multiPageScrollResidualY < 0f && !super.canScrollVertically(-1)) {
            multiPageScrollResidualY = 0f
        }
    }

    private fun getBaseScrollOffset(): Float {
        return super.computeVerticalScrollOffset().toFloat()
    }

    private fun isSinglePage(): Boolean {
        return (adapter?.itemCount ?: 0) <= 1
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
    }

    /**
     * Zooms in on the current content.
     */
    fun zoomIn() {
        if (!isZoomEnabled) return
        val targetScale = (scaleFactor + 0.5f).coerceAtMost(maxZoom)
        zoomTo(targetScale, width / 2f, height / 2f, zoomDuration)
    }

    /**
     * Zooms out on the current content.
     */
    fun zoomOut() {
        if (!isZoomEnabled) return
        val targetScale = (scaleFactor - 0.5f).coerceAtLeast(1f)
        zoomTo(targetScale, width / 2f, height / 2f, zoomDuration)
    }

    /**
     * Resets the zoom level to 1.0.
     */
    fun resetZoom() {
        if (!isZoomEnabled) return
        zoomTo(1f, width / 2f, height / 2f, zoomDuration)
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

                zoomChangeListener?.invoke(isZoomedIn(), scaleFactor)
            }
            addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        zoomEndListener?.invoke(scaleFactor)
                    }
                })
                start()

        }
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
        private const val MAX_ZOOM = 3.0f
        private const val HARD_MAX_ZOOM = 5.0f
        private const val ZOOM_DURATION = 300L
    }
}

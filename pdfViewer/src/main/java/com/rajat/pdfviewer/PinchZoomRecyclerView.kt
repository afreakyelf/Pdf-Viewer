package com.rajat.pdfviewer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView

class PinchZoomRecyclerView : RecyclerView {

    private var mActivePointerId = INVALID_POINTER_ID
    private var mScaleDetector: ScaleGestureDetector? = null
    private var mGestureDetector: GestureDetector? = null
    private var mScaleFactor = 1f
    private var mIsZoomEnabled = true
    private var mMaxZoom = MAX_ZOOM
    private var maxWidth = 0.0f
    private var maxHeight = 0.0f
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private var mPosX = 0f
    private var mPosY = 0f

    constructor(context: Context) : super(context) {
        initializeScaleDetector(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initializeScaleDetector(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initializeScaleDetector(context)
    }

    init {
        if (!isInEditMode) {
            mScaleDetector = ScaleGestureDetector(context, ScaleListener())
            mGestureDetector = GestureDetector(context, GestureListener())
        }
    }

    private fun initializeScaleDetector(context: Context) {
        if (!isInEditMode) {
            mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        maxWidth = measuredWidth.toFloat()
        maxHeight = measuredHeight.toFloat()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        try {
            return super.onInterceptTouchEvent(ev)
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val superHandled = super.onTouchEvent(ev)
        mGestureDetector?.onTouchEvent(ev)
        mScaleDetector?.onTouchEvent(ev)
        when (ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mLastTouchX = ev.x
                mLastTouchY = ev.y
                mActivePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                val x = ev.getX(pointerIndex)
                val y = ev.getY(pointerIndex)

                if (mScaleFactor > 1f) {
                    val dx = x - mLastTouchX
                    val dy = y - mLastTouchY

                    mPosX += dx
                    mPosY += dy
                    mPosX = (maxWidth - width * mScaleFactor).coerceAtLeast(mPosX.coerceAtMost(0f))
                    mPosY = (maxHeight - height * mScaleFactor).coerceAtLeast(mPosY.coerceAtMost(0f))
                }

                mLastTouchX = x
                mLastTouchY = y
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Extract the index of the pointer that left the touch sensor
                val pointerIndex = (ev.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = ev.getPointerId(pointerIndex)

                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new active pointer and adjust accordingly.
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0

                    mLastTouchX = ev.getX(newPointerIndex)
                    mLastTouchY = ev.getY(newPointerIndex)
                    mActivePointerId = ev.getPointerId(newPointerIndex)
                }
            }
            MotionEvent.ACTION_CANCEL -> mActivePointerId = INVALID_POINTER_ID
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == mActivePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    mLastTouchX = ev.getX(newPointerIndex)
                    mLastTouchY = ev.getY(newPointerIndex)
                    mActivePointerId = ev.getPointerId(newPointerIndex)
                }
            }
            MotionEvent.ACTION_SCROLL -> {
                val dy = ev.getAxisValue(MotionEvent.AXIS_VSCROLL) * mScaleFactor
                mPosY += dy
                clampPosition()
                invalidate()
            }
        }

        return superHandled || mScaleFactor > 1f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(mPosX, mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)
        super.onDraw(canvas)
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(mPosX, mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun computeVerticalScrollRange(): Int {
        val contentHeight = (height * mScaleFactor).toInt()
        return contentHeight + paddingTop + paddingBottom
    }

    override fun computeVerticalScrollOffset(): Int {
        return (mPosY * mScaleFactor).toInt()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = Math.max(1f, Math.min(mScaleFactor * detector.scaleFactor, mMaxZoom))

            // Calculate adjustments needed to keep the zoom focal point stationary
            if (scaleFactor != mScaleFactor) {
                val scaleChange = scaleFactor / mScaleFactor

                // Determine the focal point of the pinch gesture relative to the view
                val focusXRelativeToView = detector.focusX - mPosX
                val focusYRelativeToView = detector.focusY - mPosY

                // Adjust the position so the focal point remains under the fingers
                mPosX -= focusXRelativeToView * (scaleChange - 1)
                mPosY -= focusYRelativeToView * (scaleChange - 1)

                mScaleFactor = scaleFactor

                clampPosition()
                invalidate()
            }

            return true
        }
    }





    private fun resetZoom() {
        mScaleFactor = 1f
        mPosX = 0f
        mPosY = 0f
        invalidate()
    }

    private fun clampPosition() {
        // Calculate the boundaries considering the scaled size of the RecyclerView
        val contentWidth = width * mScaleFactor
        val contentHeight = height * mScaleFactor

        // Calculate the maximum allowed translation
        val maxPosX = if (contentWidth > width) (contentWidth - width) / 2 else 0f
        val maxPosY = if (contentHeight > height) (contentHeight - height) / 2 else 0f

        // Clamp the translations to ensure content does not move too far
        mPosX = Math.min(maxPosX, Math.max(-maxPosX, mPosX))
        mPosY = Math.min(maxPosY, Math.max(-maxPosY, mPosY))

        invalidate()
    }



    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!mIsZoomEnabled) return false

            if (mScaleFactor > 1f) {
                resetZoom()
            } else {
                // Zoom towards the double-tap location
                val targetScale = mMaxZoom
                val scaleDelta = targetScale / mScaleFactor

                mScaleFactor = targetScale

                // Adjust position so that it scales towards the double-tap location
                mPosX -= (e.x - mPosX) * (1 - scaleDelta)
                mPosY -= (e.y - mPosY) * (1 - scaleDelta)

                clampPosition()
            }

            invalidate()
            return true
        }
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
        private const val MAX_SCALE = 3.0f
        private const val MAX_ZOOM = 3.0f
    }
}

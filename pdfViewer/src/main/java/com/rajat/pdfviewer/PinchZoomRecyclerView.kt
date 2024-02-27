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
            MotionEvent.ACTION_UP,
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

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
//        override fun onScale(detector: ScaleGestureDetector): Boolean {
//            val scaleFactor =
//                1f.coerceAtLeast((mScaleFactor * detector.scaleFactor).coerceAtMost(MAX_SCALE))
//            val focusX = detector.focusX
//            val focusY = detector.focusY
//
//            if (scaleFactor != mScaleFactor) {
//                val scaleDelta = scaleFactor / mScaleFactor
//                mPosX -= (focusX - mPosX) * (1 - scaleDelta)
//                mPosY -= (focusY - mPosY) * (1 - scaleDelta)
//                mScaleFactor = scaleFactor
//
//                mPosX = (maxWidth - width * mScaleFactor).coerceAtLeast(mPosX.coerceAtMost(0f))
//                mPosY = (maxHeight - height * mScaleFactor).coerceAtLeast(mPosY.coerceAtMost(0f))
//
//                invalidate()
//            }
//
//            return true
//        }


        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = 1f.coerceAtLeast((mScaleFactor * detector.scaleFactor).coerceAtMost(MAX_SCALE))
            val focusX = detector.focusX
            val focusY = detector.focusY

            if (scaleFactor != mScaleFactor) {
                val scaleDelta = scaleFactor / mScaleFactor

                // Adjust position so that it scales from the pinch zoom center
                mPosX += (focusX - mPosX) * (1 - scaleDelta)
                mPosY += (focusY - mPosY) * (1 - scaleDelta)

                // Update the scale factor
                mScaleFactor = scaleFactor

                // Make sure the view is within bounds
                clampPosition() // You may need to update this method as well if necessary
            }

            return true
        }

    }
    private fun resetZoom() {
        mScaleFactor = 1f
        mPosX = 0f
        mPosY = 0f
    }

    private fun clampPosition() {
        val maxPosX = maxWidth - (width * mScaleFactor)
        val maxPosY = maxHeight - (height * mScaleFactor)
        mPosX = maxPosX.coerceAtLeast(mPosX.coerceAtMost(0f))
        mPosY = maxPosY.coerceAtLeast(mPosY.coerceAtMost(0f))
    }
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (mIsZoomEnabled) {
                if (mScaleFactor > 1f) {
                    resetZoom()
                } else {
                    mScaleFactor = mMaxZoom
                    mPosX = -(e.x * (mMaxZoom - 1f))
                    mPosY = -(e.y * (mMaxZoom - 1f))
                    clampPosition()
                }
                invalidate()
            }
            return true
        }
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
        private const val MAX_SCALE = 3.0f
        private const val MAX_ZOOM = 3.0f
    }
}

package com.example.xchat.ChatFunctions

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrix = Matrix()
    private var scaleFactor = 1f
    private val minScale = 1f
    private val maxScale = 5f
    private var lastTouch = PointF()
    private var start = PointF()
    private var mode = NONE

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    init {
        scaleType = ScaleType.MATRIX
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)
            matrix.setScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            imageMatrix = matrix
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            scaleFactor = if (scaleFactor == minScale) maxScale else minScale
            matrix.setScale(scaleFactor, scaleFactor, e.x, e.y)
            imageMatrix = matrix
            return true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val currentPoint = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(currentPoint)
                start.set(lastTouch)
                mode = DRAG
            }
            MotionEvent.ACTION_MOVE -> if (mode == DRAG) {
                val deltaX = currentPoint.x - lastTouch.x
                val deltaY = currentPoint.y - lastTouch.y
                matrix.postTranslate(deltaX, deltaY)
                imageMatrix = matrix
                lastTouch.set(currentPoint.x, currentPoint.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> mode = NONE
        }
        return true
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        scaleFactor = 1f
        matrix.reset()
    }
}
package br.com.rmfacilities.funcionarioapp

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), ScaleGestureDetector.OnScaleGestureListener {

    private val imageMatrixValues = FloatArray(9)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val tempMatrix = Matrix()
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var viewWidth = 0
    private var viewHeight = 0
    private var currentScale = 1f

    private val minScale = 1f
    private val maxScale = 5f

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        isFocusable = true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val scaleFactor = detector.scaleFactor
        val prevScale = currentScale
        currentScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
        val scaleChange = currentScale / prevScale
        tempMatrix.set(imageMatrix)
        tempMatrix.postScale(scaleChange, scaleChange, detector.focusX, detector.focusY)
        imageMatrix = tempMatrix
        fixTranslation()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        if (currentScale <= minScale) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                isDragging = currentScale > 1f
                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress && isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    tempMatrix.set(imageMatrix)
                    tempMatrix.postTranslate(dx, dy)
                    imageMatrix = tempMatrix
                    fixTranslation()
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                if (currentScale <= minScale) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!scaleGestureDetector.isInProgress && currentScale <= minScale) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        return true
    }

    override fun setImageMatrix(matrix: Matrix) {
        super.setImageMatrix(matrix)
        matrix.getValues(imageMatrixValues)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        resetZoom()
    }

    private fun resetZoom() {
        currentScale = 1f
        tempMatrix.reset()
        val drawable = drawable ?: return
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f || viewWidth == 0 || viewHeight == 0) {
            imageMatrix = tempMatrix
            return
        }
        val scale = minOf(viewWidth / drawableWidth, viewHeight / drawableHeight)
        tempMatrix.postScale(scale, scale)
        val dx = (viewWidth - drawableWidth * scale) / 2f
        val dy = (viewHeight - drawableHeight * scale) / 2f
        tempMatrix.postTranslate(dx, dy)
        imageMatrix = tempMatrix
    }

    private fun fixTranslation() {
        val drawable = drawable ?: return
        val matrixValues = imageMatrixValues
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val scaleX = matrixValues[Matrix.MSCALE_X]
        val scaleY = matrixValues[Matrix.MSCALE_Y]
        val drawableWidth = drawable.intrinsicWidth * scaleX
        val drawableHeight = drawable.intrinsicHeight * scaleY

        val maxTransX = 0f
        val minTransX = if (drawableWidth > viewWidth) viewWidth - drawableWidth else (viewWidth - drawableWidth) / 2f
        val maxTransY = 0f
        val minTransY = if (drawableHeight > viewHeight) viewHeight - drawableHeight else (viewHeight - drawableHeight) / 2f

        var correctedX = transX
        var correctedY = transY

        if (transX < minTransX) correctedX = minTransX
        if (transX > maxTransX) correctedX = maxTransX
        if (transY < minTransY) correctedY = minTransY
        if (transY > maxTransY) correctedY = maxTransY

        if (correctedX != transX || correctedY != transY) {
            tempMatrix.set(imageMatrix)
            tempMatrix.postTranslate(correctedX - transX, correctedY - transY)
            super.setImageMatrix(tempMatrix)
            tempMatrix.getValues(imageMatrixValues)
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val targetScale = if (currentScale > minScale) minScale else maxScale
            val scaleChange = targetScale / currentScale
            currentScale = targetScale
            tempMatrix.set(imageMatrix)
            tempMatrix.postScale(scaleChange, scaleChange, e.x, e.y)
            imageMatrix = tempMatrix
            fixTranslation()
            return true
        }
    }
}

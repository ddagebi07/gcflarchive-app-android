package kr.co.gcflarchive.app.library

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Pinch / double-tap zoom around a vertically scrolling RecyclerView (PDF pages).
 * While zoomed, one-finger drags pan the zoomed view; vertical movement that pans
 * past the edge scrolls the list.
 */
class ZoomLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var scale = 1f
        private set
    private var lastX = 0f
    private var lastY = 0f
    private var panning = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private val content get() = getChildAt(0)

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            setScale(scale * detector.scaleFactor)
            return true
        }
    })

    private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            setScale(if (scale > 1.05f) 1f else 2f)
            return true
        }
    })

    fun reset() = setScale(1f)

    private fun setScale(value: Float) {
        val child = content ?: return
        scale = value.coerceIn(MIN, MAX)
        child.pivotX = width / 2f
        child.pivotY = height / 2f
        child.scaleX = scale
        child.scaleY = scale
        panBy(0f, 0f)
    }

    /** Moves the zoomed content; returns the vertical amount that could not be panned. */
    private fun panBy(dx: Float, dy: Float): Float {
        val child = content ?: return dy
        val maxX = (scale - 1f) * width / 2f
        val maxY = (scale - 1f) * height / 2f
        child.translationX = (child.translationX + dx).coerceIn(-maxX, maxX)
        val wantedY = child.translationY + dy
        child.translationY = wantedY.coerceIn(-maxY, maxY)
        return wantedY - child.translationY
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        tapDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.x; lastY = ev.y; panning = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> return true
            MotionEvent.ACTION_MOVE -> if (scale > 1.01f && (abs(ev.x - lastX) > touchSlop || abs(ev.y - lastY) > touchSlop)) {
                panning = true
                lastX = ev.x; lastY = ev.y
                return true
            }
        }
        return scaleDetector.isInProgress
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        tapDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress && ev.pointerCount == 1) {
                val dx = ev.x - lastX
                val dy = ev.y - lastY
                lastX = ev.x; lastY = ev.y
                val leftover = panBy(dx, dy)
                // Past the pan edge: scroll the page list (in unscaled list coordinates).
                (content as? RecyclerView)?.scrollBy(0, (-leftover / scale).toInt())
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = if (ev.actionIndex == 0) 1 else 0
                lastX = ev.getX(idx); lastY = ev.getY(idx)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> panning = false
        }
        return true
    }

    private companion object {
        const val MIN = 1f
        const val MAX = 4f
    }
}

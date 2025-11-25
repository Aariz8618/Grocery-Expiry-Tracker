package com.aariz.expirytracker // PACKAGE NAME CHANGED

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

class AnimatedBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // --- Indicator Properties ---
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Uses the green_primary color defined in colors.xml
        color = ContextCompat.getColor(context, R.color.green_primary)
        style = Paint.Style.FILL
    }
    private val indicatorRect = RectF()
    private var indicatorCenterX = 0f
    private var indicatorWidth = 0f
    private var indicatorHeight = 0f

    // Animation control
    private var currentAnimator: ValueAnimator? = null
    private var targetCenterX = 0f

    init {
        // Set up the LinearLayout properties
        orientation = HORIZONTAL
        // Assuming R.color.background_light is defined
        setBackgroundColor(ContextCompat.getColor(context, R.color.white))
        // We handle our own drawing, so we must enable it
        setWillNotDraw(false)
        setPadding(0, 0, 0, 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculate the height of the indicator (e.g., 40dp in height)
        val density = resources.displayMetrics.density
        val indicatorFixedDp = 40f
        indicatorHeight = indicatorFixedDp * density

        // Indicator width set equal to height for a circular look when centered
        indicatorWidth = indicatorHeight

        // Initial position (if not set, default to first child)
        if (childCount > 0 && indicatorCenterX == 0f) {
            post {
                val firstChild = getChildAt(0)
                indicatorCenterX = firstChild.left + firstChild.width / 2f
                invalidate()
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Draw the indicator BEFORE drawing the children (icons/text) so the indicator is in the background
        drawIndicator(canvas)
        super.dispatchDraw(canvas)
    }

    private fun drawIndicator(canvas: Canvas) {
        // Calculate indicator bounds based on the current animated center (indicatorCenterX)
        val left = indicatorCenterX - indicatorWidth / 2
        val right = indicatorCenterX + indicatorWidth / 2

        // Center the indicator vertically, allowing 8dp top/bottom padding
        val padding = 8f * resources.displayMetrics.density // Example 8dp vertical padding
        val top = height.toFloat() - indicatorHeight - padding
        val bottom = height.toFloat() - padding

        // Update the RectF object
        indicatorRect.set(left, top, right, bottom)

        // Draw the rounded rectangle (pill shape or circle if width == height)
        val cornerRadius = indicatorHeight / 2 // Half the height makes it fully rounded
        canvas.drawRoundRect(indicatorRect, cornerRadius, cornerRadius, indicatorPaint)
    }

    /**
     * Public method to start the indicator animation to the target tab's center position.
     * @param targetView The newly selected tab's LinearLayout.
     */
    fun animateIndicatorTo(targetView: View) {
        currentAnimator?.cancel()

        // Calculate the new target center X
        targetCenterX = targetView.left + targetView.width / 2f

        // If indicator hasn't been initialized, set it immediately without animation
        if (indicatorCenterX == 0f) {
            indicatorCenterX = targetCenterX
            invalidate()
            return
        }

        // Create the smooth animation for the horizontal slide
        currentAnimator = ValueAnimator.ofFloat(indicatorCenterX, targetCenterX).apply {
            duration = 350L // Fast, punchy duration
            interpolator = OvershootInterpolator(1.2f) // Gives it a slight "pop" at the end
            addUpdateListener { animator ->
                indicatorCenterX = animator.animatedValue as Float
                // Force the view to redraw the indicator in its new position
                invalidate()
            }
            start()
        }
    }
}
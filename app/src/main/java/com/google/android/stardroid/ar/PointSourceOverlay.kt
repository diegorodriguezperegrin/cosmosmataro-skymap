package com.google.android.stardroid.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.util.Log

/**
 * A simple overlay view that draws circles at specified coordinates.
 * Expects normalized coordinates (0..1) to handle different aspect ratios/scaling.
 */
class PointSourceOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#4000FF00") // Semi-transparent green
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // List of normalized points (x, y)
    private var points: List<Pair<Float, Float>> = emptyList()
    
    // Optional labels for each point (e.g. RA/Dec)
    private var labels: List<String> = emptyList()
    
    // Source dimensions (to calculate Aspect Ratio Crop)
    private var sourceWidth: Int = 1
    private var sourceHeight: Int = 1

    private val textPaint = Paint().apply {
        color = Color.MAGENTA
        textSize = 60f
        isAntiAlias = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    fun setPoints(newPoints: List<Pair<Float, Float>>, srcW: Int, srcH: Int, newLabels: List<String> = emptyList()) {
        points = newPoints
        labels = newLabels
        sourceWidth = srcW
        sourceHeight = srcH
        invalidate() // Request redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        
        // Calculate Scale Factor (Center Crop)
        // We want to fill the view with the source image, preserving aspect ratio.
        val scaleX = w / sourceWidth.toFloat()
        val scaleY = h / sourceHeight.toFloat()
        val scale = kotlin.math.max(scaleX, scaleY)
        
        // Calculate Scaled Dimensions
        val scaledW = sourceWidth * scale
        val scaledH = sourceHeight * scale
        
        // Calculate Offset to center
        val offsetX = (w - scaledW) / 2f
        val offsetY = (h - scaledH) / 2f

        for (point in points) {
            // Point is normalized (0..1) relative to Source.
            // Map to Scaled Source Coordinates
            val sx = point.first * scaledW
            val sy = point.second * scaledH
            
            // Apply Offset to map to View Coordinates
            val cx = sx + offsetX
            val cy = sy + offsetY
            
            // Draw circle
            val radius = 30f 
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, paint)
            
            // Draw label if available (and not empty)
            val index = points.indexOf(point)
            if (index >= 0 && index < labels.size) {
                val text = labels[index]
                if (text.isNotEmpty()) {
                    canvas.drawText(text, cx + radius + 10f, cy, textPaint)
                }
            }
        }
    }
}

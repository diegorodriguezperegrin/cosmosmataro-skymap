package com.google.android.stardroid.control

import com.google.android.stardroid.math.Vector3
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * Calculates optimal Field of View (FOV) alignment between camera view and virtual sky
 * by comparing angular separations of matched stars in screen space vs sky space.
 */
class FovAlignmentCalculator {
    
    companion object {
        const val MIN_FOV_CHANGE_THRESHOLD = 2.0f // Minimum FOV change in degrees to apply
    }
    
    data class MatchedStar(
        val screenX: Float,      // Normalized screen coordinates (0..1)
        val screenY: Float,
        val skyPosition: Vector3 // 3D position on celestial sphere
    )
    
    data class FovCalculationResult(
        val fov: Float,           // Calculated FOV in degrees
        val stability: Float      // Stability score 0..1 (1 = very stable)
    )
    
    /**
     * Calculate optimal FOV based on matched star positions.
     * 
     * Algorithm:
     * 1. For each pair of matched stars, calculate angular separation in sky space
     * 2. Calculate pixel separation in screen space
     * 3. Derive FOV from: FOV = angularSeparation * (screenSize / pixelSeparation)
     * 4. Average multiple measurements for robustness
     * 
     * @param matches List of matched stars with screen and sky positions
     * @param currentFov Current FOV in degrees
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @return Calculated FOV result with stability score, or null if insufficient data
     */
    fun calculateOptimalFovWithStability(
        matches: List<MatchedStar>,
        currentFov: Float,
        screenWidth: Float,
        screenHeight: Float
    ): FovCalculationResult? {
        if (matches.size < 2) {
            return null // Need at least 2 stars
        }
        
        val fovEstimates = mutableListOf<Float>()
        
        // Compare all pairs of stars
        for (i in matches.indices) {
            for (j in i + 1 until matches.size) {
                val star1 = matches[i]
                val star2 = matches[j]
                
                // Calculate angular separation in sky (radians)
                val dotProduct = star1.skyPosition dot star2.skyPosition
                // Clamp to [-1, 1] to avoid acos domain errors
                val clampedDot = dotProduct.coerceIn(-1f, 1f)
                val angularSeparationRad = acos(clampedDot)
                val angularSeparationDeg = angularSeparationRad * 57.2957795f // rad to deg
                
                // Calculate pixel separation in screen space
                val dx = (star1.screenX - star2.screenX) * screenWidth
                val dy = (star1.screenY - star2.screenY) * screenHeight
                val pixelSeparation = sqrt(dx * dx + dy * dy)
                
                // Skip if stars are too close (unreliable measurement)
                if (pixelSeparation < 50f) continue
                
                // Calculate FOV estimate using precise Vertical FOV formula
                // We use screenHeight because SkyRenderer uses Vertical FOV for projection
                val fovEstimate = calculatePreciseVerticalFov(angularSeparationDeg, pixelSeparation, screenHeight)
                
                // Sanity check: FOV should be reasonable (10-120 degrees)
                // Wide angle lenses can be 60-90 degrees, so 120 is a safe upper bound
                if (fovEstimate in 10f..150f) {
                    fovEstimates.add(fovEstimate)
                }
            }
        }
        
        if (fovEstimates.isEmpty()) {
            return null
        }
        
        // Calculate median FOV
        fovEstimates.sort()
        val medianFov = fovEstimates[fovEstimates.size / 2]
        
        // Calculate stability based on variance of estimates
        val stability = calculateStability(fovEstimates, medianFov)
        
        return FovCalculationResult(medianFov, stability)
    }

    /**
     * Calculates precise Vertical FOV from angular separation and pixel separation.
     * 
     * Formula: FOV_y = 2 * atan( (screenHeight / pixelSeparation) * tan(angularSeparation / 2) )
     * 
     * Why this formula?
     * 1. tan(theta/2) maps the spherical angular separation to a linear projection plane ratio.
     * 2. (screenDimension / pixelSeparation) scales that ratio to the full screen.
     * 3. atan converts the full screen ratio back to degrees.
     */
    private fun calculatePreciseVerticalFov(
        angularSeparationDeg: Float, 
        pixelSeparation: Float, 
        screenHeight: Float
    ): Float {
        val angularRadiusRad = Math.toRadians(angularSeparationDeg.toDouble() / 2.0)
        val projectionRatio = kotlin.math.tan(angularRadiusRad)
        
        val scaleFactor = screenHeight / pixelSeparation
        val fovRadiusRad = kotlin.math.atan(projectionRatio * scaleFactor)
        
        return Math.toDegrees(fovRadiusRad * 2.0).toFloat()
    }
    
    /**
     * Calculate stability score based on variance of FOV estimates.
     * Returns 1.0 for very stable (low variance), 0.0 for unstable (high variance).
     */
    private fun calculateStability(estimates: List<Float>, median: Float): Float {
        if (estimates.size < 2) return 1.0f
        
        // Calculate mean absolute deviation from median
        val deviations = estimates.map { abs(it - median) }
        val meanDeviation = deviations.average().toFloat()
        
        // Normalize: 0 deviation = 1.0 stability, 10+ degrees deviation = 0.0 stability
        val normalizedDeviation = (meanDeviation / 10f).coerceIn(0f, 1f)
        return 1f - normalizedDeviation
    }
    
    /**
     * Legacy method for backward compatibility - returns just the FOV value.
     */
    fun calculateOptimalFov(
        matches: List<MatchedStar>,
        currentFov: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Float? {
        return calculateOptimalFovWithStability(matches, currentFov, screenWidth, screenHeight)?.fov
    }
    
    /**
     * Apply smoothing to FOV changes to prevent jitter.
     * Uses exponential moving average with adaptive smoothing based on stability.
     * 
     * @param currentFov Current FOV value
     * @param targetFov Newly calculated FOV
     * @param stability Stability score 0..1 (1 = very stable, 0 = unstable)
     * @param baseAlpha Base smoothing factor (0..1). Higher = more responsive, lower = smoother
     * @return Smoothed FOV value
     */
    fun smoothFov(currentFov: Float, targetFov: Float, stability: Float = 1.0f, baseAlpha: Float = 0.2f): Float {
        // Reduce alpha (increase smoothing) when stability is low
        // High stability (1.0) → use baseAlpha
        // Low stability (0.0) → use baseAlpha * 0.3 (much smoother)
        val adaptiveAlpha = baseAlpha * (0.3f + 0.7f * stability)
        return currentFov * (1f - adaptiveAlpha) + targetFov * adaptiveAlpha
    }
}

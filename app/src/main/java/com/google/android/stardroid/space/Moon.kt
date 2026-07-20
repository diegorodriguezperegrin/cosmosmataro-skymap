package com.google.android.stardroid.space

import org.cosmosmataro.skymap.R
import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.*
import com.google.android.stardroid.math.MathUtils.asin
import com.google.android.stardroid.math.MathUtils.atan2
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin
import java.util.*

/**
 * A likely temporary class to represent the Moon.
 */
class Moon : EarthOrbitingObject(SolarSystemBody.Moon) {
    // override fun getRaDec(date: Date): RaDec { ... } // Removed to use EarthOrbitingObject implementation (ELP2000)

    /** Returns the resource id for the planet's image.  */
    override fun getImageResourceId(time: Date) = getLunarPhaseImageId(time)

    /**
     * Determine the Moon's phase and return the resource ID of the correct
     * image.
     */
    fun getLunarPhaseImageId(time: Date): Int {
        // First, calculate phase angle:
        val phase: Float = calculatePhaseAngle(time)
        // Log.d(TAG, "Lunar phase = $phase")

        // Next, figure out what resource id to return.
        if (phase < 22.5f) {
            // New moon.
            return R.drawable.moon0
        } else if (phase > 150.0f) {
            // Full moon.
            return R.drawable.moon4
        }

        // Either crescent, quarter, or gibbous. Need to see whether we are
        // waxing or waning. Calculate the phase angle one day in the future.
        // If phase is increasing, we are waxing. If not, we are waning.
        val tomorrow = Date(time.time + 24 * 3600 * 1000)
        val phase2: Float = calculatePhaseAngle(tomorrow)
        // Log.d(TAG, "Tomorrow's phase = $phase2")
        if (phase < 67.5f) {
            // Crescent
            return if (phase2 > phase) R.drawable.moon1 else R.drawable.moon7
        } else if (phase < 112.5f) {
            // Quarter
            return if (phase2 > phase) R.drawable.moon2 else R.drawable.moon6
        }

        // Gibbous
        return if (phase2 > phase) R.drawable.moon3 else R.drawable.moon5
    }

    override val bodySize = -0.83f

    // TODO(serafini): For now, return semi-reasonable values for the Sun and
    // Moon. We shouldn't call this method for those bodies, but we want to do
    // something sane if we do.
    override fun getMagnitude(time: Date) = -10.0f
}
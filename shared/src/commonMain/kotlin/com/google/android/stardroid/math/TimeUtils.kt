package com.google.android.stardroid.math

import kotlin.math.floor

/**
 * Utilities for working with Dates and times in a platform-agnostic way.
 */

// Julian Date for Unix Epoch (1970-01-01 00:00:00 UTC)
const val JD_UNIX_EPOCH = 2440587.5

// Constants
const val MINUTES_PER_HOUR = 60.0
const val SECONDS_PER_HOUR = 3600.0
const val HOURS_TO_DEGREES = 360.0f / 24.0f

/**
 * Calculates the number of Julian Centuries from the epoch 2000.0
 * (equivalent to Julian Day 2451545.0).
 */
fun julianCenturies(timeInMillis: Long): Double {
    val jd = julianDay(timeInMillis)
    val delta = jd - 2451545.0
    return delta / 36525.0
}

/**
 * Calculates the Julian Day for a given timestamp (milliseconds since Unix Epoch).
 */
fun julianDay(timeInMillis: Long): Double {
    return JD_UNIX_EPOCH + timeInMillis / 86400000.0
}

/**
 * Calculates local mean sidereal time in degrees. 
 * Longitude is negative for western longitude values.
 */
fun meanSiderealTime(timeInMillis: Long, longitude: Float): Float {
    // First, calculate number of Julian days since J2000.0.
    val jd = julianDay(timeInMillis)
    val delta = jd - 2451545.0

    // Calculate the global and local sidereal times
    val gst = 280.461 + 360.98564737 * delta
    val lst = normalizeAngle(gst + longitude.toDouble())
    return lst.toFloat()
}

/**
 * Normalizes the angle to the range 0 <= value < 360.
 */
fun normalizeAngle(angleDegrees: Double): Double {
    return positiveMod(angleDegrees, 360.0)
}

/**
 * Normalizes the time to the range 0 <= value < 24.
 */
fun normalizeHours(time: Double): Double {
    return positiveMod(time, 24.0)
}

/**
 * Take a universal time between 0 and 24 and return a triple
 * [hours, minutes, seconds].
 *
 * @param universalTime Universal time - presumed to be between 0 and 24.
 * @return [hours, minutes, seconds]
 */
fun clockTimeFromHrs(universalTime: Double): IntArray {
    val hms = IntArray(3)
    hms[0] = floor(universalTime).toInt()
    val remainderMins = MINUTES_PER_HOUR * (universalTime - hms[0])
    hms[1] = floor(remainderMins).toInt()
    hms[2] = floor(remainderMins - hms[1]).toInt()
    return hms
}

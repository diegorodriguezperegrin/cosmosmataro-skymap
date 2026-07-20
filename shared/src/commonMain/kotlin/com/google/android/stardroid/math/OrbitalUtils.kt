package com.google.android.stardroid.math

import com.google.android.stardroid.ephemeris.OrbitalElements
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin

/**
 * Utilities for manipulating different coordinate systems, specifically involving orbital elements.
 */

/**
 * Converts OrbitalElements into "HeliocentricCoordinates" - cartesian coordinates
 * centered on the sun with a z-axis pointing normal to Earth's orbital plane
 * and measured in Astronomical units.
 */
fun heliocentricCoordinatesFromOrbitalElements(elem: OrbitalElements): Vector3 {
    val anomaly = elem.anomaly
    val ecc = elem.eccentricity
    val radius = elem.distance * (1 - ecc * ecc) / (1 + ecc * cos(anomaly))

    // heliocentric rectangular coordinates of planet
    val per = elem.perihelion
    val asc = elem.ascendingNode
    val inc = elem.inclination
    val xh = radius *
            (cos(asc) * cos(anomaly + per - asc) -
                    sin(asc) * sin(anomaly + per - asc) *
                    cos(inc))
    val yh = radius *
            (sin(asc) * cos(anomaly + per - asc) +
                    cos(asc) * sin(anomaly + per - asc) *
                    cos(inc))
    val zh = radius * (sin(anomaly + per - asc) * sin(inc))
    return Vector3(xh, yh, zh)
}

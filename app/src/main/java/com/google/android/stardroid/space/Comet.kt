package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.CometPhysics
import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.convertToEquatorialCoordinates
import java.util.Date

/**
 * A Comet that orbits the Sun.
 */
class Comet(
    val name: String,
    private val perihelionDistance: Double, // q (AU)
    private val eccentricity: Double,       // e
    private val inclination: Double,        // i (degrees)
    private val ascendingNode: Double,      // Omega (degrees)
    private val argPerihelion: Double,      // w (degrees)
    private val timeOfPerihelion: Long,      // Tp
    private val magAbs: Double = 10.0,       // H (Absolute Magnitude)
    private val slopeParam: Double = 4.0     // n (Slope parameter)
) : MovingObject() {

    override fun getRaDec(date: Date, location: LatLong?): RaDec {
        // 1. Get Heliocentric Coordinates of the Comet
        val cometHelio = CometPhysics.getHeliocentricCoordinates(
            perihelionDistance,
            eccentricity,
            inclination,
            ascendingNode,
            argPerihelion,
            timeOfPerihelion,
            date.time
        )

        // 2. Get Heliocentric Coordinates of Earth
        val earthHelio = SolarSystemBody.Earth.getHeliocentricCoordinates(date.time)

        // 3. Calculate Geocentric Coordinates (Comet - Earth)
        // Vector3 is mutable/clonable? Standard Vector3 in this codebase seems to support operators if Kotlin.
        // Checking existing code, they use: myCoords -= earthCoords
        val geocentric = Vector3(cometHelio.x, cometHelio.y, cometHelio.z)
        geocentric.minusAssign(earthHelio) // or x -= earth.x, etc.

        // 4. Convert to Equatorial (RA, Dec)
        val equatorial = convertToEquatorialCoordinates(geocentric)
        
        return RaDec.fromGeocentricCoords(equatorial)
    }

    fun getApparentMagnitude(date: Date): Float {
        val cometHelio = CometPhysics.getHeliocentricCoordinates(
            perihelionDistance,
            eccentricity,
            inclination,
            ascendingNode,
            argPerihelion,
            timeOfPerihelion,
            date.time
        )
        val earthHelio = SolarSystemBody.Earth.getHeliocentricCoordinates(date.time)

        val r = Vector3(cometHelio.x, cometHelio.y, cometHelio.z).length // Sun-Comet distance
        
        val deltaVec = Vector3(cometHelio.x, cometHelio.y, cometHelio.z)
        deltaVec.minusAssign(earthHelio)
        val delta = deltaVec.length // Earth-Comet distance

        // Formula: m = H + 5*log10(Delta) + 2.5*n*log10(r)
        // Note: Java Math.log10 returns primitive double.
        val mag = magAbs + 5.0 * kotlin.math.log10(delta) + 2.5 * slopeParam * kotlin.math.log10(r)
        return mag.toFloat()
    }
}

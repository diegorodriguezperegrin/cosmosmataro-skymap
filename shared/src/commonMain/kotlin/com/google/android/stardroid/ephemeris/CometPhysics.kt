package com.google.android.stardroid.ephemeris

import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import kotlin.math.*

/**
 * Physics engine for calculating cometary positions.
 * Handles Elliptical, Parabolic, and Hyperbolic orbits.
 */
object CometPhysics {
    // Gravitational Constant * Mass of Sun
    // k = 0.01720209895 (Gaussian constant)
    // mu = k^2
    // However, for typical comet data (q, e, Tp), we usually work with:
    // Mean Motion n (rad/day) or we compute it from q.
    // k = 0.01720209895 rad/day
    private const val K_GAUSS_RAD = 0.01720209895

    /**
     * Calculates the Heliocentric Ecliptic Coordinates of a comet.
     *
     * @param perihelionDistanceAU (q) Perihelion distance in AU
     * @param eccentricity (e) Orbital eccentricity
     * @param inclinationDeg (i) Inclination in degrees
     * @param ascendingNodeDeg (Omega) Longitude of Ascending Node in degrees
     * @param argPerihelionDeg (w) Argument of Perihelion in degrees
     * @param timeOfPerihelion (Tp) Date of perihelion passage
     * @param targetDate Date to compute position for
     * @return Vector3 Position in Heliocentric Ecliptic Coordinates (AU)
     */
    fun getHeliocentricCoordinates(
        perihelionDistanceAU: Double,
        eccentricity: Double,
        inclinationDeg: Double,
        ascendingNodeDeg: Double,
        argPerihelionDeg: Double,

        timeOfPerihelion: Long,
        targetDate: Long
    ): Vector3 {
        // 1. Calculate time difference in days
        val daysSincePerihelion = (targetDate - timeOfPerihelion) / 86400000.0

        // 2. Solve for True Anomaly (v) and Radius (r)
        val (v, r) = solveOrbit(perihelionDistanceAU, eccentricity, daysSincePerihelion)

        // 3. Convert orbit plane coordinates to ecliptic coordinates
        // Using standard orbital elements transformation
        // x = r * (cos(Omega) * cos(w+v) - sin(Omega) * sin(w+v) * cos(i))
        // y = r * (sin(Omega) * cos(w+v) + cos(Omega) * sin(w+v) * cos(i))
        // z = r * (sin(w+v) * sin(i))

        val iRad = inclinationDeg * DEGREES_TO_RADIANS
        val nodeRad = ascendingNodeDeg * DEGREES_TO_RADIANS
        val wRad = argPerihelionDeg * DEGREES_TO_RADIANS
        
        // Argument of Latitude u = w + v
        val u = wRad + v

        val cosU = cos(u)
        val sinU = sin(u)
        val cosNode = cos(nodeRad)
        val sinNode = sin(nodeRad)
        val cosI = cos(iRad)
        val sinI = sin(iRad)

        val x = r * (cosNode * cosU - sinNode * sinU * cosI)
        val y = r * (sinNode * cosU + cosNode * sinU * cosI)
        val z = r * (sinU * sinI)

        return Vector3(x.toFloat(), y.toFloat(), z.toFloat())
    }

    /**
     * Solves the orbital equation for a given time.
     * Returns Pair(True Anomaly in radians, Radius in AU).
     */
    private fun solveOrbit(q: Double, e: Double, dt: Double): Pair<Double, Double> {
        // Accuracy threshold
        val epsilon = 1.0e-8

        // Handle Parabolic Case (e approx 1.0)
        // Barker's Equation: M = n * dt = 1/2 * (tan(v/2) + 1/3 * tan(v/2)^3)
        // Use a threshold for "near parabolic" to avoid singularities in elliptical/hyperbolic conversion
        if (abs(e - 1.0) < 1.0e-4) {
            // Parabolic
            // q = perihelion distance
            // Mean motion for parabolic: n = k * sqrt(2) / (2 * q^1.5)  ??? 
            // Barker's eqn form: M_parabolic = k * sqrt(1/2q^3) * dt
            // n_parabolic = 0.01720209895 * sqrt(1 / (2 * q^3))  (rad/day? No, Barker works with specific constants)
            
            // Standard Barker:
            // M = k * dt * sqrt(1 / (2 * q^3))
            val k = K_GAUSS_RAD
            val mp = k * dt * sqrt(1.0 / (2.0 * q.pow(3)))
            
            // Solve M = s + s^3/3 for s where s = tan(v/2)
            // 3M = 3s + s^3
            // Cubic equation solution for s
            // s = (3M + sqrt((3M)^2 + 1))^(1/3) - (3M + sqrt((3M)^2 + 1))^(-1/3) ... wait simplified
            
            val w = 3.0 * mp
            val y = (w + sqrt(w * w + 1.0)).pow(1.0 / 3.0)
            val s = y - 1.0 / y
            
            val v = 2.0 * atan(s)
            val r = q * (1.0 + s * s)
            return Pair(v, r)
        }

        if (e < 1.0) {
            // Elliptical
            // Mean Motion n = k / a^1.5
            // a = q / (1 - e)
            val a = q / (1.0 - e)
            val n = K_GAUSS_RAD / a.pow(1.5)
            val m = n * dt // Mean Anomaly

            // Kepler's Equation: M = E - e * sin(E)
            var e0 = m
            // Initial guess strategy
            if (e > 0.8) e0 = PI // Rough guess for high eccentricity
            
            var e1 = 0.0
            var iter = 0
            do {
                e1 = e0 - (e0 - e * sin(e0) - m) / (1.0 - e * cos(e0))
                val delta = abs(e1 - e0)
                e0 = e1
                iter++
            } while (delta > epsilon && iter < 100)

            // True Anomaly v
            // tan(v/2) = sqrt((1+e)/(1-e)) * tan(E/2)
            val v = 2.0 * atan(sqrt((1.0 + e) / (1.0 - e)) * tan(e0 / 2.0))
            val r = a * (1.0 - e * cos(e0))
            return Pair(v, r)

        } else {
            // Hyperbolic (e > 1.0)
            // Mean Motion n = k / (-a)^1.5  where a < 0
            // but usually formulated with q: 
            // a = q / (1 - e)  (Will be negative)
            val a = q / (1.0 - e) // Negative
            val absA = abs(a)
            val n = K_GAUSS_RAD / absA.pow(1.5)
            val m = n * dt // Mean Anomaly

            // Hyperbolic Kepler: M = e * sinh(H) - H
            // Solve for H
            var h0 = if (e < 1.6) m / e else m // Initial guess
            if (m == 0.0) h0 = 0.0
            
            var h1 = 0.0
            var iter = 0
            do {
                // F = e * sinh(H) - H - M
                // F' = e * cosh(H) - 1
                val f = e * sinh(h0) - h0 - m
                val fPrime = e * cosh(h0) - 1.0
                h1 = h0 - f / fPrime
                val delta = abs(h1 - h0)
                h0 = h1
                iter++
            } while (delta > epsilon && iter < 100)

            // True Anomaly v
            // tan(v/2) = sqrt((e+1)/(e-1)) * tanh(H/2)
            val v = 2.0 * atan(sqrt((e + 1.0) / (e - 1.0)) * tanh(h0 / 2.0))
            val r = absA * (e * cosh(h0) - 1.0)
            return Pair(v, r)
        }
    }
}

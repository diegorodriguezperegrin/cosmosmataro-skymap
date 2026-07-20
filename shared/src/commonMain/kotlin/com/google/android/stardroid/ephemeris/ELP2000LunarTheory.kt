package com.google.android.stardroid.ephemeris

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.julianCenturies

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

/**
 * ELP-2000/82 Lunar Theory (Truncated for Mobile).
 * Calculates Geocentric Coordinates of the Moon.
 * Source: Jean Meeus, Astronomical Algorithms, Chapter 47.
 */
object ELP2000LunarTheory {

    // Helper to normalize angle to 0..2PI
    private fun to2Pi(angle: Double): Double {
        var a = angle % (2 * PI)
        if (a < 0) a += (2 * PI)
        return a
    }

    // Return Geocentric Rectangular Coordinates (X, Y, Z) in AU (to match Heliocentric system units when added)
    // Note: ELP2000 returns distance in km usually. We must convert.
    fun getGeocentricCoordinates(date: Long): Vector3 {
        val t = julianCenturies(date)
        
        // Fundamental Arguments (Radians)
        // L' = Mean Longitude of Moon
        // D = Mean Elongation of Moon
        // M = Mean Anomaly of Sun
        // M' = Mean Anomaly of Moon
        // F = Mean Distance of Moon from Ascending Node
        
        val deg2rad = API_RADS
        
        val L_prime = to2Pi(deg2rad * (218.3164477 + 481267.88123421 * t))
        val D = to2Pi(deg2rad * (297.8501921 + 445267.1114034 * t))
        val M = to2Pi(deg2rad * (357.5291092 + 35999.0502909 * t))
        val M_prime = to2Pi(deg2rad * (134.96292 + 477198.8675055 * t))
        val F = to2Pi(deg2rad * (93.2728327 + 483202.0175273 * t))

        // Calculate Periodic Terms
        // 1. Longitude (Sigma l)
        val sigmaL = calculateSeries(MOON_L_ARGS, MOON_L_COEFFS, D, M, M_prime, F)
        
        // 2. Latitude (Sigma b)
        val sigmaB = calculateSeries(MOON_B_ARGS, MOON_B_COEFFS, D, M, M_prime, F)
        
        // 3. Distance (Sigma r) - Returns distance in km
        val sigmaR = calculateSeries(MOON_R_ARGS, MOON_R_COEFFS, D, M, M_prime, F)

        // Final Geocentric Coordinates (Lambda, Beta, Delta)
        val lambda = L_prime + sigmaL / 1000000.0 * deg2rad // coefficients are in millionths of degree
        val beta = sigmaB / 1000000.0 * deg2rad
        val deltaKm = 385000.56 + sigmaR / 1000.0 // Constant + variation (km)
        
        // Convert to AU (1 AU = 149597870.700 km)
        val deltaAU = deltaKm / 149597870.700

        // Ecliptic Rectangular Coordinates (Geocentric)
        val x = deltaAU * cos(beta) * cos(lambda)
        val y = deltaAU * cos(beta) * sin(lambda)
        val z = deltaAU * sin(beta)

        return Vector3(x.toFloat(), y.toFloat(), z.toFloat())
    }

    // Calculate sum of terms: A * sin(i1*D + i2*M + i3*M' + i4*F)
    // For latitude, it's usually Sin, for Distance Cos?
    // Meeus Ch 47:
    // Longitude: Sine terms
    // Latitude: Sine terms
    // Distance: Cosine terms
    private fun calculateSeries(
        args: Array<IntArray>, 
        coeffs: DoubleArray, 
        D: Double, M: Double, Mp: Double, F: Double
    ): Double {
        var sum = 0.0
        for (i in args.indices) {
            val arg = args[i]
            val iD = arg[0]
            val iM = arg[1]
            val iMp = arg[2]
            val iF = arg[3]
            
            val theta = iD*D + iM*M + iMp*Mp + iF*F
            
            // Check if Sine or Cosine
            // If coeffs array is passed, we need to know mechanism.
            // Actually, usually Longitude/Latitude are Sine, Distance is Cosine.
            // I will use a separate method or flag, or just duplicate for simplicity of this truncated port.
            if (coeffs === MOON_R_COEFFS) {
                sum += coeffs[i] * cos(theta)
            } else {
                sum += coeffs[i] * sin(theta)
            }
        }
        return sum
    }
    
    // Constants
    private const val API_RADS = PI / 180.0

    // --- COEFFICIENTS (Truncated / Selected Largest Terms) ---
    // Format: {D, M, M', F} multipliers
    
    // Longitude (Sine) - Source: Meeus Table 47.A
    // Units: Millionths of degree
    private val MOON_L_ARGS = arrayOf(
        intArrayOf(0, 0, 1, 0),
        intArrayOf(2, 0, -1, 0),
        intArrayOf(2, 0, 0, 0),
        intArrayOf(0, 0, 2, 0),
        intArrayOf(0, 1, 0, 0),
        intArrayOf(0, 0, 0, 2),
        intArrayOf(2, 0, -2, 0),
        intArrayOf(2, -1, -1, 0),
        intArrayOf(2, 0, 1, 0),
        intArrayOf(2, -1, 0, 0),
        intArrayOf(0, 1, -1, 0),
        intArrayOf(1, 0, 0, 0),
        intArrayOf(0, 1, 1, 0)
    )
    private val MOON_L_COEFFS = doubleArrayOf(
        6288774.0, 1274027.0, 658314.0, 213618.0, -185116.0, -114332.0, 58793.0, 57066.0, 53322.0, 45758.0, -40923.0, -34720.0, -30383.0
    )

    // Latitude (Sine) - Source: Meeus Table 47.B
    // Units: Millionths of degree
    private val MOON_B_ARGS = arrayOf(
        intArrayOf(0, 0, 0, 1),
        intArrayOf(0, 0, 1, 1),
        intArrayOf(0, 0, 1, -1),
        intArrayOf(2, 0, -1, 1),
        intArrayOf(2, 0, 0, 1)
    )
    private val MOON_B_COEFFS = doubleArrayOf(
        5128122.0, 280602.0, 277693.0, 173237.0, 55413.0
    )

    // Distance (Cosine) - Source: Meeus Table 47.A (Columns Sigma R)
    // Units: Meters? No, "terms of distance should be used to find distance in Kilometers".
    // Wait, Meeus says: "The sums Sigma R... are in meters." (p342 Ed 1? Or p339 Ed 2?)
    // "Coefficients are in meters."
    // Let's verify scaling.
    // Major term (0,0,0,0) is usually handled by the constant?
    // Meeus formula 47.4: Delta = 385000.56 + Sigma R / 1000.0 (km).
    // So Sigma R is in meters.
    private val MOON_R_ARGS = arrayOf(
        intArrayOf(0, 0, 1, 0),
        intArrayOf(2, 0, -1, 0),
        intArrayOf(2, 0, 0, 0),
        intArrayOf(0, 0, 2, 0),
        intArrayOf(0, 1, 0, 0)
    )
    private val MOON_R_COEFFS = doubleArrayOf(
        -20905355.0, -3699111.0, -2955968.0, -569925.0, 48888.0
    )
}

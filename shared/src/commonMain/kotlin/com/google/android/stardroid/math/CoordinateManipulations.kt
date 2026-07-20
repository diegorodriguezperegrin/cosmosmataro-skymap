package com.google.android.stardroid.math

import com.google.android.stardroid.math.MathUtils.asin
import com.google.android.stardroid.math.MathUtils.atan2
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin

/**
 * Utilities for manipulating different coordinate systems.
 */

// Value of the obliquity of the ecliptic for J2000
private const val OBLIQUITY = 23.439281f * DEGREES_TO_RADIANS

/**
 * Updates the given vector with the supplied [RaDec].
 */
fun Vector3.updateFromRaDec(raDec: RaDec) {
    this.updateFromRaDec(raDec.ra, raDec.dec)
}

/**
 * Updates these coordinates with the given ra and dec in degrees.
 */
private fun Vector3.updateFromRaDec(ra: Float, dec: Float) {
    val raRadians = ra * DEGREES_TO_RADIANS
    val decRadians = dec * DEGREES_TO_RADIANS
    this.x = cos(raRadians) * cos(decRadians)
    this.y = sin(raRadians) * cos(decRadians)
    this.z = sin(decRadians)
}

/** Returns the RA in degrees from the given vector assuming it's a unit vector in Geocentric coordinates  */
fun getRaOfUnitGeocentricVector(v: Vector3): Float {
    // Assumes unit sphere.
    return RADIANS_TO_DEGREES * atan2(v.y, v.x)
}

/** Returns the declination in degrees from the given vector assuming it's a unit vector in Geocentric coordinates  */
fun getDecOfUnitGeocentricVector(v: Vector3): Float {
    // Assumes unit sphere.
    return RADIANS_TO_DEGREES * asin(v.z)
}

/**
 * Converts ra and dec to x,y,z Geocentric where the point is place on the unit sphere.
 */
fun getGeocentricCoords(raDec: RaDec): Vector3 {
    return getGeocentricCoords(raDec.ra, raDec.dec)
}

/**
 * Converts ra and dec to x,y,z Geocentric where the point is place on the unit sphere.
 */
fun getGeocentricCoords(ra: Float, dec: Float): Vector3 {
    val coords = Vector3(0.0f, 0.0f, 0.0f)
    coords.updateFromRaDec(ra, dec)
    return coords
}

/**
 * Converts to coordinates centered on Earth in the Earth's rotational plane to
 * coordinates in Earth's equatorial plane.
 */
fun convertToEquatorialCoordinates(earthOrbitalPlane : Vector3): Vector3 {
    return Vector3(
        earthOrbitalPlane.x,
        earthOrbitalPlane.y * cos(OBLIQUITY) - earthOrbitalPlane.z * sin(OBLIQUITY),
        earthOrbitalPlane.y * sin(OBLIQUITY) + earthOrbitalPlane.z * cos(OBLIQUITY)
    )
}

/**
 * Converts screen coordinates (pixels) to a direction vector in sky coordinates.
 * Returns null if the transformation cannot be computed.
 */
fun convertScreenToSky(screenX: Float, screenY: Float, invertedTransform: Matrix4x4?): Vector3? {
    if (invertedTransform == null) return null
    
    // Apply inverse transformation to get from screen to world coordinates.
    // We must manually handle the w-component for perspective division because
    // Matrix4x4.times(Vector3) assumes w=1 and drops the result w.
    val m = invertedTransform.floatArray
    val x = screenX
    val y = screenY
    val z = 0f
    val w = 1f

    val resultX = m[0] * x + m[4] * y + m[8] * z + m[12] * w
    val resultY = m[1] * x + m[5] * y + m[9] * z + m[13] * w
    val resultZ = m[2] * x + m[6] * y + m[10] * z + m[14] * w
    val resultW = m[3] * x + m[7] * y + m[11] * z + m[15] * w

    // Perspective division
    if (kotlin.math.abs(resultW) < 1e-6) return null
    val oneOverW = 1.0f / resultW
    
    val worldX = resultX * oneOverW
    val worldY = resultY * oneOverW
    val worldZ = resultZ * oneOverW
    
    // Normalize to get direction vector
    val length = kotlin.math.sqrt(worldX * worldX + worldY * worldY + worldZ * worldZ)
    if (length < 0.0001f) return null
    
    return Vector3(worldX / length, worldY / length, worldZ / length)
}

/**
 * Calculates the Precession Matrix (P) for a given time.
 * P transforms coordinates from the Mean Equinox of J2000.0 to the Mean Equinox of Date.
 * v_Date = P * v_J2000
 *
 * Implements IAU 2006 Precession (Capitaine et al. 2003).
 *
 * @param timeInMillis Time in milliseconds since the epoch.
 * @return 3x3 Precession Matrix
 */
fun getPrecessionMatrix(timeInMillis: Long): Matrix3x3 {
    // Julian Centuries since J2000.0 (TT)
    // T = (JD - 2451545.0) / 36525.0
    // TimeConstants.MILLISECONDS_PER_DAY = 86400000L
    // J2000 epoch is Jan 1 2000 12:00 TT (roughly matches Unix epoch offset logic used in app)
    // The app generally uses UTC, but for this precision TT-UTC difference (60s) is negligible.
    
    // J2000 in millis from Unix Epoch (1970)
    // 2000 is 30 years after 1970. 30 * 365.25 * 86400 * 1000 ~ 946,000,000,000 + leap seconds
    // Detailed: 10957 days from 1970 to 2000 (Jan 1). (7 leap years: 72,76,80,84,88,92,96)
    // 30 * 365 + 7 = 10957 days.
    // 10957.5 * 86400 * 1000 = 946728000000L
    
    // Using simple offset from J2000:
    val j2000Millis = 946728000000L
    val diffMillis = timeInMillis - j2000Millis
    val t = diffMillis / (36525.0 * 24.0 * 3600.0 * 1000.0) // Julian Centuries

    // IAU 2006 Precession Angles (arcseconds)
    // Capitaine et al. 2003, A&A 412, 567-586
    /*
    zeta_A = 2306.2181 * T + 0.30188 * T^2 + 0.017998 * T^3
    z_A    = 2306.2181 * T + 1.09468 * T^2 + 0.018203 * T^3
    theta_A= 2004.3109 * T - 0.42665 * T^2 - 0.041833 * T^3
    */
    val zetaA_arcsec = 2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t
    val zA_arcsec = 2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t
    val thetaA_arcsec = 2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t

    // Convert to radians
    val arcsecToRad = (1.0 / 3600.0) * DEGREES_TO_RADIANS
    val zetaA = zetaA_arcsec * arcsecToRad
    val zA = zA_arcsec * arcsecToRad
    val thetaA = thetaA_arcsec * arcsecToRad

    /*
    Rotation Matrix P:
    R3(-zA) * R2(thetaA) * R3(-zetaA)
     */
     
    val cosZeta = kotlin.math.cos(zetaA)
    val sinZeta = kotlin.math.sin(zetaA)
    val cosZ = kotlin.math.cos(zA)
    val sinZ = kotlin.math.sin(zA)
    val cosTheta = kotlin.math.cos(thetaA)
    val sinTheta = kotlin.math.sin(thetaA)

    val xx = cosZeta * cosTheta * cosZ - sinZeta * sinZ
    val xy = -sinZeta * cosTheta * cosZ - cosZeta * sinZ
    val xz = -sinTheta * cosZ
    
    val yx = cosZeta * cosTheta * sinZ + sinZeta * cosZ
    val yy = -sinZeta * cosTheta * sinZ + cosZeta * cosZ
    val yz = -sinTheta * sinZ
    
    val zx = cosZeta * sinTheta
    val zy = -sinZeta * sinTheta
    val zz = cosTheta
    
    return Matrix3x3(
        xx.toFloat(), xy.toFloat(), xz.toFloat(),
        yx.toFloat(), yy.toFloat(), yz.toFloat(),
        zx.toFloat(), zy.toFloat(), zz.toFloat()
    )
}

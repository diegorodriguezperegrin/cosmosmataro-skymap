package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.*
import com.google.android.stardroid.math.MathUtils.cos
import com.google.android.stardroid.math.MathUtils.sin
import com.google.android.stardroid.math.MathUtils.tan
import com.google.android.stardroid.math.MathUtils.atan2
import com.google.android.stardroid.math.MathUtils.sqrt
import com.google.android.stardroid.math.MathUtils.asin
import java.util.Date
import kotlin.math.atan

/**
 * An object that orbits Earth.
 */
abstract class EarthOrbitingObject(solarSystemBody : SolarSystemBody) : SolarSystemObject(solarSystemBody) {
    override fun getRaDec(date: Date, location: LatLong?): RaDec {
        // 1. Get Geocentric Vector (Equatorial)
        //    Object_Helio - Earth_Helio
        val myHelio = solarSystemBody.getHeliocentricCoordinates(date.time)
        val earthHelio = SolarSystemBody.Earth.getHeliocentricCoordinates(date.time)
        val geoVector = myHelio - earthHelio
        val geoEqu = convertToEquatorialCoordinates(geoVector) 
        // geoEqu is (x, y, z) in Equatorial Frame (J2000). 
        // We need RA, Dec, and Distance (AU)
        
        val raRad = atan2(geoEqu.y, geoEqu.x)
        val decRad = asin(geoEqu.z / geoEqu.length)
        val distAU = geoEqu.length
        
        // If no location, return Geocentric
        if (location == null) {
            return RaDec(mod2pi(raRad) * RADIANS_TO_DEGREES, decRad * RADIANS_TO_DEGREES)
        }

        // 2. Apply Topocentric Parallax
        // Reference: Meeus Ch 40
        
        // rhoSinPhi and rhoCosPhi (Geocentric latitude factors).
        // Assuming spherical earth for simplicity or WGS84 approx? 
        // Let's use simple spherical for now (error < 1 arcsec usually)
        // Or better:
        val latRad = location.latitude * DEGREES_TO_RADIANS
        // u = atan(0.99664719 * tan(lat)) // WGS84 flattening correction
        // rhoSinPhi = 0.99664719 * sin(u) + (Height/6378140) * sin(lat)
        // rhoCosPhi = cos(u) + (Height/6378140) * cos(lat)
        // Ignoring height and flattening for "Good Enough" 0.01 deg
        val rhoSinPhi = sin(latRad)
        val rhoCosPhi = cos(latRad)
        
        // Sidereal Time
        // meanSiderealTime returns degrees? No, let's check MathUtils usage usually.
        // Looking at CelestialObject.kt: val gst: Float = meanSiderealTime(tmp, 0f) -> GHA = GST - RA
        // So GST is likely in degrees.
        val gstDeg = meanSiderealTime(date.time, 0f)
        val lstDeg = gstDeg + location.longitude
        val lstRad = lstDeg * DEGREES_TO_RADIANS
        
        // Hour Angle H = LST - RA
        val hRad = lstRad - raRad
        
        // Parallax Constant (Earth Radius / 1 AU)
        // 1 AU = 149,597,870.7 km
        // Earth Eq Radius = 6,378.14 km
        // sin(pi) = 6378.14 / 149597870.7 = 4.2635e-5
        val sinPi = 4.2635e-5f / distAU // scaled by inverse distance
        
        // Formulas (Meeus 40.2, 40.3)
        // tan(ra') = (cos(dec) sin(H) - A) / (cos(dec) cos(H) - B) ?? No wait using vector approach easier?
        // Let's use the explicit RA/Dec shift.
        // A = cos(phi) * sin(H) 
        // B = cos(phi) * cos(H) - rho * cos(phi) * sin(pi) / cos(dec) -- NO.
        
        // Meeus 40.2
        // delta_RA = atan2( -rho*cosPhi*sinPi*sinH, cosDec - rho*cosPhi*sinPi*cosH )
        val termA = -rhoCosPhi * sinPi * sin(hRad)
        val termB = cos(decRad) - rhoCosPhi * sinPi * cos(hRad)
        val deltaRa = atan2(termA, termB)
        val raTopRad = raRad + deltaRa
        
        // Meeus 40.3
        // tan(dec') = (sinDec - rho*sinPhi*sinPi) * cos(deltaRa) / (cosDec - rho*cosPhi*sinPi*cosH)
        val num = (sin(decRad) - rhoSinPhi * sinPi) * cos(deltaRa)
        val den = cos(decRad) - rhoCosPhi * sinPi * cos(hRad)
        val decTopRad = atan2(num, den)

        return RaDec(mod2pi(raTopRad) * RADIANS_TO_DEGREES, decTopRad * RADIANS_TO_DEGREES)
    }
}
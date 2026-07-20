package com.google.android.stardroid.control

import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.Matrix4x4
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.updateFromRaDec
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RaDec
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

/**
 * Interface for calculating celestial object positions.
 * Abstraction to decouple from android/java specific Universe/Date classes.
 */
interface CelestialObjectLocator {
    fun getRaDec(body: SolarSystemBody, timeMillis: Long, location: LatLong): RaDec
}

/**
 * Platform-agnostic logic for comparing detected point sources (bright spots) 
 * against a catalog of known celestial objects (navigation stars + planets).
 * 
 * This class corresponds to "The Brain" in the Thin Client architecture.
 */
class PointSourceComparator(private val locator: CelestialObjectLocator) {
    
    // Data classes to hold results platform-agnostically
    data class ReferenceStar(val name: String, val position: Vector3)
    
    data class MatchResult(
        val pointIndex: Int,       // Index in the input list of detected points
        val starName: String,      // Name of the matched star/planet
        val starPosition: Vector3, // 3D Position of the star
        val pointDirection: Vector3, // Unprojected 3D direction of the detected point
        val score: Float           // Dot product score (closer to 1.0 is better)
    )

    private val referenceStars = mutableListOf<ReferenceStar>()

    init {
        initializeReferenceStars()
    }

    private fun getGeocentricCoords(ra: Float, dec: Float): Vector3 {
        val DEGREES_TO_RADIANS = 0.017453292f
        val raRadians = ra * DEGREES_TO_RADIANS
        val decRadians = dec * DEGREES_TO_RADIANS
        return Vector3(
            cos(raRadians) * cos(decRadians),
            sin(raRadians) * cos(decRadians),
            sin(decRadians)
        )
    }

    private fun addStar(name: String, ra: Float, dec: Float) {
        referenceStars.add(ReferenceStar(name, getGeocentricCoords(ra, dec)))
    }

    private fun initializeReferenceStars() {
        referenceStars.clear()
        // 58 Navigation Stars
        // 1. Alpheratz (SHA 358 -> RA 2)
        addStar("Alpheratz", 2f, 29f)
        // 2. Ankaa (SHA 354 -> RA 6)
        addStar("Ankaa", 6f, -42f)
        // 3. Schedar (SHA 350 -> RA 10)
        addStar("Schedar", 10f, 56f)
        // 4. Diphda (SHA 349 -> RA 11)
        addStar("Diphda", 11f, -18f)
        // 5. Achernar (SHA 336 -> RA 24)
        addStar("Achernar", 24f, -57f)
        // 6. Hamal (SHA 328 -> RA 32)
        addStar("Hamal", 32f, 23f)
        // 7. Acamar (SHA 316 -> RA 44)
        addStar("Acamar", 44f, -40f) // Mag var
        // 8. Menkar (SHA 315 -> RA 45)
        addStar("Menkar", 45f, 4f)
        // 9. Mirfak (SHA 309 -> RA 51)
        addStar("Mirfak", 51f, 50f)
        // 10. Aldebaran (SHA 291 -> RA 69)
        addStar("Aldebaran", 69f, 16f)
        // 11. Rigel (SHA 282 -> RA 78)
        addStar("Rigel", 78f, -8f)
        // 12. Capella (SHA 281 -> RA 79)
        addStar("Capella", 79f, 46f)
        // 13. Bellatrix (SHA 279 -> RA 81)
        addStar("Bellatrix", 81f, 6f)
        // 14. Elnath (SHA 279 -> RA 81)
        addStar("Elnath", 81f, 29f)
        // 15. Alnilam (SHA 276 -> RA 84)
        addStar("Alnilam", 84f, -1f)
        // 16. Betelgeuse (SHA 271 -> RA 89)
        addStar("Betelgeuse", 89f, 7f)
        // 17. Canopus (SHA 264 -> RA 96)
        addStar("Canopus", 96f, -53f)
        // 18. Sirius (SHA 259 -> RA 101)
        addStar("Sirius", 101f, -17f)
        // 19. Adhara (SHA 256 -> RA 104)
        addStar("Adhara", 104f, -29f)
        // 20. Procyon (SHA 245 -> RA 115)
        addStar("Procyon", 115f, 5f)
        // 21. Pollux (SHA 244 -> RA 116)
        addStar("Pollux", 116f, 28f)
        // 22. Avior (SHA 234 -> RA 126)
        addStar("Avior", 126f, -59f)
        // 23. Suhail (SHA 223 -> RA 137)
        addStar("Suhail", 137f, -43f)
        // 24. Miaplacidus (SHA 220 -> RA 140)
        addStar("Miaplacidus", 140f, -70f)
        // 25. Alphard (SHA 218 -> RA 142)
        addStar("Alphard", 142f, -9f)
        // 26. Regulus (SHA 208 -> RA 152)
        addStar("Regulus", 152f, 12f)
        // 27. Dubhe (SHA 194 -> RA 166)
        addStar("Dubhe", 166f, 62f)
        // 28. Denebola (SHA 183 -> RA 177)
        addStar("Denebola", 177f, 15f)
        // 29. Gienah (SHA 176 -> RA 184)
        addStar("Gienah", 184f, -17f)
        // 30. Acrux (SHA 173 -> RA 187)
        addStar("Acrux", 187f, -63f)
        // 31. Gacrux (SHA 172 -> RA 188)
        addStar("Gacrux", 188f, -57f)
        // 32. Alioth (SHA 167 -> RA 193)
        addStar("Alioth", 193f, 56f)
        // 33. Spica (SHA 159 -> RA 201)
        addStar("Spica", 201f, -11f)
        // 34. Alkaid (SHA 153 -> RA 207)
        addStar("Alkaid", 207f, 49f)
        // 35. Hadar (SHA 149 -> RA 211)
        addStar("Hadar", 211f, -60f)
        // 36. Menkent (SHA 149 -> RA 211) note: very close RA to Hadar
        addStar("Menkent", 211f, -36f)
        // 37. Arcturus (SHA 146 -> RA 214)
        addStar("Arcturus", 214f, 19f)
        // 38. Rigil Kentaurus (SHA 140 -> RA 220)
        addStar("Rigil Kentaurus", 220f, -61f)
        // 39. Zubenelgenubi (SHA 138 -> RA 222)
        addStar("Zubenelgenubi", 222f, -16f)
        // 40. Kochab (SHA 137 -> RA 223)
        addStar("Kochab", 223f, 74f)
        // 41. Alphecca (SHA 127 -> RA 233)
        addStar("Alphecca", 233f, 27f)
        // 42. Antares (SHA 113 -> RA 247)
        addStar("Antares", 247f, -26f)
        // 43. Atria (SHA 108 -> RA 252)
        addStar("Atria", 252f, -69f)
        // 44. Sabik (SHA 103 -> RA 257)
        addStar("Sabik", 257f, -16f)
        // 45. Shaula (SHA 97 -> RA 263)
        addStar("Shaula", 263f, -37f)
        // 46. Rasalhague (SHA 96 -> RA 264)
        addStar("Rasalhague", 264f, 13f)
        // 47. Eltanin (SHA 91 -> RA 269)
        addStar("Eltanin", 269f, 51f)
        // 48. Kaus Australis (SHA 84 -> RA 276)
        addStar("Kaus Australis", 276f, -34f)
        // 49. Vega (SHA 81 -> RA 279)
        addStar("Vega", 279f, 39f)
        // 50. Nunki (SHA 76 -> RA 284)
        addStar("Nunki", 284f, -26f)
        // 51. Altair (SHA 63 -> RA 297)
        addStar("Altair", 297f, 9f)
        // 52. Peacock (SHA 54 -> RA 306)
        addStar("Peacock", 306f, -57f)
        // 53. Deneb (SHA 50 -> RA 310)
        addStar("Deneb", 310f, 45f)
        // 54. Enif (SHA 34 -> RA 326)
        addStar("Enif", 326f, 10f)
        // 55. Alnair (SHA 28 -> RA 332)
        addStar("Alnair", 332f, -47f)
        // 56. Fomalhaut (SHA 16 -> RA 344)
        addStar("Fomalhaut", 344f, -30f)
        // 57. Markab (SHA 14 -> RA 346)
        addStar("Markab", 346f, 15f)
        // 58. Polaris (Approx)
        addStar("Polaris", 38f, 89f)
    }

    // RESTORED: Calculate Zenith internally for diagnostics and consistency tracking
    private fun calculateZenith(timeMillis: Long, location: LatLong): Vector3 {
        // Calculate Julian Date
        val jd = (timeMillis / 86400000.0) + 2440587.5
        val t = (jd - 2451545.0) / 36525.0

        // Mean Sidereal Time at Greenwich (IAU 1982) in degrees
        var gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t - (t * t * t) / 38710000.0
        
        // Normalize to [0, 360)
        gmst %= 360.0
        if (gmst < 0) gmst += 360.0

        // Local Sidereal Time
        var lmst = gmst + location.longitude
        lmst %= 360.0
        if (lmst < 0) lmst += 360.0

        // Zenith coordinates: RA = LMST, Dec = Latitude
        val zenithRa = lmst.toFloat()
        val zenithDec = location.latitude.toFloat()

        return getGeocentricCoords(zenithRa, zenithDec)
    }

    data class ScanResult(
        val matches: List<MatchResult>,
        val zenith: Vector3,
        val runLog: String, // debug log passed to UI
        val horizonPoints: List<Vector3> // debug visuals
    )

    /**
     * The main logic method.
     */
    fun computeMatches(
        detectedPoints: List<Pair<Float, Float>>,
        screenWidth: Float,
        screenHeight: Float,
        inverseViewMatrix: Matrix4x4,
        timeMillis: Long,
        location: LatLong
        // REMOVED: zenith argument - calculating internally
    ): ScanResult {
        val sb = StringBuilder()
        if (screenWidth <= 0 || screenHeight <= 0) return ScanResult(emptyList(), Vector3(0f,0f,1f), "Invalid Screen", emptyList())

        // Calculate Zenith
        val zenith = calculateZenith(timeMillis, location)
        val horizonThreshold = -0.01f // ~90.5 degrees (slightly below horizon)

        sb.append("Loc: ${location.latitude}, ${location.longitude} | ")
        sb.append("Zenith: ${zenith} | ")

        // 1. Prepare Targets (Filter by Horizon)
        val currentTargets = mutableListOf<ReferenceStar>()
        
        // Filter Reference Stars
        for (star in referenceStars) {
            val elev = star.position dot zenith
            if (elev >= horizonThreshold) {
                currentTargets.add(star)
            }
        }
        
        // Filter Planets
        val planets = listOf(SolarSystemBody.Jupiter, SolarSystemBody.Venus, SolarSystemBody.Mars, SolarSystemBody.Saturn)
        for (planet in planets) {
            val raDec = locator.getRaDec(planet, timeMillis, location)
            val coords = Vector3(0f, 0f, 0f)
            coords.updateFromRaDec(raDec)
            
            val elev = coords dot zenith
            if (elev >= horizonThreshold) {
                currentTargets.add(ReferenceStar(planet.name, coords))
            }
        }

        // 2. Process Points
        val candidates = mutableListOf<MatchCandidate>()
        
        for ((i, pt) in detectedPoints.withIndex()) {
            val (nx, ny) = pt
            val screenX = nx * screenWidth
            val screenY = ny * screenHeight
            
            val skyPos = unproject(screenX, screenHeight - screenY, screenWidth, screenHeight, inverseViewMatrix)
            
            if (skyPos != null) {
                val zenithDot = skyPos dot zenith
                
                // Debug log for bright points
                if (i < 3) { // Log first few points
                    sb.append("Pt$i dot=$zenithDot; ")
                }

                if (zenithDot < horizonThreshold) {
                    continue // Ignore below horizon
                }

                 for (target in currentTargets) {
                    val dot = target.position dot skyPos
                    if (dot > 0.990f) { // ~8 degrees
                        candidates.add(MatchCandidate(i, target, dot, skyPos))
                    }
                }
            }
        }

        // 4. Sort and Filter
        candidates.sortByDescending { it.score }

        val matches = mutableListOf<MatchResult>()
        val usedPoints = mutableSetOf<Int>()
        val usedStars = mutableSetOf<String>()

        for (candidate in candidates) {
            if (!usedPoints.contains(candidate.pointIndex) && !usedStars.contains(candidate.star.name)) {
                usedPoints.add(candidate.pointIndex)
                usedStars.add(candidate.star.name)
                matches.add(MatchResult(candidate.pointIndex, candidate.star.name, candidate.star.position, candidate.direction, candidate.score))
            }
        }

        // Generate Horizon Ring (Debug)
        // Create circle of points orthogonal to zenith
        val horizonPoints = mutableListOf<Vector3>()
        // Arbitrary North-ish vector to start (unless Zenith IS North)
        var northApprox = Vector3(0f, 0f, 1f)
        if (abs(zenith dot northApprox) > 0.9f) northApprox = Vector3(0f, 1f, 0f)
        
        val east = zenith * northApprox // Cross product
        east.normalize()
        val north = east * zenith
        north.normalize()

        // Generate circle
        for (angle in 0 until 360 step 10) {
            val rad = angle * 0.0174533f
            val cosA = cos(rad)
            val sinA = sin(rad)
            // p = north * cosA + east * sinA
            val p = (north * cosA) + (east * sinA)
            horizonPoints.add(p)
        }

        return ScanResult(matches, zenith, sb.toString(), horizonPoints)
    }

    /**
     * Returns a list of all reference stars (and calculated planets) that are likely visible
     * within the given Field of View from the specified look direction.
     * 
     * @param lookDir The center of the view.
     * @param fovDegrees The approximate field of view diameter (or cone angle).
     */
    fun getVisibleStars(
        lookDir: Vector3,
        fovDegrees: Float,
        timeMillis: Long,
        location: LatLong
    ): List<ReferenceStar> {
        // Calculate Cosine Threshold
        // FOV is diameter, so radius is FOV/2. 
        // We add a margin (e.g. 10 degrees) to be safe.
        val margin = 10f
        val limitRad = (fovDegrees / 2 + margin) * 0.017453292f
        val threshold = cos(limitRad)

        // 1. Prepare Targets
        val candidates = mutableListOf<ReferenceStar>()
        
        // Update planets
        val planets = listOf(
            SolarSystemBody.Jupiter,
            SolarSystemBody.Venus,
            SolarSystemBody.Mars,
            SolarSystemBody.Saturn
        )
        
        // Add Fixed Stars
        for (star in referenceStars) {
             if ((star.position dot lookDir) > threshold) {
                 candidates.add(star)
             }
        }
        
        // Add Planets
        for (planet in planets) {
            val raDec = locator.getRaDec(planet, timeMillis, location)
            val coords = Vector3(0f, 0f, 0f)
            coords.updateFromRaDec(raDec)
            
            if ((coords dot lookDir) > threshold) {
                candidates.add(ReferenceStar(planet.name, coords))
            }
        }
        
        return candidates
    }

    private data class MatchCandidate(val pointIndex: Int, val star: ReferenceStar, val score: Float, val direction: Vector3)

    /**
     * Finds the name of the closest celestial object to the given direction, 
     * within the specified angular limit.
     */
    fun getObjectName(
        lookDir: Vector3, 
        limitDegrees: Float,
        timeMillis: Long,
        location: LatLong
    ): String? {
        val limitRad = limitDegrees * 0.017453292f
        val threshold = cos(limitRad)
        
        // 1. Prepare Targets (Stars + Planets)
        val currentTargets = mutableListOf<ReferenceStar>()
        currentTargets.addAll(referenceStars)
        
        val planets = listOf(
            SolarSystemBody.Jupiter,
            SolarSystemBody.Venus,
            SolarSystemBody.Mars,
            SolarSystemBody.Saturn
        )
        
        for (planet in planets) {
            val raDec = locator.getRaDec(planet, timeMillis, location)
            val coords = Vector3(0f, 0f, 0f)
            coords.updateFromRaDec(raDec)
            currentTargets.add(ReferenceStar(planet.name, coords))
        }

        var bestName: String? = null
        var bestDot = -1.0f

        for (target in currentTargets) {
            val dot = lookDir dot target.position
            if (dot > threshold && dot > bestDot) {
                bestDot = dot
                bestName = target.name
            }
        }
        return bestName
    }

    private val matcher = PatternMatcher()

    /**
     * Attempts to align the view by matching the detected star pattern (triangle) 
     * against the catalog.
     */
    fun computePatternMatch(
        detectedPoints: List<Pair<Float, Float>>,
        screenWidth: Float,
        screenHeight: Float,
        inverseViewMatrix: Matrix4x4,
        timeMillis: Long,
        location: LatLong
    ): PatternMatcher.MatchResult? {
        if (detectedPoints.size < 3) return null

        // 1. Unproject Points
        // We only take the top 3 unprojected vectors for the pattern
        val unprojected = mutableListOf<Vector3>()
        for ((nx, ny) in detectedPoints) {
            val screenX = nx * screenWidth
            val screenY = ny * screenHeight
            val skyPos = unproject(screenX, screenHeight - screenY, screenWidth, screenHeight, inverseViewMatrix)
            if (skyPos != null) {
                unprojected.add(skyPos)
            }
        }
        
        if (unprojected.size < 3) return null

        // 2. Get Candidates
        // Use the center of the screen as look direction for finding visible stars
        val centerDir = unproject(screenWidth / 2, screenHeight / 2, screenWidth, screenHeight, inverseViewMatrix)
                        ?: return null
                        
        // Estimate FOV based on screen width/height unprojection or just assume standard ~45
        // Or calculate angle between center and corner.
        // For simplicity now, hardcode 60 degrees search cone
        val candidates = getVisibleStars(centerDir, 60f, timeMillis, location)
        
        // 3. Match
        return matcher.matchPattern(unprojected, candidates)
    }

    /**
     * Unprojects screen coordinates to a 3D vector.
     */
     private fun unproject(x: Float, y: Float, width: Float, height: Float, inverseMatrix: Matrix4x4): Vector3? {
        val x2 = 2f * x / width - 1f
        val y2 = 2f * y / height - 1f
        val z2 = 1f // Looking effectively at infinity on the far clip plane
        
        val res = FloatArray(4)
        inverseMatrix.multiplyMMV(res, 0, floatArrayOf(x2, y2, 1f, 1f), 0)
        
        if (res[3] == 0f) return null
        val rw = 1f / res[3]
        return Vector3(res[0] * rw, res[1] * rw, res[2] * rw).apply { normalize() }
    }
}

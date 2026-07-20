package com.google.android.stardroid.control

import com.google.android.stardroid.math.Vector3
import kotlin.math.abs

/**
 * Identifies stars by matching the geometry of detected point triangles against
 * the catalog of known stars.
 *
 * Algorithm: "Triangle Matching"
 * 1. Forms a triangle from the 3 brightest detected points.
 * 2. Compares its side lengths (angular distances) against all possible triangles
 *    formed by candidate stars in the current field of view.
 * 3. A match is found if all 3 sides match within a tolerance.
 */
class PatternMatcher {

    /**
     * Represents a triangle formed by 3 points on the unit sphere.
     * Sides s1, s2, s3 are the chord lengths (Euclidean distance) between vertices.
     * We use chord lengths because they are rotationally invariant.
     */
    data class Triangle(
        val p1: Vector3,
        val p2: Vector3,
        val p3: Vector3,
        val s1: Float, // Distance p1-p2
        val s2: Float, // Distance p2-p3
        val s3: Float  // Distance p3-p1
    ) {
        companion object {
            fun from(a: Vector3, b: Vector3, c: Vector3): Triangle {
                return Triangle(
                    a, b, c,
                    a.distanceFrom(b),
                    b.distanceFrom(c),
                    c.distanceFrom(a)
                )
            }
        }
    }

    data class MatchResult(
        val detectedTriangle: Triangle,
        val catalogTriangle: Triangle,
        // Mapping from the detected point vector to the identified ReferenceStar
        val mapping: Map<Vector3, PointSourceComparator.ReferenceStar>
    )

    /**
     * Attempts to find a matching star pattern.
     *
     * @param detectedPoints List of unit vectors representing bright spots on screen (unprojected).
     * @param candidateStars List of known stars currently visible in the sky direction.
     * @param tolerance Error tolerance for side length mismatch (e.g. 0.02 for ~1 degree error).
     */
    fun matchPattern(
        detectedPoints: List<Vector3>,
        candidateStars: List<PointSourceComparator.ReferenceStar>,
        tolerance: Float = 0.02f
    ): MatchResult? {
        // Need at least 3 points to form a triangle
        if (detectedPoints.size < 3 || candidateStars.size < 3) return null

        // 1. Form the "Target" triangle from the top 3 detected points
        // Assuming detectedPoints are sorted by brightness (implied by usage)
        val p1 = detectedPoints[0]
        val p2 = detectedPoints[1]
        val p3 = detectedPoints[2]
        val detectedTri = Triangle.from(p1, p2, p3)

        // 2. Optimization: Filter candidates?
        // For now, with < 60 stars, O(N^3) is acceptable (60^3 = 216,000 checks)
        // actually only ~30,000 combinations.
        
        // 3. Brute force check all triangles in candidates
        val n = candidateStars.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                for (k in j + 1 until n) {
                    val s1 = candidateStars[i]
                    val s2 = candidateStars[j]
                    val s3 = candidateStars[k]
                    
                    val candidateTri = Triangle.from(s1.position, s2.position, s3.position)
                    
                    if (trianglesMatch(detectedTri, candidateTri, tolerance)) {
                        // We found a shape match!
                        // Now we need to determine which point maps to which star.
                        // We do this by matching the specific side lengths.
                        return constructMatchResult(detectedTri, candidateTri, 
                                                    p1, p2, p3, s1, s2, s3)
                    }
                }
            }
        }
        
        return null
    }

    private fun trianglesMatch(t1: Triangle, t2: Triangle, tol: Float): Boolean {
        // The side lengths are not sorted, so we need to check permutations.
        // A triangle is a set of 3 lengths {a, b, c}.
        // Check if set {t1.s1, t1.s2, t1.s3} ~ {t2.s1, t2.s2, t2.s3}
        
        val sides1 = floatArrayOf(t1.s1, t1.s2, t1.s3).apply { sort() }
        val sides2 = floatArrayOf(t2.s1, t2.s2, t2.s3).apply { sort() }
        
        return abs(sides1[0] - sides2[0]) < tol &&
               abs(sides1[1] - sides2[1]) < tol &&
               abs(sides1[2] - sides2[2]) < tol
    }

    private fun constructMatchResult(
        det: Triangle, cat: Triangle,
        p1: Vector3, p2: Vector3, p3: Vector3,
        s1: PointSourceComparator.ReferenceStar,
        s2: PointSourceComparator.ReferenceStar,
        s3: PointSourceComparator.ReferenceStar
    ): MatchResult {
        // We know the sets of sides match, but we need to map vertices.
        // Vertex P is opposite to Side S.
        // P1 is opposite s2 (dist p2-p3).
        // P2 is opposite s3 (dist p3-p1).
        // P3 is opposite s1 (dist p1-p2).
        
        // Let's match by identifying the unique vertices based on adjacent side lengths,
        // or effectively mapping the triangle vertices.
        
        // Simple heuristic: Try all 6 permutations of (s1, s2, s3) -> (p1, p2, p3)
        // and see which one aligns the best (lowest total distance error).
        // Wait, we don't need to align 3D positions (rotation is unknown). 
        // We align based on side lengths connected to the vertex.
        
        // For P1: connected sides are det.s3 (to P3) and det.s1 (to P2).
        // We look for a Star S_x that connects to sides of length ~det.s3 and ~det.s1.
        
        // Let's brute force the 6 permutations again to find the correct mapping.
        val stars = listOf(s1, s2, s3)
        
        // Permutations of stars
        val perms = listOf(
            listOf(s1, s2, s3), listOf(s1, s3, s2),
            listOf(s2, s1, s3), listOf(s2, s3, s1),
            listOf(s3, s1, s2), listOf(s3, s2, s1)
        )
        
        for (perm in perms) {
            val (c1, c2, c3) = perm
            val candTri = Triangle.from(c1.position, c2.position, c3.position)
            
            // Check if this permutation matches the DETECTED structure (P1, P2, P3)
            // detected s1 is P1-P2. candidate s1 must be C1-C2.
            // detected s2 is P2-P3. candidate s2 must be C2-C3.
            // detected s3 is P3-P1. candidate s3 must be C3-C1.
            
            val tol = 0.05f // slightly loose for individual side check
            if (abs(det.s1 - candTri.s1) < tol &&
                abs(det.s2 - candTri.s2) < tol &&
                abs(det.s3 - candTri.s3) < tol) {
                    
                val mapping = mapOf(
                    p1 to c1,
                    p2 to c2,
                    p3 to c3
                )
                return MatchResult(det, cat, mapping)
            }
        }
        
        // Should likely not happen if trianglesMatch passed, unless tolerance issues.
        // Return a raw guess? No, fail safe.
        throw IllegalStateException("Triangle sides matched but vertices could not be mapped.")
    }
}

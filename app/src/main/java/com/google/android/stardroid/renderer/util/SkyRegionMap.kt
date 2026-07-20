// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.renderer.util

import android.util.Log
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Vector3
import java.util.TreeMap
import kotlin.math.acos

/**
 * This is a utility class which divides the sky into a fixed set of regions
 * and maps each of the regions into a generic data object which contains the
 * data for rendering that region of the sky.  For a given frame, this class
 * will determine which regions are on-screen and which are totally
 * off-screen, and will return only the on-screen ones (so we can avoid paying
 * the cost of rendering the ones that aren't on-screen).  There should
 * typically be one of these objects per type of object being rendered: for
 * example, points and labels will each have their own SkyRegionMap.
 *
 * Each region consists of a center (a point on the unit sphere) and an angle,
 * and should contain every object on the unit sphere within the specified
 * angle from the region's center.
 *
 * This also allows for a special "catchall" region which is always rendered
 * and may contain objects from anywhere on the unit sphere.  This is useful
 * because, for small layers, it is cheaper to just render the
 * whole layer than to break it up into smaller pieces.
 *
 * The center of all regions is fixed for computational reasons.  This allows
 * us to find the distance between each region and the current look direction
 * once per frame and share that between all SkyRegionMaps.  For most types
 * of objects, they can also use regions with the same radius, which means
 * that they are the same exact part of the unit sphere.  For these we can
 * compute the regions which are on screen ("active regions") once per frame,
 * and share that between all SkyRegionMaps.  These are called "standard
 * regions", as opposed to "non-standard regions", where the region's angle
 * may be greater than that of the standard region.  Non-standard regions
 * are necessary for some types of objects, such as lines, which may not be
 * fully contained within any standard region.  For lines, we can find the
 * region center which is closest to fully containing the line, and simply
 * increase the angle until it does fully contain it.
 *
 * @param <RegionRenderingData> A object which contains the data needed to
 * render a sky region.
 * @author James Powell
</RegionRenderingData> */
class SkyRegionMap<RegionRenderingData> {
    /**
     * Interface for a factory that constructs a rendering data.
     */
    fun interface RegionDataFactory<RegionRenderingData> {
        fun construct(): RegionRenderingData
    }

    /**
     * This stores data that we only want to compute once per frame about
     * which regions are on the screen.  We don't want to compute these
     * regions for every manager separately, since we can share them
     * between managers.
     */
    class ActiveRegionData(
        private val regionCenterDotProducts: FloatArray,
        private val screenAngle: Float,
        val activeStandardRegions: ArrayList<Int>
    ) {
        init {
            if (regionCenterDotProducts.size != REGION_CENTERS.size) {
                Log.e(
                    "SkyRegionMap", "Bad regionCenterDotProducts length: " +
                            regionCenterDotProducts.size + " vs " + REGION_CENTERS.size
                )
            }
        }

        /**
         * Returns true if a non-standard region is active.
         * @param region The ID of the region to check
         * @param coverageAngle the coverage angle of the region.
         * @return true if the region is active, false if not.
         */
        fun regionIsActive(region: Int, coverageAngle: Float): Boolean {
            // A region cannot be active if the angle between screen's center
            // and the region's center is greater than the sum of the region angle
            // and screen angle.  I make a few definitions:
            // S = screen direction (look direction)
            // s = screen angle
            // R = region center
            // r = region angle
            // If the region is active, then
            // (angle between S and R) < s + r
            // These angles are between 0 and Pi, and cos is decreasing here, so
            // cos(angle between S and R) > cos(s + r)
            // S and R are unit vectors, so S dot R = cos(angle between S and R)
            // S dot R > cos(s + r)
            // So the regions where this holds true are the visible regions.
            return regionCenterDotProducts[region] > MathUtils.cos(coverageAngle + screenAngle)
        }
    }

    /**
     * Data representing an individual object's position in a region.
     * We care about the region itself for obvious reasons, but we care about
     * the dot product with the center because it is a measure of how
     * close it is to the center of a region.
     */
    class ObjectRegionData {
        var region: Int = CATCHALL_REGION_ID
        var regionCenterDotProduct: Float = -1f
    }

    // This is the coverage angle of each region.  For most sky region
    // maps, this will be null, which means that the coverage is specified by
    // REGION_COVERAGE_ANGLE_IN_RADIANS.  If some regions have a coverage
    // angle bigger than that, this must be non-NULL and should specify
    // the coverage angles for all of the regions.
    // Rather than only setting this if some regions have special angles,
    // we could just set it for everything.  The reason we don't is that
    // we can just use the standard visible regions if we don't set any
    // special coverage angles, which is a significant performance win.
    var regionCoverageAngles: FloatArray? = null

    // Maps the region ID to the rendering data for the region.
    private val regionData: MutableMap<Int, RegionRenderingData> = TreeMap()

    // Used to construct a new region the first time we access it.
    private var regionDataFactory: RegionDataFactory<RegionRenderingData>? = null

    // Clear the region map and coverage angles.
    fun clear() {
        regionData.clear()
        regionCoverageAngles = null
    }

    /**
     * Sets a function for constructing an empty rendering data object
     * for a sky region.  This is used to create an object if getRegionData()
     * is called and none already exists.
     */
    fun setRegionDataFactory(
        factory: RegionDataFactory<RegionRenderingData>?
    ) {
        regionDataFactory = factory
    }

    fun setRegionData(id: Int, data: RegionRenderingData) {
        regionData[id] = data
    }

    fun getRegionCoverageAngle(id: Int): Float {
        return if (regionCoverageAngles == null) REGION_COVERAGE_ANGLE_IN_RADIANS else regionCoverageAngles!![id]
    }

    /**
     * Sets the coverage angle for a sky region.  Needed for non-point
     * objects (see the javadoc for this class).
     * @param id
     * @param angleInRadians
     */
    fun setRegionCoverageAngle(id: Int, angleInRadians: Float) {
        if (regionCoverageAngles == null) {
            regionCoverageAngles = FloatArray(REGION_CENTERS.size)
            for (i in REGION_CENTERS.indices) {
                regionCoverageAngles!![i] = REGION_COVERAGE_ANGLE_IN_RADIANS
            }
        }
        if (angleInRadians < regionCoverageAngles!![id]) {
            Log.e(
                "SkyRegionMap", "Reducing coverage angle of region " + id +
                        " from " + regionCoverageAngles!![id] + " to " + angleInRadians
            )
        }
        regionCoverageAngles!![id] = angleInRadians
    }

    /**
     * Lookup the region data corresponding to a region ID.  If none exists,
     * and a region data constructor has been set (see setRegionDataConstructor),
     * that will be used to create a new region - otherwise, this will return
     * null.  This can be useful while building or updating a region, but to get
     * the region data when rendering a frame, use getDataForActiveRegions().
     * @param id
     * @return The data for the specified region.
     */
    fun getRegionData(id: Int): RegionRenderingData? {
        var data = regionData[id]
        if (data == null && regionDataFactory != null) {
            // If we have a factory, construct a new object.
            data = regionDataFactory!!.construct()
            regionData[id] = data!!
        }
        return data
    }

    /**
     * Returns the rendering data for the active regions.  When using a
     * SkyRegionMap for rendering, this is the function will return the
     * data for the regions you need to render.
     *
     * TODO(jpowell): I've done a little bit to verify that the regions I'm
     * computing here doesn't include regions that are obviously off screen, but
     * I should do some more work to verify that.
     *
     * @param regions
     * @return ArrayList of rendering data corresponding to the on-screen
     * regions.
     */
    fun getDataForActiveRegions(regions: ActiveRegionData): ArrayList<RegionRenderingData> {
        val data = ArrayList<RegionRenderingData>()

        // Always add the catchall region if non-NULL.
        val catchallData = regionData[CATCHALL_REGION_ID]
        if (catchallData != null) {
            data.add(catchallData)
        }

        if (regionCoverageAngles == null) {
            // Just return the data for the standard visible regions.
            for (region in regions.activeStandardRegions) {
                val regionData = regionData[region]
                if (regionData != null) {
                    data.add(regionData)
                }
            }
        } else {
            for (i in REGION_CENTERS.indices) {
                // Need to specially compute the visible regions.
                if (regions.regionIsActive(i, regionCoverageAngles!![i])) {
                    val regionData = regionData[i]
                    if (regionData != null) {
                        data.add(regionData)
                    }
                }
            }
        }
        return data
    }

    val dataForAllRegions: Collection<RegionRenderingData>
        get() = regionData.values

    companion object {
        const val CATCHALL_REGION_ID: Int = -1

        // 32 points to cover the sphere.  Each region is about 22.7 degrees.
        const val REGION_COVERAGE_ANGLE_IN_RADIANS: Float = 0.396023592f

        @JvmField
        val REGION_CENTERS: Array<Vector3> = arrayOf(
            Vector3(-0.850649066269f, 0.525733930059f, -0.000001851469f),
            Vector3(-0.934170971625f, 0.000004098751f, -0.356825719588f),
            Vector3(0.577349931933f, 0.577346773818f, 0.577354100533f),
            Vector3(0.577350600623f, -0.577350601554f, -0.577349603176f),
            Vector3(-0.577354427427f, -0.577349954285f, 0.577346424572f),
            Vector3(-0.577346098609f, 0.577353779227f, -0.577350928448f),
            Vector3(-0.577349943109f, -0.577346729115f, -0.577354134060f),
            Vector3(-0.577350598760f, 0.577350586653f, 0.577349620871f),
            Vector3(0.577354458161f, 0.577349932864f, -0.577346415259f),
            Vector3(0.577346091159f, -0.577353793196f, 0.577350921929f),
            Vector3(-0.850652559660f, -0.525728277862f, -0.000004770234f),
            Vector3(-0.934173742309f, 0.000002107583f, 0.356818466447f),
            Vector3(0.525734450668f, 0.000000594184f, -0.850648744032f),
            Vector3(0.000002468936f, -0.356819496490f, -0.934173349291f),
            Vector3(0.525727798231f, -0.000004087575f, 0.850652855821f),
            Vector3(-0.000002444722f, 0.356819517910f, 0.934173340909f),
            Vector3(-0.525727787986f, 0.000004113652f, -0.850652862340f),
            Vector3(0.000004847534f, 0.356824667575f, -0.934171371162f),
            Vector3(-0.000004885718f, -0.850652267225f, 0.525728750974f),
            Vector3(-0.356825215742f, -0.934171164408f, -0.000003995374f),
            Vector3(0.000000767410f, 0.850649364293f, 0.525733447634f),
            Vector3(0.356825180352f, 0.934171177447f, 0.000003952533f),
            Vector3(-0.000000790693f, -0.850649344735f, -0.525733478367f),
            Vector3(0.356818960048f, -0.934173554182f, -0.000001195818f),
            Vector3(0.850652555004f, 0.525728284381f, 0.000004773028f),
            Vector3(0.934170960449f, -0.000004090369f, 0.356825748459f),
            Vector3(-0.525734410621f, -0.000000609085f, 0.850648769177f),
            Vector3(-0.000004815869f, -0.356824668124f, 0.934171373956f),
            Vector3(0.000004877336f, 0.850652255118f, -0.525728769600f),
            Vector3(-0.356819001026f, 0.934173538350f, 0.000001183711f),
            Vector3(0.850649050437f, -0.525733955204f, 0.000001879409f),
            Vector3(0.934173759073f, -0.000002136454f, -0.356818422675f)
        )

        /**
         * Computes the data necessary to determine which regions on the screen
         * are active.  This should be produced once per frame and passed to
         * the getDataForActiveRegions method of all SkyRegionMap objects to
         * get the active regions for each map.
         *
         * @param lookDir The direction the user is currently facing.
         * @param fovyInDegrees The field of view (in degrees).
         * @param aspect The aspect ratio of the screen.
         * @return A data object containing data for quickly determining the
         * active regions.
         */
        @JvmStatic
        fun getActiveRegions(
            lookDir: Vector3,
            fovyInDegrees: Float,
            aspect: Float
        ): ActiveRegionData {
            // We effectively compute a screen "region" here.  The center of this
            // region is the look direction, and the radius is the angle between
            // the center and one of the corners.  If any region intersects the
            // screen region, we consider that region to be active.
            //
            // First, we compute the screen angle.  The angle between the vectors
            // to the top of the screen and the center of the screen is defined to
            // be fovy/2.
            // The distance between the top and center of the view plane, then, is
            // sin(fovy / 2).  The difference between the right and center must be.
            // (width / height) * sin(fovy / 2) = aspect * sin(fovy / 2)
            // This gives us a right triangle to find the distance between the center
            // and the corner of the screen.  This distance is:
            // d = sin(fovy / 2) * sqrt(1 + aspect^2).
            // The angle for the screen region is the arcsin of this value.
            val halfFovy = fovyInDegrees * DEGREES_TO_RADIANS / 2
            val screenAngle = MathUtils.asin(
                MathUtils.sin(halfFovy) * MathUtils.sqrt(1 + aspect * aspect)
            )

            // Next, determine whether or not the region is active.  See the
            // regionIsActive method for an explanation of the math here.
            // We don't use that method because if we did, we would repeatedly
            // compute the same cosine in that function.
            val angleThreshold = screenAngle + REGION_COVERAGE_ANGLE_IN_RADIANS
            val dotProductThreshold = MathUtils.cos(angleThreshold)
            val regionCenterDotProducts = FloatArray(REGION_CENTERS.size)
            val activeStandardRegions = ArrayList<Int>()
            for (i in REGION_CENTERS.indices) {
                val dotProduct = lookDir dot REGION_CENTERS[i]
                regionCenterDotProducts[i] = dotProduct
                if (dotProduct > dotProductThreshold) {
                    activeStandardRegions.add(i)
                }
            }

            // Log.d("SkyRegionMap", "ScreenAngle: " + screenAngle);
            // Log.d("SkyRegionMap", "Angle Threshold: " + angleThreshold);
            // Log.d("SkyRegionMap", "DP Threshold: " + dotProductThreshold);
            return ActiveRegionData(
                regionCenterDotProducts, screenAngle,
                activeStandardRegions
            )
        }

        /**
         * Returns the region that a point belongs in.
         *
         * @param position
         * @return The region the point belongs in.
         */
        @JvmStatic
        fun getObjectRegion(position: Vector3): Int {
            return getObjectRegionData(position).region
        }

        /**
         * Returns the region a point belongs in, as well as the dot product of the
         * region center and the position.  The latter is a measure of how close it
         * is to the center of the region (1 being a perfect match).
         *
         * TODO(jpowell): I think this is useful for putting lines into regions, but
         * if I don't end up using this when I implement that, I should delete this.
         * @param position
         * @return The closest region and dot product with center of that region.
         */
        @JvmStatic
        fun getObjectRegionData(position: Vector3): ObjectRegionData {
            // The closest region will minimize the angle between the vectors, which
            // will maximize the dot product, so we just return the region which
            // does that.
            val data = ObjectRegionData()
            for (i in REGION_CENTERS.indices) {
                val dotProduct = REGION_CENTERS[i] dot position
                if (dotProduct > data.regionCenterDotProduct) {
                    data.regionCenterDotProduct = dotProduct
                    data.region = i
                }
            }

            // For debugging only: make sure we're within the maximum region coverage angle.
            if (data.regionCenterDotProduct < MathUtils.cos(REGION_COVERAGE_ANGLE_IN_RADIANS)) {
                Log.e(
                    "ActiveSkyRegionData",
                    "Object put in region, but outside of coverage angle. " +
                            "Angle was " + acos(data.regionCenterDotProduct.toDouble()) + " vs " +
                            REGION_COVERAGE_ANGLE_IN_RADIANS + ". Region was " + data.region
                )
            }
            return data
        }
    }
}

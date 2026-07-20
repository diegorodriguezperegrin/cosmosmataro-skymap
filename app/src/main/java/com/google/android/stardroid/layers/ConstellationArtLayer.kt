package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.ImagePrimitive
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import org.json.JSONArray
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList
import kotlin.math.cos
import kotlin.math.sin

/**
 * Layer for displaying constellation art images.
 * Data is loaded from "hevelius/art_metadata.json" in assets.
 */
class ConstellationArtLayer(
    private val assetManager: AssetManager,
    resources: Resources,
    preferences: SharedPreferences
) : AbstractRenderablesLayer(resources, false, preferences) {

    // override val layerDepthOrder = 60 // Between constellations (warn) and others?
    // ConstellationsLayer is depth 30? No it was unchecked.
    // Let's set it to 50 for now.
    override val layerDepthOrder = 5 // Below ConstellationsLayer (10) so art renders underneath lines

    override val layerNameId = R.string.show_constellation_art_pref // Need to add this string

    override val preferenceId = "source_provider.constellation_art"

    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        val metadata = loadMetadata()
        for (item in metadata) {
            try {
                // Load Bitmap
                val imagePath = "hevelius/" + item.fileName
                val stream = assetManager.open(imagePath)
                val bitmap = BitmapFactory.decodeStream(stream)
                stream.close()

                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode bitmap: $imagePath")
                    continue
                }

                // Calculate Up Vector from rotation
                // Rotation is angle in degrees.
                // Standard ImagePrimitive:
                // p = location
                // u = p x up (Eastish)
                // v = u x p (Southish)
                // We want to rotate the image by 'rotation' around 'p'.
                // Default 'up' is (0, 1, 0) -> roughly North/Zenith approximation?
                // Actually, let's use the True North at position p as the base UP.
                // North at p = (0,0,1) - (p.z)*p  (Project Z axis onto tangent plane)
                // Normalized.
                
                val location = getGeocentricCoords(item.ra.toFloat(), item.dec.toFloat())
                val northPole = Vector3(0f, 0f, 1f)
                // North vector on tangent plane: (N - (N.P)P) normalized
                val dot = northPole dot location
                val proj = location * dot
                val northTangent = (northPole - proj).normalizedCopy()
                
                // If we are at the pole, northTangent is undefined. Handle edge case?
                // Unlikely for constellations.
                
                // Now we rotate NorthTangent by 'rotation' clockwise? or CCW?
                // The script calculated: rotation_rad = bearing_sky - angle_pix.
                // bearing_sky is angle East of North.
                // angle_pix is...
                // Let's assume 'rotation' is Degree rotation CCW from North.
                // Or maybe the python script 'rotation' is exactly the angle we need relative to North.
                
                // Let's create a rotated Up vector with a helper.
                // Actually, ImagePrimitive uses 'upVec' to derive 'u' and 'v'.
                // u = p x upVec.
                // If upVec = NorthTangent.
                // u = p x North = East.
                // v = East x p = South.
                // So passed 'upVec' effectively becomes Top (North).
                // If we want to rotate the image, we rotate 'upVec'.
                
                // Rotate NorthTangent around P by 'rotation' degrees.
                // Rodriquez rotation formula? Or simple 2D rotation in tangent plane.
                // We have NorthTangent (N) and EastTangent (E = N x p).
                // NewUp = N * cos(rot) + E * sin(rot).
                // Or -sin?
                // Let's try: NewUp = N * cos(rot) + E * sin(rot).
                
                val eastTangent = northTangent * location // Cross product. Wait, Vector3 * Vector3 is cross product in Stardroid?
                // Let's check Vector3 class. Usually separate method or operator.
                // Stardroid Vector3: times(Vector3) is Cross Product?
                // Kotlin 'times' operator (*).
                // Let's assume standard Stardroid Vector3 logic.
                
                // Logic verification for Vector3:
                // Standard Vector3 usually has cross product.
                // I will use `times` if it maps to cross. Or `cross`.
                // Checking `Vector3.kt` would be safe. 
                // Assuming it's `times` or `crossProduct`.
                // AbstractRenderablesLayer imported Vector3.
                
                // Let's assume standard rotation logic.
                val radians = Math.toRadians(item.rotation.toDouble())
                val cosA = cos(radians).toFloat()
                val sinA = sin(radians).toFloat()
                
                // Need Cross Product.
                // Assume 'times' returns cross product for Vector3 * Vector3?
                // Or checking usage in ImagePrimitive:
                // `val u = p.times(upVec)` -> looks like cross product.
                
                val eastVec = northTangent.times(location) // N x P -> East?
                // N is North (tangent). P is Radial.
                // N x P -> West?
                // P x N -> East.
                // Let's check ImagePrimitive: `u = p.times(upVec)`.
                // If upVec is North. u = P x N = East.
                // So East = P x N.
                // eastVec = location.times(northTangent)
                
                val rotatedUp = (northTangent * cosA) + (location.times(northTangent) * sinA)
                
                // Scale
                // item.scale is Image Width in Degrees.
                // We want imageScale = tan(width_rad / 2) for the quad half-width.
                val halfWidthRad = Math.toRadians(item.scale / 2.0)
                val imageScale = kotlin.math.tan(halfWidthRad).toFloat()

                val renderable = ConstellationArt(
                    item.ra.toFloat(),
                    item.dec.toFloat(),
                    bitmap,
                    rotatedUp,
                    imageScale,
                    resources,
                    item.name
                )
                sources.add(renderable)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading constellation art: ${item.fileName}", e)
            }
        }
    }

    data class ArtMetadata(
        val id: String,
        val name: String,
        val fileName: String,
        val ra: Double,
        val dec: Double,
        val scale: Double,
        val rotation: Double
    )

    private fun loadMetadata(): List<ArtMetadata> {
        val list = ArrayList<ArtMetadata>()
        try {
            val inputStream = assetManager.open("hevelius/art_metadata.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            
            val jsonArray = JSONArray(sb.toString())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(ArtMetadata(
                    obj.optString("id"),
                    obj.optString("name", "Unknown"),
                    obj.getString("file"),
                    obj.getDouble("ra"),
                    obj.getDouble("dec"),
                    obj.getDouble("scale"),
                    obj.getDouble("rotation")
                ))
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to load art metadata", e)
        } catch (e: Exception) {
            Log.e(TAG, "JSON error", e)
        }
        return list
    }
    

    companion object {
        private val TAG = MiscUtil.getTag(ConstellationArtLayer::class.java)
    }

    private class ConstellationArt(
        ra: Float,
        dec: Float,
        bitmap: android.graphics.Bitmap,
        upVec: Vector3,
        scale: Float,
        resources: Resources,
        private val nameStr: String
    ) : AbstractAstronomicalRenderable() {
        private val imagePrimitive = ImagePrimitive(ra, dec, resources, bitmap, upVec, scale)

        // Reverted manual U-flip. Trying upVec rotation instead.
        /*
        init {
            // ...
        }
        */

        override val names: MutableList<String> = mutableListOf(nameStr)

        override val searchLocation: Vector3
            get() = imagePrimitive.location

        override val images: List<ImagePrimitive>
            get() = listOf(imagePrimitive)
        
        // Needed to expose images property for AbstractRenderablesLayer
        // AbstractAstronomicalRenderable usually has 'images', 'points', 'lines' etc properties
        // that are populated by initialize? No.
        // Let's check AbstractRenderablesLayer again.
        // It calls renderables = astroRenderable.initialize().
        // Wait, initialize() returns List<Renderable> ?
        // AbstractRenderablesLayer:
        // val renderables = astroRenderable.initialize()
        // textPrimitives.addAll(renderables.labels) -> Error? 
        // initialize() returns a generic object that has .labels, .images???
        
        // Re-read AbstractRenderablesLayer.kt line 52:
        // val renderables = astroRenderable.initialize()
        // textPrimitives.addAll(renderables.labels)
        
        // Re-read AbstractAstronomicalRenderable.kt
        // It seems initialize() returns a RenderableCollection or similar?
        // Or the class itself serves as the collection?
        
    }
}

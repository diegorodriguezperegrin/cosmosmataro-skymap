package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import org.cosmosmataro.skymap.R
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * A layer that shows the Galactic Equator and coordinate markers.
 */
class GalacticPlaneLayer(resources: Resources, preferences: SharedPreferences) :
    AbstractRenderablesLayer(resources, true, preferences) {

    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(GalacticPlaneRenderable(resources))
    }

    override val layerDepthOrder = 40
    override val preferenceId = "source_provider.galactic_plane"
    override val layerNameId = R.string.show_galactic_layer_pref

    private class GalacticPlaneRenderable(resources: Resources) : AbstractAstronomicalRenderable() {
        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()


        // ABGR format: 0xffff0000 -> R=00, G=00, B=ff (Blue)
        private val LINE_COLOR = 0xffff0000.toInt()
        private val labelColor = Color.rgb(0, 200, 150)
        
        // Basis Vectors for Galactic Coordinates in J2000
        private lateinit var uGalacticCenter: Vector3 // l=0, b=0 (X axis)
        private lateinit var uGalacticEast: Vector3   // l=90, b=0 (Y axis)
        private lateinit var uGalacticNorth: Vector3  // b=90 (Z axis)

        init {
            computeBasisVectors()

            // Galactic Equator Line
            val vertices = ArrayList<Vector3>()
            for (i in 0..360 step 5) {
                val l = i.toFloat() * DEGREES_TO_RADIANS
                // b = 0 for equator
                val pos = galacticToGeocentric(l, 0f)
                vertices.add(pos)
            }
            // Close the loop
            vertices.add(vertices[0])

            val line = LinePrimitive(LINE_COLOR, vertices, 1.5f)
            lines.add(line)

            // Labels
            labels.add(createLabel(0f, 0f, "l=0°"))
            labels.add(createLabel(90f * DEGREES_TO_RADIANS, 0f, "l=90°"))
            labels.add(createLabel(180f * DEGREES_TO_RADIANS, 0f, "l=180°"))
            labels.add(createLabel(270f * DEGREES_TO_RADIANS, 0f, "l=270°"))

            // North Galactic Pole
            labels.add(createLabel(0f, 90f * DEGREES_TO_RADIANS, "NGP"))
            // South Galactic Pole
            labels.add(createLabel(0f, -90f * DEGREES_TO_RADIANS, "SGP"))
        }

        private fun computeBasisVectors() {
            // North Galactic Pole (NGP) J2000
            // RA = 192.85948 deg, Dec = 27.12825 deg
            val raNGP = 192.85948f * DEGREES_TO_RADIANS
            val decNGP = 27.12825f * DEGREES_TO_RADIANS
            
            val z_g = Vector3(
                cos(decNGP) * cos(raNGP),
                cos(decNGP) * sin(raNGP),
                sin(decNGP)
            )

            // Galactic Center (GC) J2000 (Approx direction)
            // RA = 266.4051 deg, Dec = -28.936175 deg
            val raGC = 266.4051f * DEGREES_TO_RADIANS
            val decGC = -28.936175f * DEGREES_TO_RADIANS
            
            val x_g_approx = Vector3(
                cos(decGC) * cos(raGC),
                cos(decGC) * sin(raGC),
                sin(decGC)
            )
            
            // Y_g = Z_g cross X_approx (Galactic East)
            // Using Vector3 extension or manual cross product if not available in Vector3 class for Layer context
            // Vector3 in this codebase usually has a `times` operator or cross product method.
            // Let's assume standard Vector3 * Vector3 is cross product as seen in MilkyWayManager
            
            val y_g = (z_g * x_g_approx)
            y_g.normalize()
            
            // X_g = Y_g cross Z_g (Exact Galactic Center)
            val x_g = (y_g * z_g)
            x_g.normalize()

            uGalacticCenter = x_g
            uGalacticEast = y_g
            uGalacticNorth = z_g
        }

        private fun createLabel(l: Float, b: Float, text: String): TextPrimitive {
            val pos = galacticToGeocentric(l, b)
            val raDec = RaDec.fromGeocentricCoords(pos)
            return TextPrimitive(raDec.ra, raDec.dec, text, labelColor)
        }

        private fun galacticToGeocentric(l: Float, b: Float): Vector3 {
            // Spherical to Cartesian (Galactic)
            // xg projected onto X_axis (Center)
            // yg projected onto Y_axis (East)
            // zg projected onto Z_axis (North)
            
            val x_coeff = cos(b) * cos(l)
            val y_coeff = cos(b) * sin(l)
            val z_coeff = sin(b)

            // Linear combination of basis vectors
            // result = x_coeff * uGalacticCenter + y_coeff * uGalacticEast + z_coeff * uGalacticNorth
            
            val x = x_coeff * uGalacticCenter.x + y_coeff * uGalacticEast.x + z_coeff * uGalacticNorth.x
            val y = x_coeff * uGalacticCenter.y + y_coeff * uGalacticEast.y + z_coeff * uGalacticNorth.y
            val z = x_coeff * uGalacticCenter.z + y_coeff * uGalacticEast.z + z_coeff * uGalacticNorth.z

            return Vector3(x, y, z)
        }
    }
}

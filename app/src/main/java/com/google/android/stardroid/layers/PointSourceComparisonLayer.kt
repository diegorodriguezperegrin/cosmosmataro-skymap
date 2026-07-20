package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.control.PointSourceComparator
import com.google.android.stardroid.space.Universe
import org.cosmosmataro.skymap.R
import com.google.android.stardroid.math.Matrix4x4
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.ImagePrimitive
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.PointPrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.renderer.RendererObjectManager
import java.util.ArrayList
import java.util.concurrent.ConcurrentLinkedQueue

class PointSourceComparisonLayer(
    private val model: AstronomerModel,
    resources: Resources,
    preferences: SharedPreferences
) : AbstractLayer(resources, preferences) {

    private val detectedPoints = ConcurrentLinkedQueue<Pair<Float, Float>>()
    private val universe = Universe()
    
    private val calculatorAdapter = object : com.google.android.stardroid.control.CelestialObjectLocator {
        override fun getRaDec(body: com.google.android.stardroid.ephemeris.SolarSystemBody, timeMillis: Long, location: com.google.android.stardroid.math.LatLong): com.google.android.stardroid.math.RaDec {
            return universe.getRaDec(body, java.util.Date(timeMillis), location)
        }
    }
    
    private val comparator = PointSourceComparator(calculatorAdapter)

    private var rendererController: RendererController? = null
    
    // Lists for redraw
    private var currentLineList: List<LinePrimitive> = emptyList()
    private var currentPointList: List<PointPrimitive> = emptyList()

    override val layerDepthOrder = 90
    override val layerNameId = R.string.show_point_source_comparison_pref
    override val preferenceId = "point_source_comparison"
    override val layerName = "Point Source Comparison"
    
    // Empty lists for redraw method (required by signature)
    private val emptyTextList = emptyList<TextPrimitive>()
    private val emptyImageList = emptyList<ImagePrimitive>()

    
    override fun initialize() {
        // No initialization needed for comparator
    }

    override fun registerWithRenderer(rendererController: RendererController) {
        android.util.Log.e("SCREAM_TEST", ">>> I AM THE NEW VERSION 1.0.10 - LINE PRIMITIVES <<<")
        this.rendererController = rendererController
        super.registerWithRenderer(rendererController)
    }

    private var screenWidth = 0f
    private var screenHeight = 0f

    override fun updateLayerForControllerChange() {
        refresh()
    }

    /**
     * Finds the closest alignment target for the given sky position.
     * Note: This implementation currently relies on the standard catalog via a new comparator instance
     * or could be optimized to cache the last list. For now, we perform a lightweight check.
     * 
     * TODO: Expose current targets from PointSourceComparator to avoid re-calculation if needed,
     * or add a helper method there.
     */
    fun getObjectName(skyPos: Vector3, limitDegrees: Float): String? {
        return comparator.getObjectName(skyPos, limitDegrees, model.time.time, model.location)
    }

    fun setDetectedPoints(points: List<Pair<Float, Float>>, width: Float, height: Float) {
        detectedPoints.clear()
        detectedPoints.addAll(points)
        screenWidth = width
        screenHeight = height
        // Trigger refresh immediately as detections change
        refresh()
    }

    private fun refresh() {
        val points = ArrayList<PointPrimitive>()
        val lines = ArrayList<LinePrimitive>()
        val texts = ArrayList<TextPrimitive>()
        
        val matrix = rendererController?.invertedScreenTransformMatrix
        
        if (matrix != null && screenWidth > 0 && screenHeight > 0) {
            val detected = detectedPoints.toList()
            val time = model.time
            val location = model.location
            
            // DELEGATE TO KMP SHARED MODULE
            val scanResult = comparator.computeMatches(
                detected,
                screenWidth,
                screenHeight,
                matrix,
                time.time,
                location
            )
            val matches = scanResult.matches

            // DEBUG LOGGING
            android.util.Log.d("AR_MATCHES", "Detected ${detected.size} points. Matches found: ${matches.size}")

            if (matches.isNotEmpty()) {
                android.util.Log.d("AR_MATCHES", "Top Match: ${matches[0].starName} (Score: ${matches[0].score})")
            }

            // VISUALIZE MATCHES ONLY
            // Draw green circles around matched stars and red lines from detection to star
            for (match in matches) {
                // Red line from detected point to star position
                val line = LinePrimitive(Color.RED)
                line.vertices.add(match.pointDirection) 
                line.vertices.add(match.starPosition) 
                lines.add(line)
                
                // Green circle around star (square approximation)
                val s = match.starPosition
                val mSize = 0.02f
                val box = LinePrimitive(Color.GREEN)
                box.vertices.add(Vector3(s.x-mSize, s.y-mSize, s.z))
                box.vertices.add(Vector3(s.x+mSize, s.y-mSize, s.z))
                box.vertices.add(Vector3(s.x+mSize, s.y+mSize, s.z))
                box.vertices.add(Vector3(s.x-mSize, s.y+mSize, s.z))
                box.vertices.add(Vector3(s.x-mSize, s.y-mSize, s.z))
                lines.add(box)
            }
        }
        
        currentPointList = points
        currentLineList = lines
        val currentTextList = texts
        
        // Redraw
        redraw(currentTextList, currentPointList, currentLineList, emptyList(), emptyImageList, java.util.EnumSet.of(RendererObjectManager.UpdateType.Reset))
    }
}

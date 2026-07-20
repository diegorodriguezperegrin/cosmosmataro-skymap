
package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import org.cosmosmataro.skymap.R
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.updateFromRaDec
import com.google.android.stardroid.renderables.AbstractPrimitive
import com.google.android.stardroid.renderables.ImagePrimitive
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.PointPrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.space.Universe
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TrajectoryLayer(
    private val model: AstronomerModel,
    resources: Resources,
    preferences: SharedPreferences
) : AbstractLayer(resources, preferences) {

    private val universe = Universe()
    private var currentLineList: List<LinePrimitive> = emptyList()
    private var currentPointList: List<PointPrimitive> = emptyList()

    override fun initialize() {
        android.util.Log.d("TrajectoryLayer", "Initializing TrajectoryLayer")
        // No initial setup needed
    }

    // 50 is lower than SolarSystemLayer (60) but higher than StarsLayer (30)
    override val layerDepthOrder = 50
    
    // Dummy ID, we override the properties below
    override val layerNameId = R.string.app_name 
    override val preferenceId = "trajectory_layer"
    override val layerName = "Trajectory"

    private val emptyTextList = emptyList<TextPrimitive>()
    private val emptyImageList = emptyList<ImagePrimitive>()
    private var currentImageList: List<ImagePrimitive> = emptyList()

    override fun updateLayerForControllerChange() {
        refresh()
    }

    fun showTrajectory(body: SolarSystemBody) {
        val linePrimitives = ArrayList<LinePrimitive>()
        
        // Configuration
        // Configuration
        val centerTime = model.time.time
        val startOffset = -4 * 60 * 60 * 1000L // -4 hours
        val endOffset = 4 * 60 * 60 * 1000L // +4 hours
        val step = 2 * 60 * 1000L // 2 mins resolution
        val tickInterval = 60 * 60 * 1000L // 60 mins for ticks (aligned to segment start)
        
        // For dotted line (Future)
        val dashDuration = 5 * 60 * 1000L // 5 mins draw
        val gapDuration = 5 * 60 * 1000L  // 5 mins skip
        val cycleTime = dashDuration + gapDuration
        
        // Style
        val mainLineColor = Color.rgb(200, 200, 200) // Light Grey
        val mainLineWidth = 1.0f 
        val tickLineWidth = 1.0f
        
        val tempTime = Date()

        // 1. PAST: Solid Line [-4h to 0]
        val pastLine = LinePrimitive(mainLineColor, mutableListOf<Vector3>(), mainLineWidth)
        var currentTime = centerTime + startOffset
        
        // Generate Past Path
        // We go up to centerTime.
        while (currentTime <= centerTime) {
           addPointToLine(body, currentTime, centerTime, pastLine, tempTime)
           generateTick(body, currentTime, centerTime, tickInterval, linePrimitives, tickLineWidth, tempTime, mainLineColor)
           currentTime += step
        }
        // Add exact center point to connect seamlessly
        addPointToLine(body, centerTime, centerTime, pastLine, tempTime)
        
        if (pastLine.vertices.size > 1) {
            linePrimitives.add(pastLine)
        }

        // 2. FUTURE: Dotted Line [0 to +4h]
        currentTime = centerTime
        val endTime = centerTime + endOffset
        
        var currentSegment = LinePrimitive(mainLineColor, mutableListOf<Vector3>(), mainLineWidth)
        var isDrawing = true // Start at center (phase 0) which is start of dash
        
        // Start point
        addPointToLine(body, currentTime, centerTime, currentSegment, tempTime)

        var lastPhasePos = 0L // Tracks phase relative to center

        while (currentTime <= endTime) {
             val offset = currentTime - centerTime
             // Ensure positive modulo behavior
             val phase = if (offset >= 0) offset % cycleTime else (cycleTime + (offset % cycleTime)) % cycleTime
             
             val shouldDraw = phase < dashDuration
             
             if (shouldDraw != isDrawing) {
                 // State Change
                 if (isDrawing) {
                     // Finish Dash
                     if (currentSegment.vertices.size > 1) {
                        linePrimitives.add(currentSegment)
                     }
                     currentSegment = LinePrimitive(mainLineColor, mutableListOf<Vector3>(), mainLineWidth)
                 } else {
                     // Finish Gap (Start new Dash)
                     addPointToLine(body, currentTime, centerTime, currentSegment, tempTime)
                 }
                 isDrawing = shouldDraw
             }
             
             if (isDrawing) {
                 addPointToLine(body, currentTime, centerTime, currentSegment, tempTime)
             }
             
             // Independent Tick Generation
             generateTick(body, currentTime, centerTime, tickInterval, linePrimitives, tickLineWidth, tempTime, mainLineColor)
             
             currentTime += step
        }
        // Add final segment if valid
        if (isDrawing && currentSegment.vertices.size > 1) {
             linePrimitives.add(currentSegment)
        }
        
        // 3. Arrowhead (ImagePrimitive)
        if (linePrimitives.isNotEmpty()) {
             // Find last actual line segment to determine direction
             val lastLine = linePrimitives.findLast { it.vertices.size >= 2 }
             if (lastLine != null) {
                val count = lastLine.vertices.size
                val endPoint = lastLine.vertices[count - 1]
                val prevPoint = lastLine.vertices[count - 2]
                
                // Arrowhead removed by user request.
                // Keeping only markers and lines.
             }
        }

        currentLineList = linePrimitives
        currentPointList = emptyList()
        refresh()
    }
    
    // Check if tick should be generated at this time, and add it
    private fun generateTick(body: SolarSystemBody, time: Long, centerTime: Long, interval: Long, 
                             lines: ArrayList<LinePrimitive>, width: Float, tempDate: Date, color: Int) {
        val offsetMs = time - centerTime
        if (offsetMs % interval == 0L) {
             // We need a tangent. Calculate position at T and T+delta
             // Position at T
             val p0 = getPosition(body, time, centerTime, tempDate)
             // Position at T+2min (step)
             val p1 = getPosition(body, time + 2*60*1000L, centerTime, tempDate)
             
             val tangent = p1 - p0
             // Fix: Use property length2
             if (tangent.length2 > 0) {
                 tangent.normalize()
                 var perpendicular = p0 * tangent
                 perpendicular.normalize()
                 
                 val tickSize = 0.005f // Approx 0.3 degrees
                 val tickP1 = p0 + (perpendicular * tickSize)
                 val tickP2 = p0 - (perpendicular * tickSize)
                 
                 val tickLine = LinePrimitive(color, mutableListOf<Vector3>(), width)
                 tickLine.vertices.add(tickP1)
                 tickLine.vertices.add(tickP2)
                 lines.add(tickLine)
             }
        }
    }
    
    private fun getPosition(body: SolarSystemBody, time: Long, centerTime: Long, tempDate: Date): Vector3 {
        tempDate.time = time
        val raDec = universe.getRaDec(body, tempDate)
        // Diurnal Correction
        val offsetMs = time - centerTime
        val rotationDegrees = (offsetMs / (24.0 * 60 * 60 * 1000)) * 360.0
        raDec.ra -= rotationDegrees.toFloat()
        
        val vertex = Vector3(0f, 0f, 0f)
        vertex.updateFromRaDec(raDec)
        return vertex
    }
    
    // Helper to avoid duplication
    private fun addPointToLine(body: SolarSystemBody, time: Long, centerTime: Long, line: LinePrimitive, tempDate: Date) {
        val vertex = getPosition(body, time, centerTime, tempDate)
        // Avoid duplicate points if step is small
        if (line.vertices.isEmpty() || line.vertices.last() != vertex) {
            line.vertices.add(vertex)
        }
    }

    fun hideTrajectory() {
        currentLineList = emptyList()
        currentPointList = emptyList()
        currentImageList = emptyList()
        refresh()
    }
    
    private fun refresh() {
        redraw(emptyTextList, currentPointList, currentLineList, emptyList(), currentImageList, EnumSet.of(UpdateType.Reset))
    }
}

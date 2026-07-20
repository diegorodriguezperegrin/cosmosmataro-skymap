package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import org.cosmosmataro.skymap.R
import java.util.ArrayList

/**
 * Layer for the Horizon Line.
 */
class HorizonLayer(
    private val model: AstronomerModel,
    resources: Resources,
    preferences: SharedPreferences
) : AbstractRenderablesLayer(resources, true, preferences) {

    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(HorizonRenderable(model, resources))
    }

    override val layerDepthOrder = 90 // Above Ground (80), below Labels perhaps?
    override val layerNameId = R.string.show_horizon_lines_pref
    override val preferenceId = "source_provider.horizon_line"

    private class HorizonRenderable(
        private val model: AstronomerModel,
        resources: Resources
    ) : AbstractAstronomicalRenderable() {
        
        // Color: Cyan/Tea green distinct from Grid (Yellowish) and Ecliptic
        // ABGR format: 0xff007700 -> Darker Green
        private val LINE_COLOR = 0xff007700.toInt()
        private val horizonLine = LinePrimitive(LINE_COLOR)

        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()

        init {
            lines.add(horizonLine)
        }

        override fun update(): java.util.EnumSet<com.google.android.stardroid.renderer.RendererObjectManager.UpdateType> {
            // Recalculate the horizon based on current propertie
            horizonLine.vertices.clear()

            val zenith = model.zenith
            val north = model.north
            // Calculate East vector = North x Zenith (Assuming North is local North on horizon)
            // But wait, if North is NCP, then we need to project.
            // Based on AstronomerModelImpl, North IS Local North.
            
            val east = north * zenith
            east.normalize() // Safety

            val numSteps = 72 // Every 5 degrees
            
            for (i in 0..numSteps) {
                val angleRad = (i * 360.0f / numSteps) * com.google.android.stardroid.math.DEGREES_TO_RADIANS
                val cosA = kotlin.math.cos(angleRad)
                val sinA = kotlin.math.sin(angleRad)
                
                // Point on horizon circle
                val p = (north * cosA) + (east * sinA)
                horizonLine.vertices.add(p)
            }
            
            // Return Reset to force redraw of the new line primitive
            return java.util.EnumSet.of(com.google.android.stardroid.renderer.RendererObjectManager.UpdateType.Reset)
        }
    }
}

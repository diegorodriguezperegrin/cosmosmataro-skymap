package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.renderer.RendererController
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Layer that controls the "Dark Ground" overlay below the horizon.
 * It shares the same preference ID as the HorizonLayer ("source_provider.horizon").
 */
class LandscapeLayer(
    private val model: AstronomerModel,
    resources: Resources,
    private val preferences: SharedPreferences
) : Layer {
    
    private var renderer: RendererController? = null
    private val enabled = AtomicBoolean(false)
    
    override fun initialize() {}

    override fun registerWithRenderer(rendererController: RendererController) {
        this.renderer = rendererController
        rendererController.addUpdateClosure { updateLayer() }
        updateLayer()
    }

    override fun setVisible(visible: Boolean) {
        enabled.set(visible)
        updateLayer()
    }
    
    // Called every frame by the renderer (via update closure)
    private fun updateLayer() {
        val renderer = this.renderer ?: return
        val isVisible = enabled.get()
        
        // Ideally we only update orientation if visible.
        if (isVisible) {
             // Force enable (idempotent usually)
            renderer.queueEnableGround(true)
            
            // Update orientation from model
            // Model vectors (Zenith/North) are in J2000.
            // They change as time progresses.
            renderer.queueSetGroundOrientation(model.zenith, model.north)
        } else {
            renderer.queueEnableGround(false)
        }
    }

    override val preferenceId = "source_provider.landscape"
    // override val layerNameId = org.cosmosmataro.skymap.R.string.show_horizon_pref // Not part of interface
    override val layerDepthOrder = 80 
    
    // Using a simple name distinct from HorizonLayer to identify in debug logs if needed
    override val layerName = "Landscape"

    override fun searchByObjectName(name: String): List<com.google.android.stardroid.search.SearchResult> {
        return emptyList()
    }

    override fun getObjectNamesMatchingPrefix(prefix: String): Set<String> {
        return emptySet()
    }

    override fun searchByPosition(position: com.google.android.stardroid.math.Vector3, radiusDegrees: Float): List<com.google.android.stardroid.search.SearchResult> {
        return emptyList()
    }
}

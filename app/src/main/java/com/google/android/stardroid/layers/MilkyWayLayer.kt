package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import org.cosmosmataro.skymap.R
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.search.SearchResult
import com.google.android.stardroid.util.MiscUtil
import java.util.concurrent.locks.ReentrantLock

class MilkyWayLayer(resources: Resources) : Layer {
    private val rendererLock = ReentrantLock()
    private var renderer: RendererController? = null
    
    override fun initialize() {}
    
    override fun registerWithRenderer(rendererController: RendererController) {
        renderer = rendererController
        // Redraw/Enable if already visible? 
        // Layer interface calls setVisible logic usually.
    }

    override fun setVisible(visible: Boolean) {
        Log.d(TAG, "Setting showMilkyWay $visible")
        rendererLock.lock()
        try {
            if (visible) {
                 renderer?.queueEnableMilkyWay(R.drawable.milky_way)
            } else {
                 renderer?.queueDisableMilkyWay()
            }
        } finally {
            rendererLock.unlock()
        }
    }

    override val layerDepthOrder = -100 // Behind stars (-10 is Gradient)
    private val layerNameId = R.string.show_milky_way
    
    // Preference ID to toggle in settings
    override val preferenceId = "source_provider.milky_way"
    override val layerName = resources.getString(layerNameId)

    override fun searchByObjectName(name: String): List<SearchResult> {
        return emptyList()
    }

    override fun getObjectNamesMatchingPrefix(prefix: String): Set<String> {
        return emptySet()
    }

    override fun searchByPosition(position: Vector3, radiusDegrees: Float): List<SearchResult> {
        return emptyList()
    }

    companion object {
        private val TAG = MiscUtil.getTag(MilkyWayLayer::class.java)
    }
}

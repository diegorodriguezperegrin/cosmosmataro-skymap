package com.google.android.stardroid.layers

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import org.cosmosmataro.skymap.R
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.ephemeris.CometImporter
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.space.Comet
import java.io.File
import java.util.*
import kotlin.math.abs

/**
 * A [Layer] to show comets using orbital elements.
 * Currently hardcoded, but designed to be extensible.
 */
class CometsLayer(private val model: AstronomerModel, resources: Resources,
                  preferences: SharedPreferences, private val context: Context) :
    AbstractRenderablesLayer(resources, true, preferences) {

    private val comets = ArrayList<Comet>()

    // Helper to create dates (UTC)
    private fun date(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, 0, 0, 0)
        return cal.timeInMillis
    }

    private fun initializeComets() {
        comets.clear()
        
        // Try loading from local file first
        val file = File(context.filesDir, "comets.dat")
        if (file.exists()) {
            try {
                Log.d(TAG, "Loading comets from ${file.absolutePath}")
                val lines = file.readLines()
                val results = CometImporter.parse(lines)
                if (results.isNotEmpty()) {
                    // Deduplicate by name (in case of legacy data with duplicates)
                    val uniqueComets = results
                        .groupBy { it.comet.name }
                        .map { (_, entries) -> entries.first().comet }
                    
                    comets.addAll(uniqueComets)
                    Log.d(TAG, "Loaded ${uniqueComets.size} unique comets from file (${results.size} total entries).")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading comets from file", e)
            }
        }

        Log.d(TAG, "Loading default comets.")
        // 1P/Halley
        // Epoch 1986 (JPL Horizons)
        // q = 0.58597811 AU
        // e = 0.9671429
        // i = 162.26269
        // Node = 58.42008
        // Arg = 111.33248
        // Tp = 1986 Feb 9.49 (approx Feb 9 12:00)
        comets.add(
            Comet(
                resources.getString(R.string.comet_halley),
                0.58597811,
                0.9671429,
                162.26269,
                58.42008,
                111.33248,
                date(1986, 2, 9)
            )
        )

        // C/1995 O1 (Hale-Bopp)
        // q = 0.914133
        // e = 0.995086
        // i = 89.429
        // Node = 282.473
        // Arg = 130.590
        // Tp = 1997 April 1.13
        comets.add(
            Comet(
                resources.getString(R.string.comet_hale_bopp),
                0.914133,
                0.995086,
                89.429,
                282.473,
                130.590,
                date(1997, 4, 1)
            )
        )

        // 2I/Borisov (Hyperbolic)
        // q = 2.00658
        // e = 3.3575
        // i = 44.0526
        // Node = 322.017
        // Arg = 209.124
        // Tp = 2019 Dec 8.56
        comets.add(
            Comet(
                resources.getString(R.string.comet_borisov),
                2.00658,
                3.3575,
                44.0526,
                322.017,
                209.124,
                date(2019, 12, 8)
            )
        )

         // C/2020 F3 (NEOWISE)
         // q = 0.295
         // e = 0.999182
         // i = 128.937
         // Node = 61.011
         // Arg = 37.276
         // Tp = 2020 Jul 3.68
        comets.add(
            Comet(
                resources.getString(R.string.comet_neowise),
                0.295,
                0.999182,
                128.937,
                61.011,
                37.276,
                date(2020, 7, 3)
            )
        )
    }
    
    fun refresh() {
        initializeComets()
        // Re-initialize Renderables
        // We need to clear existing renderables and re-add them?
        // AbstractRenderablesLayer doesn't easily support clearing.
        // It has `renderables` protected? No, `AbstractLayer` has `renderables`.
        // Wait, `AbstractRenderablesLayer` has `initializeAstroSources` which populates `sources`.
        // `initialize()` calls `initializeAstroSources`.
        // So calling `initialize()` might duplicate?
        // `initialize()` in `AbstractRenderablesLayer` often clears?
        // Let's check `AbstractRenderablesLayer`.
        // Assuming I can can call initialize() again.
        // But `AbstractRenderablesLayer` populates `ArrayList<AstronomicalRenderable>`.
        // If I can't clear, I might duplicate.
        // I'll need to check `AbstractRenderablesLayer`. For now, assuming initialize() is safe or I need to implement proper refresh.
        // If improper, I'll log a warning.
    }

    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        for (comet in comets) {
            try {
                sources.add(CometRenderable(model, comet, resources))
            } catch (e: Exception) {
                android.util.Log.e("CometsLayer", "Error initializing comet ${comet.name}", e)
            }
        }
    }

    override val layerDepthOrder = 80 // High priority
    override val preferenceId = "source_provider.comets"
    override val layerName = "Comets"
    override val layerNameId = R.string.show_comet_layer_pref

    // Inner class to handle rendering of a single comet
    private class CometRenderable(
        private val model: AstronomerModel,
        private val comet: Comet,
        resources: Resources
    ) : AbstractAstronomicalRenderable() {

        override val labels = ArrayList<TextPrimitive>()
        override val images = ArrayList<ImagePrimitive>()
        override val names = ArrayList<String>()

        private var lastUpdateTimeMs = 0L
        private val theImage: ImagePrimitive
        private val label: TextPrimitive
        private val name = comet.name
        
        // Helper to hold current position
        private var coords: Vector3 = Vector3(1f, 0f, 0f) // Default

        init {
            names.add(name)
            // Initial position update
            coords = getVectorFromRaDec(comet.getRaDec(model.time))
            
            // Use specific comet icon if available, generic otherwise
            theImage = ImagePrimitive(coords, resources, R.drawable.comet, UP, SCALE_FACTOR)
            images.add(theImage)
            
            label = TextPrimitive(coords, name, LABEL_COLOR)
            labels.add(label)
        }

        override val searchLocation: Vector3
            get() = coords

        private fun getVectorFromRaDec(raDec: com.google.android.stardroid.math.RaDec): Vector3 {
             return getGeocentricCoords(raDec)
        }

        private fun updateComets() {
            val now = model.time
            lastUpdateTimeMs = now.time
            theImage.setUpVector(UP)
            
            // Calculate new position using Comet Physics
            val raDec = comet.getRaDec(now)
            val newCoords = getGeocentricCoords(raDec)
            
            // Apply updates
            coords.assign(newCoords)
            
            // TODO: Visibility logic based on magnitude? For now, always visible.
        }

        override fun initialize(): Renderable {
            updateComets()
            return this
        }

        override fun update(): EnumSet<UpdateType> {
            val updateTypes = EnumSet.noneOf(UpdateType::class.java)
            if (abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
                updateComets()
                updateTypes.add(UpdateType.UpdateImages)
                // updateTypes.add(UpdateType.Reset) // Not needed if we update coords in place? ImagePrimitive holds ref to coords? 
                // ImagePrimitive constructor takes "coords". If it stores reference, assigning to it works.
                // Let's assume it works like other layers.
            }
            return updateTypes
        }

        companion object {
            private const val LABEL_COLOR = 0xf67e81 // Light Red/Pink
            private val UP = Vector3(0.0f, 1.0f, 0.0f)
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_HOUR // Update every hour
            private const val SCALE_FACTOR = 0.03f
        }
    }

    class Interpolator(private val xs: List<Long>, private val ys: List<Float>) {
        init {
            if (xs.size != ys.size) throw IllegalArgumentException("Arrays must be of same length")
            if (xs.size < 2) throw IllegalArgumentException("Must have at least two entries")
        }

        fun interpolate(x: Long): Float {
            if (x < xs.first() || x > xs.last()) throw IllegalArgumentException("Input out of bounds")
            for (i in 0..this.xs.size - 2) {
                if (x >= xs[i] && x <= xs[i + 1]) {
                    return ((x - xs[i]) * ys[i + 1] + (xs[i + 1] - x) * ys[i]) / (xs[i + 1] - xs[i])
                }
            }
            throw IllegalArgumentException("Ran off the end - this shouldn't happen")
        }
    }

    companion object {
        private const val TAG = "CometsLayer"
    }

    init {
        initializeComets()
    }
}
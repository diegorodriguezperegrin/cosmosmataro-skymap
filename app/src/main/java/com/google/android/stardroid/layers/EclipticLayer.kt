// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import org.cosmosmataro.skymap.R
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import java.util.*

/**
 * Creates a Layer for the Ecliptic.
 *
 * @author John Taylor
 * @author Brent Bryan
 */
class EclipticLayer(resources: Resources, preferences: SharedPreferences) : AbstractRenderablesLayer
      (resources, false, preferences) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(EclipticRenderable(resources))
    }

    override val layerDepthOrder = 50
    override val layerNameId = R.string.show_grid_pref
    override val preferenceId = "source_provider.ecliptic"

    /** Implementation of [AstronomicalRenderable] for the ecliptic source.  */
    private class EclipticRenderable(resources: Resources) : AbstractAstronomicalRenderable() {
        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()

        companion object {
            private const val EARTHS_ANGULAR_TILT = 23.439281f
            // ABGR format: 0xff00ffff -> R=ff, G=ff, B=00 (Yellow)
            private val LINE_COLOR = 0xff00ffff.toInt()
            private const val EPSILON = EARTHS_ANGULAR_TILT * com.google.android.stardroid.math.DEGREES_TO_RADIANS
        }

        init {
            val title = resources.getString(R.string.ecliptic)
            labels.add(TextPrimitive(90.0f, EARTHS_ANGULAR_TILT, title, LINE_COLOR))
            labels.add(TextPrimitive(270f, -EARTHS_ANGULAR_TILT, title, LINE_COLOR))

            // Create line source with high resolution
            val vertices = ArrayList<Vector3>()
            val numSteps = 72 // Every 5 degrees
            val sinEpsilon = kotlin.math.sin(EPSILON)
            val cosEpsilon = kotlin.math.cos(EPSILON)

            for (i in 0..numSteps) {
                // Ecliptic Longitude (lambda)
                val lambdaDeg = i * (360.0f / numSteps)
                val lambdaRad = lambdaDeg * com.google.android.stardroid.math.DEGREES_TO_RADIANS

                // Conversion from Ecliptic (beta=0) to Equatorial
                // sin(delta) = sin(epsilon) * sin(lambda)
                // tan(alpha) = cos(epsilon) * tan(lambda)
                
                val sinDelta = sinEpsilon * kotlin.math.sin(lambdaRad)
                val deltaRad = kotlin.math.asin(sinDelta)
                
                val y = kotlin.math.sin(lambdaRad) * cosEpsilon
                val x = kotlin.math.cos(lambdaRad)
                val alphaRad = kotlin.math.atan2(y, x)
                
                val ra = com.google.android.stardroid.math.mod2pi(alphaRad) * com.google.android.stardroid.math.RADIANS_TO_DEGREES
                val dec = deltaRad * com.google.android.stardroid.math.RADIANS_TO_DEGREES
                
                vertices.add(getGeocentricCoords(ra, dec))
            }
            lines.add(LinePrimitive(LINE_COLOR, vertices, 1.5f)) // Standard thickness
        }
    }
}
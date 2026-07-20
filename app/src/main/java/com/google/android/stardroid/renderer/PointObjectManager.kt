// Copyright 2008 Google Inc.
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
package com.google.android.stardroid.renderer

import android.opengl.GLES20
import android.util.Log
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.PointPrimitive
import com.google.android.stardroid.renderer.shader.StarShaderProgram
import com.google.android.stardroid.renderer.util.SkyRegionMap
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.TextureReference
import org.cosmosmataro.skymap.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.EnumSet
import javax.microedition.khronos.opengles.GL10

class PointObjectManager(layer: Int, textureManager: TextureManager) :
    RendererObjectManager(layer, textureManager) {
    private var shaderProgram: StarShaderProgram? = null

    private class RegionData {
        var sources: ArrayList<PointPrimitive> = ArrayList()
        var vertexBuffer: FloatBuffer? = null
        var numVertices = 0
    }

    private var mNumPoints = 0
    private val mSkyRegions = SkyRegionMap<RegionData>()
    private var mTextureRef: TextureReference? = null

    init {
        mSkyRegions.setRegionDataFactory { RegionData() }
    }

    fun setShaderProgram(program: StarShaderProgram) {
        this.shaderProgram = program
    }

    fun updateObjects(points: List<PointPrimitive>, updateType: EnumSet<UpdateType>) {
        if (updateType.contains(UpdateType.Reset)) {
            // Check for reset
        } else if (updateType.contains(UpdateType.UpdatePositions)) {
            if (points.size != mNumPoints) {
                Log.e(
                    "PointObjectManager",
                    "Updating PointObjectManager a different number of points"
                )
                return
            }
        } else {
            return
        }

        val safePoints = ArrayList(points)
        mNumPoints = safePoints.size
        mSkyRegions.clear()

        if (COMPUTE_REGIONS) {
            for (point in safePoints) {
                val region = if (safePoints.size < MINIMUM_NUM_POINTS_FOR_REGIONS) 
                    SkyRegionMap.CATCHALL_REGION_ID 
                else 
                    SkyRegionMap.getObjectRegion(point.location)
                
                mSkyRegions.getRegionData(region)!!.sources.add(point)
            }
        } else {
            mSkyRegions.getRegionData(SkyRegionMap.CATCHALL_REGION_ID)!!.sources = safePoints
        }

        for (data in mSkyRegions.dataForAllRegions) {
            val numStars = data.sources.size
            val numFloats = numStars * FLOATS_PER_VERTEX

            val bb = ByteBuffer.allocateDirect(numFloats * 4)
            bb.order(ByteOrder.nativeOrder())
            data.vertexBuffer = bb.asFloatBuffer()
            data.numVertices = numStars

            val starWidthInTexels = 1.0f / NUM_STARS_IN_TEXTURE

            for (p in data.sources) {
                val pos = p.location
                data.vertexBuffer!!.put(pos.x)
                data.vertexBuffer!!.put(pos.y)
                data.vertexBuffer!!.put(pos.z)

                // Point Size
                data.vertexBuffer!!.put(p.size.toFloat())

                // Texture Offset
                val starIndex = p.pointShape.imageIndex
                val texOffsetU = starWidthInTexels * starIndex
                data.vertexBuffer!!.put(texOffsetU)

                // Color (RGB)
                var c = p.color
                if (c == 0) {
                    c = -0x1 // 0xFFFFFFFF, Default to white if color is missing/black
                }
                val r = (c shr 16 and 0xFF) / 255.0f
                val g = (c shr 8 and 0xFF) / 255.0f
                val b = (c and 0xFF) / 255.0f
                data.vertexBuffer!!.put(r)
                data.vertexBuffer!!.put(g)
                data.vertexBuffer!!.put(b)
            }
            data.vertexBuffer!!.position(0)
            data.sources.clear()
        }
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        mTextureRef = textureManager.getTextureFromResource(gl!!, R.drawable.stars_texture)
    }

    override fun drawInternal(gl: GL10?) {
        if (shaderProgram == null) return

        shaderProgram!!.useProgram()

        // Ensure we aren't using a VBO from a previous renderer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Set Uniforms
        if (renderState!!.transformToDeviceMatrix != null) {
            val mvp = renderState!!.transformToDeviceMatrix!!.floatArray
            shaderProgram!!.setUniforms(mvp, mTextureRef!!.textureId)
            shaderProgram!!.setNightMode(renderState!!.nightVisionMode)
        }

        val activeRegions = renderState!!.activeSkyRegions ?: return
        val activeRegionData = mSkyRegions.getDataForActiveRegions(activeRegions)

        for (data in activeRegionData) {
            if (data.numVertices == 0) continue

            data.vertexBuffer!!.position(0)

            // Attribute: Position (3 floats)
            data.vertexBuffer!!.position(0)
            GLES20.glVertexAttribPointer(
                shaderProgram!!.aPositionLocation, 3, GLES20.GL_FLOAT, false,
                FLOATS_PER_VERTEX * 4, data.vertexBuffer
            )
            GLES20.glEnableVertexAttribArray(shaderProgram!!.aPositionLocation)

            // Attribute: PointSize (1 float)
            data.vertexBuffer!!.position(3)
            GLES20.glVertexAttribPointer(
                shaderProgram!!.aPointSizeLocation, 1, GLES20.GL_FLOAT, false,
                FLOATS_PER_VERTEX * 4, data.vertexBuffer
            )
            GLES20.glEnableVertexAttribArray(shaderProgram!!.aPointSizeLocation)

            // Attribute: TexOffset (1 float)
            data.vertexBuffer!!.position(4)
            GLES20.glVertexAttribPointer(
                shaderProgram!!.aTexOffsetLocation, 1, GLES20.GL_FLOAT, false,
                FLOATS_PER_VERTEX * 4, data.vertexBuffer
            )
            GLES20.glEnableVertexAttribArray(shaderProgram!!.aTexOffsetLocation)

            // Attribute: Color (3 floats)
            val colorLoc = shaderProgram!!.aColorLocation
            if (colorLoc >= 0) {
                data.vertexBuffer!!.position(5)
                GLES20.glVertexAttribPointer(
                    colorLoc, 3, GLES20.GL_FLOAT, false,
                    FLOATS_PER_VERTEX * 4, data.vertexBuffer
                )
                GLES20.glEnableVertexAttribArray(colorLoc)
            }

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, data.numVertices)

            GLES20.glDisableVertexAttribArray(shaderProgram!!.aPositionLocation)
            GLES20.glDisableVertexAttribArray(shaderProgram!!.aPointSizeLocation)
            GLES20.glDisableVertexAttribArray(shaderProgram!!.aTexOffsetLocation)
            if (colorLoc >= 0) {
                GLES20.glDisableVertexAttribArray(colorLoc)
            }
        }
    }

    companion object {
        private const val NUM_STARS_IN_TEXTURE = 2
        private const val MINIMUM_NUM_POINTS_FOR_REGIONS = 200
        // x, y, z, size, texOffset, r, g, b
        private const val FLOATS_PER_VERTEX = 8
        private const val COMPUTE_REGIONS = true
    }
}

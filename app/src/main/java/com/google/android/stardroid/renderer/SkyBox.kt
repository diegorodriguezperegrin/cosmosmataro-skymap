// Copyright 2009 Google Inc.
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

import android.util.Log
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.TWO_PI
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.util.ColorBuffer
import com.google.android.stardroid.renderer.util.IndexBuffer
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.VertexBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.floor

class SkyBox(layer: Int, textureManager: TextureManager) :
    RendererObjectManager(layer, textureManager) {
    private val mVertexBuffer = VertexBuffer(true)
    private val mColorBuffer = ColorBuffer(true)
    private val mIndexBuffer = IndexBuffer(true)
    private var mSunPos = Vector3(0f, 1f, 0f)

    init {
        val numVertices = NUM_VERTEX_BANDS * NUM_STEPS_IN_BAND
        val numIndices = (NUM_VERTEX_BANDS - 1) * NUM_STEPS_IN_BAND * 6
        mVertexBuffer.reset(numVertices)
        mColorBuffer.reset(numVertices)
        mIndexBuffer.reset(numIndices)

        val sinAngles = FloatArray(NUM_STEPS_IN_BAND)
        val cosAngles = FloatArray(NUM_STEPS_IN_BAND)

        var angleInBand = 0f
        val dAngle = TWO_PI / (NUM_STEPS_IN_BAND - 1)
        for (i in 0 until NUM_STEPS_IN_BAND) {
            sinAngles[i] = MathUtils.sin(angleInBand)
            cosAngles[i] = MathUtils.cos(angleInBand)
            angleInBand += dAngle
        }

        val bandStep = 2.0f / (NUM_VERTEX_BANDS - 1) + EPSILON

        val vb = mVertexBuffer
        val cb = mColorBuffer
        var bandPos = 1f
        for (band in 0 until NUM_VERTEX_BANDS) {
            val color: Int
            if (bandPos > 0) {
                // TODO(jpowell): This isn't really intensity, name it more appropriately.
                // I=70 at bandPos = 1, I=50 at bandPos = 0
                val intensity = (bandPos * 20 + 50).toInt().toByte()
                color = (intensity.toInt() shl 16) or -0x1000000
            } else {
                // I=40 at bandPos = -1, I=0 at bandPos = 0
                val intensity = (bandPos * 40 + 40).toInt().toByte()
                color =
                    (intensity.toInt() shl 16) or (intensity.toInt() shl 8) or intensity.toInt() or -0x1000000
            }

            val sinPhi = if (bandPos > -1) MathUtils.sqrt(1 - bandPos * bandPos) else 0f
            for (i in 0 until NUM_STEPS_IN_BAND) {
                vb.addPoint(cosAngles[i] * sinPhi, bandPos, sinAngles[i] * sinPhi)
                cb.addColor(color)
            }
            bandPos -= bandStep
        }
        Log.d("SkyBox", "Vertices: " + vb.size())

        val ib = mIndexBuffer

        // Set the indices for the first band.
        var topBandStart: Short = 0
        var bottomBandStart = NUM_STEPS_IN_BAND.toShort()
        for (triangleBand in 0 until NUM_VERTEX_BANDS - 1) {
            for (offsetFromStart in 0 until NUM_STEPS_IN_BAND - 1) {
                // Draw one quad as two triangles.
                val topLeft = (topBandStart + offsetFromStart).toShort()
                val topRight = (topLeft + 1).toShort()

                val bottomLeft = (bottomBandStart + offsetFromStart).toShort()
                val bottomRight = (bottomLeft + 1).toShort()

                // First triangle
                ib.addIndex(topLeft)
                ib.addIndex(bottomRight)
                ib.addIndex(bottomLeft)

                // Second triangle
                ib.addIndex(topRight)
                ib.addIndex(bottomRight)
                ib.addIndex(topLeft)
            }

            // Last quad: connect the end with the beginning.

            // Top left, bottom right, bottom left
            ib.addIndex((topBandStart + NUM_STEPS_IN_BAND - 1).toShort())
            ib.addIndex(bottomBandStart)
            ib.addIndex((bottomBandStart + NUM_STEPS_IN_BAND - 1).toShort())

            // Top right, bottom right, top left
            ib.addIndex(topBandStart)
            ib.addIndex(bottomBandStart)
            ib.addIndex((topBandStart + NUM_STEPS_IN_BAND - 1).toShort())


            topBandStart = (topBandStart + NUM_STEPS_IN_BAND).toShort()
            bottomBandStart = (bottomBandStart + NUM_STEPS_IN_BAND).toShort()
        }
        Log.d("SkyBox", "Indices: " + ib.size())
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        mVertexBuffer.reload()
        mColorBuffer.reload()
        mIndexBuffer.reload()
    }

    fun setSunPosition(pos: Vector3?) {
        mSunPos = pos?.copyForJ() ?: Vector3(0f, 1f, 0f)
        //Log.d("SkyBox", "SunPos: " + pos.toString());
    }

    override fun drawInternal(gl: GL10?) {
        if (renderState!!.nightVisionMode) {
            return
        }

        gl!!.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisable(GL10.GL_TEXTURE_2D)

        gl.glEnable(GL10.GL_CULL_FACE)
        gl.glFrontFace(GL10.GL_CW)
        gl.glCullFace(GL10.GL_BACK)

        gl.glShadeModel(GL10.GL_SMOOTH)

        gl.glPushMatrix()

        // Rotate the sky box to the position of the sun.
        val cp = Vector3(0f, 1f, 0f) * mSunPos
        val normalizedCp = cp.normalizedCopy()
        val angle = RADIANS_TO_DEGREES * MathUtils.acos(mSunPos.y)
        gl.glRotatef(angle, normalizedCp.x, normalizedCp.y, normalizedCp.z)

        mVertexBuffer.set(gl)
        mColorBuffer.set(gl)

        mIndexBuffer.draw(gl, GL10.GL_TRIANGLES)

        gl.glPopMatrix()
    }

    companion object {
        private const val NUM_VERTEX_BANDS = 8
        // This number MUST be even
        private const val NUM_STEPS_IN_BAND = 10

        // Used to make sure rounding error doesn't make us have off-by-one errors in our iterations.
        private const val EPSILON = 1e-3f
    }
}

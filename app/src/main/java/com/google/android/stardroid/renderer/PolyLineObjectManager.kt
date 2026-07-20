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

import android.util.Log
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderer.shader.LineShaderProgram
import com.google.android.stardroid.renderer.util.IndexBuffer
import com.google.android.stardroid.renderer.util.NightVisionColorBuffer
import com.google.android.stardroid.renderer.util.TexCoordBuffer
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.TextureReference
import com.google.android.stardroid.renderer.util.VertexBuffer
import org.cosmosmataro.skymap.R
import java.util.EnumSet
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

class PolyLineObjectManager(layer: Int, textureManager: TextureManager) :
    RendererObjectManager(layer, textureManager) {
    private val mVertexBuffer = VertexBuffer(false)
    private val mColorBuffer = NightVisionColorBuffer(false)
    private val mTexCoordBuffer = TexCoordBuffer(false)
    private val mIndexBuffer = IndexBuffer(false)
    private var mTexRef: TextureReference? = null
    private var mOpaque = true
    private var shaderProgram: LineShaderProgram? = null

    fun setShaderProgram(program: LineShaderProgram) {
        this.shaderProgram = program
    }

    fun updateObjects(lines: List<LinePrimitive>, updateType: EnumSet<UpdateType>) {
        // We only care about updates to positions, ignore any other updates.
        if (!updateType.contains(UpdateType.Reset) &&
            !updateType.contains(UpdateType.UpdatePositions)
        ) {
            return
        }
        var numLineSegments = 0
        for (l in lines) {
            numLineSegments += l.vertices.size - 1
        }

        // To render everything in one call, we render everything as a line list
        // rather than a series of line strips.
        val numVertices = 4 * numLineSegments
        val numIndices = 6 * numLineSegments
// Log.d("PolyLineDebugging", "Lines: $numLineSegments, Verts: $numVertices, Indices: $numIndices, opaque: $mOpaque")

        val vb = mVertexBuffer
        vb.reset(4 * numLineSegments)
        val cb = mColorBuffer
        cb.reset(4 * numLineSegments)
        val tb = mTexCoordBuffer
        tb.reset(numVertices)
        val ib = mIndexBuffer
        ib.reset(numIndices)

        // See comment in PointObjectManager for justification of this calculation.
        val fovyInRadians = 60 * DEGREES_TO_RADIANS
        val sizeFactor = MathUtils.tan(fovyInRadians * 0.5f) / 480

        var opaque = true

        var vertexIndex: Short = 0
        for (l in lines) {
            val coords = l.vertices
            if (coords.size < 2) continue

            // If the color isn't fully opaque, set opaque to false.
            val color = l.color
            opaque = opaque and ((color and -0x1000000) == -0x1000000)

            // Add the vertices.
            for (i in 0 until coords.size - 1) {
                val p1 = coords[i]
                val p2 = coords[i + 1]
                val u = p2 - p1
                // The normal to the quad should face the origin at its midpoint.
                val avg = p1 + p2
                avg *= 0.5f // timesAssign
                // I'm assuming that the points will already be on a unit sphere. If this is not
                // the case,
                // then we should normalize it here.
                val v = (u * avg).normalizedCopy()
                v *= (sizeFactor * l.lineWidth)

                // Add the vertices

                // Lower left corner
                vb.addPoint(p1 - v)
                cb.addColor(color)
                tb.addTexCoords(0f, 1f)

                // Upper left corner
                vb.addPoint(p1 + v)
                cb.addColor(color)
                tb.addTexCoords(0f, 0f)

                // Lower left corner
                vb.addPoint(p2 - v)
                cb.addColor(color)
                tb.addTexCoords(1f, 1f)

                // Upper left corner
                vb.addPoint(p2 + v)
                cb.addColor(color)
                tb.addTexCoords(1f, 0f)

                // Add the indices
                val bottomLeft = vertexIndex++
                val topLeft = vertexIndex++
                val bottomRight = vertexIndex++
                val topRight = vertexIndex++

                // First triangle
                ib.addIndex(bottomLeft)
                ib.addIndex(topLeft)
                ib.addIndex(bottomRight)

                // Second triangle
                ib.addIndex(bottomRight)
                ib.addIndex(topLeft)
                ib.addIndex(topRight)
            }
            if (numLineSegments < 100) { // Log only for small batches (like Ecliptic)
                 Log.d("PolyLineDetail", "Line Color: ${String.format("%x", color)}, Opaque: $opaque, Width: ${l.lineWidth}, SizeFactor: $sizeFactor")
                 val p1 = coords[0]
                 val p2 = coords[1]
                 Log.d("PolyLineDetail", "P1: $p1, P2: $p2")
            }
        }
        mOpaque = opaque
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        mTexRef = textureManager.getTextureFromResource(gl!!, R.drawable.line_flat)
        // Ensure we re-bind the texture to the shader if the shader is already active? 
        // Or just wait for next drawInternal call?
        // drawInternal will handle binding, but we should clear any stale state if needed.
        mVertexBuffer.reload()
        mColorBuffer.reload()
        mTexCoordBuffer.reload()
        mIndexBuffer.reload()
        Log.d("PolyLineObjectManager", "Reloaded. Texture ID: ${mTexRef?.textureId}")
    }

    override fun drawInternal(gl: GL10?) {
        if (mIndexBuffer.size() == 0 || shaderProgram == null) return

        shaderProgram!!.useProgram()

        // Set Uniforms
        if (renderState!!.transformToDeviceMatrix != null) {
            val mvp = renderState!!.transformToDeviceMatrix!!.floatArray
            shaderProgram!!.setUniforms(mvp, mTexRef!!.textureId)
            shaderProgram!!.setNightMode(renderState!!.nightVisionMode)
        }

        if (!mOpaque) {
            android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_BLEND)
            android.opengl.GLES20.glBlendFunc(
                android.opengl.GLES20.GL_SRC_ALPHA,
                android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA
            )
        }

        val gl11 = gl as GL11
        mVertexBuffer.bindToAttribute(gl11, shaderProgram!!.aPositionLocation)
        mColorBuffer.bindToAttribute(
            gl11,
            shaderProgram!!.aColorLocation,
            false // Always use Day buffer. Shader handles Night Mode redaction.
        )
        mTexCoordBuffer.bindToAttribute(gl11, shaderProgram!!.aTexCoordLocation)

        // Explicitly unbind GL_ARRAY_BUFFER to avoid interference checks
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ARRAY_BUFFER, 0)
        // Explicitly unbind GL_ELEMENT_ARRAY_BUFFER as we are using client-side memory for indices
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        mIndexBuffer.draw(gl11, android.opengl.GLES20.GL_TRIANGLES)
        
        // DEBUG: Draw Vertices as Points to verify geometry - REMOVED
        // android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_POINTS, 0, mVertexBuffer.size())

        android.opengl.GLES20.glDisableVertexAttribArray(shaderProgram!!.aPositionLocation)
        android.opengl.GLES20.glDisableVertexAttribArray(shaderProgram!!.aColorLocation)
        android.opengl.GLES20.glDisableVertexAttribArray(shaderProgram!!.aTexCoordLocation)

        if (!mOpaque) {
            android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_BLEND)
        }
    }
}

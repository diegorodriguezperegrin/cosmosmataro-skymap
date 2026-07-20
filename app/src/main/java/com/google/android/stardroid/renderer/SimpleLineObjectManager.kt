// Copyright 2010 Google Inc.
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
import com.google.android.stardroid.renderables.HairlinePrimitive
import com.google.android.stardroid.renderer.shader.LineShaderProgram
import com.google.android.stardroid.renderer.util.IndexBuffer
import com.google.android.stardroid.renderer.util.NightVisionColorBuffer
import com.google.android.stardroid.renderer.util.TexCoordBuffer
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.VertexBuffer
import java.util.EnumSet
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

class SimpleLineObjectManager(layer: Int, textureManager: TextureManager) :
    RendererObjectManager(layer, textureManager) {
    private val mVertexBuffer = VertexBuffer(false)
    private val mColorBuffer = NightVisionColorBuffer(false)
    private val mIndexBuffer = IndexBuffer(false)
    private val mTexCoordBuffer = TexCoordBuffer(false) // Not strictly needed but Shader requires it
    private var shaderProgram: LineShaderProgram? = null
    private var mTexRef: com.google.android.stardroid.renderer.util.TextureReference? = null

    fun setShaderProgram(program: LineShaderProgram) {
        this.shaderProgram = program
    }

    fun updateObjects(lines: List<HairlinePrimitive>, updateType: EnumSet<UpdateType>) {
        if (!updateType.contains(UpdateType.Reset) &&
            !updateType.contains(UpdateType.UpdatePositions)
        ) {
            return
        }

        var numSegments = 0
        for (l in lines) {
            if (l.vertices.size >= 2) {
                numSegments += (l.vertices.size - 1)
            }
        }
        
        val numVertices = numSegments * 2
        
        mVertexBuffer.reset(numVertices)
        mColorBuffer.reset(numVertices)
        mTexCoordBuffer.reset(numVertices)
        mIndexBuffer.reset(numVertices)
        
        var idx: Short = 0
        for (l in lines) {
            if (l.vertices.size < 2) continue
            val color = l.color
            for (i in 0 until l.vertices.size - 1) {
                val v1 = l.vertices[i]
                val v2 = l.vertices[i+1]
                
                mVertexBuffer.addPoint(v1)
                mColorBuffer.addColor(color)
                mTexCoordBuffer.addTexCoords(0.5f, 0.5f) // Sample center of texture to be safe
                mIndexBuffer.addIndex(idx++)
                
                mVertexBuffer.addPoint(v2)
                mColorBuffer.addColor(color)
                mTexCoordBuffer.addTexCoords(0.5f, 0.5f)
                mIndexBuffer.addIndex(idx++)
            }
        }
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        mTexRef = textureManager.getTextureFromResource(gl!!, org.cosmosmataro.skymap.R.drawable.line_flat)
        mVertexBuffer.reload()
        mColorBuffer.reload()
        mIndexBuffer.reload()
        mTexCoordBuffer.reload()
    }

    override fun drawInternal(gl: GL10?) {
        if (mIndexBuffer.size() == 0 || shaderProgram == null) return

        shaderProgram!!.useProgram()

        if (renderState!!.transformToDeviceMatrix != null) {
            val mvp = renderState!!.transformToDeviceMatrix!!.floatArray
            shaderProgram!!.setUniforms(mvp, mTexRef?.textureId ?: 0)
            shaderProgram!!.setNightMode(renderState!!.nightVisionMode)
        }
        
        val gl11 = gl as GL11
        mVertexBuffer.bindToAttribute(gl11, shaderProgram!!.aPositionLocation)
        mColorBuffer.bindToAttribute(gl11, shaderProgram!!.aColorLocation, false)
        mTexCoordBuffer.bindToAttribute(gl11, shaderProgram!!.aTexCoordLocation)

        GLES20.glLineWidth(1f) // Standard hairline
        
        mIndexBuffer.draw(gl11, GLES20.GL_LINES)
        
        // Reset state
        GLES20.glDisableVertexAttribArray(shaderProgram!!.aPositionLocation)
        GLES20.glDisableVertexAttribArray(shaderProgram!!.aColorLocation)
        GLES20.glDisableVertexAttribArray(shaderProgram!!.aTexCoordLocation)
    }
}

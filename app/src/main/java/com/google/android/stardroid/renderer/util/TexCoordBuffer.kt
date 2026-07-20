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
package com.google.android.stardroid.renderer.util

import android.opengl.GLES20
import android.util.Log
import com.google.android.stardroid.util.FixedPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

class TexCoordBuffer @JvmOverloads constructor(numVertices: Int = 0, useVBO: Boolean = false) {
    var texCoordBuffer: java.nio.FloatBuffer? = null
        private set
    private var numVertices = 0
    private val glBuffer = GLBuffer(GL11.GL_ARRAY_BUFFER)
    private var useVBO = false

    init {
        this.useVBO = useVBO
        reset(numVertices)
    }

    constructor(useVBO: Boolean) : this(0, useVBO)

    fun size(): Int {
        return numVertices
    }

    fun reset(numVertices: Int) {
        var numVertices = numVertices
        if (numVertices < 0) {
            Log.e("TexCoordBuffer", "reset attempting to set numVertices to $numVertices")
            numVertices = 0
        }
        this.numVertices = numVertices
        regenerateBuffer()
    }

    // Call this when we have to re-create the surface and reloading all OpenGL
    // resources.
    fun reload() {
        glBuffer.reload()
    }

    fun addTexCoords(u: Float, v: Float) {
        texCoordBuffer!!.put(u)
        texCoordBuffer!!.put(v)
    }

    fun set(gl: GL10) {
        if (numVertices == 0) {
            return
        }
        texCoordBuffer!!.position(0)
        if (useVBO && GLBuffer.canUseVBO()) {
            val gl11 = gl as GL11
            glBuffer.bind(gl11, texCoordBuffer, 4 * texCoordBuffer!!.capacity())
            gl11.glTexCoordPointer(2, GL10.GL_FLOAT, 0, 0)
        } else {
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texCoordBuffer)
        }
    }

    fun bindToAttribute(gl: GL11, attributeLocation: Int) {
        if (numVertices == 0) {
            return
        }
        texCoordBuffer!!.position(0)
        val useVBO = useVBO && GLBuffer.canUseVBO()
        if (useVBO) {
            if (glBuffer.bind(gl, texCoordBuffer, 4 * texCoordBuffer!!.capacity())) {
                GLES20.glVertexAttribPointer(attributeLocation, 2, GLES20.GL_FLOAT, false, 0, 0)
            }
        } else {
            GLES20.glVertexAttribPointer(
                attributeLocation, 2, GLES20.GL_FLOAT, false, 0,
                texCoordBuffer
            )
        }
        GLES20.glEnableVertexAttribArray(attributeLocation)
    }

    private fun regenerateBuffer() {
        if (numVertices == 0) {
            return
        }
        val bb = ByteBuffer.allocateDirect(4 * 2 * numVertices)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.position(0)
        texCoordBuffer = fb
    }
}

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
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.util.FixedPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

class VertexBuffer @JvmOverloads constructor(numVertices: Int = 0, useVBO: Boolean = false) {
    var positionBuffer: java.nio.FloatBuffer? = null
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
        this.numVertices = numVertices
        regenerateBuffer()
    }

    // Call this when we have to re-create the surface and reloading all OpenGL
    // resources.
    fun reload() {
        glBuffer.reload()
    }

    fun addPoint(p: Vector3) {
        addPoint(p.x, p.y, p.z)
    }

    fun addPoint(x: Float, y: Float, z: Float) {
        positionBuffer!!.put(x)
        positionBuffer!!.put(y)
        positionBuffer!!.put(z)
    }

    fun set(gl: GL10) {
        if (numVertices == 0) {
            return
        }
        positionBuffer!!.position(0)
        if (useVBO && GLBuffer.canUseVBO()) {
            val gl11 = gl as GL11
            glBuffer.bind(gl11, positionBuffer, 4 * positionBuffer!!.capacity())
            gl11.glVertexPointer(3, GL10.GL_FLOAT, 0, 0)
        } else {
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, positionBuffer)
        }
    }

    fun bindToAttribute(gl: GL11, attributeLocation: Int) {
        if (numVertices == 0) {
            return
        }
        positionBuffer!!.position(0)
        val useVBO = useVBO && GLBuffer.canUseVBO()
        if (useVBO) {
            if (glBuffer.bind(gl, positionBuffer, 4 * positionBuffer!!.capacity())) {
                GLES20.glVertexAttribPointer(attributeLocation, 3, GLES20.GL_FLOAT, false, 0, 0)
            }
        } else {
            GLES20.glVertexAttribPointer(
                attributeLocation, 3, GLES20.GL_FLOAT, false, 0,
                positionBuffer
            )
        }
        GLES20.glEnableVertexAttribArray(attributeLocation)
    }

    private fun regenerateBuffer() {
        if (numVertices == 0) {
            return
        }
        val bb = ByteBuffer.allocateDirect(4 * 3 * numVertices)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.position(0)
        positionBuffer = fb
    }
}

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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

class ColorBuffer @JvmOverloads constructor(numVertices: Int = 0, useVBO: Boolean = false) {
    private var colorBuffer: IntBuffer? = null
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

    fun addColor(a: Int, r: Int, g: Int, b: Int) {
        addColor(a and 0xff shl 24 or (b and 0xff shl 16) or (g and 0xff shl 8) or (r and 0xff))
    }

    fun addColor(abgr: Int) {
        colorBuffer!!.put(abgr)
    }

    fun set(gl: GL10) {
        if (numVertices == 0) {
            return
        }
        colorBuffer!!.position(0)
        if (useVBO && GLBuffer.canUseVBO()) {
            val gl11 = gl as GL11
            glBuffer.bind(gl11, colorBuffer, 4 * colorBuffer!!.capacity())
            gl11.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, 0)
        } else {
            gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, colorBuffer)
        }
    }

    fun bindToAttribute(gl: GL11, attributeLocation: Int) {
        if (numVertices == 0) {
            return
        }
        colorBuffer!!.position(0)
        val useVBO = useVBO && GLBuffer.canUseVBO()
        if (useVBO) {
            if (glBuffer.bind(gl, colorBuffer, 4 * colorBuffer!!.capacity())) {
                GLES20.glVertexAttribPointer(
                    attributeLocation, 4, GLES20.GL_UNSIGNED_BYTE, true,
                    0, 0
                )
            }
        } else {
            GLES20.glVertexAttribPointer(
                attributeLocation, 4, GLES20.GL_UNSIGNED_BYTE, true, 0,
                colorBuffer
            )
        }
        GLES20.glEnableVertexAttribArray(attributeLocation)
    }

    private fun regenerateBuffer() {
        if (numVertices == 0) {
            return
        }
        val bb = ByteBuffer.allocateDirect(4 * numVertices)
        bb.order(ByteOrder.nativeOrder())
        val ib = bb.asIntBuffer()
        ib.position(0)
        colorBuffer = ib
    }
}

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
import java.nio.ShortBuffer
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

class IndexBuffer @JvmOverloads constructor(numVertices: Int = 0, useVbo: Boolean = false) {
    var indexBuffer: ShortBuffer? = null
        private set
    private var numIndices = 0
    private val glBuffer = GLBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER)
    private var useVbo = false

    init {
        this.useVbo = useVbo
        reset(numVertices)
    }

    // Secondary constructor for just boolean, needed to match Java's overloading capability if JvmOverloads isn't enough for (Boolean) alone?
    // JvmOverloads gives: (Int, Boolean), (Int), ()
    // It DOES NOT give (Boolean).
    // So we need:
    constructor(useVBO: Boolean) : this(0, useVBO)

    fun size(): Int {
        return numIndices
    }

    fun reset(numVertices: Int) {
        numIndices = numVertices
        regenerateBuffer()
    }

    // Call this when we have to re-create the surface and reloading all OpenGL
    // resources.
    fun reload() {
        glBuffer.reload()
    }

    private fun regenerateBuffer() {
        if (numIndices == 0) {
            return
        }
        val bb = ByteBuffer.allocateDirect(2 * numIndices)
        bb.order(ByteOrder.nativeOrder())
        val ib = bb.asShortBuffer()
        ib.position(0)
        indexBuffer = ib
    }

    fun addIndex(index: Short) {
        indexBuffer!!.put(index)
    }

    fun draw(gl: GL10, primitiveType: Int) {
        if (numIndices == 0) {
            return
        }
        indexBuffer!!.position(0)
        if (useVbo && GLBuffer.canUseVBO()) {
            val gl11 = gl as GL11
            glBuffer.bind(gl11, indexBuffer, 2 * indexBuffer!!.capacity())
            gl11.glDrawElements(primitiveType, size(), GL10.GL_UNSIGNED_SHORT, 0)
            GLBuffer.unbind(gl11)
        } else {
            gl.glDrawElements(primitiveType, size(), GL10.GL_UNSIGNED_SHORT, indexBuffer)
        }
    }

    fun draw(gl: GL11, primitiveType: Int) {
        if (numIndices == 0) {
            return
        }
        indexBuffer!!.position(0)
        if (useVbo && GLBuffer.canUseVBO()) {
            if (glBuffer.bind(gl, indexBuffer, 2 * indexBuffer!!.capacity())) {
                GLES20.glDrawElements(primitiveType, size(), GLES20.GL_UNSIGNED_SHORT, 0)
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
            }
        } else {
            GLES20.glDrawElements(
                primitiveType, size(), GLES20.GL_UNSIGNED_SHORT,
                indexBuffer
            )
        }
    }
}

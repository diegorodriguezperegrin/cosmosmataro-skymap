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

import java.nio.Buffer
import javax.microedition.khronos.opengles.GL11

/**
 * This is a utility class which encapsulates and OpenGL buffer object. Several
 * other classes
 * need to be able to lazily create OpenGL buffers, so this class takes care of
 * the work of lazily
 * creating and updating them.
 *
 * @author jpowell
 */
class GLBuffer internal constructor(private val bufferType: Int) {
    private var buffer: Buffer? = null
    private var bufferSize = 0
    private var glBufferID = -1

    fun bind(gl: GL11, buffer: Buffer?, bufferSize: Int): Boolean {
        if (canUseVBO()) {
            maybeRegenerateBuffer(gl, buffer, bufferSize)
            gl.glBindBuffer(bufferType, glBufferID)
            return true
        }
        return false
    }

    fun reload() {
        // Just reset all of the values so we'll reload on the next call
        // to maybeRegenerateBuffer.
        buffer = null
        bufferSize = 0
        glBufferID = -1
    }

    private fun maybeRegenerateBuffer(gl: GL11, buffer: Buffer?, bufferSize: Int) {
        if (buffer !== this.buffer || bufferSize != this.bufferSize) {
            this.buffer = buffer
            this.bufferSize = bufferSize

            // Allocate the buffer ID if we don't already have one.
            if (glBufferID == -1) {
                val buffers = IntArray(1)
                gl.glGenBuffers(1, buffers, 0)
                glBufferID = buffers[0]
            }
            gl.glBindBuffer(bufferType, glBufferID)
            gl.glBufferData(bufferType, bufferSize, buffer, GL11.GL_STATIC_DRAW)
        }
    }

    companion object {
        // TODO(jpowell): This is ugly, we should have a buffer factory which knows
        // this rather than a static constant. I should refactor this accordingly
        // when I get a chance.
        private var sCanUseVBO = false

        @JvmStatic
        fun setCanUseVBO(canUseVBO: Boolean) {
            sCanUseVBO = canUseVBO
        }

        // A caller should verify that this returns true before using a GLBuffer.
        // If this returns false, any operation using the VBO will be a no-op.
        @JvmStatic
        fun canUseVBO(): Boolean {
            return sCanUseVBO
        }

        // Unset any GL buffer which is set on the device. You need to call this if you
        // want to render
        // without VBOs. Otherwise it will try to use whatever buffer is currently set.
        @JvmStatic
        fun unbind(gl: GL11) {
            if (canUseVBO()) {
                gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, 0)
                gl.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, 0)
            }
        }
    }
}

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

import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

/// Encapsulates a color vertex buffer where night vision can be enabled or diabled by a function call.
class NightVisionColorBuffer(numVertices: Int = 0, useVBO: Boolean = false) {
    private var normalBuffer: ColorBuffer
    private var redBuffer: ColorBuffer

    constructor() : this(0, false)

    constructor(useVBO: Boolean) : this(0, useVBO)

    init {
        normalBuffer = ColorBuffer(numVertices, useVBO)
        redBuffer = ColorBuffer(numVertices, useVBO)
    }

    fun size(): Int {
        return normalBuffer.size()
    }

    fun reset(numVertices: Int) {
        normalBuffer.reset(numVertices)
        redBuffer.reset(numVertices)
    }

    // Call this when we have to re-create the surface and reloading all OpenGL
    // resources.
    fun reload() {
        normalBuffer.reload()
        redBuffer.reload()
    }

    fun addColor(a: Int, r: Int, g: Int, b: Int) {
        normalBuffer.addColor(a, r, g, b)
        // I tried luminance here first, but many objects we care a lot about weren't
        // very noticable because they were
        // bluish. An average gets a better result.
        val avg = (r + g + b) / 3
        redBuffer.addColor(a, avg, 0, 0)
    }

    fun addColor(abgr: Int) {
        val a = abgr shr 24 and 0xff
        val b = abgr shr 16 and 0xff
        val g = abgr shr 8 and 0xff
        val r = abgr and 0xff
        addColor(a, r, g, b)
    }

    fun set(gl: GL10, nightVisionMode: Boolean) {
        if (nightVisionMode) {
            redBuffer.set(gl)
        } else {
            normalBuffer.set(gl)
        }
    }

    fun bindToAttribute(gl: GL11, attributeLocation: Int, nightVisionMode: Boolean) {
        if (nightVisionMode) {
            redBuffer.bindToAttribute(gl, attributeLocation)
        } else {
            normalBuffer.bindToAttribute(gl, attributeLocation)
        }
    }
}

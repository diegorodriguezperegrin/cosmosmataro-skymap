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
import com.google.android.stardroid.renderer.shader.TextureShaderProgram
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

/**
 * A simple class for rendering a textured quad.
 *
 * @author James Powell
 */
class TexturedQuad(
    tex: TextureReference?,
    px: Float, py: Float, pz: Float,
    ux: Float, uy: Float, uz: Float,
    vx: Float, vy: Float, vz: Float
) {
    private val texCoords: TexCoordBuffer = TexCoordBuffer(12)
    private val position: VertexBuffer = VertexBuffer(12)
    private val texture: TextureReference? = tex

    /**
     * Constructs the textured quad.
     * p is the point at the center of the quad.
     * u is the vector from the center of quad, pointing right.
     * v is the vector from the center of the quad, pointing up.
     * The four vertices of the quad are: by p +/- u +/- v
     *
     * @param tex The texture to apply to the quad
     * @param px
     * @param py
     * @param pz
     * @param ux
     * @param uy
     * @param uz
     * @param vx
     * @param vy
     * @param vz
     */
    init {
        // Upper left
        position.addPoint(px - ux - vx, py - uy - vy, pz - uz - vz)
        texCoords.addTexCoords(0f, 1f)

        // upper left
        position.addPoint(px - ux + vx, py - uy + vy, pz - uz + vz)
        texCoords.addTexCoords(0f, 0f)

        // lower right
        position.addPoint(px + ux - vx, py + uy - vy, pz + uz - vz)
        texCoords.addTexCoords(1f, 1f)

        // upper right
        position.addPoint(px + ux + vx, py + uy + vy, pz + uz + vz)
        texCoords.addTexCoords(1f, 0f)
    }

    fun draw(gl: GL10) {
        if (texture == null) return

        gl.glEnable(GL10.GL_TEXTURE_2D)
        texture.bind(gl)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        position.set(gl)
        texCoords.set(gl)
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
        gl.glDisable(GL10.GL_TEXTURE_2D)
    }

    fun draw(
        gl: GL10, shader: TextureShaderProgram,
        mvpMatrix: FloatArray, r: Float, g: Float, b: Float, a: Float
    ) {
        if (texture == null) return
        shader.setUniforms(mvpMatrix, texture.textureId, r, g, b, a)

        // Bind attributes
        // Position
        position.bindToAttribute(gl as GL11, shader.aPositionLocation)

        // TexCoord
        texCoords.bindToAttribute(gl, shader.aTexCoordLocation)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Cleanup?
        GLES20.glDisableVertexAttribArray(shader.aPositionLocation)
        GLES20.glDisableVertexAttribArray(shader.aTexCoordLocation)
    }
}

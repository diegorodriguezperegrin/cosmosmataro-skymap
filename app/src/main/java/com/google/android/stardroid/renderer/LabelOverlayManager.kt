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

import android.content.res.Resources
import android.graphics.Paint
import com.google.android.stardroid.renderer.util.IndexBuffer
import com.google.android.stardroid.renderer.util.LabelMaker
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.TextureReference
import com.google.android.stardroid.renderer.util.VertexBuffer
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

/**
 * Manages rendering of which appears at fixed points on the screen, rather
 * than text which appears at fixed points in the world.
 *
 * @author James Powell
 */
class LabelOverlayManager {
    private var mLabels: Array<Label>? = null
    private val mLabelMaker = LabelMaker(true)
    private val mLabelPaint = Paint()
    private var mTexture: TextureReference? = null
    private var mVertexBuffer: VertexBuffer? = null
    private var mIndexBuffer: IndexBuffer? = null

    class Label(text: String, color: Int, size: Int) : LabelMaker.LabelData(text, color, size) {
        var isEnabled: Boolean = true
        var x: Int = 0
        var y: Int = 0
        var alpha: Float = 1f

        fun setPosition(x: Int, y: Int) {
            this.x = x
            this.y = y
        }
    }

    /**
     * Made private as unused at the moment.
     */
    init {
        mLabelPaint.isAntiAlias = true

        val vb = VertexBuffer(4, false)
        vb.addPoint(0f, 0f, 0f) // Bottom left
        vb.addPoint(0f, 1f, 0f) // Top left
        vb.addPoint(1f, 0f, 0f) // Bottom right
        vb.addPoint(1f, 1f, 0f) // Top right
        mVertexBuffer = vb

        val ib = IndexBuffer(6)
        // Triangle one: bottom left, top left, bottom right.
        ib.addIndex(0.toShort())
        ib.addIndex(1.toShort())
        ib.addIndex(2.toShort())

        // Triangle two: bottom right, top left, top right.
        ib.addIndex(2.toShort())
        ib.addIndex(1.toShort())
        ib.addIndex(3.toShort())
        mIndexBuffer = ib
    }

    fun initialize(
        gl: GL10, labels: Array<Label>, res: Resources,
        textureManager: TextureManager
    ) {
        mLabels = labels.clone()
        // We need to cast our Array<Label> to Array<LabelMaker.LabelData>
        // Since Label extends LabelData, this is safe but Kotlin arrays are invariant.
        // We might need to copy logic from LabelMaker to handle this clean or change signature.
        // For now, let's create a new array.
        val labelDataArray: Array<LabelMaker.LabelData> = Array(labels.size) { i -> labels[i] }
        
        mTexture = mLabelMaker.initialize(gl, mLabelPaint, labelDataArray, res, textureManager)
    }

    fun releaseTexture(gl: GL10) {
        // TODO(jpowell): Figure out if LabelMaker should have a shutdown() method
        // and delete the texture or if I should do it myself.
        if (mTexture != null) {
            mLabelMaker.shutdown(gl)
            mTexture = null
        }
    }

    fun draw(gl: GL10, screenWidth: Int, screenHeight: Int) {
        val labels = mLabels
        val texture = mTexture
        val vertexBuffer = mVertexBuffer
        val indexBuffer = mIndexBuffer
        
        if (labels == null || texture == null || vertexBuffer == null || indexBuffer == null) {
            return
        }

        gl.glEnable(GL10.GL_TEXTURE_2D)
        texture.bind(gl)

        gl.glEnable(GL10.GL_BLEND)
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA)

        gl.glTexEnvx(
            GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE,
            GL10.GL_MODULATE
        )

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)

        // Change to orthographic projection, where the units in model view space
        // are the same as in screen space.
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glPushMatrix()
        gl.glLoadIdentity()
        gl.glOrthof(0f, screenWidth.toFloat(), 0f, screenHeight.toFloat(), -100f, 100f)

        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glPushMatrix()

        for (label in labels) {
            if (label.isEnabled) {
                val x = label.x - label.widthInPixels / 2
                val y = label.y

                gl.glLoadIdentity()

                // Move the label to the correct offset.
                gl.glTranslatef(x.toFloat(), y.toFloat(), 0.0f)

                // Scale the label to the correct size.
                gl.glScalef(label.widthInPixels.toFloat(), label.heightInPixels.toFloat(), 0.0f)

                // Set the alpha for the label.
                gl.glColor4f(1f, 1f, 1f, label.alpha)

                // Draw the label.
                vertexBuffer.set(gl)
                gl.glTexCoordPointer(2, GL10.GL_FIXED, 0, label.texCoords)
                indexBuffer.draw(gl as GL11, GL10.GL_TRIANGLES)
            }
        }

        // Restore the old matrices.
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glPopMatrix()

        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glPopMatrix()

        gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE.toFloat())
        gl.glDisable(GL10.GL_BLEND)
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)

        gl.glDisable(GL10.GL_TEXTURE_2D)
    }
}

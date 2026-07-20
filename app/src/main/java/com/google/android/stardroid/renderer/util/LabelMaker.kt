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

import android.content.res.Resources
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLUtils
import com.google.android.stardroid.util.FixedPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * A `LabelMaker` creates and managers a texture atlas containing all the
 * supplied
 * labels (represented as `LabelData`). The letter get updated with their
 * locations
 * in the texture atlas.
 *
 * @param fullColor true if we want a full color backing store (4444),
 * otherwise we generate a grey L8 backing store.
 */
class LabelMaker(private val fullColor: Boolean) {
    private var strikeWidth = 0
    private var strikeHeight = 0
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var res: Resources? = null
    private var texture: TextureReference? = null
    private var texelWidth = 0f // Convert texel to U
    private var texelHeight = 0f // Convert texel to V

    /**
     * A class which contains data that describes a label and its position in the
     * texture.
     */
    open class LabelData(val text: String, val color: Int, val fontSize: Int) {
        var widthInPixels = 0
            private set
        var heightInPixels = 0
            private set
        var texCoords: IntBuffer? = null
            private set
        var crop: IntArray? = null
            private set

        // Sets data about the label's position in the texture.
        fun setTextureData(
            widthInPixels: Int, heightInPixels: Int,
            cropU: Int, cropV: Int, cropW: Int, cropH: Int,
            texelWidth: Float, texelHeight: Float
        ) {
            this.widthInPixels = widthInPixels
            this.heightInPixels = heightInPixels
            val texCoords = IntArray(8)
            // lower left
            texCoords[0] = FixedPoint.floatToFixedPoint(cropU * texelWidth)
            texCoords[1] = FixedPoint.floatToFixedPoint(cropV * texelHeight)

            // upper left
            texCoords[2] = FixedPoint.floatToFixedPoint(cropU * texelWidth)
            texCoords[3] = FixedPoint.floatToFixedPoint((cropV + cropH) * texelHeight)

            // lower right
            texCoords[4] = FixedPoint.floatToFixedPoint((cropU + cropW) * texelWidth)
            texCoords[5] = FixedPoint.floatToFixedPoint(cropV * texelHeight)

            // upper right
            texCoords[6] = FixedPoint.floatToFixedPoint((cropU + cropW) * texelWidth)
            texCoords[7] = FixedPoint.floatToFixedPoint((cropV + cropH) * texelHeight)
            this.texCoords = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
            this.texCoords!!.put(texCoords)
            this.texCoords!!.position(0)
            crop = intArrayOf(cropU, cropV, cropW, cropH)
        }
    }

    /**
     * Create a label maker or maximum compatibility with various OpenGL ES
     * implementations, the strike width and height must be powers of two, We want
     * the strike width to be at least as wide as the widest window.
     */
    init {
        strikeWidth = -1
        strikeHeight = -1
    }

    /**
     * Call to initialize the class. Call whenever the surface has been created.
     *
     * @param gl
     */
    fun initialize(
        gl: GL10, textPaint: Paint, labels: Array<LabelData>,
        res: Resources, textureManager: TextureManager
    ): TextureReference {
        this.res = res
        texture = textureManager.createTexture(gl)
        texture!!.bind(gl)
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,
            GL10.GL_NEAREST.toFloat()
        )
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER,
            GL10.GL_NEAREST.toFloat()
        )
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S,
            GL10.GL_CLAMP_TO_EDGE.toFloat()
        )
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T,
            GL10.GL_CLAMP_TO_EDGE.toFloat()
        )
        gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE.toFloat())

        // Maximum allowed text label width, set to window width
        val maxLabelWidth = this.res!!.displayMetrics.widthPixels
        // mStrikeWidth should be enough to hold maxTextWidth and
        // rounded up to the nearest power of two, since textures have to be a power of
        // two in size.
        var roundedWidth = 512
        while (roundedWidth < maxLabelWidth) roundedWidth = roundedWidth shl 1
        strikeWidth = roundedWidth
        val minHeight = addLabelsInternal(gl, textPaint, false, labels, maxLabelWidth)

        // Round up to the nearest power of two, since textures have to be a power of
        // two in size.
        var roundedHeight = 1
        while (roundedHeight < minHeight) roundedHeight = roundedHeight shl 1
        strikeHeight = roundedHeight
        texelWidth = (1.0 / strikeWidth).toFloat()
        texelHeight = (1.0 / strikeHeight).toFloat()
        beginAdding(gl)
        addLabelsInternal(gl, textPaint, true, labels, maxLabelWidth)
        endAdding(gl)
        return texture!!
    }

    /**
     * Call when the surface has been destroyed
     */
    fun shutdown(gl: GL10) {
        if (texture != null) {
            texture!!.delete(gl)
        }
    }

    /**
     * Call to add a list of labels
     *
     * @param gl
     * @param textPaint     the paint of the label
     * @param labels        the array of labels being added
     * @param maxLabelWidth maximum display width of a label
     * @return the required height
     */
    private fun addLabelsInternal(
        gl: GL10, textPaint: Paint, drawToCanvas: Boolean,
        labels: Array<LabelData>, maxLabelWidth: Int
    ): Int {
        var u = 0
        var v = 0
        var lineHeight = 0
        for (label in labels) {
            val text = label.text
            var split = false
            var line1 = text
            var line2 = ""

            // Split long text into two lines
            if (text.length > 16 && text.contains(" ")) {
                val mid = text.length / 2
                var splitIndex = -1
                var minDistance = Int.MAX_VALUE
                for (i in 0 until text.length) {
                    if (text[i] == ' ') {
                        val dist = abs(i - mid)
                        if (dist < minDistance) {
                            minDistance = dist
                            splitIndex = i
                        }
                    }
                }
                if (splitIndex != -1) {
                    line1 = text.substring(0, splitIndex)
                    line2 = text.substring(splitIndex + 1)
                    split = true
                }
            }
            var ascent: Int
            var descent: Int
            var measuredTextWidth: Int
            var width1 = 0
            var width2 = 0
            var height: Int
            var width: Int
            var singleLineHeight: Int
            var fontSize = label.fontSize
            do {
                textPaint.color = -0x1000000 or label.color
                Log.d("LabelDebugging", "Text: ${label.text}, Color: ${String.format("#%08X", textPaint.color)}")
                textPaint.textSize = fontSize * res!!.displayMetrics.density

                // Paint.ascent is negative, so negate it.
                ascent = ceil(-textPaint.ascent().toDouble()).toInt()
                descent = ceil(textPaint.descent().toDouble()).toInt()
                singleLineHeight = ascent + descent
                if (split) {
                    width1 = ceil(textPaint.measureText(line1).toDouble()).toInt()
                    width2 = ceil(textPaint.measureText(line2).toDouble()).toInt()
                    width = max(width1.toDouble(), width2.toDouble()).toInt()
                    height = singleLineHeight * 2
                } else {
                    measuredTextWidth = ceil(textPaint.measureText(text).toDouble()).toInt()
                    width = measuredTextWidth
                    height = singleLineHeight
                }

                // If it's wider than the screen, try it again with a font size of 1
                // smaller.
                fontSize--
            } while (fontSize > 0 && width > maxLabelWidth)
            val nextU: Int

            // Is there room for this string on the current line?
            if (u + width > strikeWidth) {
                // No room, go to the next line:
                u = 0
                nextU = width
                v += lineHeight
                lineHeight = 0
            } else {
                nextU = u + width
            }
            lineHeight = max(lineHeight.toDouble(), height.toDouble()).toInt()
            if (v + lineHeight > strikeHeight && drawToCanvas) {
                throw IllegalArgumentException("Out of texture space.")
            }
            val vBase = v + ascent
            if (drawToCanvas) {
                if (split) {
                    canvas!!.drawText(line1, (u + (width - width1) / 2).toFloat(), vBase.toFloat(), textPaint)
                    canvas!!.drawText(
                        line2,
                        (u + (width - width2) / 2).toFloat(),
                        (vBase + singleLineHeight).toFloat(),
                        textPaint
                    )
                } else {
                    canvas!!.drawText(text, u.toFloat(), vBase.toFloat(), textPaint)
                }
                label.setTextureData(
                    width, height, u, v + height, width, -height,
                    texelWidth, texelHeight
                )
            }
            u = nextU
        }
        return v + lineHeight
    }

    private fun beginAdding(gl: GL10) {
        val config = if (fullColor) Bitmap.Config.ARGB_8888 else Bitmap.Config.ALPHA_8
        bitmap = Bitmap.createBitmap(strikeWidth, strikeHeight, config)
        canvas = Canvas(bitmap!!)
        bitmap!!.eraseColor(0)
    }

    private fun endAdding(gl: GL10) {
        texture!!.bind(gl)
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)
        // Reclaim storage used by bitmap and canvas.
        bitmap!!.recycle()
        bitmap = null
        canvas = null
    }
}

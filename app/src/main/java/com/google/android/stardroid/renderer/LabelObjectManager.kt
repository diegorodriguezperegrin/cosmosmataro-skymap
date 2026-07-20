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

import android.graphics.Paint
import android.graphics.Typeface
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Matrix4x4
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.TextPrimitive
import com.google.android.stardroid.renderer.shader.TextureShaderProgram
import com.google.android.stardroid.renderer.util.LabelMaker
import com.google.android.stardroid.renderer.util.SkyRegionMap
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.TextureReference
import com.google.android.stardroid.util.FixedPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.EnumSet
import javax.microedition.khronos.opengles.GL10

class LabelObjectManager(
    layer: Int,
    textureManager: TextureManager,
    private val fontSizeScale: Double
) : RendererObjectManager(layer, textureManager) {
    private var mLabelPaint: Paint = Paint()
    private var mLabelMaker: LabelMaker? = null
    // Use Array<Label> explicitly, initialized to empty
    private var mLabels: Array<Label> = emptyArray()
    private val mSkyRegions = SkyRegionMap<ArrayList<Label>>()

    private var mQuadBuffer: java.nio.FloatBuffer

    // These are intermediate variables set in beginDrawing() and used in
    // draw() to make the transformations more efficient
    private var mLabelOffset = Vector3(0f, 0f, 0f)
    private var mDotProductThreshold = 0f

    private var mTexture: TextureReference? = null
    private var shaderProgram: TextureShaderProgram? = null

    init {
        mLabelPaint.isAntiAlias = true
        mLabelPaint.typeface = Typeface.create("Verdana", Typeface.NORMAL)

        val quadBuffer = ByteBuffer.allocateDirect(4 * 2 * 4) // 4 verts * 2 coords * 4 bytes
        quadBuffer.order(ByteOrder.nativeOrder())
        mQuadBuffer = quadBuffer.asFloatBuffer()
        mQuadBuffer.position(0)
        // A quad with size 1 on each size, so we just need to multiply
        // by the label's width and height to get it to the right size for each
        // label.
        val vertices = floatArrayOf(
            -0.5f, -0.5f,  // lower left
            -0.5f, 0.5f,  // upper left
            0.5f, -0.5f,  // lower right
            0.5f, 0.5f
        ) // upper right
        mQuadBuffer.put(vertices)
        mQuadBuffer.position(0)

        // We want to initialize the labels of a sky region to an empty list.
        mSkyRegions.setRegionDataFactory { ArrayList() }
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        // We need to regenerate the texture.
        if (!fullReload && mLabelMaker != null) {
            mLabelMaker!!.shutdown(gl!!)
        }

        mLabelMaker = LabelMaker(true)
        // Create a temporary array of LabelData for initialization
        val labelDataArray: Array<LabelMaker.LabelData> = Array(mLabels.size) { i -> mLabels[i] }
        
        mTexture = mLabelMaker!!.initialize(
            gl!!, mLabelPaint, labelDataArray,
            renderState!!.resources!!,
            textureManager
        )
    }

    fun updateObjects(labels: List<TextPrimitive>, updateType: EnumSet<UpdateType>) {
        if (updateType.contains(UpdateType.Reset)) {
            // Protect against labels being changed mid-iteration.
            val safeLabels = ArrayList(labels)
            mLabels = Array(safeLabels.size) { i -> Label(safeLabels[i], fontSizeScale) }
            queueForReload(false)
        } else if (updateType.contains(UpdateType.UpdatePositions)) {
            if (labels.size != mLabels.size) {
                logUpdateMismatch("LabelObjectManager", mLabels.size, labels.size, updateType)
                return
            }
            // Update positions
            for (i in mLabels.indices) {
                val pos = labels[i].location
                mLabels[i].x = pos.x
                mLabels[i].y = pos.y
                mLabels[i].z = pos.z
            }
        }

        // Put all of the labels in their sky regions.
        mSkyRegions.clear()
        for (l in mLabels) {
            val region: Int
            if (COMPUTE_REGIONS) {
                region = SkyRegionMap.getObjectRegion(Vector3(l.x, l.y, l.z))
            } else {
                region = SkyRegionMap.CATCHALL_REGION_ID
            }
            mSkyRegions.getRegionData(region)!!.add(l)
        }
    }

    fun setShaderProgram(shaderProgram: TextureShaderProgram) {
        this.shaderProgram = shaderProgram
    }

    override fun drawInternal(gl: GL10?) {
        if (shaderProgram == null || mTexture == null) return

        shaderProgram!!.useProgram()
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ARRAY_BUFFER, 0)

        android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_BLEND)
        android.opengl.GLES20.glBlendFunc(
            android.opengl.GLES20.GL_SRC_ALPHA,
            android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA
        )

        val width = renderState!!.screenWidth.toFloat()
        val height = renderState!!.screenHeight.toFloat()

        val projection = FloatArray(16)
        android.opengl.Matrix.setIdentityM(projection, 0)
        android.opengl.Matrix.orthoM(projection, 0, 0f, width, 0f, height, -1f, 1f)

        val rs = super.renderState!!
        
        val activeRegions = rs.activeSkyRegions
        // Safety check for activeRegions
        if (activeRegions == null) {
             android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_BLEND)
             return
        }
        
        val allActiveLabels = mSkyRegions.getDataForActiveRegions(activeRegions)

        val rotation = Matrix4x4.createRotation(rs.upAngle, rs.lookDir!!)
        mLabelOffset = Matrix4x4.multiplyMV(rotation, rs.upDir!!)
        val viewWidth = rs.screenWidth.toFloat()
        val viewHeight = rs.screenHeight.toFloat()
        mDotProductThreshold = MathUtils.cos(
            rs.radiusOfView * DEGREES_TO_RADIANS * (1 + viewWidth / viewHeight) * 0.5f
        )

        val posLoc = shaderProgram!!.aPositionLocation
        val texLoc = shaderProgram!!.aTexCoordLocation

        android.opengl.GLES20.glEnableVertexAttribArray(posLoc)
        android.opengl.GLES20.glEnableVertexAttribArray(texLoc)

        mQuadBuffer.position(0)
        android.opengl.GLES20.glVertexAttribPointer(
            posLoc,
            2,
            android.opengl.GLES20.GL_FLOAT,
            false,
            0,
            mQuadBuffer
        )

        for (labelsInRegion in allActiveLabels) {
            for (l in labelsInRegion) {
                drawLabel(gl, l, projection, posLoc, texLoc)
            }
        }

        android.opengl.GLES20.glDisableVertexAttribArray(posLoc)
        android.opengl.GLES20.glDisableVertexAttribArray(texLoc)

        android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_BLEND)
    }

    private fun drawLabel(
        gl: GL10?,
        label: Label,
        projectionMatrix: FloatArray,
        posLoc: Int,
        texLoc: Int
    ) {
        val lookDir = renderState!!.lookDir!!
        if (lookDir.x * label.x + lookDir.y * label.y + lookDir.z * label.z < mDotProductThreshold) {
            return
        }

        val v = Vector3(
            label.x - mLabelOffset.x * label.offset,
            label.y - mLabelOffset.y * label.offset,
            label.z - mLabelOffset.z * label.offset
        )

        val screenPos = Matrix4x4.transformVector(
            renderState!!.transformToScreenMatrix!!,
            v
        )

        val MAGIC_OFFSET = 0.25f
        screenPos.x = screenPos.x.toInt() + MAGIC_OFFSET
        screenPos.y = screenPos.y.toInt() + MAGIC_OFFSET

        val modelMatrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(modelMatrix, 0)
        android.opengl.Matrix.translateM(modelMatrix, 0, screenPos.x, screenPos.y, 0f)
        android.opengl.Matrix.rotateM(
            modelMatrix,
            0,
            RADIANS_TO_DEGREES * renderState!!.upAngle,
            0f,
            0f,
            -1f
        )
        android.opengl.Matrix.scaleM(
            modelMatrix,
            0,
            label.widthInPixels.toFloat(),
            label.heightInPixels.toFloat(),
            1f
        )

        val mvp = FloatArray(16)
        android.opengl.Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, modelMatrix, 0)

        val r: Float
        val g: Float
        val b: Float
        val a: Float
        
        // Always use label native color (Day mode). Shader handles Night Mode redaction.
        r = label.fixedR / 65536.0f
        g = label.fixedG / 65536.0f
        b = label.fixedB / 65536.0f
        a = label.fixedA / 65536.0f

        label.texCoords!!.position(0)
        android.opengl.GLES20.glVertexAttribPointer(
            texLoc,
            2,
            GL10.GL_FIXED,
            false,
            0,
            label.texCoords
        )

        shaderProgram!!.setUniforms(mvp, mTexture!!.textureId, r, g, b, a)
        shaderProgram!!.setNightMode(renderState!!.nightVisionMode)

        android.opengl.GLES20.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4)
    }

    private class Label(ts: TextPrimitive, fontSizeScale: Double) : LabelMaker.LabelData(
        ts.text ?: throw RuntimeException("Bad Label: " + ts.javaClass),
        -0x1,
        (fontSizeScale * ts.fontSize).toInt()
    ) {
        var x: Float = 0f
        var y: Float = 0f
        var z: Float = 0f
        var offset: Float = 0f
        var fixedR: Int = 0
        var fixedG: Int = 0
        var fixedB: Int = 0
        var fixedA: Int = 0

        init {
            val pos = ts.location
            x = pos.x
            y = pos.y
            z = pos.z

            offset = ts.offset

            val rgb = ts.color
            val a = 0xff
            val r = rgb shr 16 and 0xff
            val g = rgb shr 8 and 0xff
            val b = rgb and 0xff
            fixedA = FixedPoint.floatToFixedPoint(a / 255.0f)
            fixedB = FixedPoint.floatToFixedPoint(b / 255.0f)
            fixedG = FixedPoint.floatToFixedPoint(g / 255.0f)
            fixedR = FixedPoint.floatToFixedPoint(r / 255.0f)
        }
    }

    companion object {
        private const val COMPUTE_REGIONS = true
    }
}

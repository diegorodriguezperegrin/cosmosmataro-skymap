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
package com.google.android.stardroid.renderer

import android.content.res.Resources
import android.opengl.GLES20
import android.opengl.Matrix
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.PI
import com.google.android.stardroid.math.TWO_PI
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.shader.TextureShaderProgram
import com.google.android.stardroid.renderer.util.SearchHelper
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.TextureReference
import com.google.android.stardroid.renderer.util.TexturedQuad
import org.cosmosmataro.skymap.R
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

class SearchArrow {
    // The arrow quad is 10% of the screen width or height, whichever is smaller.
    private val ARROW_SIZE = 0.1f
    // The circle quad is 40% of the screen width or height, whichever is smaller.
    private val CIRCLE_SIZE = 0.4f

    // The target position is (1, theta, phi) in spherical coordinates.
    private var mTargetTheta = 0f
    private var mTargetPhi = 0f
    private var mCircleQuad: TexturedQuad? = null
    private var mArrowQuad: TexturedQuad? = null
    private var mArrowOffset = 0f
    private var mCircleSizeFactor = 1f
    private var mArrowSizeFactor = 1f
    private var mFullCircleScaleFactor = 1f

    private var mArrowTex: TextureReference? = null
    private var mCircleTex: TextureReference? = null
    private var mLastAngle = 0f

    fun reloadTextures(gl: GL10, res: Resources?, textureManager: TextureManager) {
        mArrowTex = textureManager.getTextureFromResource(gl, R.drawable.arrow)
        mCircleTex = textureManager.getTextureFromResource(gl, R.drawable.arrowcircle)
    }

    fun resize(gl: GL10?, screenWidth: Int, screenHeight: Int, fullCircleSize: Float) {
        mArrowSizeFactor = ARROW_SIZE * min(screenWidth.toFloat(), screenHeight.toFloat())
        mArrowQuad = TexturedQuad(
            mArrowTex!!,
            0f, 0f, 0f,
            0.5f, 0f, 0f,
            0f, 0.5f, 0f
        )

        mFullCircleScaleFactor = fullCircleSize
        mCircleSizeFactor = CIRCLE_SIZE * mFullCircleScaleFactor
        mCircleQuad = TexturedQuad(
            mCircleTex!!,
            0f, 0f, 0f,
            0.5f, 0f, 0f,
            0f, 0.5f, 0f
        )

        mArrowOffset = mCircleSizeFactor + mArrowSizeFactor
    }

    fun draw(
        gl: GL10, lookDir: Vector3, upDir: Vector3, searchHelper: SearchHelper,
        nightVisionMode: Boolean, shader: TextureShaderProgram, projectionMatrix: FloatArray?
    ) {
        val lookPhi = MathUtils.acos(lookDir.y)
        val lookTheta = MathUtils.atan2(lookDir.z, lookDir.x)

        // Positive diffPhi means you need to look up.
        val diffPhi = lookPhi - mTargetPhi

        // Positive diffTheta means you need to look right.
        var diffTheta = lookTheta - mTargetTheta

        // diffTheta could potentially be in the range from (-2*Pi, 2*Pi), but we need
        // it
        // in the range (-Pi, Pi).
        if (diffTheta > PI) {
            diffTheta -= TWO_PI
        } else if (diffTheta < -PI) {
            diffTheta += TWO_PI
        }

        // The image I'm using is an arrow pointing right, so an angle of 0 corresponds
        // to that.
        // This is why we're taking arctan(diffPhi / diffTheta), because diffTheta
        // corresponds to
        // the amount we need to rotate in the xz plane and diffPhi in the up direction.
        var angle = MathUtils.atan2(diffPhi, diffTheta)

        // Need to add on the camera roll, which is the amount you need to rotate the
        // vector (0, 1, 0)
        // about the look direction in order to get it in the same plane as the up
        // direction.
        val roll = angleBetweenVectorsWithRespectToAxis(Vector3(0f, 1f, 0f), upDir, lookDir)

        angle += roll

        // Distance is a normalized value of the distance.
        val distance = 1.0f / (1.414f * PI) *
                MathUtils.sqrt(diffTheta * diffTheta + diffPhi * diffPhi)

        // Prevent "bursting" (rapid spinning) when very close to the target.
        // The atan2 function becomes unstable when diffPhi and diffTheta are near zero.
        // We freeze the angle if we are within a small threshold.
        if (distance > 0.005f) { // ~0.9 degrees threshold
            mLastAngle = angle
        } else {
            angle = mLastAngle
        }

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val baseModel = FloatArray(16)
        // Rotate in Z. Matrix.setRotateM takes angle in degrees.
        Matrix.setRotateM(baseModel, 0, angle * 180.0f / PI, 0f, 0f, -1f)

        // 0 means the circle is not expanded at all. 1 means fully expanded.
        val expandFactor = searchHelper.transitionFactor

        if (expandFactor == 0f) {
            val r: Float
            val g: Float
            val b: Float
            val a: Float
            if (nightVisionMode) {
                r = 0.6f
                g = 0f
                b = 0f
                a = 1.0f
            } else {
                r = 1.0f - distance
                g = 0f
                b = distance
                a = 1.0f
            }

            // Draw Circle
            val circleModel = FloatArray(16)
            Matrix.scaleM(
                circleModel,
                0,
                baseModel,
                0,
                mCircleSizeFactor,
                mCircleSizeFactor,
                mCircleSizeFactor
            )
            val circleMVP = FloatArray(16)
            Matrix.multiplyMM(circleMVP, 0, projectionMatrix, 0, circleModel, 0)
            mCircleQuad!!.draw(gl, shader, circleMVP, r, g, b, a)

            // Draw Arrow
            val arrowModel = FloatArray(16)
            Matrix.translateM(arrowModel, 0, baseModel, 0, mArrowOffset * 0.5f, 0f, 0f)
            Matrix.scaleM(
                arrowModel,
                0,
                arrowModel,
                0,
                mArrowSizeFactor,
                mArrowSizeFactor,
                mArrowSizeFactor
            )
            val arrowMVP = FloatArray(16)
            Matrix.multiplyMM(arrowMVP, 0, projectionMatrix, 0, arrowModel, 0)
            mArrowQuad!!.draw(gl, shader, arrowMVP, r, g, b, a)
        } else {
            val r: Float
            val g: Float
            val b: Float
            val a: Float
            // glColor4x(1,1,1, 0.7)
            // EnvColor(1, 0/0.5, 0, 0)
            if (nightVisionMode) {
                r = 1.0f
                g = 0f
                b = 0f
                a = 0.7f
            } else {
                r = 1.0f
                g = 0.5f
                b = 0f
                a = 0.7f
            }

            val circleScaleMatrix = FloatArray(16)
            val circleScale = mFullCircleScaleFactor * expandFactor +
                    mCircleSizeFactor * (1 - expandFactor)

            Matrix.scaleM(circleScaleMatrix, 0, baseModel, 0, circleScale, circleScale, circleScale)
            val circleMVP = FloatArray(16)
            Matrix.multiplyMM(circleMVP, 0, projectionMatrix, 0, circleScaleMatrix, 0)

            mCircleQuad!!.draw(gl, shader, circleMVP, r, g, b, a)
        }

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun setTarget(position: Vector3) {
        val position = position.normalizedCopy()
        mTargetPhi = MathUtils.acos(position.y)
        mTargetTheta = MathUtils.atan2(position.z, position.x)
    }

    companion object {
        // Given vectors v1 and v2, and an axis, this function returns the angle which
        // you must rotate v1
        // by in order for it to be in the same plane as v2 and axis. Assumes that all
        // vectors are unit
        // vectors and v2 and axis are perpendicular.
        private fun angleBetweenVectorsWithRespectToAxis(
            v1: Vector3,
            v2: Vector3,
            axis: Vector3
        ): Float {
            // Make v1 perpendicular to axis. We want an orthonormal basis for the plane
            // perpendicular
            // to axis. After rotating v1, the projection of v1 and v2 into this plane
            // should be equal.
            var v1proj = v1.minus(v1.projectOnto(axis))
            v1proj = v1proj.normalizedCopy()

            // Get the vector perpendicular to the one you're rotating and the axis. Since
            // axis and v1proj
            // are orthonormal, this one must be a unit vector perpendicular to all three.
            val perp = axis * v1proj

            // v2 is perpendicular to axis, so therefore it's already in the same plane as
            // v1proj perp.
            val cosAngle = v1proj dot v2
            val sinAngle = -(perp dot v2)

            return MathUtils.atan2(sinAngle, cosAngle)
        }
    }
}

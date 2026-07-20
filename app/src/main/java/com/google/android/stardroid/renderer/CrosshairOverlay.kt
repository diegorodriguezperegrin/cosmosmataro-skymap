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
import android.opengl.GLES20
import android.opengl.Matrix
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.TWO_PI
import com.google.android.stardroid.renderer.shader.TextureShaderProgram
import com.google.android.stardroid.renderer.util.SearchHelper
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.TextureReference
import com.google.android.stardroid.renderer.util.TexturedQuad
import org.cosmosmataro.skymap.R
import javax.microedition.khronos.opengles.GL10

class CrosshairOverlay {
    private var mQuad: TexturedQuad? = null
    private var mTex: TextureReference? = null

    fun reloadTextures(gl: GL10, res: Resources?, textureManager: TextureManager) {
        // Load the crosshair texture.
        mTex = textureManager.getTextureFromResource(gl, R.drawable.reticle_transparent)
    }

    fun resize(gl: GL10?, screenWidth: Int, screenHeight: Int) {
        mQuad = TexturedQuad(
            mTex!!,
            0f, 0f, 0f,
            40.0f / screenWidth, 0f, 0f,
            0f, 40.0f / screenHeight, 0f
        )
    }

    fun draw(
        gl: GL10, searchHelper: SearchHelper, nightVisionMode: Boolean,
        shader: TextureShaderProgram, projectionMatrix: FloatArray?
    ) {
        // Return if the label has a negative z.
        val position = searchHelper.getTransformedPosition()
        if (position!!.z < 0) {
            return
        }

        val model = FloatArray(16)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, position.x, position.y, 0f)

        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, model, 0)

        val period = 1000
        val time = System.currentTimeMillis()
        val intensity = 0.7f + 0.3f * MathUtils.sin((time % period) * TWO_PI / period)

        val r = intensity
        var g = intensity
        var b = intensity
        val a = 0.7f

        if (nightVisionMode) {
            g = 0f
            b = 0f
        }

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        mQuad!!.draw(gl, shader, mvp, r, g, b, a)

        GLES20.glDisable(GLES20.GL_BLEND)
    }
}

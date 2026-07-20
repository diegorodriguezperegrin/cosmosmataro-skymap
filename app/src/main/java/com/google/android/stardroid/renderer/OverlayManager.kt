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

import android.opengl.Matrix
import android.util.Log
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Matrix4x4
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.shader.TextureShaderProgram
import com.google.android.stardroid.renderer.util.SearchHelper
import com.google.android.stardroid.renderer.util.TextureManager
import javax.microedition.khronos.opengles.GL10

class OverlayManager(layer: Int, manager: TextureManager) : RendererObjectManager(layer, manager) {
    private var mWidth = 2
    private var mHeight = 2
    private var mGeoToViewerTransform = Matrix4x4.createIdentity()
    private var mLookDir = Vector3(0f, 0f, 0f)
    private var mUpDir = Vector3(0f, 1f, 0f)
    private var mTransformedLookDir = Vector3(0f, 0f, 0f)
    private var mTransformedUpDir = Vector3(0f, 1f, 0f)
    private var mMustUpdateTransformedOrientation = true

    private var mSearching = false
    private val mSearchHelper = SearchHelper()

    // private ColoredQuad mDarkQuad = null; // Unused in original code (commented out draw calls)
    private val mSearchArrow = SearchArrow()
    private val mCrosshair = CrosshairOverlay()

    private var mTextureShaderProgram: TextureShaderProgram? = null

    fun setShaderProgram(shader: TextureShaderProgram) {
        mTextureShaderProgram = shader
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        val res = renderState!!.resources
        // Ensure GL and Res are not null before passing to these methods
        if (gl != null && res != null) {
            mSearchArrow.reloadTextures(gl, res, textureManager)
            mCrosshair.reloadTextures(gl, res, textureManager)
        }
    }

    fun resize(gl: GL10?, screenWidth: Int, screenHeight: Int) {
        mWidth = screenWidth
        mHeight = screenHeight

        // If the search target is within this radius of the center of the screen, the
        // user is considered to have "found" it.
        val searchTargetRadius = Math.min(screenWidth, screenHeight) - 20f
        mSearchHelper.setTargetFocusRadius(searchTargetRadius)
        mSearchHelper.resize(screenWidth, screenHeight)

        if (gl != null) {
            mSearchArrow.resize(gl, screenWidth, screenHeight, searchTargetRadius)
            mCrosshair.resize(gl, screenWidth, screenHeight)
        }

        // mDarkQuad = new ColoredQuad(...) // Unused
    }

    fun setViewOrientation(lookDir: Vector3, upDir: Vector3) {
        mLookDir = lookDir
        mUpDir = upDir
        mMustUpdateTransformedOrientation = true
    }

    override fun drawInternal(gl: GL10?) {
        if (mTextureShaderProgram == null || gl == null) {
            return
        }
        mTextureShaderProgram!!.useProgram()

        updateTransformedOrientationIfNecessary()

        // Construct Ortho Projection Matrix
        // GLU.gluOrtho2D(gl, left, -left, bottom, -bottom);
        val left = mWidth / 2.0f
        val bottom = mHeight / 2.0f
        val projectionMatrix = FloatArray(16)
        // orthoM(m, offset, left, right, bottom, top, near, far)
        Matrix.orthoM(projectionMatrix, 0, left, -left, bottom, -bottom, -10f, 10f)

        if (mSearching) {
            mSearchHelper.setTransform(renderState!!.transformToDeviceMatrix)
            mSearchHelper.checkState()

            // float transitionFactor = mSearchHelper.getTransitionFactor();

            // Draw the crosshair.
            mCrosshair.draw(
                gl, mSearchHelper, renderState!!.nightVisionMode, mTextureShaderProgram!!,
                projectionMatrix
            )

            // Draw the search arrow.
            mSearchArrow.draw(
                gl, mTransformedLookDir, mTransformedUpDir, mSearchHelper,
                renderState!!.nightVisionMode, mTextureShaderProgram!!, projectionMatrix
            )
        }

        // Disable vertex arrays managed by TexturedQuad/Shader is good practice?
        // TexturedQuad mostly handles it.
    }

    // viewerUp MUST be normalized.
    fun setViewerUpDirection(viewerUp: Vector3) {
        // Log.d("OverlayManager", "Setting viewer up " + viewerUp);
        if (MathUtils.abs(viewerUp.y) < 0.999f) {
            var cp = viewerUp * Vector3(0f, 1f, 0f)
            cp = cp.normalizedCopy()
            mGeoToViewerTransform = Matrix4x4.createRotation(MathUtils.acos(viewerUp.y), cp)
        } else {
            mGeoToViewerTransform = Matrix4x4.createIdentity()
        }
        mMustUpdateTransformedOrientation = true
    }

    fun enableSearchOverlay(target: Vector3, targetName: String) {
        Log.d("OverlayManager", "Searching for $target")
        mSearching = true
        mSearchHelper.setTransform(renderState!!.transformToDeviceMatrix)
        mSearchHelper.setTarget(target, targetName)
        val transformedPosition = Matrix4x4.multiplyMV(mGeoToViewerTransform, target)
        mSearchArrow.setTarget(transformedPosition)
        queueForReload(false)
    }

    fun disableSearchOverlay() {
        mSearching = false
    }

    private fun updateTransformedOrientationIfNecessary() {
        if (mMustUpdateTransformedOrientation && mSearching) {
            mTransformedLookDir = Matrix4x4.multiplyMV(mGeoToViewerTransform, mLookDir)
            mTransformedUpDir = Matrix4x4.multiplyMV(mGeoToViewerTransform, mUpDir)
            mMustUpdateTransformedOrientation = false
        }
    }
}

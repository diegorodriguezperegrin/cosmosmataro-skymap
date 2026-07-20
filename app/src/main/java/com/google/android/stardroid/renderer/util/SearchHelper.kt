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

import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Matrix4x4
import com.google.android.stardroid.math.Vector3
import kotlin.math.max
import kotlin.math.min

class SearchHelper {
    private var target = Vector3(0f, 0f, 0f)
    private var cachedTransformedPosition: Vector3? = Vector3(0f, 0f, 0f)

    private var halfScreenWidth = 1f
    private var halfScreenHeight = 1f
    private var transformMatrix: Matrix4x4? = null
    private var targetFocusRadius = 0f

    // Returns a number between 0 and 1, 0 meaning that we should draw the UI as if the target
    // is not in focus, 1 meaning it should be fully in focus, and between the two meaning
    // it just transitioned between the two, so we should be drawing the transition.
    var transitionFactor = 0f
        private set
    private var lastUpdateTime: Long = 0
    private var wasInFocusLastCheck = false
    var targetName = "Default target name"
        private set

    fun resize(width: Int, height: Int) {
        halfScreenWidth = width * 0.5f
        halfScreenHeight = height * 0.5f
    }

    fun setTarget(target: Vector3, targetName: String) {
        this.targetName = targetName
        this.target = target.copyForJ()
        cachedTransformedPosition = null
        lastUpdateTime = System.currentTimeMillis()
        transitionFactor = if (targetInFocusRadiusImpl()) 1f else 0f
    }

    fun setTransform(transformMatrix: Matrix4x4?) {
        this.transformMatrix = transformMatrix
        cachedTransformedPosition = null
    }

    fun getTransformedPosition(): Vector3? {
        if (cachedTransformedPosition == null && transformMatrix != null) {
            // Transform the label position by our transform matrix
            cachedTransformedPosition = Matrix4x4.transformVector(transformMatrix!!, target)
        }
        return cachedTransformedPosition
    }

    fun targetInFocusRadius(): Boolean {
        return wasInFocusLastCheck
    }

    fun setTargetFocusRadius(radius: Float) {
        targetFocusRadius = radius
    }

    // Checks whether the search target is in the focus or not, and updates the seconds in the state
    // accordingly.
    fun checkState() {
        val inFocus = targetInFocusRadiusImpl()
        wasInFocusLastCheck = inFocus
        val time = System.currentTimeMillis()
        val delta = 0.001f * (time - lastUpdateTime)
        transitionFactor += delta * (if (inFocus) 1 else -1)
        transitionFactor = min(1f, max(0f, transitionFactor))
        lastUpdateTime = time
    }

    // Returns the distance from the center of the screen, in pixels, if the target is in front of
    // the viewer.  Returns infinity if the point is behind the viewer.
    private val distanceFromCenterOfScreen: Float
        private get() {
            val position = getTransformedPosition()
            return if (position!!.z > 0) {
                val dx = position.x * halfScreenWidth
                val dy = position.y * halfScreenHeight
                MathUtils.sqrt(dx * dx + dy * dy)
            } else {
                Float.POSITIVE_INFINITY
            }
        }

    private fun targetInFocusRadiusImpl(): Boolean {
        val distFromCenter = distanceFromCenterOfScreen
        return 0.5f * targetFocusRadius > distFromCenter
    }
}

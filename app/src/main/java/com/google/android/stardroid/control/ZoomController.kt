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

package com.google.android.stardroid.control

import android.util.Log
import com.google.android.stardroid.base.VisibleForTesting
import com.google.android.stardroid.util.MiscUtil
import kotlin.math.min

/**
 * Controls the field of view of a user.
 *
 * @author John Taylor
 */
class ZoomController : AbstractController() {

    private fun setFieldOfView(zoomDegrees: Float) {
        if (!controllerEnabled) {
            return
        }
        Log.d(TAG, "Setting field of view to $zoomDegrees")
        astronomerModel.fieldOfView = zoomDegrees
    }

    override fun start() {
        // Nothing to do
    }

    override fun stop() {
        // Nothing to do
    }

    /**
     * Increases the field of view by the given ratio.  That is, a number >1 will zoom the user
     * out, up to a predetermined maximum.
     */
    fun zoomBy(ratio: Float) {
        var zoomDegrees = astronomerModel.fieldOfView
        zoomDegrees = min(zoomDegrees * ratio, MAX_ZOOM_OUT)
        setFieldOfView(zoomDegrees)
    }

    companion object {
        private val TAG = MiscUtil.getTag(ZoomController::class.java)

        @VisibleForTesting
        const val MAX_ZOOM_OUT = 90.0f
    }
}

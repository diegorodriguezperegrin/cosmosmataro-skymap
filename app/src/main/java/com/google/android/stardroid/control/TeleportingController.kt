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

package com.google.android.stardroid.control

import android.util.Log
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.util.MiscUtil

/**
 * Flies the user to the search target in manual mode.
 *
 * @author John Taylor
 */
class TeleportingController : AbstractController() {

    /**
     * Teleport the astronomer instantaneously from his current pointing to a new
     * one.
     *
     * @param targetXyz The destination pointing.
     */
    fun teleport(targetXyz: Vector3) {
        Log.d(TAG, "Teleporting to target $targetXyz")
        val pointing = astronomerModel.pointing
        val hereXyz = pointing.lineOfSight
        if (targetXyz == hereXyz) {
            return
        }

        // Here we calculate the new direction of 'up' along the screen in
        // celestial coordinates.  This is not uniquely defined - it just needs
        // to be perpendicular to the target (which is effectively the normal into
        // the screen in celestial coordinates.)
        val hereTopXyz = pointing.perpendicular
        hereTopXyz.normalize()
        val normal = hereXyz * hereTopXyz
        val newUpXyz = normal * targetXyz

        astronomerModel.setPointing(targetXyz, newUpXyz)
    }

    override fun start() {
        // Nothing to do.
    }

    override fun stop() {
        // Nothing to do.
        // We could consider aborting the teleport, but it's OK for now.
    }

    companion object {
        private val TAG = MiscUtil.getTag(TeleportingController::class.java)
    }
}

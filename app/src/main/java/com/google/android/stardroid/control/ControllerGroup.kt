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

import android.content.Context
import android.util.Log
import com.google.android.stardroid.base.VisibleForTesting
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.util.MiscUtil
import java.util.ArrayList
import java.util.Date
import javax.inject.Inject

/**
 * Manages all the different controllers that affect the model of the observer.
 * Is both a factory and acts as a facade to the underlying controllers.
 *
 * @author John Taylor
 */
class ControllerGroup @Inject constructor(
    context: Context,
    private val sensorOrientationController: SensorOrientationController,
    locationController: LocationController
) : Controller {
    private val controllers = ArrayList<Controller>()
    private val zoomController: ZoomController
    private val manualDirectionController: ManualOrientationController
    private val timeTravelClock = TimeTravelClock()
    private val transitioningClock = TransitioningCompositeClock(timeTravelClock, RealClock())
    private val teleportingController: TeleportingController
    var isAutoMode: Boolean = true
        set(enabled) {
            field = enabled
            // We want to allow manual adjustment even in auto mode to correct the alignment.
            // So we don't disable the manual controller.
            manualDirectionController.setEnabled(true)
            sensorOrientationController.setEnabled(enabled)
            model?.autoUpdatePointing = enabled
        }
    private var model: AstronomerModel? = null

    init {
        addController(locationController)
        addController(sensorOrientationController)
        manualDirectionController = ManualOrientationController()
        addController(manualDirectionController)
        zoomController = ZoomController()
        addController(zoomController)
        teleportingController = TeleportingController()
        addController(teleportingController)
        this.isAutoMode = true
        // Allow manual interactions to "correct" the view even in auto mode.
        manualDirectionController.setEnabled(true)
    }

    override fun setEnabled(enabled: Boolean) {
        Log.i(TAG, "Enabling all controllers")
        for (controller in controllers) {
            controller.setEnabled(enabled)
        }
    }

    override fun setModel(model: AstronomerModel) {
        Log.i(TAG, "Setting model")
        for (controller in controllers) {
            controller.setModel(model)
        }
        this.model = model
        model.autoUpdatePointing = isAutoMode
        model.setClock(transitioningClock)
    }

    /**
     * Switches to time-travel model and start with the supplied time.
     * See [.useRealTime].
     */
    fun goTimeTravel(d: Date) {
        transitioningClock.goTimeTravel(d)
    }

    /**
     * Gets the id of the string used to display the current speed of time travel.
     */
    val currentSpeedTag: Int
        get() = timeTravelClock.currentSpeedTag

    /**
     * Sets the model back to using real time.
     * See [.goTimeTravel].
     */
    fun useRealTime() {
        transitioningClock.returnToRealTime()
    }

    /**
     * Increases the rate of time travel into the future (or decreases the rate of
     * time travel into the past) if in time travel mode.
     */
    fun accelerateTimeTravel() {
        timeTravelClock.accelerateTimeTravel()
    }

    /**
     * Decreases the rate of time travel into the future (or increases the rate of
     * time travel into the past) if in time travel mode.
     */
    fun decelerateTimeTravel() {
        timeTravelClock.decelerateTimeTravel()
    }

    /**
     * Pauses time, if in time travel mode.
     */
    fun pauseTime() {
        timeTravelClock.pauseTime()
    }

    override fun start() {
        Log.i(TAG, "Starting controllers")
        for (controller in controllers) {
            controller.start()
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping controllers")
        for (controller in controllers) {
            controller.stop()
        }
    }

    /**
     * Moves the pointing right and left.
     *
     * @param radians the angular change in the pointing in radians (only
     * accurate in the limit as radians tends to 0.)
     */
    fun changeRightLeft(radians: Float) {
        manualDirectionController.changeRightLeft(radians)
    }

    /**
     * Moves the pointing up and down.
     *
     * @param radians the angular change in the pointing in radians (only
     * accurate in the limit as radians tends to 0.)
     */
    fun changeUpDown(radians: Float) {
        manualDirectionController.changeUpDown(radians)
    }

    /**
     * Rotates the view about the current center point.
     */
    fun rotate(degrees: Float) {
        manualDirectionController.rotate(degrees)
    }

    /**
     * Changes the azimuth (horizontal direction) of the view.
     */
    fun changeAzimuth(radians: Float) {
        manualDirectionController.changeAzimuth(radians)
    }

    /**
     * Sends the astronomer's pointing to the new target.
     *
     * @param target the destination
     */
    fun teleport(target: Vector3) {
        teleportingController.teleport(target)
    }

    /**
     * Adds a new controller to this
     */
    @VisibleForTesting
    fun addController(controller: Controller) {
        controllers.add(controller)
    }

    fun zoomBy(ratio: Float) {
        zoomController.zoomBy(ratio)
    }

    fun setZoomEnabled(enabled: Boolean) {
        zoomController.setEnabled(enabled)
    }

    companion object {
        private val TAG = MiscUtil.getTag(ControllerGroup::class.java)
    }
}

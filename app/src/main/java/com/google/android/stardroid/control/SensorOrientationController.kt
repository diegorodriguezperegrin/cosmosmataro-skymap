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

import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.util.MiscUtil
import com.google.android.stardroid.util.smoothers.ExponentiallyWeightedSmoother
import com.google.android.stardroid.util.smoothers.PlainSmootherModelAdaptor
import javax.inject.Inject
import javax.inject.Provider

/**
 * Sets the direction of view from the orientation sensors.
 *
 * @author John Taylor
 */
class SensorOrientationController @Inject constructor(
    private val modelAdaptorProvider: Provider<PlainSmootherModelAdaptor>,
    private val manager: SensorManager?,
    private val sharedPreferences: SharedPreferences
) : AbstractController(), SensorEventListener {
    // TODO(johntaylor): this class needs to be refactored to use the new
    // sensor API and to behave properly when sensors are not available.

    private class SensorDampingSettings(var damping: Float, var exponent: Int)

    private var accelerometerSmoother: SensorEventListener? = null
    private var compassSmoother: SensorEventListener? = null
    private val rotationSensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    override fun start() {
        val modelAdaptor = modelAdaptorProvider.get()

        if (manager != null) {
            if (!sharedPreferences.getBoolean(
                    ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO,
                    false
                )
            ) {
                Log.d(TAG, "Using rotation sensor")
                manager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                // TODO(jontayler): remove this code once enough it's used in few enough phones.
                Log.d(TAG, "Using classic sensors")
                Log.d(TAG, "Exponentially weighted smoothers used")
                val dampingPreference = sharedPreferences.getString(
                    ApplicationConstants.SENSOR_DAMPING_PREF_KEY,
                    ApplicationConstants.SENSOR_DAMPING_STANDARD
                )
                val speedPreference = sharedPreferences.getString(
                    ApplicationConstants.SENSOR_SPEED_PREF_KEY,
                    ApplicationConstants.SENSOR_SPEED_STANDARD
                )
                Log.d(TAG, "Sensor damping preference $dampingPreference")
                Log.d(TAG, "Sensor speed preference $speedPreference")
                var dampingIndex = 0
                if (ApplicationConstants.SENSOR_DAMPING_HIGH == dampingPreference) {
                    dampingIndex = 1
                } else if (ApplicationConstants.SENSOR_DAMPING_EXTRA_HIGH == dampingPreference) {
                    dampingIndex = 2
                } else if (ApplicationConstants.SENSOR_DAMPING_REALLY_HIGH == dampingPreference) {
                    dampingIndex = 3
                }
                var sensorSpeed = SensorManager.SENSOR_DELAY_GAME
                if (ApplicationConstants.SENSOR_SPEED_SLOW == speedPreference) {
                    sensorSpeed = SensorManager.SENSOR_DELAY_NORMAL
                } else if (ApplicationConstants.SENSOR_SPEED_HIGH == speedPreference) {
                    sensorSpeed = SensorManager.SENSOR_DELAY_FASTEST
                }
                accelerometerSmoother = ExponentiallyWeightedSmoother(
                    modelAdaptor,
                    ACC_DAMPING_SETTINGS[dampingIndex].damping,
                    ACC_DAMPING_SETTINGS[dampingIndex].exponent
                )
                compassSmoother = ExponentiallyWeightedSmoother(
                    modelAdaptor,
                    MAG_DAMPING_SETTINGS[dampingIndex].damping,
                    MAG_DAMPING_SETTINGS[dampingIndex].exponent
                )
                val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                val compass = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                if (accelerometer == null || compass == null) {
                    Log.e(TAG, "Missing accelerometer or compass! Aborting")
                    return
                }

                manager.registerListener(
                    accelerometerSmoother,
                    accelerometer,
                    sensorSpeed
                )
                manager.registerListener(
                    compassSmoother,
                    compass,
                    sensorSpeed
                )
            }
        }
        Log.d(TAG, "Registered sensor listener")
    }

    override fun stop() {
        Log.d(
            TAG, "Unregistering sensor listeners: " + accelerometerSmoother + ", "
                    + compassSmoother + ", " + this
        )
        manager?.unregisterListener(accelerometerSmoother)
        manager?.unregisterListener(compassSmoother)
        manager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor != rotationSensor) {
            return
        }
        astronomerModel.setPhoneSensorValues(event.values)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Ignore
    }

    companion object {
        private val TAG = MiscUtil.getTag(SensorOrientationController::class.java)

        /**
         * Parameters that control the smoothing of the accelerometer and
         * magnetic sensors.
         */
        private val ACC_DAMPING_SETTINGS = arrayOf(
            SensorDampingSettings(0.7f, 3),
            SensorDampingSettings(0.7f, 3),
            SensorDampingSettings(0.1f, 3),
            SensorDampingSettings(0.1f, 3)
        )
        private val MAG_DAMPING_SETTINGS = arrayOf(
            SensorDampingSettings(0.05f, 3),  // Derived for the Nexus One
            SensorDampingSettings(0.001f, 4),  // Derived for the unpatched MyTouch Slide
            SensorDampingSettings(0.0001f, 5),  // Just guessed for Nexus 6
            SensorDampingSettings(0.000001f, 5)  // Just guessed for Nexus 6
        )
    }
}

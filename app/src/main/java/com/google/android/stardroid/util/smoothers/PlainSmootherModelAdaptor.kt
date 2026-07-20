// Copyright 2010 Google Inc.
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

package com.google.android.stardroid.util.smoothers

import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.util.MiscUtil
import javax.inject.Inject

/**
 * Adapts sensor output for use with the astronomer model.
 *
 * @author John Taylor
 */
class PlainSmootherModelAdaptor @Inject constructor(
    private val model: AstronomerModel,
    sharedPreferences: SharedPreferences
) : SensorEventListener {

    // Helper to copy Vector3 if needed, or just use copy() if it's a data class or has method.
    // Assuming Vector3 has a copy constructor or copy method.
    // ApplicationConstants.INITIAL_... are Vector3.
    // If Vector3 involves mutable state in this logic, we must copy.
    // Vector3 in this codebase seems mutable.
    private val magneticValues = ApplicationConstants.INITIAL_MAGNETIC_FIELD.copy()
    private val acceleration = ApplicationConstants.INITIAL_ACCELERATION.copy()
    private val reverseMagneticZaxis = sharedPreferences.getBoolean(
        ApplicationConstants.REVERSE_MAGNETIC_Z_PREFKEY, false
    )

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        val sensor = sensorEvent.sensor
        val values = sensorEvent.values
        if (sensor.type == Sensor.TYPE_ACCELEROMETER) {
            acceleration.x = values[0]
            acceleration.y = values[1]
            acceleration.z = values[2]
        } else if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues.x = values[0]
            magneticValues.y = values[1]
            magneticValues.z = if (reverseMagneticZaxis) -values[2] else values[2]
        } else {
            Log.e(TAG, "Pump is receiving values that aren't accel or magnetic")
        }
        model.setPhoneSensorValues(acceleration, magneticValues)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do nothing
    }

    companion object {
        private val TAG = MiscUtil.getTag(PlainSmootherModelAdaptor::class.java)
    }
}

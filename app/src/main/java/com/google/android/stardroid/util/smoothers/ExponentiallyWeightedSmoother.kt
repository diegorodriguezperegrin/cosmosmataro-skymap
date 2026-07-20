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
package com.google.android.stardroid.util.smoothers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.util.MiscUtil
import kotlin.math.abs

/**
 * Exponentially weighted smoothing, as suggested by Chris M.
 *
 */
class ExponentiallyWeightedSmoother(
    listener: SensorEventListener,
    private val alpha: Float,
    private val exponent: Int
) : SensorSmoother(listener) {

    private val last = FloatArray(3)
    private val current = FloatArray(3)

    init {
        Log.d(TAG, "ExponentionallyWeightedSmoother with alpha = $alpha and exp = $exponent")
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        val values = sensorEvent.values

        for (i in 0 until 3) {
            last[i] = current[i]
            val diff = values[i] - last[i]
            var correction = diff * alpha
            for (j in 1 until exponent) {
                correction *= abs(diff)
            }
            if (correction > abs(diff) || correction < -abs(diff)) {
                correction = diff
            }
            current[i] = last[i] + correction
            sensorEvent.values[i] = current[i]
        }

        listener.onSensorChanged(sensorEvent)
    }

    companion object {
        private val TAG = MiscUtil.getTag(ExponentiallyWeightedSmoother::class.java)
    }
}

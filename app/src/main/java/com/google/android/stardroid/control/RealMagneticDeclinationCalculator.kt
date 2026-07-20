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

import android.hardware.GeomagneticField
import com.google.android.stardroid.math.LatLong

/**
 * Encapsulates the calculation of magnetic declination for the user's location
 * and position.
 *
 * @author John Taylor
 */
class RealMagneticDeclinationCalculator : MagneticDeclinationCalculator {
    private var geomagneticField: GeomagneticField? = null

    /**
     * {@inheritDoc}
     * Silently returns zero if the time and location have not been set.
     */
    override val declination: Float
        get() = geomagneticField?.declination ?: 0f

    /**
     * Sets the user's current location and time.
     */
    override fun setLocationAndTime(location: LatLong, timeInMillis: Long) {
        geomagneticField = GeomagneticField(
            location.latitude,
            location.longitude,
            0f,
            timeInMillis
        )
    }

    override fun toString(): String {
        return "Real Magnetic Correction"
    }
}

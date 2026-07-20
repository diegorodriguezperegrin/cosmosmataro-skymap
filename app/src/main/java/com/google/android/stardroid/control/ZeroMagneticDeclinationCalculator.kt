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

import com.google.android.stardroid.math.LatLong

/**
 * A trivial calculator that returns zero magnetic declination.  Used when
 * the user does not want magnetic correction.
 *
 * @author John Taylor
 */
class ZeroMagneticDeclinationCalculator : MagneticDeclinationCalculator {

    override val declination: Float
        get() = 0f

    override fun setLocationAndTime(location: LatLong, timeInMillis: Long) {
        // Do nothing.
    }

    override fun toString(): String {
        return "Zero Magnetic Correction"
    }
}

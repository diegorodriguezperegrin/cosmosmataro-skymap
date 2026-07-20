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

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.util.Log
import com.google.android.stardroid.util.MiscUtil
import javax.inject.Inject
import javax.inject.Named

/**
 * Aggregates the RealMagneticDeclinationCalculator and the
 * ZeroMagneticDeclinationCalculator and switches them in the AstronomerModel.
 *
 * @author John Taylor
 */
// TODO(johntaylor): add a unit test once we've reworked the tests to use
// the Android environment (so we can access SharePreferences).
class MagneticDeclinationCalculatorSwitcher @Inject constructor(
    private val model: AstronomerModel,
    preferences: SharedPreferences,
    @Named("zero") private val zeroCalculator: MagneticDeclinationCalculator,
    @Named("real") private val realCalculator: MagneticDeclinationCalculator
) : OnSharedPreferenceChangeListener {

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
        setTheModelsCalculator(preferences)
    }

    private fun setTheModelsCalculator(preferences: SharedPreferences) {
        val useRealCalculator = preferences.getBoolean(KEY, true)
        if (useRealCalculator) {
            model.setMagneticDeclinationCalculator(realCalculator)
        } else {
            model.setMagneticDeclinationCalculator(zeroCalculator)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        // TODO(johntaylor): investigate the preferences API - currently we have too
        // many classes all hanging off SharedPreferences.
        if (KEY == key) {
            Log.i(TAG, "Magnetic declination preference changed")
            setTheModelsCalculator(sharedPreferences)
        }
    }

    companion object {
        private const val KEY = "use_magnetic_correction"
        private val TAG = MiscUtil.getTag(MagneticDeclinationCalculatorSwitcher::class.java)
    }
}

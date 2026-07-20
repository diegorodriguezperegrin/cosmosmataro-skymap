package com.google.android.stardroid.activities.util

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import javax.inject.Inject

/**
 * Controls an activity's illumination levels.
 *
 * @author John Taylor
 */
class ActivityLightLevelManager @Inject constructor(
    private val lightLevelChanger: ActivityLightLevelChanger,
    private val sharedPreferences: SharedPreferences
) : OnSharedPreferenceChangeListener {
    private enum class LightMode {
        DAY, NIGHT, AUTO
    }

    fun onResume() {
        registerWithPreferences()
        val currentMode = lightModePreference
        setActivityMode(currentMode)
    }

    private fun setActivityMode(currentMode: LightMode) {
        when (currentMode) {
            LightMode.DAY -> lightLevelChanger.setNightMode(false)
            LightMode.NIGHT -> lightLevelChanger.setNightMode(true)
            LightMode.AUTO -> throw UnsupportedOperationException("not implemented yet")
        }
    }

    private val lightModePreference: LightMode
        private get() {
            val preference =
                sharedPreferences.getString(LIGHT_MODE_KEY, LightMode.DAY.name)
            return LightMode.valueOf(preference!!)
        }

    private fun registerWithPreferences() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun onPause() {
        unregisterWithPreferences()
    }

    private fun unregisterWithPreferences() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (LIGHT_MODE_KEY != key) {
            return
        }
        setActivityMode(lightModePreference)
    }

    companion object {
        const val LIGHT_MODE_KEY = "lightmode"
    }
}

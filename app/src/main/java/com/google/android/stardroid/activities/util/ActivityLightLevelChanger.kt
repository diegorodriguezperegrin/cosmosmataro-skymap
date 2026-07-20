package com.google.android.stardroid.activities.util

import android.content.SharedPreferences
import android.view.Window
import android.view.WindowManager
import com.google.android.stardroid.ApplicationConstants
import javax.inject.Inject

/**
 * Controls the brightness level of an activity.
 *
 * @author John Taylor
 */
class ActivityLightLevelChanger @Inject constructor(
    private val window: Window,
    private val sharedPreferences: SharedPreferences,
    private val nightModeable: NightModeable?
) {
    /**
     * Activities that have some kind of custom night mode (rather than just
     * dimming the screen) implement this.
     *
     * @author John Taylor
     */
    interface NightModeable {
        fun setNightMode(nightMode: Boolean)
    }

    // Following must match the values defined in notranslate-arrays.xml
    private enum class DIM_OPTIONS {
        DIM, SYSTEM, CLASSIC
    }

    fun setNightMode(nightMode: Boolean) {
        nightModeable?.setNightMode(nightMode)
        val params = window.attributes
        if (nightMode) {
            val dimnessOption = DIM_OPTIONS.valueOf(
                sharedPreferences.getString(
                    ApplicationConstants.AUTO_DIMNESS, DIM_OPTIONS.SYSTEM.toString()
                )!!
            )
            var dimnessSetting = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            when (dimnessOption) {
                DIM_OPTIONS.DIM -> dimnessSetting = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
                DIM_OPTIONS.CLASSIC -> dimnessSetting = BRIGHTNESS_DIM_ORIGINAL
                else -> {
                }
            }
            params.screenBrightness = dimnessSetting
            params.buttonBrightness = dimnessSetting
        } else {
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = params
    }

    companion object {
        // Back in the bad old days it was hard to get the brightness level right.  On some phones
        // a particular level would be invisible, on others too bright.  We settled on the following
        // value after some experimentation....
        // This value is based on inspecting the Android source code for the
        // SettingsAppWidgetProvider:
        // http://hi-android.info/src/com/android/settings/widget/SettingsAppWidgetProvider.java.html
        // (We know that 0.05 is OK on the G1 and N1, but not some other phones, so we don't make this
        // as dim as we could...)
        private const val BRIGHTNESS_DIM_ORIGINAL = 20f / 255f
    }
}

package com.google.android.stardroid.activities.util

import android.content.Context
import android.content.res.Resources
import android.hardware.SensorManager
import org.cosmosmataro.skymap.R
import javax.inject.Inject

/**
 * Created by johntaylor on 4/24/16.
 */
class SensorAccuracyDecoder @Inject constructor(private val context: Context) {
    private val resources: Resources = context.resources

    fun getTextForAccuracy(accuracy: Int): String {
        var accuracyTxt = context.getString(R.string.sensor_accuracy_unknown)
        when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> accuracyTxt =
                context.getString(R.string.sensor_accuracy_unreliable)

            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> accuracyTxt =
                context.getString(R.string.sensor_accuracy_low)

            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> accuracyTxt =
                context.getString(R.string.sensor_accuracy_medium)

            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> accuracyTxt =
                context.getString(R.string.sensor_accuracy_high)

            SensorManager.SENSOR_STATUS_NO_CONTACT -> accuracyTxt =
                context.getString(R.string.sensor_accuracy_nocontact)
        }
        return accuracyTxt
    }

    fun getColorForAccuracy(accuracy: Int): Int {
        var accuracyColor = resources.getColor(R.color.bad_sensor)
        when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> accuracyColor =
                resources.getColor(R.color.bad_sensor)

            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> accuracyColor =
                resources.getColor(R.color.low_accuracy)

            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> accuracyColor =
                resources.getColor(R.color.medium_accuracy)

            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> accuracyColor =
                resources.getColor(R.color.high_accuracy)

            SensorManager.SENSOR_STATUS_NO_CONTACT -> accuracyColor =
                resources.getColor(R.color.bad_sensor)
        }
        return accuracyColor
    }
}

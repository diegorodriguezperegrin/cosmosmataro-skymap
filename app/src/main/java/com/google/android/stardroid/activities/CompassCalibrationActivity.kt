package com.google.android.stardroid.activities

import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.TextView
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.activities.util.SensorAccuracyDecoder
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.MiscUtil
import com.google.android.stardroid.util.Toaster
import org.cosmosmataro.skymap.R
import javax.inject.Inject

class CompassCalibrationActivity : InjectableActivity(), SensorEventListener {
    private var magneticSensor: Sensor? = null
    private lateinit var checkBoxView: CheckBox

    @Inject
    @JvmField
    var sensorManager: SensorManager? = null

    @Inject
    lateinit var accuracyDecoder: SensorAccuracyDecoder

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var analytics: Analytics

    @Inject
    lateinit var toaster: Toaster

    @Inject
    lateinit var lightLevelManager: ActivityLightLevelManager
    private var accuracyReceived = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerCompassCalibrationComponent.builder()
            .applicationComponent(applicationComponent)
            .compassCalibrationModule(CompassCalibrationModule(this)).build().inject(this)
        setContentView(R.layout.activity_compass_calibration)
        
        val web = findViewById<WebView>(R.id.compass_calib_activity_webview)
        web.loadUrl("file:///android_asset/html/animated_gif_wrapper.html")
        
        checkBoxView = findViewById(R.id.compass_calib_activity_donotshow)
        val hideCheckbox = intent.getBooleanExtra(HIDE_CHECKBOX, false)
        val whatToDoText: String
        if (hideCheckbox) {
            // Dialog was user-initiated.
            checkBoxView.visibility = View.GONE
            val reasonText = findViewById<View>(R.id.compass_calib_activity_explain_why)
            reasonText.visibility = View.GONE
            whatToDoText = getString(R.string.compass_calib_what_to_do_user)
        } else {
            checkBoxView.visibility = View.VISIBLE
            val reasonText = findViewById<View>(R.id.compass_calib_activity_explain_why)
            reasonText.visibility = View.VISIBLE
            whatToDoText = getString(R.string.compass_calib_what_to_do)
        }
        val explanationText = findViewById<TextView>(R.id.compass_calib_what_to_do)
        explanationText.text = String.format(
            whatToDoText,
            "https://www.youtube.com/watch?v=-Uq7AmSAjt8"
        )
        
        sensorManager?.let {
            magneticSensor = it.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
        
        if (magneticSensor == null) {
            (findViewById<View>(R.id.compass_calib_activity_compass_accuracy) as TextView).text =
                getString(R.string.sensor_absent)
        }
    }

    override fun onResume() {
        super.onResume()
        lightLevelManager.onResume()
        if (magneticSensor != null && sensorManager != null) {
            sensorManager?.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        lightLevelManager.onPause()
        sensorManager?.unregisterListener(this)
        if (checkBoxView.isChecked) {
            sharedPreferences.edit().putBoolean(DONT_SHOW_CALIBRATION_DIALOG, true).apply()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!accuracyReceived) {
            onAccuracyChanged(event.sensor, event.accuracy)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        accuracyReceived = true
        val accuracyTextView =
            findViewById<TextView>(R.id.compass_calib_activity_compass_accuracy)
        val accuracyText = accuracyDecoder.getTextForAccuracy(accuracy)
        accuracyTextView.text = accuracyText
        accuracyTextView.setTextColor(accuracyDecoder.getColorForAccuracy(accuracy))
        if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH && intent.getBooleanExtra(
                AUTO_DISMISSABLE,
                false
            )
        ) {
            toaster.toastLong(R.string.sensor_accuracy_high)
            this.finish()
        }
    }

    fun onOkClicked(unused: View?) {
        finish()
    }

    override fun onStart() {
        super.onStart()
    }

    companion object {
        const val HIDE_CHECKBOX = "hide checkbox"
        const val DONT_SHOW_CALIBRATION_DIALOG = "no calibration dialog"
        const val AUTO_DISMISSABLE = "auto dismissable"
        private val TAG = MiscUtil.getTag(CompassCalibrationActivity::class.java)
    }
}

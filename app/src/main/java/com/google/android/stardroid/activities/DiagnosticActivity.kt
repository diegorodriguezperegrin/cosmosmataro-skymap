package com.google.android.stardroid.activities

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.android.stardroid.StardroidApplication
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.activities.util.SensorAccuracyDecoder
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.control.LocationController
import com.google.android.stardroid.math.getDecOfUnitGeocentricVector
import com.google.android.stardroid.math.getRaOfUnitGeocentricVector
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class DiagnosticActivity : InjectableActivity(), SensorEventListener {
    @Inject
    lateinit var analytics: Analytics

    @Inject
    lateinit var app: StardroidApplication

    @Inject
    @JvmField
    var sensorManager: SensorManager? = null

    @Inject
    @JvmField
    var connectivityManager: ConnectivityManager? = null

    @Inject
    @JvmField
    var locationManager: LocationManager? = null

    @Inject
    lateinit var locationController: LocationController

    @Inject
    lateinit var model: AstronomerModel

    @Inject
    lateinit var handler: Handler

    @Inject
    lateinit var sensorAccuracyDecoder: SensorAccuracyDecoder

    @Inject
    lateinit var activityLightLevelManager: ActivityLightLevelManager
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var rotationVectorSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var continueUpdates = false
    private val knownSensorAccuracies: MutableSet<Sensor> = HashSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerDiagnosticActivityComponent.builder().applicationComponent(
            applicationComponent
        ).diagnosticActivityModule(DiagnosticActivityModule(this))
            .build().inject(this)
        setContentView(R.layout.activity_diagnostic)

        findViewById<View>(R.id.diagnostics_calibrate_button).setOnClickListener {
            val intent = android.content.Intent(this, CompassCalibrationActivity::class.java)
            intent.putExtra(CompassCalibrationActivity.HIDE_CHECKBOX, true)
            startActivity(intent)
        }


        applyMonospaceFont()
    }

    override fun onStart() {
        super.onStart()
        setText(
            R.id.diagnose_phone_txt, Build.MODEL + " (" + Build.HARDWARE + ") " +
                    Locale.getDefault().language
        )
        val androidVersion = String.format("${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
        setText(R.id.diagnose_android_version_txt, androidVersion)
        val skyMapVersion = String.format(
            app.versionName + " (" + app.version + ")"
        )
        setText(R.id.diagnose_skymap_version_txt, skyMapVersion)
    }

    override fun onResume() {
        super.onResume()
        onResumeSensors()
        activityLightLevelManager.onResume()
        continueUpdates = true
        handler.post(object : Runnable {
            override fun run() {
                updateLocation()
                updateModel()
                updateNetwork()
                if (continueUpdates) {
                    handler.postDelayed(this, UPDATE_PERIOD_MILLIS.toLong())
                }
            }
        })
    }

    private fun onResumeSensors() {
        accelSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val absentSensorColor = resources.getColor(R.color.absent_sensor)
        if (accelSensor == null) {
            setColor(R.id.diagnose_accelerometer_values_txt, absentSensorColor)
        } else {
            sensorManager!!.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (magSensor == null) {
            setColor(R.id.diagnose_compass_values_txt, absentSensorColor)
        } else {
            sensorManager!!.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroSensor == null) {
            setColor(R.id.diagnose_gyro_values_txt, absentSensorColor)
        } else {
            sensorManager!!.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        rotationVectorSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor == null) {
            setColor(R.id.diagnose_rotation_values_txt, absentSensorColor)
        } else {
            sensorManager!!.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        lightSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            setColor(R.id.diagnose_light_values_txt, absentSensorColor)
        } else {
            sensorManager!!.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun updateLocation() {
        // TODO(johntaylor): add other things like number of satellites and status
        var gpsStatusMessage: String
        try {
            val gps = locationManager!!.getProvider(LocationManager.GPS_PROVIDER)
            val gpsStatus = locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
            gpsStatusMessage = if (gps == null) {
                getString(R.string.no_gps)
            } else {
                if (gpsStatus) getString(R.string.enabled) else getString(R.string.disabled)
            }
        } catch (ex: SecurityException) {
            gpsStatusMessage = getString(R.string.permission_disabled)
        }
        setText(R.id.diagnose_gps_status_txt, gpsStatusMessage)
        val currentLocation = locationController.currentLocation
        val locationMessage = "${currentLocation.latitude}, ${currentLocation.longitude}"
        // Current provider not working    + " (" + locationController.getCurrentProvider() + ")";
        setText(R.id.diagnose_location_txt, locationMessage)
    }

    private fun updateModel() {
        val magCorrection = model.magneticCorrection
        setText(
            R.id.diagnose_magnetic_correction_txt,
            Math.abs(magCorrection).toString() + " " + (if (magCorrection > 0) getString(R.string.east) else getString(
                R.string.west
            )) + " "
                    + getString(R.string.degrees)
        )
        val pointing = model.pointing
        val lineOfSight = pointing.lineOfSight
        setText(
            R.id.diagnose_pointing_txt,
            getDegreeInHour(getRaOfUnitGeocentricVector(lineOfSight)) + ", " + getDecOfUnitGeocentricVector(
                lineOfSight
            )
        )
        val nowTime = model.time
        val dateFormatUtc = SimpleDateFormat("yyyy-MMM-dd HH:mm:ss")
        dateFormatUtc.timeZone = TimeZone.getTimeZone("UTC")
        val dateFormatLocal = SimpleDateFormat("yyyy-MMM-dd HH:mm:ss")
        setText(R.id.diagnose_utc_datetime_txt, dateFormatUtc.format(nowTime))
        setText(R.id.diagnose_local_datetime_txt, dateFormatLocal.format(nowTime))
    }

    override fun onPause() {
        super.onPause()
        continueUpdates = false
        sensorManager!!.unregisterListener(this)
        activityLightLevelManager.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        knownSensorAccuracies.add(sensor)
        Log.d(TAG, "set size" + knownSensorAccuracies.size)
        val sensorViewId: Int
        sensorViewId = if (sensor === accelSensor) {
            R.id.diagnose_accelerometer_values_txt
        } else if (sensor === magSensor) {
            R.id.diagnose_compass_values_txt
        } else if (sensor === gyroSensor) {
            R.id.diagnose_gyro_values_txt
        } else if (sensor === rotationVectorSensor) {
            R.id.diagnose_rotation_values_txt
        } else if (sensor === lightSensor) {
            R.id.diagnose_light_values_txt
        } else {
            Log.e(TAG, "Receiving accuracy change for unknown sensor $sensor")
            return
        }
        setColor(sensorViewId, sensorAccuracyDecoder.getColorForAccuracy(accuracy))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = event.sensor
        if (!knownSensorAccuracies.contains(sensor)) {
            onAccuracyChanged(sensor, event.accuracy)
        }
        val valuesViewId: Int
        valuesViewId = if (sensor === accelSensor) {
            R.id.diagnose_accelerometer_values_txt
        } else if (sensor === magSensor) {
            val accuracy = event.accuracy
            // Color code the values text based on accuracy
            setColor(R.id.diagnose_compass_values_txt, sensorAccuracyDecoder.getColorForAccuracy(accuracy))
            R.id.diagnose_compass_values_txt
        } else if (sensor === gyroSensor) {
            R.id.diagnose_gyro_values_txt
        } else if (sensor === rotationVectorSensor) {
            R.id.diagnose_rotation_values_txt
        } else if (sensor === lightSensor) {
            R.id.diagnose_light_values_txt
        } else {
            Log.e(TAG, "Receiving values for unknown sensor $sensor")
            return
        }
        val values = event.values
        setArrayValuesInUi(valuesViewId, values)

        // Something special for rotation sensor - convert to a matrix.
        if (sensor === rotationVectorSensor) {
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            for (row in 0..2) {
                var rowViewId = 0
                when (row) {
                    0 -> rowViewId = R.id.diagnose_rotation_matrix_row1_txt
                    1 -> rowViewId = R.id.diagnose_rotation_matrix_row2_txt
                    2, 3 -> rowViewId = R.id.diagnose_rotation_matrix_row3_txt
                }
                val rowValues = FloatArray(3)
                System.arraycopy(matrix, row * 3, rowValues, 0, 3)
                setArrayValuesInUi(rowViewId, rowValues)
            }
        }
    }

    private fun setArrayValuesInUi(valuesViewId: Int, values: FloatArray) {
        val valuesText = StringBuilder()
        for (value in values) {
            valuesText.append(String.format("%+.2f", value))
            valuesText.append(" | ")
        }
        valuesText.setLength(valuesText.length - 3)
        setText(valuesViewId, valuesText.toString())
    }

    private fun updateNetwork() {
        val activeNetwork = connectivityManager!!.activeNetworkInfo
        val isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting
        var message = if (isConnected) getString(R.string.connected) else getString(R.string.disconnected)
        if (isConnected) {
            if (activeNetwork!!.type == ConnectivityManager.TYPE_WIFI) {
                message += getString(R.string.wifi)
            }
            if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE) {
                message += getString(R.string.cell_network)
            }
        }
        setText(R.id.diagnose_network_status_txt, message)
    }

    private fun setText(viewId: Int, text: String) {
        (findViewById<View>(viewId) as TextView).text = text
    }

    private fun applyMonospaceFont() {
        val ids = intArrayOf(
            R.id.diagnose_compass_values_txt,
            R.id.diagnose_accelerometer_values_txt,
            R.id.diagnose_gyro_values_txt,
            R.id.diagnose_rotation_values_txt,
            R.id.diagnose_rotation_matrix_row1_txt,
            R.id.diagnose_rotation_matrix_row2_txt,
            R.id.diagnose_rotation_matrix_row3_txt,
            R.id.diagnose_light_values_txt
        )
        for (id in ids) {
            val tv = findViewById<View>(id) as TextView
            tv.typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    private fun setColor(viewId: Int, color: Int) {
        (findViewById<View>(viewId) as TextView).setTextColor(color)
    }

    private fun getDegreeInHour(deg: Float): String {
        val h = deg.toInt() / 15
        val m = ((deg / 15 - h) * 60).toInt()
        val s = ((((deg / 15 - h) * 60) - m) * 60).toInt()
        return h.toString() + "h " + m + "m " + s + "s "
    }

    companion object {
        private val TAG = MiscUtil.getTag(DiagnosticActivity::class.java)
        private const val UPDATE_PERIOD_MILLIS = 500
    }
}

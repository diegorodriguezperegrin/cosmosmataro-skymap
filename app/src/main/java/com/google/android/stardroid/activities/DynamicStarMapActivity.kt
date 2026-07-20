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
package com.google.android.stardroid.activities



import android.Manifest
import androidx.fragment.app.FragmentManager
import android.app.SearchManager
import android.content.Intent
import android.content.SharedPreferences
import android.text.Html
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.util.concurrent.ExecutorService
import com.google.android.stardroid.ar.PointSourceDetector
import com.google.android.stardroid.ar.PointSourceOverlay
import com.google.android.stardroid.control.FovAlignmentCalculator
import java.util.concurrent.Executors
import android.graphics.PixelFormat
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment
import com.google.android.stardroid.activities.dialogs.HelpDialogFragment
import com.google.android.stardroid.activities.dialogs.MultipleSearchResultsDialogFragment
import com.google.android.stardroid.activities.dialogs.NoSearchResultsDialogFragment
import com.google.android.stardroid.activities.dialogs.NoSensorsDialogFragment
import com.google.android.stardroid.activities.dialogs.TimeTravelDialogFragment
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger.NightModeable
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.activities.util.FullscreenControlsManager
import com.google.android.stardroid.activities.util.GooglePlayServicesChecker
import com.google.android.stardroid.base.Lists
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.control.ControllerGroup
import com.google.android.stardroid.control.MagneticDeclinationCalculatorSwitcher
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.layers.TrajectoryLayer
import com.google.android.stardroid.ephemeris.SolarSystemRenderable
import com.google.android.stardroid.ephemeris.SolarSystemBody

import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.renderer.SkyRenderer
import com.google.android.stardroid.search.SearchResult
import com.google.android.stardroid.touch.DragRotateZoomGestureDetector
import com.google.android.stardroid.touch.GestureInterpreter
import com.google.android.stardroid.touch.MapMover
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil
import com.google.android.stardroid.util.SensorAccuracyMonitor
import com.google.android.stardroid.views.ButtonLayerView
import org.cosmosmataro.skymap.R
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider


/**
 * The main map-rendering Activity.
 */
class DynamicStarMapActivity : InjectableActivity(), OnSharedPreferenceChangeListener,
    NightModeable, HasComponent<DynamicStarMapComponent>, SensorAccuracyMonitor.Callback {
    enum class ViewMode {
        MANUAL,
        AUTO,
        AR
    }




    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private var currentViewMode = ViewMode.AUTO // Default

    override val component: DynamicStarMapComponent
        get() = daggerComponent

    /**
     * Passed to the renderer to get per-frame updates from the model.
     *
     * @author John Taylor
     */
    private class RendererModelUpdateClosure(
        private val model: AstronomerModel,
        private val rendererController: RendererController,
        sharedPreferences: SharedPreferences
    ) : Runnable {
        init {
            // TODO(jontayler): figure out why we need to do this here.
            updateViewDirectionMode(model, sharedPreferences)
        }

        override fun run() {
            val pointing = model.pointing
            val directionX = pointing.lineOfSightX
            val directionY = pointing.lineOfSightY
            val directionZ = pointing.lineOfSightZ
            val upX = pointing.perpendicularX
            val upY = pointing.perpendicularY
            val upZ = pointing.perpendicularZ
            rendererController.queueSetViewOrientation(
                directionX,
                directionY,
                directionZ,
                upX,
                upY,
                upZ
            )
            val up = model.phoneUpDirection
            rendererController.queueTextAngle(MathUtils.atan2(up.x, up.y))
            rendererController.queueViewerUpDirection(model.zenith.copyForJ())
            val fieldOfView = model.fieldOfView
            rendererController.queueFieldOfView(fieldOfView)
        }
    }

    private lateinit var cancelSearchButton: ImageButton

    @Inject
    lateinit var controller: ControllerGroup
    private lateinit var gestureDetector: GestureDetector

    @Inject
    lateinit var model: AstronomerModel
    private lateinit var rendererController: RendererController
    private var nightMode = false
    private var searchMode = false
    private var searchTarget: Vector3 = getGeocentricCoords(0f, 0f)

    @Inject
    lateinit var sharedPreferences: SharedPreferences
    private lateinit var skyView: GLSurfaceView
    private lateinit var viewFinder: PreviewView
    private var wakeLock: PowerManager.WakeLock? = null
    // ...
    private var touchFeedbackView: View? = null
    private lateinit var cameraExecutor: ExecutorService

    private var overlayView: View? = null
    // AR Overlay
    private var pointSourceOverlay: PointSourceOverlay? = null
    private var searchTargetName: String? = null

    @Inject
    lateinit var layerManager: LayerManager
    private lateinit var trajectoryLayer: TrajectoryLayer

    // TODO(widdows): Figure out if we should break out the
    // time dialog and time player into separate activities.
    private lateinit var timePlayerUI: View
    private lateinit var daggerComponent: DynamicStarMapComponent

    @Inject
    @Named("timetravel")
    lateinit var timeTravelNoiseProvider: Provider<MediaPlayer>

    @Inject
    @Named("timetravelback")
    lateinit var timeTravelBackNoiseProvider: Provider<MediaPlayer>
    private var timeTravelNoise: MediaPlayer? = null
    private var timeTravelBackNoise: MediaPlayer? = null

    @Inject
    lateinit var handler: Handler

    @Inject
    lateinit var analytics: AnalyticsInterface

    @Inject
    lateinit var playServicesChecker: GooglePlayServicesChecker

    // @Inject
    // lateinit var injectedFragmentManager: FragmentManager

    @Inject
    lateinit var eulaDialogFragmentNoButtons: EulaDialogFragment

    @Inject
    lateinit var timeTravelDialogFragment: TimeTravelDialogFragment

    @Inject
    lateinit var helpDialogFragment: HelpDialogFragment

    @Inject
    lateinit var noSearchResultsDialogFragment: NoSearchResultsDialogFragment

    @Inject
    lateinit var multipleSearchResultsDialogFragment: MultipleSearchResultsDialogFragment

    @Inject
    lateinit var noSensorsDialogFragment: NoSensorsDialogFragment

    @Inject
    lateinit var sensorAccuracyMonitor: SensorAccuracyMonitor

    // A list of runnables to post on the handler when we resume.
    private val onResumeRunnables: MutableList<Runnable> = ArrayList()

    // We need to maintain references to these objects to keep them from
    // getting gc'd.
    @Suppress("unused")
    @Inject
    lateinit var magneticSwitcher: MagneticDeclinationCalculatorSwitcher
    private lateinit var dragZoomRotateDetector: DragRotateZoomGestureDetector

    @Inject
    lateinit var flashAnimation: Animation

    @Inject
    lateinit var activityLightLevelManager: ActivityLightLevelManager
    private var sessionStartTime: Long = 0


    lateinit var fullscreenControlsManager: FullscreenControlsManager

    private fun setAutoMode(auto: Boolean) {
        val mode = if (auto) ViewMode.AUTO else ViewMode.MANUAL
        setViewMode(mode)
    }
    


    override fun onCreate(icicle: Bundle?) {
        Log.d(TAG, "onCreate at " + System.currentTimeMillis())
        super.onCreate(icicle)
        daggerComponent = DaggerDynamicStarMapComponent.builder()
            .applicationComponent(applicationComponent)
            .dynamicStarMapModule(DynamicStarMapModule(this)).build()
        daggerComponent.inject(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        // Set up full screen mode, hide the system UI etc.
        /*
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
         */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Modern Fullscreen / Immersive Mode implementation


        // Allow layout into the display cutout (Notch) area
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val component = window.attributes
            component.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = component
        }

        // Eventually we should check at the point of use, but this will do for now. If
        // the
        // user revokes the permission later then odd things may happen.
        playServicesChecker.maybeCheckForGooglePlayServices()
        initializeModelViewController()

        // Initialize Overlay for Night Mode transitions (Must be done AFTER setContentView)


        // checkForSensorsAndMaybeWarn(); // Moved to after permission check
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE
            )
        } else {
            checkForSensorsAndMaybeWarn()
        }

        // Search related
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL)
        val pm = ContextCompat.getSystemService(this, PowerManager::class.java)
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG)
        }

        // Were we started as the result of a search?
        val intent = intent
        Log.d(TAG, "Intent received: $intent")
        if (Intent.ACTION_SEARCH == intent.action) {
            Log.d(TAG, "Started as a result of a search")
            doSearchWithIntent(intent)
        }
        Log.d(TAG, "-onCreate at " + System.currentTimeMillis())
    }

    override fun setNightMode(mode: Boolean) {
        rendererController.queueNightVisionMode(mode)
    }

    private fun checkForSensorsAndMaybeWarn() {
        val sensorManager = ContextCompat.getSystemService(this, SensorManager::class.java)
        if (sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sensorManager.getDefaultSensor(
                Sensor.TYPE_MAGNETIC_FIELD
            ) != null
        ) {
            Log.i(TAG, "Minimum sensors present")
            // We want to reset to auto mode on every restart, as users seem to get
            // stuck in manual mode and can't find their way out.
            // TODO(johntaylor): this is a bit of an abuse of the prefs system, but
            // the button we use is wired into the preferences system. Should probably
            // change this to a use a different mechanism.
            sharedPreferences.edit().putBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, true)
                .apply()
            setAutoMode(true)
            return
        }
        // Missing at least one sensor. Warn the user.
        handler.post {
            if (!sharedPreferences
                    .getBoolean(ApplicationConstants.NO_WARN_ABOUT_MISSING_SENSORS, false)
            ) {
                Log.d(TAG, "showing no sensor dialog")
                noSensorsDialogFragment.show(supportFragmentManager, "No sensors dialog")
                // First time, force manual mode.
                sharedPreferences.edit()
                    .putBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, false)
                    .apply()
                setAutoMode(false)
            } else {
                Log.d(TAG, "showing no sensor toast")
                Toast.makeText(
                    this@DynamicStarMapActivity,
                    R.string.no_sensor_warning,
                    Toast.LENGTH_LONG
                ).show()
                // Don't force manual mode second time through - leave it up to the user.
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        // Created, to briefly hint to the user that UI controls
        // are available.
        fullscreenControlsManager.flashTheControls()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onDestroy() {
        Log.d(TAG, "DynamicStarMap onDestroy")
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                Log.d(TAG, "Key left")
                controller.rotate(-10.0f)
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                Log.d(TAG, "Key right")
                controller.rotate(10.0f)
            }

            KeyEvent.KEYCODE_BACK -> {
                // If we're in search mode when the user presses 'back' the natural
                // thing is to back out of search.
                Log.d(TAG, "In search mode $searchMode")
                if (searchMode) {
                    cancelSearch()
                    // break; // In Kotlin, break is for loops. Here we just return true.
                    return true
                }
                Log.d(TAG, "Key: $event")
                return super.onKeyDown(keyCode, event)
            }

            else -> {
                Log.d(TAG, "Key: $event")
                return super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        super.onOptionsItemSelected(item)
        super.onOptionsItemSelected(item)
        fullscreenControlsManager.delayHideTheControls()
        val menuEventBundle = Bundle()
        val itemId = item.itemId
        if (itemId == R.id.menu_item_search) {
            Log.d(TAG, "Search")
            menuEventBundle.putString(
                AnalyticsInterface.MENU_ITEM_EVENT_VALUE,
                AnalyticsInterface.SEARCH_REQUESTED_LABEL
            )
            onSearchRequested()
        } else if (itemId == R.id.menu_item_settings) {
            Log.d(TAG, "Settings")
            menuEventBundle.putString(
                AnalyticsInterface.MENU_ITEM_EVENT_VALUE,
                AnalyticsInterface.SETTINGS_OPENED_LABEL
            )
            startActivity(Intent(this, EditSettingsActivity::class.java))
        } else if (itemId == R.id.menu_item_help) {
            Log.d(TAG, "Help")
            menuEventBundle.putString(AnalyticsInterface.MENU_ITEM_EVENT_VALUE, AnalyticsInterface.HELP_OPENED_LABEL)
            helpDialogFragment.show(supportFragmentManager, "Help Dialog")
        } else if (itemId == R.id.menu_item_dim) {
            Log.d(TAG, "Toggling nightmode")
            nightMode = !nightMode
            sharedPreferences.edit().putString(
                ActivityLightLevelManager.LIGHT_MODE_KEY,
                if (nightMode) "NIGHT" else "DAY"
            ).commit()
            menuEventBundle.putString(
                AnalyticsInterface.MENU_ITEM_EVENT_VALUE,
                AnalyticsInterface.TOGGLED_NIGHT_MODE_LABEL
            )
        } else if (itemId == R.id.menu_item_time) {
            Log.d(TAG, "Starting Time Dialog from menu")
            menuEventBundle.putString(
                AnalyticsInterface.MENU_ITEM_EVENT_VALUE,
                AnalyticsInterface.TIME_TRAVEL_OPENED_LABEL
            )
            if (!timePlayerUI.isShown) {
                Log.d(TAG, "Resetting time in time travel dialog.")
                controller.goTimeTravel(Date())
            } else {
                Log.d(TAG, "Resuming current time travel dialog.")
            }
            timeTravelDialogFragment.show(supportFragmentManager, "Time Travel")


        } else if (itemId == R.id.menu_item_diagnostics) {
            Log.d(TAG, "Loading Diagnostics")
            menuEventBundle.putString(
                AnalyticsInterface.MENU_ITEM_EVENT_VALUE,
                AnalyticsInterface.DIAGNOSTICS_OPENED_LABEL
            )
            startActivity(Intent(this, DiagnosticActivity::class.java))
        } else {
            Log.e(TAG, "Unwired-up menu item")
            return false
        }
        analytics.trackEvent(AnalyticsInterface.MENU_ITEM_EVENT, menuEventBundle)
        return true
    }

    override fun onStart() {
        super.onStart()
        sessionStartTime = System.currentTimeMillis()
    }

    private enum class SessionBucketLength(val seconds: Int) {
        LESS_THAN_TEN_SECS(10), TEN_SECS_TO_THIRTY_SECS(30), THIRTY_SECS_TO_ONE_MIN(60), ONE_MIN_TO_FIVE_MINS(
            300
        ),
        MORE_THAN_FIVE_MINS(Int.MAX_VALUE);

    }

    private fun getSessionLengthBucket(sessionLengthSeconds: Int): SessionBucketLength {
        for (bucket in SessionBucketLength.values()) {
            if (sessionLengthSeconds < bucket.seconds) {
                return bucket
            }
        }
        Log.e(TAG, "Programming error - should not get here")
        return SessionBucketLength.MORE_THAN_FIVE_MINS
    }

    override fun onStop() {
        super.onStop()
        // Define a session as being the time between the main activity being in
        // the foreground and pushed back. Note that this will mean that sessions
        // do get interrupted by (e.g.) loading preference or help screens.
        val sessionLengthSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
        val bucket = getSessionLengthBucket(sessionLengthSeconds)
        val b = Bundle()
        // Let's see how well Analytics buckets things and log the raw number
        b.putInt(AnalyticsInterface.SESSION_LENGTH_TIME_VALUE, sessionLengthSeconds)
        analytics.trackEvent(AnalyticsInterface.SESSION_LENGTH_EVENT, b)
    }

    override fun onResume() {
        Log.d(TAG, "onResume at " + System.currentTimeMillis())
        super.onResume()
        Log.i(TAG, "Resuming")
        timeTravelNoise = timeTravelNoiseProvider.get()
        timeTravelBackNoise = timeTravelBackNoiseProvider.get()
        wakeLock?.acquire()
        Log.i(TAG, "Starting view")
        skyView.onResume()
        Log.i(TAG, "Starting controller")
        controller.start()
        activityLightLevelManager.onResume()

        // Sync nightMode state from preferences, as ActivityLightLevelManager might rely on a listener
        // that doesn't fire immediately or the local variable might be stale (false) on fresh start.
        // User request: "Let's forget about persistence... Let's start ALWAYS in day mode."
        // We forcibly disable Night Mode on launch and update the preference to match.
        nightMode = false
        sharedPreferences.edit()
            .putString(ActivityLightLevelManager.LIGHT_MODE_KEY, "DAY")
            .apply()
        setNightMode(false)

        if (controller.isAutoMode) {
             startSensorMonitoring()
        }
        for (runnable in onResumeRunnables) {
            handler.post(runnable)
        }
        Log.d(TAG, "-onResume at " + System.currentTimeMillis())
    }

    fun setTimeTravelMode(newTime: Date?) {
        val dateFormatter = SimpleDateFormat("yyyy.MM.dd G  HH:mm:ss z")
        Toast.makeText(
            this,
            String.format(
                getString(R.string.time_travel_start_message_alt),
                dateFormatter.format(newTime)
            ),
            Toast.LENGTH_LONG
        ).show()
        if (sharedPreferences.getBoolean(ApplicationConstants.SOUND_EFFECTS, true)) {
            try {
                timeTravelNoise?.start()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Exception trying to play time travel sound", e)
                // It's not the end of the world - carry on.
            } catch (e: NullPointerException) {
                Log.e(TAG, "Exception trying to play time travel sound", e)
                // It's not the end of the world - carry on.
            }
        }
        Log.d(TAG, "Showing TimePlayer UI.")
        timePlayerUI.visibility = View.VISIBLE
        timePlayerUI.requestFocus()
        flashTheScreen()
        if (newTime != null) {
            controller.goTimeTravel(newTime)
        }
    }

    fun setNormalTimeModel() {
        if (sharedPreferences.getBoolean(ApplicationConstants.SOUND_EFFECTS, true)) {
            try {
                timeTravelBackNoise?.start()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Exception trying to play return time travel sound", e)
                // It's not the end of the world - carry on.
            } catch (e: NullPointerException) {
                Log.e(TAG, "Exception trying to play return time travel sound", e)
                // It's not the end of the world - carry on.
            }
        }
        flashTheScreen()
        controller.useRealTime()
        Toast.makeText(
            this,
            R.string.time_travel_close_message,
            Toast.LENGTH_SHORT
        ).show()
        Log.d(TAG, "Leaving Time Travel mode.")
        timePlayerUI.visibility = View.GONE
    }

    private fun flashTheScreen() {
        val view = findViewById<View>(R.id.view_mask)
        // We don't need to set it invisible again - the end of the
        // animation will see to that.
        // TODO(johntaylor): check if setting it to GONE will bring
        // performance benefits.
        view.visibility = View.VISIBLE
        view.startAnimation(flashAnimation)
    }

    override fun onPause() {
        Log.d(TAG, "DynamicStarMap onPause")
        super.onPause()
        stopSensorMonitoring()
        if (timeTravelNoise != null) {
            timeTravelNoise?.release()
            timeTravelNoise = null
        }
        if (timeTravelBackNoise != null) {
            timeTravelBackNoise?.release()
            timeTravelBackNoise = null
        }
        for (runnable in onResumeRunnables) {
            handler.removeCallbacks(runnable)
        }
        activityLightLevelManager.onPause()
        controller.stop()
        skyView.onPause()
        wakeLock?.release()
        // Debug.stopMethodTracing();
        Log.d(TAG, "DynamicStarMap -onPause")
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(TAG, "Preferences changed: key=$key")
        if (key == null) {
            return
        }
        when (key) {
            ApplicationConstants.AUTO_MODE_PREF_KEY -> {
                val autoMode = sharedPreferences.getBoolean(key, true)
                Log.d(TAG, "Automode is set to $autoMode")
                if (!autoMode) {
                    Log.d(TAG, "Switching to manual control")
                    Toast.makeText(this@DynamicStarMapActivity, R.string.set_manual, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Log.d(TAG, "Switching to sensor control")
                    Toast.makeText(this@DynamicStarMapActivity, R.string.set_auto, Toast.LENGTH_SHORT)
                        .show()
                }
                setAutoMode(autoMode)
            }

            ApplicationConstants.VIEW_MODE_PREFKEY -> updateViewDirectionMode(
                model,
                sharedPreferences
            )
            "source_provider.milky_way", "source_provider.sky_gradient", "source_provider.landscape" -> {
                if (currentViewMode == ViewMode.AR) {
                    if (sharedPreferences.getBoolean(key, true)) {
                        Toast.makeText(this, "Layer disabled in AR mode", Toast.LENGTH_SHORT).show()
                        sharedPreferences.edit().putBoolean(key, false).apply()
                    }
                }
            }
            else -> return
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Log.d(TAG, "Touch event " + event);
        // Either of the following detectors can absorb the event, but one
        // must not hide it from the other
        var eventAbsorbed = false
        if (gestureDetector.onTouchEvent(event)) {
            eventAbsorbed = true
        }
        if (dragZoomRotateDetector.onTouchEvent(event)) {
            eventAbsorbed = true
        }
        return eventAbsorbed
    }

    override fun onTrackballEvent(event: MotionEvent): Boolean {
        // Log.d(TAG, "Trackball motion " + event);
        controller.rotate(event.x * ROTATION_SPEED)
        return true
    }

    private fun doSearchWithIntent(searchIntent: Intent) {
        val queryString = searchIntent.getStringExtra(SearchManager.QUERY)
        doSearchWithQuery(queryString ?: "")
    }

    private fun doSearchWithQuery(queryString: String): Int {
        // If we're already in search mode, cancel it.
        if (searchMode) {
            cancelSearch()
        }
        Log.d(TAG, "Performing Search: $queryString")
        searchMode = true
        val results = layerManager.searchByObjectName(queryString).filterNotNull()
        // Analytics code removed
        if (results.isEmpty()) {
            Log.d(TAG, "No results returned")
            noSearchResultsDialogFragment.show(supportFragmentManager, "No Search Results")
            searchMode = false
        } else if (results.size > 1) {
            Log.d(TAG, "Multiple results returned")
            showUserChooseResultDialog(results)
        } else {
            Log.d(TAG, "One result returned.")
            activateTarget(results[0])
        }
        return results.size
    }

    fun activateTarget(result: SearchResult) {
        activateSearchTarget(result.coords(), result.capitalizedName ?: "")
        val renderable = result.renderable
        Log.d(TAG, "activateTarget: ${result.capitalizedName}, renderable type: ${renderable?.javaClass?.simpleName}")
        if (renderable is SolarSystemRenderable) {
            Log.d(TAG, "activateTarget: Showing trajectory for ${renderable.solarSystemBody}")
            trajectoryLayer.showTrajectory(renderable.solarSystemBody)
        } else {
            Log.d(TAG, "activateTarget: Hiding trajectory")
            trajectoryLayer.hideTrajectory()
        }
    }

    private fun showUserChooseResultDialog(results: List<SearchResult>) {
        multipleSearchResultsDialogFragment.clearResults()
        for (result in results) {
            multipleSearchResultsDialogFragment.add(result)
        }
        multipleSearchResultsDialogFragment.show(supportFragmentManager, "Multiple Search Results")
    }

    private fun initializeModelViewController() {
        Log.i(TAG, "Initializing Model, View and Controller @ " + System.currentTimeMillis())
        setContentView(R.layout.skyrenderer)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.my_toolbar)
        setSupportActionBar(toolbar)
    // Custom styled title per user request: "Cosmos Mataró" (Bold) "Sky Map" (Normal)
    // Custom styled title per user request: "Cosmos Mataró" (Bold) "Sky Map" (Normal)
    supportActionBar?.title = "Cosmos Mataró Sky Map"
    supportActionBar?.setDisplayShowHomeEnabled(false) // Using navigationIcon instead
    supportActionBar?.setDisplayShowTitleEnabled(false) // Using custom layout for title
    
    // Wire up custom toolbar buttons
    // Wire up custom toolbar buttons
    val searchView = findViewById<SearchView>(R.id.search_view_inline)
    val titleContainer = findViewById<View>(R.id.title_container_custom)

    compassAccuracyView = findViewById(R.id.compass_accuracy_warning)

    // Enable Search Suggestions
    val searchManager = getSystemService(Context.SEARCH_SERVICE) as android.app.SearchManager
    searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
    
    findViewById<View>(R.id.toolbar_action_search).setOnClickListener {
        Log.d(TAG, "Search button clicked. Showing Inline Search.")
        titleContainer.visibility = View.GONE
        searchView.visibility = View.VISIBLE
        searchView.isIconified = false
    }

    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            val searchTerm = query ?: ""
            val resultCount = doSearchWithQuery(searchTerm)
            searchView.clearFocus()
            if (resultCount > 0) {
                searchView.visibility = View.GONE
                titleContainer.visibility = View.VISIBLE
            }
            return true
        }

        override fun onQueryTextChange(newText: String?): Boolean {
            return false
        }
    })

    searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
        override fun onSuggestionSelect(position: Int): Boolean {
            return false
        }

        override fun onSuggestionClick(position: Int): Boolean {
            val cursor = searchView.suggestionsAdapter.cursor
            if (cursor.moveToPosition(position)) {
                val suggestion = cursor.getString(cursor.getColumnIndexOrThrow(android.app.SearchManager.SUGGEST_COLUMN_TEXT_1))
                searchView.setQuery(suggestion, true)
            }
            return true
        }
    })

    searchView.setOnCloseListener {
        Log.d(TAG, "Search view closed.")
        searchView.visibility = View.GONE
        titleContainer.visibility = View.VISIBLE
        false
    }
    
    // Also handle "X" button click logic if OnCloseListener isn't enough (it often is for the X button when not iconified)
    // Actually, setOnCloseListener is for the "close" button (X).
    // If the user presses Back, we might need to handle it in onBackPressed, but let's stick to UI buttons first.

    findViewById<View>(R.id.toolbar_action_night_mode).setOnClickListener {
        nightMode = !nightMode
        sharedPreferences.edit().putString(
            ActivityLightLevelManager.LIGHT_MODE_KEY,
            if (nightMode) "NIGHT" else "DAY"
        ).commit()
        val b = Bundle()
        b.putString(
            AnalyticsInterface.MENU_ITEM_EVENT_VALUE,
            AnalyticsInterface.TOGGLED_NIGHT_MODE_LABEL
        )
        analytics.trackEvent(AnalyticsInterface.MENU_ITEM_EVENT, b)
    }

    val menuButton = findViewById<View>(R.id.menu_item_menu)
    menuButton.setOnClickListener {
        val popup = androidx.appcompat.widget.PopupMenu(this, menuButton)
        popup.menuInflater.inflate(R.menu.main, popup.menu)
        // Hide items already present in the toolbar
        popup.menu.findItem(R.id.menu_item_search)?.isVisible = false
        popup.menu.findItem(R.id.menu_item_dim)?.isVisible = false
            
        popup.setOnMenuItemClickListener { item ->
            onOptionsItemSelected(item)
        }
        popup.show()
    }

    // touchFeedbackView = findViewById<View>(R.id.touch_feedback_view)
        
    viewFinder = findViewById<PreviewView>(R.id.viewFinder)
    pointSourceOverlay = findViewById(R.id.point_source_overlay)
    cameraExecutor = Executors.newSingleThreadExecutor()

    skyView = findViewById<View>(R.id.skyrenderer_view) as GLSurfaceView
        // We need an alpha channel for AR mode transparency.
        // Requesting 8 bits for Red, Green, Blue, Alpha, 16 for Depth, 0 for Stencil.
        skyView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        skyView.setEGLContextClientVersion(2)
        skyView.setZOrderMediaOverlay(true) // Ensure it draws ON TOP of the Camera Preview
        val renderer = SkyRenderer(resources)
        skyView.setRenderer(renderer)
        rendererController = RendererController(renderer, skyView)
        // The renderer will now call back every frame to get model updates.
        rendererController.addUpdateClosure(
            RendererModelUpdateClosure(model, rendererController, sharedPreferences)
        )
        Log.i(TAG, "Setting layers @ " + System.currentTimeMillis())
        layerManager.registerWithRenderer(rendererController)
        trajectoryLayer = TrajectoryLayer(model, resources, sharedPreferences)
        trajectoryLayer.initialize()
        trajectoryLayer.registerWithRenderer(rendererController)
        trajectoryLayer.setVisible(true)

        Log.i(TAG, "Set up controllers @ " + System.currentTimeMillis())
        controller.setModel(model)
        wireUpScreenControls() // TODO(johntaylor) move these?
        wireUpTimePlayer() // TODO(widdows) move these?
    }

    private fun updateControlModeButton(mode: ViewMode) {
        val manualButton = findViewById<View>(R.id.btn_mode_manual) as ImageButton
        val autoButton = findViewById<View>(R.id.btn_mode_auto) as ImageButton
        val arButton = findViewById<View>(R.id.btn_mode_ar) as ImageButton

        val activeColor = ContextCompat.getColor(this, R.color.control_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.control_inactive)

        manualButton.setColorFilter(if (mode == ViewMode.MANUAL) activeColor else inactiveColor)
        autoButton.setColorFilter(if (mode == ViewMode.AUTO) activeColor else inactiveColor)
        arButton.setColorFilter(if (mode == ViewMode.AR) activeColor else inactiveColor)
    }

    private fun setViewMode(mode: ViewMode) {
        currentViewMode = mode
        val auto = mode != ViewMode.MANUAL
        controller.isAutoMode = auto
        
        if (auto) {
            startSensorMonitoring()
        } else {
            stopSensorMonitoring()
        }
        
        val label = when(mode) {
            ViewMode.MANUAL -> AnalyticsInterface.TOGGLED_MANUAL_MODE_LABEL
            ViewMode.AUTO -> "auto_mode"
            ViewMode.AR -> "ar_mode"
        }
        val b = Bundle()
        b.putString(AnalyticsInterface.MENU_ITEM_EVENT_VALUE, label)
        analytics.trackEvent(AnalyticsInterface.MENU_ITEM_EVENT, b)

        if (mode == ViewMode.AR) {
             if (allPermissionsGranted()) {
                 startCamera()
             } else {
                 ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
             }

             skyView.holder.setFormat(PixelFormat.TRANSLUCENT)
             viewFinder.visibility = View.VISIBLE
             pointSourceOverlay?.visibility = View.VISIBLE
             
             // Disable opaque layers for AR
             layerManager.setLayerVisibility("source_provider.sky_gradient", false)
             layerManager.setLayerVisibility("source_provider.milky_way", false)
             layerManager.setLayerVisibility("source_provider.landscape", false)

             // Persist AR State
             sharedPreferences.edit().putBoolean(ApplicationConstants.AR_MODE_ACTIVE_PREF_KEY, true).commit()

             // AR Polish: Block Zoom and Reset FOV
             controller.setZoomEnabled(false)
             // Load persisted FOV or default to 45f
             val savedFov = sharedPreferences.getFloat(ApplicationConstants.AR_CAMERA_FOV_PREF_KEY, 45f)
             model.fieldOfView = savedFov 
             Log.d("AR_FOV", "Restored Saved FOV: $savedFov")

             // AR Polish: Grey out Landscape Button
             val layerButtons = findViewById<com.google.android.stardroid.views.ButtonLayerView>(R.id.layer_buttons_control)
             if (layerButtons != null) {
                 for (i in 0 until layerButtons.childCount) {
                     val child = layerButtons.getChildAt(i)
                     if (child is com.google.android.stardroid.views.PreferencesButton) {
                         if (child.prefKey == "source_provider.landscape") {
                             child.isEnabled = false
                             child.alpha = 0.5f // Visual indication
                         }
                     }
                 }
             }

        } else {
             stopCamera()

             skyView.holder.setFormat(PixelFormat.RGB_565)
             viewFinder.visibility = View.GONE
             pointSourceOverlay?.visibility = View.GONE
             
             // Restore layers from preferences
             val skyGradientEnabled = sharedPreferences.getBoolean("source_provider.sky_gradient", true)
             layerManager.setLayerVisibility("source_provider.sky_gradient", skyGradientEnabled)
             
             val milkyWayEnabled = sharedPreferences.getBoolean("source_provider.milky_way", true)
             layerManager.setLayerVisibility("source_provider.milky_way", milkyWayEnabled)
             
             val groundEnabled = sharedPreferences.getBoolean("source_provider.landscape", true)
             layerManager.setLayerVisibility("source_provider.landscape", groundEnabled)

             // Persist AR State (Off)
             sharedPreferences.edit().putBoolean(ApplicationConstants.AR_MODE_ACTIVE_PREF_KEY, false).commit()

             // Restore Zoom and FOV control
             controller.setZoomEnabled(true)
             
             // Restore Landscape Button
             val layerButtons = findViewById<com.google.android.stardroid.views.ButtonLayerView>(R.id.layer_buttons_control)
             for (i in 0 until layerButtons.childCount) {
                 val child = layerButtons.getChildAt(i)
                 if (child is com.google.android.stardroid.views.PreferencesButton) {
                     if (child.prefKey == "source_provider.landscape") {
                         child.isEnabled = true
                         child.alpha = 1.0f
                     }
                 }
             }
        }
        updateControlModeButton(mode)
    }

    private fun wireUpScreenControls() {
        cancelSearchButton = findViewById<View>(R.id.cancel_search_button) as ImageButton
        // TODO(johntaylor): move to set this in the XML once we don't support 1.5
        cancelSearchButton.setOnClickListener { cancelSearch() }
        val providerButtons = findViewById<View>(R.id.layer_buttons_control) as ButtonLayerView
        val numChildren = providerButtons.childCount
        val buttonViews: MutableList<View> = ArrayList()
        for (i in 0 until numChildren) {
            val button = providerButtons.getChildAt(i) as ImageButton
            buttonViews.add(button)
        }

        
        // Unified Control Button Logic
        val manualBar = findViewById<View>(R.id.manual_bar)
        
        val btnManual = findViewById<View>(R.id.btn_mode_manual) as ImageButton
        val btnAuto = findViewById<View>(R.id.btn_mode_auto) as ImageButton
        val btnAr = findViewById<View>(R.id.btn_mode_ar) as ImageButton

        buttonViews.add(btnManual)
        buttonViews.add(btnAuto)
        buttonViews.add(btnAr)

        fullscreenControlsManager = FullscreenControlsManager(
            this,
            findViewById(R.id.main_sky_view),
            Lists.asList<View>(providerButtons, manualBar),
            buttonViews
        )

        btnManual.setOnClickListener {
            setViewMode(ViewMode.MANUAL)
            sharedPreferences.edit().putBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, false).apply()
        }

        btnAuto.setOnClickListener {
             setViewMode(ViewMode.AUTO)
             sharedPreferences.edit().putBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, true).apply()
        }
        
        btnAr.setOnClickListener {
             setViewMode(ViewMode.AR)
             sharedPreferences.edit().putBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, true).apply()
        }

        // Initial state update
        val startInAuto = sharedPreferences.getBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, true)
        val initialMode = if (startInAuto) ViewMode.AUTO else ViewMode.MANUAL
        // Force view mode update
        setViewMode(initialMode)
        val mapMover = MapMover(model, controller, this)
        gestureDetector = GestureDetector(
            this, GestureInterpreter(
                fullscreenControlsManager, mapMover, rendererController, layerManager, this
            )
        )
        dragZoomRotateDetector = DragRotateZoomGestureDetector(mapMover)
        
        /*
        // Programmatic UI removed for debugging
        */
    }

    private fun cancelSearch() {
        try {
            Log.d(TAG, "Debug: Cancel search clicked")
            val searchControlBar = findViewById<View>(R.id.search_control_bar)
            searchControlBar.visibility = View.INVISIBLE
            rendererController.queueDisableSearchOverlay()
            searchMode = false
            trajectoryLayer.hideTrajectory()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling search", e)
        }
    }

    fun isSearchMode(): Boolean {
        return searchMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "New Intent received $intent")
        if (Intent.ACTION_SEARCH == intent.action) {
            doSearchWithIntent(intent)
        }
    }

    override fun onRestoreInstanceState(icicle: Bundle) {
        Log.d(TAG, "DynamicStarMap onRestoreInstanceState")
        super.onRestoreInstanceState(icicle)
        if (icicle == null) return
        searchMode = icicle.getBoolean(ApplicationConstants.BUNDLE_SEARCH_MODE)
        val x = icicle.getFloat(ApplicationConstants.BUNDLE_X_TARGET)
        val y = icicle.getFloat(ApplicationConstants.BUNDLE_Y_TARGET)
        val z = icicle.getFloat(ApplicationConstants.BUNDLE_Z_TARGET)
        searchTarget = Vector3(x, y, z)
        searchTargetName = icicle.getString(ApplicationConstants.BUNDLE_TARGET_NAME)
        if (searchMode) {
            Log.d(TAG, "Searching for target " + searchTargetName + " at target=" + searchTarget)
            rendererController.queueEnableSearchOverlay(searchTarget, searchTargetName ?: "")
            cancelSearchButton.visibility = View.VISIBLE
        }
        nightMode = icicle.getBoolean(ApplicationConstants.BUNDLE_NIGHT_MODE, false)
    }

    override fun onSaveInstanceState(icicle: Bundle) {
        Log.d(TAG, "DynamicStarMap onSaveInstanceState")
        icicle.putBoolean(ApplicationConstants.BUNDLE_SEARCH_MODE, searchMode)
        icicle.putFloat(ApplicationConstants.BUNDLE_X_TARGET, searchTarget.x)
        icicle.putFloat(ApplicationConstants.BUNDLE_Y_TARGET, searchTarget.y)
        icicle.putFloat(ApplicationConstants.BUNDLE_Z_TARGET, searchTarget.z)
        icicle.putString(ApplicationConstants.BUNDLE_TARGET_NAME, searchTargetName)
        icicle.putBoolean(ApplicationConstants.BUNDLE_NIGHT_MODE, nightMode)
        super.onSaveInstanceState(icicle)
    }

    fun activateSearchTarget(target: Vector3, searchTerm: String?) {
        Log.d(TAG, "Item $searchTerm selected")
        // Store these for later.
        searchTarget = target
        searchTargetName = searchTerm
        Log.d(TAG, "Searching for target=$target")
        rendererController.queueViewerUpDirection(model.zenith.copyForJ())
        rendererController.queueEnableSearchOverlay(target.copyForJ(), searchTerm ?: "")
        searchMode = true // Fix: Ensure searchMode is set so UI controls don't toggle
        val searchPromptText = findViewById<View>(R.id.search_status_label) as TextView
        val label = "Target: $searchTerm"
        searchPromptText.text = label
        Log.d(TAG, "Setting search label to: $label")
        Log.d(TAG, "Setting search label to: $label")
        fullscreenControlsManager.hideControls()
        val searchControlBar = findViewById<View>(R.id.search_control_bar)
        searchControlBar.visibility = View.VISIBLE
        searchControlBar.bringToFront()
        searchControlBar.requestLayout()
        Log.d(TAG, "Search control bar visibility: " + searchControlBar.visibility)
    }

    fun showTouchFeedback(x: Float, y: Float) {
        val view = findViewById<View>(R.id.touch_feedback_view)
        if (view != null) {
            // Center the view on the touch point
            view.x = x - view.width / 2f
            view.y = y - view.height / 2f

            // Reset state
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f

            // Animate
            view.animate()
                .alpha(0f)
                .scaleX(2f)
                .scaleY(2f)
                .setDuration(500)
                .withEndAction { view.visibility = View.INVISIBLE }
                .start()
        }
    }

    fun showSelectionFeedback(x: Float, y: Float) {
        val view = findViewById<View>(R.id.touch_feedback_view)
        if (view != null) {
            // Center the view on the touch point
            view.x = x - view.width / 2f
            view.y = y - view.height / 2f

            // Reset state with GREEN color
            view.setBackgroundResource(R.drawable.touch_feedback_circle_green)
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f

            // Animate
            view.animate()
                .alpha(0f)
                .scaleX(2f)
                .scaleY(2f)
                .setDuration(800)
                .withEndAction {
                    view.visibility = View.INVISIBLE
                    view.setBackgroundResource(R.drawable.touch_feedback_circle) // Reset to red
                }
                .start()
        }
    }

    val skyViewHeight: Int
        get() = if (skyView != null) skyView.height else 0

    /**
     * Creates and wire up all time player controls.
     */
    private fun wireUpTimePlayer() {
        Log.d(TAG, "Initializing TimePlayer UI.")
        timePlayerUI = findViewById(R.id.time_player_view)
        val timePlayerCancelButton = findViewById<View>(R.id.time_player_close) as ImageButton
        val timePlayerBackwardsButton =
            findViewById<View>(R.id.time_player_play_backwards) as ImageButton
        val timePlayerStopButton = findViewById<View>(R.id.time_player_play_stop) as ImageButton
        val timePlayerForwardsButton =
            findViewById<View>(R.id.time_player_play_forwards) as ImageButton
        val timeTravelSpeedLabel = findViewById<View>(R.id.time_travel_speed_label) as TextView
        timePlayerCancelButton.setOnClickListener {
            Log.d(TAG, "Heard time player close click.")
            setNormalTimeModel()
        }
        timePlayerBackwardsButton.setOnClickListener {
            Log.d(TAG, "Heard time player play backwards click.")
            controller.decelerateTimeTravel()
            timeTravelSpeedLabel.setText(controller.currentSpeedTag)
        }
        timePlayerStopButton.setOnClickListener {
            Log.d(TAG, "Heard time player play stop click.")
            controller.pauseTime()
            timeTravelSpeedLabel.setText(controller.currentSpeedTag)
        }
        timePlayerForwardsButton.setOnClickListener {
            Log.d(TAG, "Heard time player play forwards click.")
            controller.accelerateTimeTravel()
            timeTravelSpeedLabel.setText(controller.currentSpeedTag)
        }
        val displayUpdater: Runnable = object : Runnable {
            private val timeTravelTimeReadout = findViewById<View>(
                R.id.time_travel_time_readout
            ) as TextView
            private val timeTravelStatusLabel = findViewById<View>(
                R.id.time_travel_status_label
            ) as TextView
            private val timeTravelSpeedLabel = findViewById<View>(
                R.id.time_travel_speed_label
            ) as TextView
            private val dateFormatter = SimpleDateFormat(
                "yyyy.MM.dd G  HH:mm:ss z"
            )
            private val date = Date()
            override fun run() {
                val time = model.timeMillis
                date.time = time
                timeTravelTimeReadout.text = dateFormatter.format(date)
                if (time > System.currentTimeMillis()) {
                    timeTravelStatusLabel.setText(R.string.time_travel_label_future)
                } else {
                    timeTravelStatusLabel.setText(R.string.time_travel_label_past)
                }
                timeTravelSpeedLabel.setText(controller.currentSpeedTag)
                handler.postDelayed(this, TIME_DISPLAY_DELAY_MILLIS.toLong())
            }
        }
        onResumeRunnables.add(displayUpdater)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == GOOGLE_PLAY_SERVICES_REQUEST_CODE) {
            playServicesChecker.runAfterDialog()
            return
        }
        Log.w(TAG, "Unhandled activity result")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE) {
            playServicesChecker.runAfterPermissionsCheck(requestCode, permissions, grantResults)
            checkForSensorsAndMaybeWarn()
            return
        }
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // Disable opaque layers for AR
                layerManager.setLayerVisibility("source_provider.sky_gradient", false)
                layerManager.setLayerVisibility("source_provider.milky_way", false)
                layerManager.setLayerVisibility("source_provider.landscape", false)
                
                skyView.holder.setFormat(PixelFormat.TRANSLUCENT)
                viewFinder.visibility = View.VISIBLE
                pointSourceOverlay?.visibility = View.VISIBLE
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission not granted. AR Mode disabled.", Toast.LENGTH_SHORT).show()
                setViewMode(ViewMode.AUTO) // Fallback
            }
            return
        }
        Log.w(TAG, "Unhandled request permissions result")
    }

    override fun onBackPressed() {
        val searchView = findViewById<SearchView>(R.id.search_view_inline)
        val titleContainer = findViewById<View>(R.id.title_container_custom)
        if (searchView.visibility == View.VISIBLE) {
            searchView.visibility = View.GONE
            titleContainer.visibility = View.VISIBLE
            // Also need to be sure we are not in search mode? 
            // If we hide, we should probably clear focus logic too.
            searchView.clearFocus()
            // And potentially cancel search mode flag?
            if (searchMode) {
                 cancelSearch()
            }
            return
        }
        super.onBackPressed()
    }

    // CameraX Logic
    private var cameraFovDegrees: Float = 45f // Default, will be updated from camera
    private val fovAlignmentCalculator = FovAlignmentCalculator()
    private var lastFovUpdateTime: Long = 0L
    private var lastMatchedStarNames: Set<String> = emptySet()
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, PointSourceDetector { points, srcW, srcH ->
                    Log.d("AR_DEBUG", "Analyzer run. Points: ${points.size}")
                    
                    // Data class to hold matched stars with screen positions
                    data class MatchedStar(
                        val point: Pair<Float, Float>,
                        val label: String,
                        val skyPosition: Vector3,
                        val distanceToCenter: Float
                    )
                    
                    val matchedStars = ArrayList<MatchedStar>()
                    
                    // Capture zenith for horizon filtering
                    val zenith = model.zenith
                    val horizonThreshold = -0.05f // ~3 degrees tolerance

                    points.forEach { (nx, ny) ->
                         try {
                            // Convert Normalized Buffer Coordinates -> Screen View Coordinates
                            pointSourceOverlay?.let { overlay ->
                                val viewW = overlay.width.toFloat()
                                val viewH = overlay.height.toFloat()
                                if (viewW > 0f && viewH > 0f) {
                                    val scaleX = viewW / srcW.toFloat()
                                    val scaleY = viewH / srcH.toFloat()
                                    val scale = kotlin.math.max(scaleX, scaleY)
                                    
                                    val scaledW = srcW * scale
                                    val scaledH = srcH * scale
                                    val offsetX = (viewW - scaledW) / 2f
                                    val offsetY = (viewH - scaledH) / 2f
                                    
                                    val screenX = nx * scaledW + offsetX
                                    val screenY = ny * scaledH + offsetY
                                    
                                    // Unproject Screen -> Sky
                                    val inverted = rendererController.invertedScreenTransformMatrix
                                    val skyPos = com.google.android.stardroid.math.convertScreenToSky(
                                        screenX, viewH - screenY, inverted
                                    )
                                    
                                    if (skyPos != null) {
                                        // Horizon Check
                                        if ((skyPos dot zenith) > horizonThreshold) {
                                            // Identify Object
                                            val name = layerManager.identifyObjectForDiagnostics(skyPos) ?: ""
                                            if (name.isNotEmpty()) {
                                                // Calculate distance to center (normalized 0..1, center is 0.5, 0.5)
                                                val dx = nx - 0.5f
                                                val dy = ny - 0.5f
                                                val dist = dx*dx + dy*dy // squared distance is fine for sorting
                                                
                                                matchedStars.add(MatchedStar(Pair(nx, ny), name, skyPos, dist))
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AR_DEBUG", "Error calculating sky pos", e)
                        }
                    }

                    // Sort by distance to center
                    matchedStars.sortBy { it.distanceToCenter }
                    
                    // Take up to 5 matches for FOV alignment, but only label the closest one
                    val topMatches = matchedStars.take(5)
                    
                    // Log matched stars for debugging
                    if (topMatches.isNotEmpty()) {
                        Log.d("AR_MATCHES", "Found ${topMatches.size} matched stars: ${topMatches.map { it.label }}")
                    }
                    
                    // For display: only show the closest match
                    val displayMatch = topMatches.take(1)
                    val filteredPoints = displayMatch.map { it.point }
                    val filteredLabels = displayMatch.map { it.label }

                    // Only update if overlay is visible
                    pointSourceOverlay?.let { overlay ->
                        if (overlay.visibility == View.VISIBLE) {
                            overlay.setPoints(filteredPoints, srcW, srcH, filteredLabels)
                            
                            // Send to Diagnostic Layer (send all top matches for visualization)
                            val viewW = overlay.width.toFloat()
                            val viewH = overlay.height.toFloat()
                            if (viewW > 0 && viewH > 0) {
                                // Send top matches to diagnostic layer
                                layerManager.setDetectedPointSources(topMatches.map { it.point }, viewW, viewH)
                            }
                        }
                    }
                    
                    // FOV Alignment: Use matched stars to calculate optimal FOV
                    if (topMatches.size >= 2) {
                        pointSourceOverlay?.let { overlay ->
                            val viewW = overlay.width.toFloat()
                            val viewH = overlay.height.toFloat()
                            if (viewW > 0 && viewH > 0) {
                                // Convert matched stars to FovAlignmentCalculator format
                                val alignmentMatches = topMatches.map {
                                    FovAlignmentCalculator.MatchedStar(
                                        screenX = it.point.first,
                                        screenY = it.point.second,
                                        skyPosition = it.skyPosition
                                    )
                                }
                                
                                // Calculate optimal FOV with stability score
                                val fovResult = fovAlignmentCalculator.calculateOptimalFovWithStability(
                                    alignmentMatches,
                                    model.fieldOfView,
                                    viewW,
                                    viewH
                                )
                                
                                if (fovResult != null) {
                                    val currentTime = System.currentTimeMillis()
                                    val currentMatchNames = topMatches.map { it.label }.toSet()
                                    val timeSinceLastUpdate = currentTime - lastFovUpdateTime
                                    
                                    // Check if matches are stable (similar stars detected)
                                    val matchStability = if (lastMatchedStarNames.isEmpty()) {
                                        1.0f // First update, assume stable
                                    } else {
                                        val intersection = currentMatchNames.intersect(lastMatchedStarNames).size
                                        val union = currentMatchNames.union(lastMatchedStarNames).size
                                        if (union > 0) intersection.toFloat() / union.toFloat() else 0f
                                    }
                                    
                                    // Combined stability: FOV calculation stability + match stability
                                    val combinedStability = (fovResult.stability + matchStability) / 2f
                                    
                                    // Apply adaptive smoothing based on stability
                                    val smoothedFov = fovAlignmentCalculator.smoothFov(
                                        cameraFovDegrees,
                                        fovResult.fov,
                                        stability = combinedStability,
                                        baseAlpha = 0.2f
                                    )
                                    
                                    val fovChange = kotlin.math.abs(smoothedFov - model.fieldOfView)
                                    
                                    // Only update if:
                                    // 1. Change exceeds minimum threshold (2 degrees)
                                    // 2. Sufficient time has elapsed (500ms) OR change is significant (5+ degrees)
                                    val shouldUpdate = fovChange >= FovAlignmentCalculator.MIN_FOV_CHANGE_THRESHOLD &&
                                                      (timeSinceLastUpdate >= 500 || fovChange >= 5.0f)
                                    
                                    if (shouldUpdate) {
                                        cameraFovDegrees = smoothedFov
                                        lastFovUpdateTime = currentTime
                                        lastMatchedStarNames = currentMatchNames
                                        
                                        // Update model FOV
                                        runOnUiThread {
                                            model.fieldOfView = smoothedFov
                                            // Persist the new FOV so next session starts closer to reality
                                            sharedPreferences.edit()
                                                .putFloat(ApplicationConstants.AR_CAMERA_FOV_PREF_KEY, smoothedFov)
                                                .apply()
                                            
                                            Log.d("AR_FOV", "Updated & Saved FOV: $smoothedFov° (calc: ${fovResult.fov}°, change: ${String.format("%.1f", fovChange)}°, stability: ${String.format("%.2f", combinedStability)}, matches: ${currentMatchNames.joinToString(", ")})")
                                        }
                                    } else {
                                        // Log why we skipped the update
                                        if (fovChange < FovAlignmentCalculator.MIN_FOV_CHANGE_THRESHOLD) {
                                            Log.d("AR_FOV", "Skipped FOV update: change too small (${String.format("%.1f", fovChange)}° < ${FovAlignmentCalculator.MIN_FOV_CHANGE_THRESHOLD}°)")
                                        } else if (timeSinceLastUpdate < 500) {
                                            Log.d("AR_FOV", "Skipped FOV update: too soon (${timeSinceLastUpdate}ms < 500ms)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                })

                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                
                // Extract camera FOV if available
                try {
                    val cameraInfo = camera.cameraInfo
                    // Note: CameraX doesn't directly expose FOV, but we can estimate from sensor size
                    // For now, we'll calculate it from matched stars later
                    Log.d("AR_FOV", "Camera bound. Will calculate FOV from matched stars.")
                } catch (e: Exception) {
                    Log.w("AR_FOV", "Could not extract camera FOV", e)
                }
                
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (exc: Exception) {
                Log.e(TAG, "Camera unbind failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
         ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TIME_DISPLAY_DELAY_MILLIS = 1000
        private fun updateViewDirectionMode(
            model: AstronomerModel,
            sharedPreferences: SharedPreferences
        ) {
            val viewDirectionMode = sharedPreferences.getString(
                ApplicationConstants.VIEW_MODE_PREFKEY,
                "STANDARD"
            )
            when (viewDirectionMode) {
                "ROTATE90" -> model.setViewDirectionMode(AstronomerModel.ViewDirectionMode.ROTATE90)
                "TELESCOPE" -> model.setViewDirectionMode(AstronomerModel.ViewDirectionMode.TELESCOPE)
                else -> model.setViewDirectionMode(AstronomerModel.ViewDirectionMode.STANDARD)
            }
        }

        // Activity for result Ids
        const val GOOGLE_PLAY_SERVICES_REQUEST_CODE = 1
        const val GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE = 2

        // End Activity for result Ids
        private const val ROTATION_SPEED = 10f
        private val TAG = MiscUtil.getTag(DynamicStarMapActivity::class.java)
    }
    // Compass Accuracy Logic
    private var isCompassStartup = false
    private var lastSensorReliability = true
    private val startupResetRunnable = Runnable {
        isCompassStartup = false
        // Re-evaluate reliability after startup period
        runOnUiThread {
            updateCompassAccuracyView(lastSensorReliability)
        }
    }

    private fun startSensorMonitoring() {
        sensorAccuracyMonitor.start()
        sensorAccuracyMonitor.register(this)

        // Green Flash Startup Logic
        isCompassStartup = true
        compassAccuracyView.visibility = View.VISIBLE
        compassAccuracyView.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
        compassAccuracyView.clearAnimation()

        handler.removeCallbacks(startupResetRunnable)
        handler.postDelayed(startupResetRunnable, 3000)
    }

    private fun stopSensorMonitoring() {
        handler.removeCallbacks(startupResetRunnable)
        isCompassStartup = false
        
        sensorAccuracyMonitor.stop()
        sensorAccuracyMonitor.unregister()
        // Ensure visual state is cleared
        updateCompassAccuracyView(true) // Treat as reliable (hidden) when off
    }

    private lateinit var compassAccuracyView: View

    private fun updateCompassAccuracyView(isReliable: Boolean) {
        val view = compassAccuracyView
        if (isReliable) {
            view.clearAnimation()
            view.visibility = View.GONE
        } else {
            // Blink if not reliable
            // We force visibility and Red color
            view.visibility = View.VISIBLE
            view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            
            // Only start animation if not already animating or if coming from a different state
            if (view.animation == null || view.animation.hasEnded()) {
                val blink = android.view.animation.AlphaAnimation(0.0f, 1.0f)
                blink.duration = 500
                blink.startOffset = 20
                blink.repeatMode = android.view.animation.Animation.REVERSE
                blink.repeatCount = android.view.animation.Animation.INFINITE
                view.startAnimation(blink)
            }
        }
    }

    override fun onAccuracyChanged(isReliable: Boolean) {
        lastSensorReliability = isReliable
        if (!isCompassStartup) {
            runOnUiThread {
                updateCompassAccuracyView(isReliable)
            }
        }
    }
}

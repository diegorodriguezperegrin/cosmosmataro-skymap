package com.google.android.stardroid.activities

import android.app.AlertDialog
import android.content.SharedPreferences
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.preference.PreferenceScreen
import android.util.Log
import android.widget.Toast
import com.google.android.stardroid.ApplicationComponent
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.StardroidApplication
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import java.io.IOException
import javax.inject.Inject

/**
 * Edit the user's preferences.
 */
class EditSettingsActivity : PreferenceActivity() {
    private lateinit var preferenceFragment: MyPreferenceFragment
        class MyPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preference_screen)
        }
        
        override fun onPreferenceTreeClick(preferenceScreen: PreferenceScreen, preference: Preference): Boolean {
            if (preference is PreferenceScreen && preference.key == "source_provider_prefs") {
                // When navigating to Layers, try to hide dividers in the new list (if accessible)
                // This is tricky with standard PreferenceFragment.
                // We rely on the structure change (Sub-screen) to satisfy the "Shorten List" requirement.
                // The styling "No Divider" might be applied if we can get the dialog/screen.
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference)
        }
    }

    private lateinit var geocoder: Geocoder

    @Inject
    lateinit var activityLightLevelManager: ActivityLightLevelManager

    @Inject
    lateinit var analytics: Analytics

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var layerManager: com.google.android.stardroid.layers.LayerManager

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerEditSettingsActivityComponent.builder().applicationComponent(
            applicationComponent
        ).editSettingsActivityModule(EditSettingsActivityModule(this))
            .build().inject(this)
        geocoder = Geocoder(this)
        preferenceFragment = MyPreferenceFragment()
        fragmentManager.beginTransaction().replace(
            android.R.id.content,
            preferenceFragment
        ).commit()
    }

    private val applicationComponent: ApplicationComponent
        get() = (application as StardroidApplication).applicationComponent

    public override fun onStart() {
        super.onStart()
        val locationPreference = preferenceFragment.findPreference(LOCATION)
        val latitudePreference = preferenceFragment.findPreference(LATITUDE)
        val longitudePreference = preferenceFragment.findPreference(LONGITUDE)
        locationPreference.onPreferenceChangeListener =
            OnPreferenceChangeListener { preference, newValue ->
                Log.d(TAG, "Place to be updated to $newValue")
                setLatLongFromPlace(newValue.toString())
            }
        latitudePreference.onPreferenceChangeListener =
            OnPreferenceChangeListener { preference, newValue ->
                (locationPreference as EditTextPreference).text = ""
                true
            }
        longitudePreference.onPreferenceChangeListener =
            OnPreferenceChangeListener { preference, newValue ->
                (locationPreference as EditTextPreference).text = ""
                true
            }

        // Logic for "All Layers" toggle
        val allLayersPreference = preferenceFragment.findPreference("source_provider.all")
        allLayersPreference?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                val isChecked = newValue as Boolean
                val layerKeys = listOf(
                    "source_provider.0",
                    "source_provider.1",
                    "source_provider.2",
                    "source_provider.3",
                    "source_provider.4",
                    "source_provider.landscape",
                    "source_provider.sky_gradient",
                    "source_provider.milky_way",
                    "source_provider.6",
                    "source_provider.galactic_plane",
                    "source_provider.ecliptic",
                    "source_provider.7",
                    "point_source_comparison"
                )
                for (key in layerKeys) {
                    val pref = preferenceFragment.findPreference(key) as? CheckBoxPreference
                    pref?.isChecked = isChecked
                }
                true
            }

        val gyroPreference = preferenceFragment.findPreference(
            ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO
        )
        gyroPreference.onPreferenceChangeListener =
            OnPreferenceChangeListener { preference, newValue ->
                Log.d(TAG, "Toggling gyro preference $newValue")
                enableNonGyroSensorPrefs(newValue as Boolean)
                true
            }
        enableNonGyroSensorPrefs(
            sharedPreferences.getBoolean(
                ApplicationConstants.SHARED_PREFERENCE_DISABLE_GYRO,
                false
            )
        )

        // AR Polish: Check if AR Mode is active and disable opaque layers
        val arModeActive = sharedPreferences.getBoolean(ApplicationConstants.AR_MODE_ACTIVE_PREF_KEY, false)
        if (arModeActive) {
            val arDisabledKeys = listOf(
                "source_provider.sky_gradient",
                "source_provider.milky_way",
                "source_provider.landscape"
            )
            for (key in arDisabledKeys) {
                val pref = preferenceFragment.findPreference(key)
                if (pref != null) {
                    pref.isEnabled = false
                }
            }
        } else {
            // Not in AR Mode: Disable AR-only layers
            val arRequiredKeys = listOf("point_source_comparison")
            for (key in arRequiredKeys) {
                val pref = preferenceFragment.findPreference(key)
                if (pref != null) {
                    pref.isEnabled = false
                    pref.summary = "Only available in AR Mode"
                }
            }
        }

        // Import Comets Logic
        val importCometsPref = preferenceFragment.findPreference("import_comets")
        importCometsPref?.setOnPreferenceClickListener {
            val progress = android.app.ProgressDialog(this)
            progress.setTitle("Importing Comets")
            progress.setMessage("Downloading data from MPC...")
            progress.setCancelable(false)
            progress.show()
            
            Thread {
                try {
                    val url = "https://www.minorplanetcenter.net/iau/Ephemerides/Comets/Soft00Cmt.txt"
                    android.util.Log.d(TAG, "Downloading comets from $url")
                    val lines = com.google.android.stardroid.ephemeris.CometImporter.download(url)
                    android.util.Log.d(TAG, "Downloaded ${lines.size} lines")
                    
                    val file = java.io.File(filesDir, "comets.dat")
                    val results = com.google.android.stardroid.ephemeris.CometImporter.parse(lines)
                    
                    if (results.isNotEmpty()) {
                        // Filter by brightness: Visible with larger telescope (Mag < 14) to give more results
                        val now = java.util.Date()
                        // Also remove any empty names or names that still look like references
                        val visibleComets = results.filter { 
                            val mag = it.comet.getApparentMagnitude(now)
                            mag < 14.0 && it.comet.name.isNotEmpty()
                        }
                        
                        // Deduplicate: Keep only the brightest entry for each comet name
                        // (MPC file can have multiple orbital solutions for the same comet)
                        val deduplicated = visibleComets
                            .groupBy { it.comet.name }
                            .map { (_, entries) ->
                                // Keep the brightest one (lowest magnitude)
                                entries.minByOrNull { it.comet.getApparentMagnitude(now) }!!
                            }
                        
                        // Sort by brightness
                        val sorted = deduplicated.sortedBy { it.comet.getApparentMagnitude(now) }
                        
                        val fileContent = sorted.joinToString("\n") { it.originalLine }
                        file.writeText(fileContent)
                        
                        runOnUiThread {
                            layerManager.refreshComets()
                            updateCometDataSummary()
                            progress.dismiss()
                            Toast.makeText(this, "Success! Imported ${sorted.size} comets (Mag < 14). Please restart.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        runOnUiThread {
                            progress.dismiss()
                            Toast.makeText(this, "No valid comets found in download.", Toast.LENGTH_LONG).show()
                        }
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Import failed", e)
                    runOnUiThread {
                        progress.dismiss()
                        Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            true
        }
        
        // Import Earth Orientation Parameters Logic
        val importEOPPref = preferenceFragment.findPreference("import_eop")
        importEOPPref?.setOnPreferenceClickListener {
            val progress = android.app.ProgressDialog(this)
            progress.setTitle("Importing Earth Orientation")
            progress.setMessage("Downloading data from IERS...")
            progress.setCancelable(false)
            progress.show()
            
            Thread {
                try {
                    val url = "https://datacenter.iers.org/data/9/finals2000A.all"
                    android.util.Log.d(TAG, "Downloading EOP from $url")
                    val lines = com.google.android.stardroid.ephemeris.EOPImporter.download(url)
                    android.util.Log.d(TAG, "Downloaded ${lines.size} lines")
                    
                    val file = java.io.File(filesDir, "eop.dat")
                    val results = com.google.android.stardroid.ephemeris.EOPImporter.parse(lines)
                    
                    if (results.isNotEmpty()) {
                        // Filter to last 30 days (observations) + 30 days (predictions)
                        val nowMJD = System.currentTimeMillis() / 86400000.0 + 40587.0
                        val recent = results.filter { 
                            it.eop.mjd >= nowMJD - 30 && it.eop.mjd <= nowMJD + 30
                        }
                        
                        // Save as CSV
                        val fileContent = recent.joinToString("\n") { 
                            com.google.android.stardroid.ephemeris.EarthOrientationParameters.toCSV(it.eop)
                        }
                        file.writeText(fileContent)
                        
                        // Load into global cache
                        com.google.android.stardroid.ephemeris.EOPData.load(recent.map { it.eop })
                        
                        runOnUiThread {
                            updateEOPDataSummary()
                            progress.dismiss()
                            Toast.makeText(this, "Success! Imported ${recent.size} days of EOP data.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        runOnUiThread {
                            progress.dismiss()
                            Toast.makeText(this, "No valid EOP data found in download.", Toast.LENGTH_LONG).show()
                        }
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "EOP import failed", e)
                    runOnUiThread {
                        progress.dismiss()
                        Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            true
        }
        
        // Update comet data summary on load
        updateCometDataSummary()
        updateEOPDataSummary()
    }

    private fun updateCometDataSummary() {
        try {
            val importCometsPref = preferenceFragment.findPreference("import_comets")
            val cometListPref = preferenceFragment.findPreference("comet_list")
            val file = java.io.File(filesDir, "comets.dat")
            
            if (importCometsPref != null) {
                if (file.exists()) {
                    val lastMod = file.lastModified()
                    val currentTime = System.currentTimeMillis()
                    
                    // Sanity check: if file is from the future or more than 10 years old, something is wrong
                    if (lastMod > currentTime || (currentTime - lastMod) > (10L * 365 * 24 * 60 * 60 * 1000)) {
                        importCometsPref.summary = "Invalid file timestamp - please re-import"
                    } else {
                        val diffMillis = currentTime - lastMod
                        val diffDays = diffMillis / (1000L * 60 * 60 * 24)
                        
                        when {
                            diffDays == 0L -> importCometsPref.summary = "Data imported today"
                            diffDays == 1L -> importCometsPref.summary = "Data imported yesterday"
                            diffDays < 7L -> importCometsPref.summary = "Data imported $diffDays days ago"
                            diffDays < 30L -> {
                                val weeks = diffDays / 7
                                importCometsPref.summary = "Data imported $weeks week${if (weeks > 1) "s" else ""} ago"
                            }
                            diffDays < 365L -> {
                                val months = diffDays / 30
                                importCometsPref.summary = "Data imported $months month${if (months > 1) "s" else ""} ago"
                            }
                            else -> {
                                val years = diffDays / 365
                                importCometsPref.summary = "Data imported $years year${if (years > 1) "s" else ""} ago"
                            }
                        }
                    }
                } else {
                    importCometsPref.summary = "No downloaded data (using built-in comets)"
                }
            }
            
            // Update comet list
            if (cometListPref != null) {
                if (file.exists()) {
                    try {
                        val lines = file.readLines()
                        val results = com.google.android.stardroid.ephemeris.CometImporter.parse(lines)
                        
                        if (results.isNotEmpty()) {
                            // Create a formatted list of comet names (deduplicated)
                            val allCometNames = results.map { it.comet.name }
                            val uniqueCometNames = allCometNames.distinct()
                            val cometNames = uniqueCometNames.take(50) // Limit to first 50
                            
                            val displayText = if (uniqueCometNames.size > 50) {
                                cometNames.joinToString("\n    ", prefix = "    ") + "\n    ... and ${uniqueCometNames.size - 50} more"
                            } else {
                                cometNames.joinToString("\n    ", prefix = "    ")
                            }
                            
                            cometListPref.title = "Downloaded Comets (${uniqueCometNames.size})"
                            cometListPref.summary = displayText
                        } else {
                            cometListPref.title = "Downloaded Comets"
                            cometListPref.summary = "No valid comets in file"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading comet list", e)
                        cometListPref.title = "Downloaded Comets"
                        cometListPref.summary = "Error reading comet data"
                    }
                } else {
                    cometListPref.title = "Downloaded Comets"
                    cometListPref.summary = "No comets downloaded (using 4 built-in comets:\nHalley, Hale-Bopp, Borisov, NEOWISE)"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating summary", e)
        }
    }
    
    private fun updateEOPDataSummary() {
        try {
            val eopDataPref = preferenceFragment.findPreference("eop_data")
            val file = java.io.File(filesDir, "eop.dat")
            
            if (eopDataPref != null) {
                if (file.exists()) {
                    try {
                        val lines = file.readLines()
                        val eops = lines.mapNotNull { 
                            com.google.android.stardroid.ephemeris.EarthOrientationParameters.fromCSV(it)
                        }
                        
                        if (eops.isNotEmpty()) {
                            // Get current EOP (closest to today)
                            val nowMJD = System.currentTimeMillis() / 86400000.0 + 40587.0
                            val current = eops.minByOrNull { kotlin.math.abs(it.mjd - nowMJD) }
                            
                            if (current != null) {
                                // Convert MJD to calendar date
                                val jd = current.mjd + 2400000.5
                                val dateMillis = ((jd - 2440587.5) * 86400000.0).toLong()
                                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
                                val dateStr = dateFormat.format(java.util.Date(dateMillis))
                                
                                val summary = "    PM: X=${String.format("%.6f", current.polarMotionX)}\", Y=${String.format("%.6f", current.polarMotionY)}\" | UT1-UTC: ${String.format("%.7f", current.ut1MinusUtc)}s ${if (current.isPrediction) "(P)" else "(O)"} | ${eops.size} days | IERS finals2000A.all"
                                
                                eopDataPref.title = "Earth Orientation ($dateStr)"
                                eopDataPref.summary = summary
                            }
                        } else {
                            eopDataPref.title = "Earth Orientation Data"
                            eopDataPref.summary = "No valid data in file"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading EOP data", e)
                        eopDataPref.title = "Earth Orientation Data"
                        eopDataPref.summary = "Error reading data"
                    }
                } else {
                    eopDataPref.title = "Earth Orientation Data"
                    eopDataPref.summary = "No data downloaded"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating EOP summary", e)
        }
    }



    public override fun onPause() {
        super.onPause()
        updatePreferences()
        activityLightLevelManager.onPause()
    }

    private fun enableNonGyroSensorPrefs(enabled: Boolean) {
        // These settings aren't compatible with the gyro.
        preferenceFragment.findPreference(
            ApplicationConstants.SENSOR_SPEED_PREF_KEY
        ).isEnabled = enabled
        preferenceFragment.findPreference(
            ApplicationConstants.SENSOR_DAMPING_PREF_KEY
        ).isEnabled = enabled
        preferenceFragment.findPreference(
            ApplicationConstants.REVERSE_MAGNETIC_Z_PREFKEY
        ).isEnabled = enabled
    }

    /**
     * Updates preferences on singletons, so we don't have to register
     * preference change listeners for them.
     */
    private fun updatePreferences() {
        Log.d(TAG, "Updating preferences")
        // Analytics preference removed per user request
    }

    protected fun setLatLongFromPlace(place: String?): Boolean {
        val addresses = try {
            geocoder.getFromLocationName(place!!, 1)
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.location_unable_to_geocode), Toast.LENGTH_SHORT)
                .show()
            return false
        }
        if (addresses.isNullOrEmpty()) {
            showNotFoundDialog(place)
            return false
        }
        // TODO(johntaylor) let the user choose, but for now just pick the first.
        val first = addresses[0]
        setLatLong(first.latitude, first.longitude)
        return true
    }

    private fun setLatLong(latitude: Double, longitude: Double) {
        val latPreference = preferenceFragment.findPreference(LATITUDE) as EditTextPreference
        val longPreference = preferenceFragment.findPreference(LONGITUDE) as EditTextPreference
        latPreference.text = java.lang.Double.toString(latitude)
        longPreference.text = java.lang.Double.toString(longitude)
        val message = String.format(getString(R.string.location_place_found), latitude, longitude)
        Log.d(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showNotFoundDialog(place: String?) {
        val message = String.format(getString(R.string.location_not_found), place)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.location_not_found_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
        dialog.show()
    }

    companion object {
        /**
         * These must match the keys in the preference_screen.xml file.
         */
        private const val LONGITUDE = "longitude"
        private const val LATITUDE = "latitude"
        private const val LOCATION = "location"
        private val TAG = MiscUtil.getTag(EditSettingsActivity::class.java)
    }
}

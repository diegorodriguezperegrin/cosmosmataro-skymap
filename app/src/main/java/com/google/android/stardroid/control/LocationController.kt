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

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Criteria
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import java.io.IOException
import java.util.ArrayList
import javax.inject.Inject

/**
 * Sets the AstronomerModel's (and thus the user's) position using one of the
 * network, GPS or user-set preferences.
 *
 * @author John Taylor
 */
class LocationController @Inject constructor(
    private val activity: Activity,
    private val locationManager: LocationManager?
) : AbstractController(), LocationListener {

    init {
        if (locationManager != null) {
            Log.d(TAG, "Got location Manager")
        } else {
            Log.d(TAG, "Didn't get location manager")
        }
    }

    override fun start() {
        Log.d(TAG, "LocationController start")
        val noAutoLocate = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
            NO_AUTO_LOCATE, false
        )

        val forceGps =
            PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(FORCE_GPS, false)

        if (noAutoLocate) {
            Log.d(TAG, "User has elected to set location manually.")
            setLocationFromPrefs()
            Log.d(TAG, "LocationController -start")
            return
        }

        try {
            if (locationManager == null) {
                // TODO(johntaylor): find out under what circumstances this can happen.
                Log.e(TAG, "Location manager was null - using preferences")
                setLocationFromPrefs()
                return
            }

            // TODO(jontayler): Another Android Tax - Criteria based APIs are now deprecated
            // as a bad design decision and this probably needs to be updated.
            val locationCriteria = Criteria()
            locationCriteria.accuracy =
                if (forceGps) Criteria.ACCURACY_FINE else Criteria.ACCURACY_COARSE
            locationCriteria.isAltitudeRequired = false
            locationCriteria.isBearingRequired = false
            locationCriteria.isCostAllowed = true
            locationCriteria.isSpeedRequired = false
            locationCriteria.powerRequirement = Criteria.POWER_LOW

            val locationProvider = locationManager.getBestProvider(locationCriteria, true)
            if (locationProvider == null) {
                Log.w(TAG, "No location provider is enabled")
                val possibleLocationProvider =
                    locationManager.getBestProvider(locationCriteria, false)
                if (possibleLocationProvider == null) {
                    Log.i(TAG, "No location provider is even available")
                    // TODO(johntaylor): should we make this a dialog?
                    Toast.makeText(activity, R.string.location_no_auto, Toast.LENGTH_LONG).show()
                    setLocationFromPrefs()
                    return
                }

                val alertDialog = switchOnGPSDialog
                alertDialog.show()
                return
            } else {
                Log.d(TAG, "Got location provider $locationProvider")
            }

            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager?.requestLocationUpdates(
                    locationProvider, LOCATION_UPDATE_TIME_MILLISECONDS.toLong(),
                    MINIMUM_DISTANCE_BEFORE_UPDATE_METRES.toFloat(),
                    this
                )

                val location = locationManager?.getLastKnownLocation(locationProvider)
                if (location != null) {
                    val myLocation = LatLong(location.latitude, location.longitude)
                    setLocationInModel(myLocation, location.provider)
                }
            }


        } catch (securityException: SecurityException) {
            Log.d(TAG, "Caught $securityException")
            Log.d(TAG, "Most likely user has not enabled this permission")
        }

        Log.d(TAG, "LocationController -start")
    }

    private fun setLocationInModel(location: LatLong, provider: String?) {
        val oldLocation = astronomerModel.location
        if (location.distanceFrom(oldLocation) > MIN_DIST_TO_SHOW_TOAST_DEGS) {
            Log.d(TAG, "Informing user of change of location")
            showLocationToUser(location, provider)
        } else {
            Log.d(TAG, "Location not changed sufficiently to tell the user")
        }
        currentProvider = provider ?: "unknown"
        astronomerModel.location = location
    }

    /**
     * Last known provider;
     */
    private var currentProvider = "unknown"

    val currentLocation: LatLong
        get() = astronomerModel.location

    private val switchOnGPSDialog: AlertDialog.Builder
        get() {
            val dialog = AlertDialog.Builder(activity)
            dialog.setTitle(R.string.location_offer_to_enable_gps_title)
            dialog.setMessage(R.string.location_offer_to_enable)
            dialog.setPositiveButton(android.R.string.ok) { _, _ ->
                Log.d(TAG, "Sending to editor location prefs page")
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivity(intent)
            }
            dialog.setNegativeButton(android.R.string.cancel) { _, _ ->
                Log.d(TAG, "User doesn't want to enable location.")
                val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                val editor = prefs.edit()
                editor.putBoolean(NO_AUTO_LOCATE, true)
                editor.apply()
                setLocationFromPrefs()
            }
            return dialog
        }

    private fun setLocationFromPrefs() {
        Log.d(TAG, "Setting location from preferences")
        val longitudeS = PreferenceManager.getDefaultSharedPreferences(activity)
            .getString("longitude", "0")
        val latitudeS = PreferenceManager.getDefaultSharedPreferences(activity)
            .getString("latitude", "0")

        var longitude = 0f
        var latitude = 0f
        try {
            longitude = longitudeS!!.toFloat()
            latitude = latitudeS!!.toFloat()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Error parsing latitude or longitude preference")
            Toast.makeText(activity, R.string.malformed_loc_error, Toast.LENGTH_SHORT).show()
        }

        val location = Location(activity.getString(R.string.preferences))
        location.latitude = latitude.toDouble()
        location.longitude = longitude.toDouble()

        Log.d(TAG, "Latitude $longitude")
        Log.d(TAG, "Longitude $latitude")
        val myPosition = LatLong(latitude.toDouble(), longitude.toDouble())
        setLocationInModel(myPosition, activity.getString(R.string.preferences))
    }

    override fun stop() {
        Log.d(TAG, "LocationController stop")

        if (locationManager == null) {
            return
        }
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
              locationManager.removeUpdates(this)
        }

        Log.d(TAG, "LocationController -stop")
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "LocationController onLocationChanged")

        val newLocation = LatLong(location.latitude, location.longitude)

        Log.d(TAG, "Latitude " + newLocation.latitude)
        Log.d(TAG, "Longitude " + newLocation.longitude)
        setLocationInModel(newLocation, location.provider)

        // Only need get the location once.
        if (ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        ) {
                locationManager?.removeUpdates(this)
        }

        Log.d(TAG, "LocationController -onLocationChanged")
    }

    private fun showLocationToUser(location: LatLong, provider: String?) {
        // TODO(johntaylor): move this notification to a separate thread)
        Log.d(TAG, "Reverse geocoding location")

        Thread {
            val place = getPlaceFromLocation(location)

            Log.d(TAG, "Location set to $place")

            activity.runOnUiThread {
                val messageTemplate = activity.getString(R.string.location_set_auto)
                val message = String.format(messageTemplate, provider, place)
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /**
     * Gets a strinc representation of a location to show to the user.
     * Might be long running so don't run on the UI thread.
     * @param location
     * @return
     */
    private fun getPlaceFromLocation(location: LatLong): String {
        val geoCoder = Geocoder(activity)
        var addresses: List<Address> = ArrayList()
        var place = "Unknown"
        try {
            // Geocoder can return null list or throw exception
            val result = geoCoder.getFromLocation(location.latitude.toDouble(), location.longitude.toDouble(), 1)
            if (result != null) {
                addresses = result
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to reverse geocode location $location")
        }

        if (addresses.isEmpty()) {
            Log.d(TAG, "No addresses returned")
            place = String.format(
                activity.getString(R.string.location_long_lat),
                location.longitude, location.latitude
            )
        } else {
            place = getSummaryOfPlace(location, addresses[0])
        }
        return place
    }

    private fun getSummaryOfPlace(location: LatLong, address: Address?): String {
        val template = activity.getString(R.string.location_long_lat)
        val longLat = String.format(template, location.longitude, location.latitude)
        if (address == null) {
            return longLat
        }
        var place = address.locality
        if (place == null) {
            place = address.subAdminArea
        }
        if (place == null) {
            place = address.adminArea
        }
        if (place == null) {
            place = longLat
        }
        return place
    }

    override fun onProviderDisabled(provider: String) {
        // No action.
    }

    override fun onProviderEnabled(provider: String) {
        // No action.
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        // No action.
    }

    companion object {
        // Must match the key in the preferences file.
        const val NO_AUTO_LOCATE = "no_auto_locate"

        // Must match the key in the preferences file.
        private const val FORCE_GPS = "force_gps"
        private const val MINIMUM_DISTANCE_BEFORE_UPDATE_METRES = 2000
        private const val LOCATION_UPDATE_TIME_MILLISECONDS = 600000
        private val TAG = MiscUtil.getTag(LocationController::class.java)
        private const val MIN_DIST_TO_SHOW_TOAST_DEGS = 0.01f
    }
}

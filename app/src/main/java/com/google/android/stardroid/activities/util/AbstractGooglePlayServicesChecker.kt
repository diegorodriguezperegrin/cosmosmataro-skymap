package com.google.android.stardroid.activities.util

import android.Manifest
import android.app.Activity

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentManager
import com.google.android.stardroid.activities.DynamicStarMapActivity
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleFragment
import com.google.android.stardroid.util.MiscUtil

/**
 * Created by johntaylor on 4/2/16.
 */
abstract class AbstractGooglePlayServicesChecker internal constructor(
    @JvmField protected val parent: Activity,
    @JvmField protected val preferences: SharedPreferences,
    private val rationaleDialog: LocationPermissionRationaleFragment,
    private val fragmentManager: FragmentManager
) : LocationPermissionRationaleFragment.Callback {
    init {
        rationaleDialog.setCallback(this)
    }

    /**
     * Checks whether play services is available and up to date and prompts the user
     * if necessary.
     * <p/>
     * Note that at present we only need it for location services so if the user is setting
     * their location manually we don't do the check.
     */
    abstract fun maybeCheckForGooglePlayServices()

    protected fun checkLocationServicesEnabled() {
        if (ActivityCompat.checkSelfPermission(parent, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Location permission not enabled - maybe prompting user")
            // Check Permissions now
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    parent, Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                rationaleDialog.show(fragmentManager, "Rationale Dialog")
            } else {
                requestLocationPermission()
            }
        } else {
            Log.d(TAG, "Location permission is granted")
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            parent,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            DynamicStarMapActivity.GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE
        )
    }

    /**
     * Called after a request to check permissions.
     */
    fun runAfterPermissionsCheck(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "User granted permission")
        } else {
            Log.i(TAG, "User denied permission")
            // TODO(jontayler): Send them to the location dialog;
        }
    }

    /**
     * Called after the user is prompted to resolve any issues.
     */
    fun runAfterDialog() {
        // Just log for now.
        Log.d(TAG, "Play Services Dialog has been shown")
    }

    override fun done() {
        Log.d(TAG, "Location rationale Dialog has been shown")
        requestLocationPermission()
    }

    companion object {
        @JvmField
        val TAG = MiscUtil.getTag(AbstractGooglePlayServicesChecker::class.java)
    }
}

package com.google.android.stardroid.activities.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import javax.inject.Inject

/**
 * No sensors dialog fragment.
 * Created by johntaylor on 4/9/16.
 */
class NoSensorsDialogFragment : DialogFragment() {
    @Inject
    lateinit var parentActivity: Activity

    @Inject
    lateinit var preferences: SharedPreferences

    interface ActivityComponent {
        fun inject(fragment: NoSensorsDialogFragment?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Activities using this dialog MUST implement this interface.  Obviously.
        val hasComponent = activity as HasComponent<ActivityComponent>
        hasComponent.component.inject(this)

        val inflater = parentActivity.layoutInflater
        val view: View = inflater.inflate(R.layout.no_sensor_warning, null)
        return AlertDialog.Builder(parentActivity)
            .setTitle(R.string.warning_dialog_title)
            .setView(view).setNegativeButton(
                android.R.string.ok
            ) { dialog, whichButton ->
                Log.d(TAG, "No Sensor Dialog closed")
                preferences.edit().putBoolean(
                    ApplicationConstants.NO_WARN_ABOUT_MISSING_SENSORS,
                    (view.findViewById<View>(R.id.no_show_dialog_again) as CheckBox).isChecked
                ).commit()
                dialog.dismiss()
            }.create()
    }

    companion object {
        private val TAG = MiscUtil.getTag(NoSensorsDialogFragment::class.java)
    }
}

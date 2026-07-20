package com.google.android.stardroid.activities.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R

/**
 * Dialog explaining the need for the auto-location permission.
 * Created by johntaylor on 4/3/16.
 */
class LocationPermissionRationaleFragment : DialogFragment(), DialogInterface.OnClickListener {
    private var resultListener: Callback? = null

    interface Callback {
        fun done()
    }

    fun setCallback(resultListener: Callback?) {
        this.resultListener = resultListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogBuilder = AlertDialog.Builder(activity)
            .setTitle(R.string.location_rationale_title)
            .setMessage(R.string.location_rationale_text)
            .setNeutralButton(R.string.dialog_ok_button, this)
        return dialogBuilder.create()
    }

    override fun onClick(ignore1: DialogInterface, ignore2: Int) {
        resultListener?.done()
    }

    companion object {
        private val TAG = MiscUtil.getTag(EulaDialogFragment::class.java)
    }
}

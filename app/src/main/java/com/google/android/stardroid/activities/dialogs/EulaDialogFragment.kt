package com.google.android.stardroid.activities.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.stardroid.StardroidApplication
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import javax.inject.Inject

/**
 * End User License agreement dialog.
 * Created by johntaylor on 4/3/16.
 */
class EulaDialogFragment : DialogFragment() {
    @Inject
    lateinit var parentActivity: Activity

    @Inject
    lateinit var analytics: AnalyticsInterface
    private var resultListener: EulaAcceptanceListener? = null

    interface EulaAcceptanceListener {
        fun eulaAccepted()
        fun eulaRejected()
    }

    interface ActivityComponent {
        fun inject(fragment: EulaDialogFragment?)
    }

    fun setEulaAcceptanceListener(resultListener: EulaAcceptanceListener?) {
        this.resultListener = resultListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "onCreateDialog")
        // Activities using this dialog MUST implement this interface.  Obviously.
        val hasComponent = activity as HasComponent<ActivityComponent>
        hasComponent.component.inject(this)

        val inflater = parentActivity.layoutInflater
        val view: View = inflater.inflate(R.layout.tos_view, null)

        val eulaText = parentActivity.getString(R.string.eula_text)
        val formattedEulaText = Html.fromHtml(eulaText)
        val eulaTextView = view.findViewById<View>(R.id.eula_box_text) as TextView
        eulaTextView.setText(formattedEulaText, TextView.BufferType.SPANNABLE)

        val tosDialogBuilder = AlertDialog.Builder(parentActivity)
            .setTitle(R.string.menu_tos)
            .setView(view)
        if (resultListener != null) {
            tosDialogBuilder
                .setPositiveButton(R.string.dialog_accept) { dialog, whichButton -> acceptEula(dialog) }
                .setNegativeButton(R.string.dialog_decline) { dialog, whichButton -> rejectEula(dialog) }
        }
        return tosDialogBuilder.create()
    }

    private fun acceptEula(dialog: DialogInterface) {
        Log.d(TAG, "TOS Dialog closed.  User accepts.")
        dialog.dismiss()
        analytics.trackEvent(AnalyticsInterface.TOS_ACCEPTED_EVENT, Bundle())
        resultListener?.eulaAccepted()
    }

    private fun rejectEula(dialog: DialogInterface) {
        Log.d(TAG, "TOS Dialog closed.  User declines.")
        dialog.dismiss()
        analytics.trackEvent(AnalyticsInterface.TOS_REJECTED_EVENT, Bundle())
        resultListener?.eulaRejected()
    }

    private val versionName: String
        get() = (parentActivity.application as StardroidApplication).versionName ?: ""

    override fun onCancel(dialog: DialogInterface) {
        rejectEula(dialog)
    }

    companion object {
        private val TAG = MiscUtil.getTag(EulaDialogFragment::class.java)
    }
}

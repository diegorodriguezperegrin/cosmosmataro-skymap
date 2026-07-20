package com.google.android.stardroid.activities.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.android.stardroid.StardroidApplication
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import javax.inject.Inject

/**
 * Help dialog fragment.
 * Created by johntaylor on 4/9/16.
 */
class HelpDialogFragment : DialogFragment() {
    @Inject
    lateinit var application: StardroidApplication

    @Inject
    lateinit var parentActivity: Activity

    interface ActivityComponent {
        fun inject(fragment: HelpDialogFragment?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Activities using this dialog MUST implement this interface.  Obviously.
        val hasComponent = activity as HasComponent<ActivityComponent>
        hasComponent.component.inject(this)

        val inflater = parentActivity.layoutInflater
        val view: View = inflater.inflate(R.layout.help, null)
        val alertDialog = AlertDialog.Builder(parentActivity)
            .setTitle(R.string.help_dialog_title)
            .setView(view).setNegativeButton(
                android.R.string.ok
            ) { dialog, whichButton ->
                Log.d(TAG, "Help Dialog closed")
                dialog.dismiss()
            }.create()
        val helpText = String.format(
            parentActivity.getString(R.string.help_text),
            application.versionName
        )
        val formattedHelpText = Html.fromHtml(helpText)
        val helpTextView = view.findViewById<View>(R.id.help_box_text) as TextView
        helpTextView.setText(formattedHelpText, TextView.BufferType.SPANNABLE)
        return alertDialog
    }

    companion object {
        private val TAG = MiscUtil.getTag(HelpDialogFragment::class.java)
    }
}

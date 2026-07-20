package com.google.android.stardroid.activities.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.stardroid.StardroidApplication
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import javax.inject.Inject

/**
 * Created by johntaylor on 6/10/16.
 */
class WhatsNewDialogFragment : DialogFragment() {
    @Inject
    lateinit var parentActivity: Activity
    private var closeListener: CloseListener? = null

    interface CloseListener {
        fun dialogClosed()
    }

    fun setCloseListener(closeListener: CloseListener?) {
        this.closeListener = closeListener
    }

    interface ActivityComponent {
        fun inject(fragment: WhatsNewDialogFragment?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Activities using this dialog MUST implement this interface.  Obviously.
        val hasComponent = activity as HasComponent<ActivityComponent>
        hasComponent.component.inject(this)

        val inflater = parentActivity.layoutInflater
        val view: View = inflater.inflate(R.layout.whatsnew_view, null)

        val whatsNewText = String.format(parentActivity.getString(R.string.whats_new_text), versionName)
        val formattedWhatsNewText = Html.fromHtml(whatsNewText)
        val whatsNewTextView = view.findViewById<View>(R.id.whats_new_box_text) as TextView
        whatsNewTextView.setText(formattedWhatsNewText, TextView.BufferType.SPANNABLE)

        val dialogBuilder = AlertDialog.Builder(parentActivity)
            .setTitle(getString(R.string.whats_new_dialog_title))
            .setView(view)
            .setNegativeButton(
                R.string.dialog_ok_button
            ) { dialog, whichButton -> endItNow(dialog) }
        return dialogBuilder.create()
    }

    private fun endItNow(dialog: DialogInterface) {
        closeListener?.dialogClosed()
        dialog.dismiss()
    }

    override fun onCancel(dialog: DialogInterface) {
        endItNow(dialog)
    }

    private val versionName: String
        get() = (parentActivity.application as StardroidApplication).versionName ?: ""

    companion object {
        private val TAG = MiscUtil.getTag(WhatsNewDialogFragment::class.java)
    }
}

package com.google.android.stardroid.activities.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import javax.inject.Inject

/**
 * End User License agreement dialog.
 * Created by johntaylor on 4/3/16.
 */
class NoSearchResultsDialogFragment : DialogFragment() {
    @Inject
    lateinit var parentActivity: Activity

    interface ActivityComponent {
        fun inject(fragment: NoSearchResultsDialogFragment?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Activities using this dialog MUST implement this interface.  Obviously.
        val hasComponent = activity as HasComponent<ActivityComponent>
        hasComponent.component.inject(this)

        return AlertDialog.Builder(parentActivity)
            .setTitle(R.string.no_search_title).setMessage(R.string.no_search_results_text2)
            .setNegativeButton(
                android.R.string.ok
            ) { dialog1, whichButton ->
                Log.d(TAG, "No search results Dialog closed")
                dialog1.dismiss()
            }.create()
    }

    companion object {
        private val TAG = MiscUtil.getTag(NoSearchResultsDialogFragment::class.java)
    }
}

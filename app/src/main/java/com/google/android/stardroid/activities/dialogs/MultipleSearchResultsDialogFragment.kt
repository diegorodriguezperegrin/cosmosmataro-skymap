package com.google.android.stardroid.activities.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import com.google.android.stardroid.activities.DynamicStarMapActivity
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.search.SearchResult
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import javax.inject.Inject

/**
 * End User License agreement dialog.
 * Created by johntaylor on 4/3/16.
 */
class MultipleSearchResultsDialogFragment : DialogFragment() {
    @Inject
    lateinit var parentActivity: DynamicStarMapActivity
    private lateinit var multipleSearchResultsAdaptor: ArrayAdapter<SearchResult>

    interface ActivityComponent {
        fun inject(fragment: MultipleSearchResultsDialogFragment?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Activities using this dialog MUST implement this interface.  Obviously.
        val hasComponent = activity as HasComponent<ActivityComponent>
        hasComponent.component.inject(this)

        multipleSearchResultsAdaptor = ArrayAdapter(
            parentActivity, android.R.layout.simple_list_item_1, ArrayList()
        )

        val onClickListener = DialogInterface.OnClickListener { dialog, whichButton ->
                if (whichButton == Dialog.BUTTON_NEGATIVE) {
                    Log.d(TAG, "Many search results Dialog closed with cancel")
                } else {
                    val item = multipleSearchResultsAdaptor.getItem(whichButton)
                    item?.let {
                        parentActivity.activateSearchTarget(it.coords(), it.capitalizedName)
                    }
                }
                dialog.dismiss()
            }
        return AlertDialog.Builder(parentActivity)
            .setTitle(R.string.many_search_results_title)
            .setNegativeButton(android.R.string.cancel, onClickListener)
            .setAdapter(multipleSearchResultsAdaptor, onClickListener)
            .create()
    }

    fun clearResults() {
        multipleSearchResultsAdaptor.clear()
    }

    fun add(result: SearchResult?) {
        multipleSearchResultsAdaptor.add(result)
    }

    companion object {
        private val TAG = MiscUtil.getTag(MultipleSearchResultsDialogFragment::class.java)
    }
}

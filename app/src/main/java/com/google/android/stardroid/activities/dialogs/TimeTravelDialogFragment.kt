package com.google.android.stardroid.activities.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import com.google.android.stardroid.activities.DynamicStarMapActivity
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.util.MiscUtil
import javax.inject.Inject

import androidx.fragment.app.DialogFragment

/**
 * Time travel dialog fragment.
 * Created by johntaylor on 4/3/16.
 */
// TODO(jontayler): see if this crashes when backgrounded on older devices and use
// the fragment in this package if so.
class TimeTravelDialogFragment : DialogFragment() {
    @Inject
    lateinit var parentActivity: DynamicStarMapActivity

    interface ActivityComponent {
        fun inject(fragment: TimeTravelDialogFragment?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Activities using this dialog MUST implement this interface.  Obviously.
        val hasComponent = activity as HasComponent<ActivityComponent>
        hasComponent.component.inject(this)
        return TimeTravelDialog(
            parentActivity,
            parentActivity.model
        )
    }

    companion object {
        private val TAG = MiscUtil.getTag(TimeTravelDialogFragment::class.java)
    }
}

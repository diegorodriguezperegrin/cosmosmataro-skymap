package com.google.android.stardroid.activities

import android.app.Activity
import androidx.fragment.app.FragmentManager
import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import android.view.Window
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment
import com.google.android.stardroid.activities.dialogs.HelpDialogFragment
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleFragment
import com.google.android.stardroid.activities.dialogs.MultipleSearchResultsDialogFragment
import com.google.android.stardroid.activities.dialogs.NoSearchResultsDialogFragment
import com.google.android.stardroid.activities.dialogs.NoSensorsDialogFragment
import com.google.android.stardroid.activities.dialogs.TimeTravelDialogFragment
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger
import com.google.android.stardroid.inject.PerActivity
import com.google.android.stardroid.util.MiscUtil
import dagger.Module
import dagger.Provides
import org.cosmosmataro.skymap.R
import javax.inject.Named

/**
 * Dagger module
 * Created by johntaylor on 3/29/16.
 */
@Module
open class AbstractDynamicStarMapModule(private val activity: DynamicStarMapActivity) {
    companion object {
        private val TAG = MiscUtil.getTag(AbstractDynamicStarMapModule::class.java)
    }

    init {
        Log.d(TAG, "Creating activity module for $activity")
    }

    @Provides
    @PerActivity
    fun provideDynamicStarMapActivity(): DynamicStarMapActivity {
        return activity
    }

    @Provides
    @PerActivity
    fun provideActivity(): Activity {
        return activity
    }

    @Provides
    @PerActivity
    fun provideActivityContext(): Context {
        return activity
    }

    @Provides
    @PerActivity
    fun provideNightModeable(): ActivityLightLevelChanger.NightModeable {
        return activity
    }

    @Provides
    @PerActivity
    fun provideWindow(): Window {
        return activity.window
    }

    @Provides
    @PerActivity
    fun provideEulaDialogFragment(): EulaDialogFragment {
        return EulaDialogFragment()
    }

    @Provides
    @PerActivity
    fun provideTimeTravelDialogFragment(): TimeTravelDialogFragment {
        return TimeTravelDialogFragment()
    }

    @Provides
    @PerActivity
    fun provideHelpDialogFragment(): HelpDialogFragment {
        return HelpDialogFragment()
    }

    @Provides
    @PerActivity
    fun provideNoSearchResultsDialogFragment(): NoSearchResultsDialogFragment {
        return NoSearchResultsDialogFragment()
    }

    @Provides
    @PerActivity
    fun provideMultipleSearchResultsDialogFragment(): MultipleSearchResultsDialogFragment {
        return MultipleSearchResultsDialogFragment()
    }

    @Provides
    @PerActivity
    fun provideNoSensorsDialogFragment(): NoSensorsDialogFragment {
        return NoSensorsDialogFragment()
    }

    @Provides
    @PerActivity
    @Named("timetravel")
    fun provideTimeTravelNoise(): MediaPlayer {
        return MediaPlayer.create(activity, R.raw.timetravel)
    }

    @Provides
    @PerActivity
    @Named("timetravelback")
    fun provideTimeTravelBackNoise(): MediaPlayer {
        return MediaPlayer.create(activity, R.raw.timetravelback)
    }

    @Provides
    @PerActivity
    fun provideTimeTravelFlashAnimation(): Animation {
        return AnimationUtils.loadAnimation(activity, R.anim.timetravelflash)
    }

    @Provides
    @PerActivity
    fun provideHandler(): Handler {
        return Handler()
    }

    @Provides
    @PerActivity
    fun provideFragmentManager(): FragmentManager {
        return activity.supportFragmentManager
    }

    @Provides
    @PerActivity
    fun provideLocationFragment(): LocationPermissionRationaleFragment {
        return LocationPermissionRationaleFragment()
    }
}

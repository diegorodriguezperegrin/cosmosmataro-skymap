package com.google.android.stardroid.activities

import android.content.Context
import android.view.Window
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger
import com.google.android.stardroid.inject.PerActivity
import dagger.Module
import dagger.Provides
import javax.annotation.Nullable

/**
 * Created by johntaylor on 4/24/16.
 */
@Module
class CompassCalibrationModule(private val activity: CompassCalibrationActivity) {

    @Provides
    @PerActivity
    fun provideContext(): Context {
        return activity
    }

    @Provides
    @PerActivity
    fun provideWindow(): Window {
        return activity.window
    }

    @Provides
    @PerActivity
    @Nullable
    fun provideNightModeable(): ActivityLightLevelChanger.NightModeable? {
        return null
    }
}

package com.google.android.stardroid.activities

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.view.Window
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger
import com.google.android.stardroid.inject.PerActivity
import dagger.Module
import dagger.Provides
import javax.annotation.Nullable

/**
 * Created by johntaylor on 4/15/16.
 */
@Module
class DiagnosticActivityModule(private val activity: DiagnosticActivity) {

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
    fun provideHandler(): Handler {
        return Handler()
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

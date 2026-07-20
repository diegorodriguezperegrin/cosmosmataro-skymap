package com.google.android.stardroid.activities

import android.app.Activity
import androidx.fragment.app.FragmentManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment
import com.google.android.stardroid.inject.PerActivity
import dagger.Module
import dagger.Provides
import org.cosmosmataro.skymap.R

/**
 * Created by johntaylor on 4/2/16.
 */
@Module
class SplashScreenModule(private val activity: SplashScreenActivity) {

    @Provides
    @PerActivity
    fun provideEulaDialogFragment(): EulaDialogFragment {
        val fragment = EulaDialogFragment()
        fragment.setEulaAcceptanceListener(activity)
        return fragment
    }

    @Provides
    @PerActivity
    fun provideWhatsNewDialogFragment(): WhatsNewDialogFragment {
        val whatsNewDialogFragment = WhatsNewDialogFragment()
        whatsNewDialogFragment.setCloseListener(activity)
        return whatsNewDialogFragment
    }

    @Provides
    @PerActivity
    fun provideActivity(): Activity {
        return activity
    }

    @Provides
    @PerActivity
    fun provideFadeoutAnimation(): Animation {
        return AnimationUtils.loadAnimation(activity, R.anim.fadeout)
    }

    @Provides
    @PerActivity
    fun provideFragmentManager(): FragmentManager {
        return activity.supportFragmentManager
    }
}

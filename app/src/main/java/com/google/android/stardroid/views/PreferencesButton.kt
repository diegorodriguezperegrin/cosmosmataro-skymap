package com.google.android.stardroid.views

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.widget.AppCompatImageButton
import androidx.preference.PreferenceManager
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R

class PreferencesButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageButton(context, attrs, defStyle), OnClickListener, OnSharedPreferenceChangeListener {

    private var secondaryOnClickListener: OnClickListener? = null
    private var imageOn: Drawable? = null
    private var imageOff: Drawable? = null
    private var imageTintOn: Int = 0
    private var imageTintOff: Int = 0
    private var isOn: Boolean = false
    var prefKey: String? = null
        private set
    private lateinit var preferences: SharedPreferences
    private var defaultValue: Boolean = true

    init {
        setAttrs(context, attrs)
        initialize()
    }

    override fun setOnClickListener(l: OnClickListener?) {
        this.secondaryOnClickListener = l
    }

    private fun setAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PreferencesButton)
        try {
            imageOn = a.getDrawable(R.styleable.PreferencesButton_image_on)
            imageOff = a.getDrawable(R.styleable.PreferencesButton_image_off)
            imageTintOn = a.getColor(R.styleable.PreferencesButton_image_tint_on, 0)
            imageTintOff = a.getColor(R.styleable.PreferencesButton_image_tint_off, 0)
            prefKey = a.getString(R.styleable.PreferencesButton_pref_key)
            defaultValue = a.getBoolean(R.styleable.PreferencesButton_default_value, true)
        } finally {
            a.recycle()
        }
        Log.d(TAG, "Preference key is $prefKey")
    }

    private fun initialize() {
        super.setOnClickListener(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(this)
        isOn = if (prefKey != null) preferences.getBoolean(prefKey, defaultValue) else defaultValue
        Log.d(TAG, "Setting initial value of preference $prefKey to $isOn")
        setVisuallyOnOrOff()
    }

    private fun setVisuallyOnOrOff() {
        var d = if (isOn) imageOn else imageOff
        if (d != null) {
            // Mutate the drawable so we don't affect other instances of the same resource
            d = d.mutate()
            val tint = if (isOn) imageTintOn else imageTintOff
            if (tint != 0) {
                d.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
            } else {
                d.clearColorFilter()
            }
        }
        setImageDrawable(d)
    }

    private fun setPreference() {
        Log.d(TAG, "Setting preference $prefKey to... $isOn")
        if (prefKey != null) {
            preferences.edit().putBoolean(prefKey, isOn).apply()
        }
    }

    override fun onClick(v: View) {
        isOn = !isOn
        analytics?.let {
            val b = Bundle()
            b.putString("preference_toggle_value", "$prefKey:$isOn")
            it.trackEvent("preference_button_toggled_ev", b)
        }
        setVisuallyOnOrOff()
        setPreference()
        secondaryOnClickListener?.onClick(v)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, changedKey: String?) {
        if (changedKey != null && changedKey == prefKey) {
            isOn = sharedPreferences.getBoolean(changedKey, isOn)
            setVisuallyOnOrOff()
        }
    }

    companion object {
        private val TAG = MiscUtil.getTag(PreferencesButton::class.java)
        private var analytics: AnalyticsInterface? = null

        /**
         * Sets the [Analytics] instance for reporting preference toggles.
         *
         * This class gets instantiated by the system and there's not obvious way to
         * access anything
         * dagger-ey to inject the [Analytics]. Since it's not vital to the class'
         * functioning and we'll probably kill this class anyway at some point I can
         * live with this
         * hack.
         *
         * @param analytics
         */
        @JvmStatic
        fun setAnalytics(analytics: AnalyticsInterface) {
            PreferencesButton.analytics = analytics
        }
    }
}

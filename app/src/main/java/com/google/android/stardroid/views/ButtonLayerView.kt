package com.google.android.stardroid.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.LinearLayout
import org.cosmosmataro.skymap.R

/**
 * Contains the provider buttons.
 */
class ButtonLayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // TODO(jontayler): clear up the fade code which is no longer used.
    private var fadeTime = 500

    init {
        isFocusable = false
        val a = context.obtainStyledAttributes(attrs, R.styleable.ButtonLayerView)
        try {
            fadeTime = a.getResourceId(R.styleable.ButtonLayerView_fade_time, 500)
        } finally {
            a.recycle()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        /* Consume all touch events so they don't get dispatched to the view
         * beneath this view.
         */
        return true
    }

    fun show() {
        fade(View.VISIBLE, 0.0f, 1.0f)
    }

    fun hide() {
        fade(View.GONE, 1.0f, 0.0f)
    }

    private fun fade(visibility: Int, startAlpha: Float, endAlpha: Float) {
        val anim = AlphaAnimation(startAlpha, endAlpha)
        anim.duration = fadeTime.toLong()
        startAnimation(anim)
        this.visibility = visibility
    }

    override fun hasFocus(): Boolean {
        val numChildren = childCount
        var hasFocus = false
        for (i in 0 until numChildren) {
            hasFocus = hasFocus || getChildAt(i).hasFocus()
        }
        return hasFocus
    }
}

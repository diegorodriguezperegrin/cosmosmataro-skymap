// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.touch

import android.util.Log
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import com.google.android.stardroid.activities.DynamicStarMapActivity
import com.google.android.stardroid.activities.util.FullscreenControlsManager
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.math.convertScreenToSky
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.util.MiscUtil.getTag

/**
 * Processes touch events and scrolls the screen in manual mode.
 *
 * @author John Taylor
 */
class GestureInterpreter(
  private val fullscreenControlsManager: FullscreenControlsManager,
  private val mapMover: MapMover,
  private val rendererController: RendererController,
  private val layerManager: LayerManager,
  private val activity: DynamicStarMapActivity
) : SimpleOnGestureListener() {
  private val flinger = Flinger { distanceX: Float, distanceY: Float ->
    mapMover.onDrag(
      distanceX,
      distanceY,
      1
    )
  }

  override fun onDown(unused: MotionEvent): Boolean {
    Log.d(TAG, "Tap down")
    flinger.stop()
    return true
  }

  override fun onFling(
    unused1: MotionEvent?,
    unused2: MotionEvent,
    velocityX: Float,
    velocityY: Float
  ): Boolean {
    Log.d(TAG, "Flinging $velocityX, $velocityY")
    flinger.fling(velocityX, velocityY)
    return true
  }

  override fun onSingleTapUp(e: MotionEvent): Boolean {
    Log.d(TAG, "Tap up at ${e.x}, ${e.y}")
    activity.showTouchFeedback(e.x, e.y)
    
    // Try to select an object
    val inverted = rendererController.invertedScreenTransformMatrix
    val height = activity.skyViewHeight.toFloat()
    val flippedY = height - e.y
    val skyPos = convertScreenToSky(e.x, flippedY, inverted)
    
    if (skyPos != null) {
      val results = layerManager.searchByPosition(skyPos, 2.0f) // 2 degree radius
      if (results.isNotEmpty()) {
        val best = results[0]
        Log.d(TAG, "Selected object: ${best.capitalizedName}")
        activity.activateTarget(best)
        activity.showSelectionFeedback(e.x, e.y)
        return true
      }
    }

    // If no object selected, toggle controls ONLY if not in search mode
    if (!activity.isSearchMode()) {
      fullscreenControlsManager.toggleControls()
    }
    return true
  }

  override fun onDoubleTap(e: MotionEvent): Boolean {
    Log.d(TAG, "Double tap")
    return false
  }

  override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
    Log.d(TAG, "Confirmed single tap")
    return false
  }

  companion object {
    private val TAG = getTag(GestureInterpreter::class.java)
  }
}
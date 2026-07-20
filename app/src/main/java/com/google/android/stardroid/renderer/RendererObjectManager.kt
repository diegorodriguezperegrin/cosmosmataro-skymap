// Copyright 2009 Google Inc.
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
package com.google.android.stardroid.renderer

import android.util.Log
import com.google.android.stardroid.renderer.util.TextureManager
import java.util.EnumSet
import javax.microedition.khronos.opengles.GL10

abstract class RendererObjectManager(
    val layer: Int,
    protected val textureManager: TextureManager
) : Comparable<RendererObjectManager> {

    fun textureManager(): TextureManager {
        return textureManager
    }

    // Specifies options for updating a specific RendererObjectManager.
    enum class UpdateType {
        Reset,           // Throw away any previous data and set entirely new data.
        UpdatePositions, // Only update positions of existing objects.
        UpdateImages     // Only update images of existing objects.
    }

    fun interface UpdateListener {
        fun queueForReload(rom: RendererObjectManager, fullReload: Boolean)
    }

    private var enabled = true
    var renderState: RenderStateInterface? = null
    private var listener: UpdateListener? = null
    private var maxRadiusOfView = 360f // in degrees
    private val index: Int

    init {
        synchronized(RendererObjectManager::class.java) {
            index = sIndex++
        }
    }

    fun enable(enable: Boolean) {
        enabled = enable
    }

    fun setMaxRadiusOfView(radiusOfView: Float) {
        maxRadiusOfView = radiusOfView
    }

    override fun compareTo(other: RendererObjectManager): Int {
        if (this.javaClass != other.javaClass) {
            return this.javaClass.name.compareTo(other.javaClass.name)
        }
        return index.compareTo(other.index)
    }

    fun draw(gl: GL10?) {
        if (enabled && renderState!!.radiusOfView <= maxRadiusOfView) {
            drawInternal(gl)
        }
    }

    fun setUpdateListener(listener: UpdateListener) {
        this.listener = listener
    }

    // Notifies the renderer that the manager must be reloaded before the next time it is drawn.
    fun queueForReload(fullReload: Boolean) {
        listener?.queueForReload(this, fullReload)
    }

    protected fun logUpdateMismatch(
        managerType: String, expectedLength: Int, actualLength: Int,
        type: EnumSet<UpdateType>
    ) {
        Log.e(
            "ImageObjectManager",
            "Trying to update objects in " + managerType + ", but number of input sources was "
                    + "different from the number currently set on the manager (" + actualLength
                    + " vs " + expectedLength + "\n"
                    + "Update options were: " + type + "\n"
                    + "Ignoring update"
        )
    }

    // Reload all OpenGL resources needed by the object (ie, textures, VBOs).  If fullReload is true,
    // this means that the object needs to reload everything (this is the case when the object
    // is loaded for the first time, or when the activity is being recreated, and all the previous
    // resources have been invalid.  Sometimes a manager may only need to be partially reloaded (for
    // example, if new objects are set, they might need to be reloaded, but the texture shared
    // between them all is the same so it does not need to be).  The renderer will only ever do a
    // full reload - fullReload will only be false if the manager queues itself for a partial reload.
    abstract fun reload(gl: GL10?, fullReload: Boolean)

    protected abstract fun drawInternal(gl: GL10?)

    companion object {
        // Used to distinguish between different renderers, so we can have sets of them.
        private var sIndex = 0
    }
}

// Copyright 2008 Google Inc.
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

import android.os.ConditionVariable
import android.util.Log
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.HairlinePrimitive
import com.google.android.stardroid.renderables.ImagePrimitive
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.PointPrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import java.util.EnumSet

abstract class RendererControllerBase(protected val mRenderer: SkyRenderer) {

    /**
     * Base class for all renderer managers.
     */
    abstract class RenderManager<E>(internal val mManager: RendererObjectManager) {
        fun queueEnabled(enable: Boolean, controller: RendererControllerBase) {
            val msg = (if (enable) "Enabling" else "Disabling") + " manager " + mManager
            controller.queueRunnable(msg, CommandType.Data) { mManager.enable(enable) }
        }

        fun queueMaxFieldOfView(fov: Float, controller: RendererControllerBase) {
            val msg = "Setting manager max field of view: $fov"
            controller.queueRunnable(msg, CommandType.Data) { mManager.setMaxRadiusOfView(fov) }
        }

        abstract fun queueObjects(
            objects: List<E>,
            updateType: EnumSet<RendererObjectManager.UpdateType>,
            controller: RendererControllerBase
        )
    }

    // TODO(brent): collapse these into a single class?
    /**
     * Class for managing a set of point objects.
     */
    class PointManager(manager: PointObjectManager) : RenderManager<PointPrimitive>(manager) {
        override fun queueObjects(
            points: List<PointPrimitive>,
            updateType: EnumSet<RendererObjectManager.UpdateType>,
            controller: RendererControllerBase
        ) {
            val msg = "Setting point objects"
            controller.queueRunnable(msg, CommandType.Data) {
                (mManager as PointObjectManager).updateObjects(
                    points,
                    updateType
                )
            }
        }
    }

    /**
     * Class for managing a set of polyline objects.
     */
    class LineManager(manager: PolyLineObjectManager) : RenderManager<LinePrimitive>(manager) {
        override fun queueObjects(
            lines: List<LinePrimitive>,
            updateType: EnumSet<RendererObjectManager.UpdateType>,
            controller: RendererControllerBase
        ) {
            val msg = "Setting line objects"
            controller.queueRunnable(
                msg,
                CommandType.Data
            ) { (mManager as PolyLineObjectManager).updateObjects(lines, updateType) }
        }
    }

    /**
     * Class for managing a set of hairline objects.
     */
    class HairlineManager(manager: SimpleLineObjectManager) : RenderManager<HairlinePrimitive>(manager) {
        override fun queueObjects(
            lines: List<HairlinePrimitive>,
            updateType: EnumSet<RendererObjectManager.UpdateType>,
            controller: RendererControllerBase
        ) {
            val msg = "Setting hairline objects"
            controller.queueRunnable(
                msg,
                CommandType.Data
            ) { (mManager as SimpleLineObjectManager).updateObjects(lines, updateType) }
        }
    }

    /**
     * Class for managing a set of text label objects.
     */
    class LabelManager(manager: LabelObjectManager) : RenderManager<TextPrimitive>(manager) {
        override fun queueObjects(
            labels: List<TextPrimitive>,
            updateType: EnumSet<RendererObjectManager.UpdateType>,
            controller: RendererControllerBase
        ) {
            val msg = "Setting label objects"
            controller.queueRunnable(
                msg,
                CommandType.Data
            ) { (mManager as LabelObjectManager).updateObjects(labels, updateType) }
        }
    }

    /**
     * Class for managing a set of image objects.
     */
    class ImageManager(manager: ImageObjectManager) : RenderManager<ImagePrimitive>(manager) {
        override fun queueObjects(
            images: List<ImagePrimitive>,
            updateType: EnumSet<RendererObjectManager.UpdateType>,
            controller: RendererControllerBase
        ) {
            val msg = "Setting image objects"
            controller.queueRunnable(
                msg,
                CommandType.Data
            ) { (mManager as ImageObjectManager).updateObjects(images, updateType) }
        }
    }

    fun interface EventQueuer {
        fun queueEvent(r: Runnable)
    }

    // Used only to allow logging different types of events.  The distinction
    // can be somewhat ambiguous at times, so when in doubt, I tend to use
    // "view" for those things that change all the time (like the direction
    // the user is looking) and "data" for those that change less often
    // (like whether a layer is visible or not).
    enum class CommandType {
        View,  // The command only changes the user's view.
        Data,  // The command changes what is actually rendered.
        Synchronization // The command relates to synchronization.
    }

    fun createPointManager(layer: Int): PointManager {
        val manager = PointManager(mRenderer.createPointManager(layer))
        queueAddManager(manager)
        return manager
    }

    fun createLineManager(layer: Int): LineManager {
        val manager = LineManager(mRenderer.createPolyLineManager(layer))
        queueAddManager(manager)
        return manager
    }

    fun createHairlineManager(layer: Int): HairlineManager {
        val manager = HairlineManager(mRenderer.createHairlineManager(layer))
        queueAddManager(manager)
        return manager
    }

    fun createLabelManager(layer: Int, fontSizeScale: Double): LabelManager {
        val manager = LabelManager(mRenderer.createLabelManager(layer, fontSizeScale))
        queueAddManager(manager)
        return manager
    }

    fun createImageManager(layer: Int): ImageManager {
        val manager = ImageManager(mRenderer.createImageManager(layer))
        queueAddManager(manager)
        return manager
    }

    fun queueNightVisionMode(enable: Boolean) {
        val msg = "Setting night vision mode: $enable"
        queueRunnable(msg, CommandType.View) { mRenderer.setNightVisionMode(enable) }
    }

    fun queueFieldOfView(fov: Float) {
        val msg = "Setting fov: $fov"
        queueRunnable(msg, CommandType.View) { mRenderer.setRadiusOfView(fov) }
    }

    fun queueTextAngle(angleInRadians: Float) {
        val msg = "Setting text angle: $angleInRadians"
        queueRunnable(msg, CommandType.View) { mRenderer.setTextAngle(angleInRadians) }
    }

    fun queueViewerUpDirection(up: Vector3) {
        val msg = "Setting up direction: $up"
        queueRunnable(msg, CommandType.View) { mRenderer.setViewerUpDirection(up) }
    }

    fun queueSetViewOrientation(
        dirX: Float, dirY: Float, dirZ: Float,
        upX: Float, upY: Float, upZ: Float
    ) {
        val msg = "Setting view orientation"
        queueRunnable(msg, CommandType.Data) {
            mRenderer.setViewOrientation(dirX, dirY, dirZ, upX, upY, upZ)
        }
    }

    fun queueEnableSkyGradient(sunPosition: Vector3) {
        val msg = "Enabling sky gradient at: $sunPosition"
        queueRunnable(msg, CommandType.Data) { mRenderer.enableSkyGradient(sunPosition) }
    }

    fun queueDisableSkyGradient() {
        val msg = "Disabling sky gradient"
        queueRunnable(msg, CommandType.Data) { mRenderer.disableSkyGradient() }
    }

    fun queueEnableMilkyWay(resId: Int) {
        val msg = "Enabling Milky Way"
        queueRunnable(msg, CommandType.Data) { mRenderer.enableMilkyWay(resId) }
    }

    fun queueDisableMilkyWay() {
        val msg = "Disabling Milky Way"
        queueRunnable(msg, CommandType.Data) { mRenderer.disableMilkyWay() }
    }

    fun queueEnableGround(enable: Boolean) {
        val msg = "Setting Ground enable: $enable"
        queueRunnable(msg, CommandType.Data) { mRenderer.enableGround(enable) }
    }

    fun queueSetGroundOrientation(zenith: Vector3, north: Vector3) {
        // This is View or Data? Data (changes rotation of mesh).
        val msg = "Setting Ground orientation"
        queueRunnable(msg, CommandType.Data) { mRenderer.setGroundOrientation(zenith, north) }
    }

    fun queueEnableSearchOverlay(target: Vector3, targetName: String) {
        val msg = "Enabling search overlay"
        queueRunnable(
            msg,
            CommandType.Data
        ) { mRenderer.enableSearchOverlay(target, targetName) }
    }

    fun queueDisableSearchOverlay() {
        val msg = "Disabling search overlay"
        queueRunnable(msg, CommandType.Data) { mRenderer.disableSearchOverlay() }
    }

    fun addUpdateClosure(runnable: Runnable) {
        val msg = "Setting update callback"
        queueRunnable(msg, CommandType.Data) { mRenderer.addUpdateClosure(runnable) }
    }

    /**
     * Must be called once to register an object manager to the renderer.
     * @param rom
     */
    fun <E> queueAddManager(rom: RenderManager<E>) {
        val msg = "Adding manager: $rom"
        queueRunnable(msg, CommandType.Data) { mRenderer.addObjectManager(rom.mManager) }
    }

    fun waitUntilFinished() {
        val cv = ConditionVariable()
        val msg = "Waiting until operations have finished"
        queueRunnable(msg, CommandType.Synchronization) { cv.open() }
        cv.block()
    }

    protected abstract fun getQueuer(): EventQueuer

    protected fun queueRunnable(msg: String, type: CommandType, r: Runnable) {
        val queuer = getQueuer()
        val fullMessage = "$this - $msg"
        queueRunnable(queuer, fullMessage, type, r)
    }

    companion object {
        private const val SHOULD_LOG_QUEUE = false
        private const val SHOULD_LOG_RUN = false
        private const val SHOULD_LOG_FINISH = false

        protected fun queueRunnable(
            queuer: EventQueuer, msg: String,
            type: CommandType, r: Runnable
        ) {
            // If we're supposed to log something, then wrap the runnable with the
            // appropriate logging statements.  Otherwise, just queue it.
            if (SHOULD_LOG_QUEUE || SHOULD_LOG_RUN || SHOULD_LOG_FINISH) {
                logQueue(msg, type)
                queuer.queueEvent {
                    logRun(msg, type)
                    r.run()
                    logFinish(msg, type)
                }
            } else {
                queuer.queueEvent(r)
            }
        }

        protected fun logQueue(description: String, type: CommandType) {
            if (SHOULD_LOG_QUEUE) {
                Log.d("RendererController-$type", "Queuing: $description")
            }
        }

        protected fun logRun(description: String, type: CommandType) {
            if (SHOULD_LOG_RUN) {
                Log.d("RendererController-$type", "Running: $description")
            }
        }

        protected fun logFinish(description: String, type: CommandType) {
            if (SHOULD_LOG_FINISH) {
                Log.d("RendererController-$type", "Finished: $description")
            }
        }
    }
}

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

import android.content.res.Resources
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.util.Log
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Matrix4x4
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.shader.StarShaderProgram
import com.google.android.stardroid.renderer.util.GLBuffer
import com.google.android.stardroid.renderer.util.SkyRegionMap
import com.google.android.stardroid.renderer.util.TextureManager
import java.io.PrintWriter
import java.io.StringWriter
import java.util.TreeMap
import java.util.TreeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SkyRenderer(res: Resources) : GLSurfaceView.Renderer {
    private val skyBox: SkyBox
    private val milkyWay: MilkyWayManager
    private val groundOverlay: GroundOverlayManager
    private val overlayManager: OverlayManager
    private val renderState = RenderState()
    private var projectionMatrix: Matrix4x4? = null
    private var viewMatrix: Matrix4x4? = null

    private var starShaderProgram: StarShaderProgram? = null
    private var lineShaderProgram: com.google.android.stardroid.renderer.shader.LineShaderProgram? = null
    private var textureShaderProgram: com.google.android.stardroid.renderer.shader.TextureShaderProgram? = null

    // ... (rest of class)

    // Returns true if the buffers should be swapped, false otherwise.
    override fun onDrawFrame(gl: GL10?) {
        // Initialize any of the unloaded managers.
        for (data in managersToReload) {
            data.manager.reload(gl, data.fullReload)
        }
        managersToReload.clear()
        maybeUpdateMatrices()

        // Determine which sky regions should be rendered.
        renderState.setActiveSkyRegions(
            SkyRegionMap.getActiveRegions(
                renderState.lookDir!!,
                renderState.radiusOfView,
                renderState.screenWidth.toFloat() / renderState.screenHeight
            )
        )
        
        // Pass Camera Matrices to MilkyWayManager
        if (viewMatrix != null && projectionMatrix != null) {
            milkyWay.setCamera(viewMatrix!!.floatArray, projectionMatrix!!.floatArray)
            groundOverlay.setCamera(viewMatrix!!.floatArray, projectionMatrix!!.floatArray)
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST) // Ensure Depth Test is disabled (MilkyWay leaks it)
        
        // Safety: Unbind VBOs to prevent state leakage between managers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        // Reset critical GL state that might leak from previous frames or other managers
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA) // Reset blend func to standard
        GLES20.glDisable(GLES20.GL_CULL_FACE) // Ensure culling is disabled by default
        
        // Reset Texture Unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        
        // Disable Legacy Client States (Critical for proper GLES2 rendering if GL10 was used)
        gl?.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        gl?.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl?.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl?.glDisableClientState(GL10.GL_NORMAL_ARRAY)

        for (managers in layersToManagersMap.values) {
            for (rom in managers) {
                if (rom is PointObjectManager && starShaderProgram != null) {
                    rom.setShaderProgram(starShaderProgram!!)
                } else if (rom is PolyLineObjectManager && lineShaderProgram != null) {
                    rom.setShaderProgram(lineShaderProgram!!)
                } else if (rom is SimpleLineObjectManager && lineShaderProgram != null) {
                    rom.setShaderProgram(lineShaderProgram!!)
                } else if (rom is LabelObjectManager && textureShaderProgram != null) {
                    rom.setShaderProgram(textureShaderProgram!!)
                } else if (rom is ImageObjectManager && textureShaderProgram != null) {
                    rom.setShaderProgram(textureShaderProgram!!)
                } else if (rom is OverlayManager && textureShaderProgram != null) {
                    rom.setShaderProgram(textureShaderProgram!!)
                } else if (rom is MilkyWayManager && textureShaderProgram != null) {
                    rom.setShaderProgram(textureShaderProgram!!)
                } else if (rom is GroundOverlayManager && textureShaderProgram != null) {
                    rom.setShaderProgram(textureShaderProgram!!)
                }
                rom.draw(gl)
                // Granular Error Check
                val error = GLES20.glGetError()
                if (error != 0) {
                    Log.e("SkyRenderer", "GL Error after drawing ${rom.javaClass.simpleName}: $error")
                }
            }
        }
        checkForErrors()

        // Queue updates for the next frame.
        for (update in updateClosures) {
            update.run()
        }
    }

    private var mustUpdateView = true
    private var mustUpdateProjection = true
    private val updateClosures: MutableSet<Runnable> = HashSet()
    private val updateListener = RendererObjectManager.UpdateListener { rom, fullReload ->
        managersToReload.add(ManagerReloadData(rom, fullReload))
    }

    // All managers - we need to reload all of these when we recreate the surface.
    private val allManagers: MutableSet<RendererObjectManager> = TreeSet()
    protected val textureManager: TextureManager

    private class ManagerReloadData(var manager: RendererObjectManager, var fullReload: Boolean)

    // A list of managers which need to be reloaded before the next frame is
    // rendered. This may
    // be because they haven't ever been loaded yet, or because their objects have
    // changed since
    // the last frame.
    private val managersToReload = ArrayList<ManagerReloadData>()

    // Maps an integer indicating render order to a list of objects at that level.
    // The managers
    // will be rendered in order, with the lowest number coming first.
    private val layersToManagersMap = TreeMap<Int, MutableSet<RendererObjectManager>>()

    init {
        renderState.resources = res
        textureManager = TextureManager(res)

        // The skybox should go behind everything.
        // The skybox should go behind everything.
        skyBox = SkyBox(Int.MIN_VALUE, textureManager)
        skyBox.enable(false) // DISABLED: Legacy GL10 code causes 1282
        addObjectManager(skyBox)

        // Milky Way (behind stars, above SkyBox)
        milkyWay = MilkyWayManager(-100, textureManager)
        milkyWay.setResourceId(org.cosmosmataro.skymap.R.drawable.milky_way) // Set resource ID immediately
        addObjectManager(milkyWay)

        // Ground (Landscape) - covers stars below horizon
        // Layer -50 -> Above Milky Way, Below Stars?
        // If stars are 0...
        // Wait, stars are handled by PointObjectManager at layer... checks ApplicationModule. 
        // StarsLayer is... layerManager adds it first. default layer is 10?
        // StarsLayer layerDepthOrder = 10.
        // So Ground should be above stars if we want to Occlude them?
        // Yes, Ground needs to draw ON TOP of stars to hide them. 
        // Or draw first and write to depth buffer? But we disabled depth test for transparency.
        // If Ground is semi-transparent, it must be drawn AFTER stars to blend correctly on top.
        // So Layer > 10.
        // HorizonLayer is 90.
        // Let's create Ground at 80.
        groundOverlay = GroundOverlayManager(80, textureManager)
        groundOverlay.enable(false) // DISABLED: Legacy GL10 code causes 1282
        addObjectManager(groundOverlay)

        // The overlays go on top of everything.
        overlayManager = OverlayManager(Int.MAX_VALUE, textureManager)
        addObjectManager(overlayManager)
        Log.d("SkyRenderer", "SkyRenderer::SkyRenderer()")
    }

    // Returns true if the buffers should be swapped, false otherwise.

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d("SkyRenderer", "surfaceCreated")
        // gl.glEnable(GL10.GL_DITHER) // Not needed in GLES 2.0

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        
        // Initialize Shader Program
        try {
            starShaderProgram = StarShaderProgram(renderState.resources!!)
            Log.d("SkyRenderer", "Shader program created: " + starShaderProgram!!.programId)
            lineShaderProgram = com.google.android.stardroid.renderer.shader.LineShaderProgram(renderState.resources!!)
            textureShaderProgram = com.google.android.stardroid.renderer.shader.TextureShaderProgram(renderState.resources!!)
        } catch (e: Exception) {
            Log.e("SkyRenderer", "Failed to init shader", e)
            e.printStackTrace()
        }

        // Release references to all of the old textures.
        textureManager.reset()
        
        // VBO logic... GLES 2.0 supports VBOs natively.
        GLBuffer.setCanUseVBO(true) 

        // Reload all of the managers.
        for (rom in allManagers) {
            rom.reload(gl, true)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d("SkyRenderer", "Starting sizeChanged, size = ($width, $height)")
        renderState.setScreenSize(width, height)
        overlayManager.resize(gl, width, height)

        // Need to set the matrices.
        mustUpdateView = true
        mustUpdateProjection = true
        Log.d("SkyRenderer", "Changing viewport size")
        GLES20.glViewport(0, 0, width, height)
        Log.d("SkyRenderer", "Done with sizeChanged")
    }

    fun setRadiusOfView(degrees: Float) {
        // Log.d("SkyRenderer", "setRadiusOfView(" + degrees + ")");
        renderState.radiusOfView = degrees
        mustUpdateProjection = true
    }

    fun addUpdateClosure(update: Runnable) {
        updateClosures.add(update)
    }

    // Sets up from the perspective of the viewer.
    // ie, the zenith in celestial coordinates.
    fun setViewerUpDirection(up: Vector3) {
        overlayManager.setViewerUpDirection(up)
    }

    fun addObjectManager(m: RendererObjectManager) {
        m.renderState = renderState
        m.setUpdateListener(updateListener)
        allManagers.add(m)

        // It needs to be reloaded before we try to draw it.
        managersToReload.add(ManagerReloadData(m, true))

        // Add it to the appropriate layer.
        var managers = layersToManagersMap[m.layer]
        if (managers == null) {
            managers = TreeSet()
            layersToManagersMap[m.layer] = managers
        }
        managers.add(m)
    }

    fun removeObjectManager(m: RendererObjectManager) {
        allManagers.remove(m)
        val managers = layersToManagersMap[m.layer]
        // managers shouldn't ever be null, so don't bother checking. Let it crash if it
        // is so we
        // know there's a bug.
        managers!!.remove(m)
    }

    fun enableSkyGradient(sunPosition: Vector3?) {
        skyBox.setSunPosition(sunPosition)
        skyBox.enable(true)
    }

    fun disableSkyGradient() {
        skyBox.enable(false)
    }

    fun enableMilkyWay(resId: Int) {
        milkyWay.setResourceId(resId)
        milkyWay.enable(true)
    }
    
    fun disableMilkyWay() {
        milkyWay.enable(false)
    }

    fun enableGround(enable: Boolean) {
        groundOverlay.enable(enable)
    }

    fun setGroundOrientation(zenith: Vector3, north: Vector3) {
        groundOverlay.setOrientation(zenith, north)
    }

    fun enableSearchOverlay(target: Vector3, targetName: String) {
        overlayManager.enableSearchOverlay(target, targetName)
    }

    fun disableSearchOverlay() {
        overlayManager.disableSearchOverlay()
    }

    fun setNightVisionMode(enabled: Boolean) {
        renderState.nightVisionMode = enabled
    }

    // Used to set the orientation of the text. The angle parameter is the roll
    // of the phone. This angle is rounded to the nearest multiple of 90 degrees
    // to keep the text readable.
    fun setTextAngle(angleInRadians: Float) {
        val TWO_OVER_PI = 2.0f / Math.PI.toFloat()
        val PI_OVER_TWO = Math.PI.toFloat() / 2.0f
        val newAngle = Math.round(angleInRadians * TWO_OVER_PI) * PI_OVER_TWO
        renderState.setUpAngle(newAngle)
    }

    fun setViewOrientation(
        dirX: Float, dirY: Float, dirZ: Float,
        upX: Float, upY: Float, upZ: Float
    ) {
        var dirX = dirX
        var dirY = dirY
        var dirZ = dirZ
        var upX = upX
        var upY = upY
        var upZ = upZ
        // Normalize the look direction
        Log.d(
            "SkyRenderer",
            "setViewOrientation: dir=$dirX,$dirY,$dirZ up=$upX,$upY,$upZ"
        )
        val dirLen = MathUtils.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        val oneOverDirLen = 1.0f / dirLen
        dirX *= oneOverDirLen
        dirY *= oneOverDirLen
        dirZ *= oneOverDirLen
        
        // We need up to be perpendicular to the look direction, so we subtract
        // off the projection of the look direction onto the up vector
        val lookDotUp = dirX * upX + dirY * upY + dirZ * upZ
        upX -= lookDotUp * dirX
        upY -= lookDotUp * dirY
        upZ -= lookDotUp * dirZ

        // Normalize the up vector
        val upLen = MathUtils.sqrt(upX * upX + upY * upY + upZ * upZ)
        val oneOverUpLen = 1.0f / upLen
        upX *= oneOverUpLen
        upY *= oneOverUpLen
        upZ *= oneOverUpLen
        renderState.lookDir = Vector3(dirX, dirY, dirZ)
        renderState.upDir = Vector3(upX, upY, upZ)
        mustUpdateView = true
        overlayManager.setViewOrientation(
            Vector3(dirX, dirY, dirZ),
            Vector3(upX, upY, upZ)
        )
    }

    val width: Int
        get() = renderState.screenWidth

    val height: Int
        get() = renderState.screenHeight

    private fun updateView() {
        // Get a vector perpendicular to both, pointing to the right, by taking
        // lookDir cross up.
        val lookDir = renderState.lookDir
        val upDir = renderState.upDir
        val right = lookDir!!.times(upDir!!)
        viewMatrix = Matrix4x4.createView(lookDir, upDir, right)
        // gl.glMatrixMode(GL10.GL_MODELVIEW)
        // gl.glLoadMatrixf(viewMatrix!!.floatArray, 0)
    }

    private fun updatePerspective() {
        projectionMatrix = Matrix4x4.createPerspectiveProjection(
            renderState.screenWidth.toFloat(),
            renderState.screenHeight.toFloat(),
            renderState.radiusOfView * 3.141593f / 360.0f
        )
        // gl.glMatrixMode(GL10.GL_PROJECTION)
        // gl.glLoadMatrixf(projectionMatrix!!.floatArray, 0)

        // Switch back to the model view matrix.
        // gl.glMatrixMode(GL10.GL_MODELVIEW)
    }

    private fun maybeUpdateMatrices() {
        val updateTransform = mustUpdateView || mustUpdateProjection
        if (mustUpdateView) {
            updateView()
            mustUpdateView = false
        }
        if (mustUpdateProjection) {
            updatePerspective()
            mustUpdateProjection = false
        }
        if (updateTransform) {
            // Device coordinates are a square from (-1, -1) to (1, 1). Screen
            // coordinates are (0, 0) to (width, height). Both coordinates
            // are useful in different circumstances, so we'll pre-compute
            // matrices to do the transformations from world coordinates
            // into each of these.
            val transformToDevice = Matrix4x4.times(projectionMatrix!!, viewMatrix!!)
            val translate = Matrix4x4.createTranslation(1f, 1f, 0f)
            val scale = Matrix4x4.createScaling(
                renderState.screenWidth * 0.5f,
                renderState.screenHeight * 0.5f, 1f
            )
            val transformToScreen = Matrix4x4.times(
                Matrix4x4.times(scale, translate),
                transformToDevice
            )
            renderState.setTransformationMatrices(transformToDevice, transformToScreen)
        }
    }

    // WARNING! These factory methods are invoked from another thread and
    // therefore cannot do any OpenGL operations or any nontrivial nontrivial
    // initialization.
    //
    // TODO(jpowell): This would be much safer if the renderer controller
    // schedules creation of the objects in the queue.
    fun createPointManager(layer: Int): PointObjectManager {
        return PointObjectManager(layer, textureManager)
    }

    fun createPolyLineManager(layer: Int): PolyLineObjectManager {
        return PolyLineObjectManager(layer, textureManager)
    }

    fun createHairlineManager(layer: Int): SimpleLineObjectManager {
        return SimpleLineObjectManager(layer, textureManager)
    }

    fun createLabelManager(layer: Int, fontSizeScale: Double): LabelObjectManager {
        return LabelObjectManager(layer, textureManager, fontSizeScale)
    }

    fun createImageManager(layer: Int): ImageObjectManager {
        return ImageObjectManager(layer, textureManager)
    }

    val invertedScreenTransformMatrix: Matrix4x4?
        get() {
            val transformToScreen = renderState.transformToScreenMatrix
            return transformToScreen?.inverse()
        }

    companion object {
        fun checkForErrors() {
            checkForErrors(false)
        }

        fun checkForErrors(printStackTrace: Boolean) {
            var error = GLES20.glGetError()
            while (error != 0) {
            // val error = gl.glGetError()
            // if (error != 0) {
                Log.e("SkyRenderer", "GL error: $error")
                Log.e("SkyRenderer", GLU.gluErrorString(error))
                if (printStackTrace) {
                    val writer = StringWriter()
                    Throwable().printStackTrace(PrintWriter(writer))
                    Log.e("SkyRenderer", writer.toString())
                }
                error = GLES20.glGetError()
            }
        }
    }
}

interface RenderStateInterface {
    val cameraPos: Vector3?
    val lookDir: Vector3?
    val upDir: Vector3?
    val radiusOfView: Float
    val upAngle: Float
    val cosUpAngle: Float
    val sinUpAngle: Float
    val screenWidth: Int
    val screenHeight: Int
    val transformToDeviceMatrix: Matrix4x4?
    val transformToScreenMatrix: Matrix4x4?
    val resources: Resources?
    val nightVisionMode: Boolean
    val activeSkyRegions: SkyRegionMap.ActiveRegionData?
}

// TODO(jpowell): RenderState is a bad name. This class is a grab-bag of
// general state which is set once per-frame, and which individual managers
// may need to render the frame. Come up with a better name for this.
class RenderState : RenderStateInterface {
    override var cameraPos: Vector3? = Vector3(0f, 0f, 0f)
    override var lookDir: Vector3? = Vector3(1f, 0f, 0f)
    override var upDir: Vector3? = Vector3(0f, 1f, 0f)
    override var radiusOfView = 45f // in degrees
    override var upAngle = 0f
        private set
    override var cosUpAngle = 1f
        private set
    override var sinUpAngle = 0f
        private set
    override var screenWidth = 100
        private set
    override var screenHeight = 100
        private set
    private var mTransformToDevice = Matrix4x4.createIdentity()
    private var mTransformToScreen = Matrix4x4.createIdentity()
    override var resources: Resources? = null
    override var nightVisionMode = false
    private var mActiveSkyRegionSet: SkyRegionMap.ActiveRegionData? = null

    override val transformToDeviceMatrix: Matrix4x4?
        get() = mTransformToDevice
    override val transformToScreenMatrix: Matrix4x4?
        get() = mTransformToScreen
    override val activeSkyRegions: SkyRegionMap.ActiveRegionData?
        get() = mActiveSkyRegionSet

    fun setUpAngle(angle: Float) {
        upAngle = angle
        cosUpAngle = MathUtils.cos(angle)
        sinUpAngle = MathUtils.sin(angle)
    }

    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    fun setTransformationMatrices(
        transformToDevice: Matrix4x4,
        transformToScreen: Matrix4x4
    ) {
        mTransformToDevice = transformToDevice
        mTransformToScreen = transformToScreen
    }

    fun setActiveSkyRegions(set: SkyRegionMap.ActiveRegionData?) {
        mActiveSkyRegionSet = set
    }
}


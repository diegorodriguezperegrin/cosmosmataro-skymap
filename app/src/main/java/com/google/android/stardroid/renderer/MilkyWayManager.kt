package com.google.android.stardroid.renderer

import android.util.Log
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.TWO_PI
import com.google.android.stardroid.renderer.util.IndexBuffer
import com.google.android.stardroid.renderer.util.TexCoordBuffer
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.VertexBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.acos
import kotlin.math.atan2

class MilkyWayManager(layer: Int, textureManager: TextureManager) :
    RendererObjectManager(layer, textureManager) {

    private val mVertexBuffer = VertexBuffer(false)
    private val mTexCoordBuffer = TexCoordBuffer(false)
    private val mIndexBuffer = IndexBuffer(false)

    private var mTextureID = -1
    private var mRotationMatrix: com.google.android.stardroid.math.Matrix4x4? = null

    // Matrices for rendering
    private val mViewMatrix = FloatArray(16)
    private val mProjectionMatrix = FloatArray(16)

    fun setCamera(viewM: FloatArray, projM: FloatArray) {
        System.arraycopy(viewM, 0, mViewMatrix, 0, 16)
        System.arraycopy(projM, 0, mProjectionMatrix, 0, 16)
    }

    init {
        val numVertices = NUM_VERTEX_BANDS * NUM_STEPS_IN_BAND
        val numIndices = (NUM_VERTEX_BANDS - 1) * NUM_STEPS_IN_BAND * 6
        mVertexBuffer.reset(numVertices)
        mTexCoordBuffer.reset(numVertices)
        mIndexBuffer.reset(numIndices)

        val sinAngles = FloatArray(NUM_STEPS_IN_BAND)
        val cosAngles = FloatArray(NUM_STEPS_IN_BAND)

        var angleInBand = 0f
        val dAngle = TWO_PI / (NUM_STEPS_IN_BAND - 1)
        
        // Precompute Azimuth angles (used for X/Z and U coord)
        for (i in 0 until NUM_STEPS_IN_BAND) {
            sinAngles[i] = MathUtils.sin(angleInBand)
            cosAngles[i] = MathUtils.cos(angleInBand)
            angleInBand += dAngle
        }

        // Angular step for latitude (Phi) to match equirectangular projection
        // Phi goes from +PI/2 (Top) to -PI/2 (Bottom)
        for (band in 0 until NUM_VERTEX_BANDS) {
            val v = band.toFloat() / (NUM_VERTEX_BANDS - 1) // 0 at Top, 1 at Bottom (Matches our "flipped" V)
            
            val phi = (kotlin.math.PI.toFloat() / 2f) - (v * kotlin.math.PI.toFloat()) // +90 to -90
            
            // Spherical coordinates
            // y = sin(phi), radius = cos(phi)
            val y = MathUtils.sin(phi)
            val radius = MathUtils.cos(phi)

            for (i in 0 until NUM_STEPS_IN_BAND) {
                // Pos
                val x = cosAngles[i] * radius
                // y is constant for the band
                val z = sinAngles[i] * radius
                
                mVertexBuffer.addPoint(x, y, z)

                // Texture U coordinate: Maps 0..360 -> 0..1
                // i goes 0 to NUM_STEPS (inclusive of wrap)
                // INVERT U to flip texture horizontally (as requested)
                val u = 1.0f - (i.toFloat() / (NUM_STEPS_IN_BAND - 1))
                
                mTexCoordBuffer.addTexCoords(u, v)
            }
        }

        // Indices (Same as SkyBox)
        val ib = mIndexBuffer
        var topBandStart: Short = 0
        var bottomBandStart = NUM_STEPS_IN_BAND.toShort()
        for (triangleBand in 0 until NUM_VERTEX_BANDS - 1) {
            for (offsetFromStart in 0 until NUM_STEPS_IN_BAND - 1) {
                val topLeft = (topBandStart + offsetFromStart).toShort()
                val topRight = (topLeft + 1).toShort()
                val bottomLeft = (bottomBandStart + offsetFromStart).toShort()
                val bottomRight = (bottomLeft + 1).toShort()

                // Triangle 1
                ib.addIndex(topLeft)
                ib.addIndex(bottomRight)
                ib.addIndex(bottomLeft)

                // Triangle 2
                ib.addIndex(topRight)
                ib.addIndex(bottomRight)
                ib.addIndex(topLeft)
            }
            topBandStart = (topBandStart + NUM_STEPS_IN_BAND).toShort()
            bottomBandStart = (bottomBandStart + NUM_STEPS_IN_BAND).toShort()
        }
        initGalacticRotation()
    }

    private var mResourceId = -1

    override fun reload(gl: GL10?, fullReload: Boolean) {
        mVertexBuffer.reload()
        mTexCoordBuffer.reload()
        mIndexBuffer.reload()
        if (fullReload) {
            mTextureID = -1 // Force reload
        }
    }
    
    fun setResourceId(id: Int) {
        mResourceId = id
        mTextureID = -1 // Invalidate so it reloads
    }

    private var shaderProgram: com.google.android.stardroid.renderer.shader.TextureShaderProgram? = null

    fun setShaderProgram(program: com.google.android.stardroid.renderer.shader.TextureShaderProgram) {
        this.shaderProgram = program
    }

    override fun drawInternal(gl: GL10?) {
        if (shaderProgram == null) return
        
        val shader = shaderProgram!!
        shader.useProgram()
        
        // RESET GL STATE: Ensure no VBOs are bound from previous legacy draws
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ARRAY_BUFFER, 0)
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        // DISABLE LEGACY CLIENT STATES (Critical for mixing GL10 and GLES2)
        // SkyBox (GL10) leaves these enabled, causing INVALID_OPERATION when GLES2 draws.
        gl?.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        gl?.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl?.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl?.glDisableClientState(GL10.GL_NORMAL_ARRAY)

        val posLoc = shader.aPositionLocation
        val texLoc = shader.aTexCoordLocation
        
        if (posLoc == -1 || texLoc == -1) return

        // Ensure Texture ID is loaded
        if (mTextureID == -1 && mResourceId != -1) {
            if (gl != null) {
                mTextureID = textureManager.getTextureFromResource(gl, mResourceId).textureId
            }
            if (mTextureID == -1) {
                // Still failed? maybe gl was null or texture loading failed
                return
            }
        } else if (mResourceId == -1) {
            return // No texture resource set
        }

        // Ensure we have a rotation matrix
        if (mRotationMatrix == null) {
            initGalacticRotation()
        }

        // Bind Vertices
        val vb = mVertexBuffer.positionBuffer
        if (vb != null) {
            vb.position(0)
            android.opengl.GLES20.glVertexAttribPointer(posLoc, 3, android.opengl.GLES20.GL_FLOAT, false, 0, vb)
            android.opengl.GLES20.glEnableVertexAttribArray(posLoc)
        }
        
        // Bind Texture Coords
        val tb = mTexCoordBuffer.texCoordBuffer
        if (tb != null) {
            tb.position(0)
            android.opengl.GLES20.glVertexAttribPointer(texLoc, 2, android.opengl.GLES20.GL_FLOAT, false, 0, tb)
            android.opengl.GLES20.glEnableVertexAttribArray(texLoc)
        }
        
        // Calculate MVP Matrix
        // MVP = Projection * View * Model(Rotation)
        val viewProj = FloatArray(16)
        android.opengl.Matrix.multiplyMM(viewProj, 0, mProjectionMatrix, 0, mViewMatrix, 0)
        
        val mvp = FloatArray(16)
        val model = mRotationMatrix!!.floatArray
        android.opengl.Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)

        // Set Uniforms
        shader.setUniforms(mvp, mTextureID, 1f, 1f, 1f, 1f)
        shader.setNightMode(false) // TODO: Get actual night mode?

        // GL State
        android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_CULL_FACE)
        android.opengl.GLES20.glDepthMask(false)
        android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_BLEND)
        android.opengl.GLES20.glBlendFunc(android.opengl.GLES20.GL_SRC_ALPHA, android.opengl.GLES20.GL_ONE) // Additive blending usually looks good for Milky Way, or SRC_ALPHA/ONE_MINUS_SRC_ALPHA

        // Draw Elements
        val ib = mIndexBuffer.indexBuffer
        if (ib != null) {
            ib.position(0)
            android.opengl.GLES20.glDrawElements(android.opengl.GLES20.GL_TRIANGLES, mIndexBuffer.size(), android.opengl.GLES20.GL_UNSIGNED_SHORT, ib)
        }
        
        // Cleanup
        android.opengl.GLES20.glDisableVertexAttribArray(posLoc)
        android.opengl.GLES20.glDisableVertexAttribArray(texLoc)
        android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_BLEND)
        // android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_DEPTH_TEST) // REMOVED: Leaks state
    }

    private fun initGalacticRotation() {
        // North Galactic Pole (NGP) J2000
        // RA = 192.85948 deg, Dec = 27.12825 deg
        val raNGP = 192.85948f * com.google.android.stardroid.math.DEGREES_TO_RADIANS
        val decNGP = 27.12825f * com.google.android.stardroid.math.DEGREES_TO_RADIANS
        
        val z_g = com.google.android.stardroid.math.Vector3(
            MathUtils.cos(decNGP) * MathUtils.cos(raNGP),
            MathUtils.cos(decNGP) * MathUtils.sin(raNGP),
            MathUtils.sin(decNGP)
        )

        // Galactic Center (GC) J2000 (Approx direction)
        // RA = 266.4051 deg, Dec = -28.936175 deg
        val raGC = 266.4051f * com.google.android.stardroid.math.DEGREES_TO_RADIANS
        val decGC = -28.936175f * com.google.android.stardroid.math.DEGREES_TO_RADIANS
        
        val x_g_approx = com.google.android.stardroid.math.Vector3(
            MathUtils.cos(decGC) * MathUtils.cos(raGC),
            MathUtils.cos(decGC) * MathUtils.sin(raGC),
            MathUtils.sin(decGC)
        )
        
        // Y_g = Z_g cross X_approx (Galactic East)
        // Using Vector3.times for cross product (based on SkyRenderer usage)
        val y_g = (z_g * x_g_approx).normalizedCopy()
        
        // X_g = Y_g cross Z_g (Exact Galactic Center)
        val x_g = (y_g * z_g).normalizedCopy()
        
        // Construct Basis Matrix
        // Col0 = X_g, Col1 = Z_g (Mesh Y maps to Z_g), Col2 = Y_g
        // Wait: Mesh axes:
        // Vertex generation: x=cos(u), y=v, z=sin(u)
        // Mesh Y is the pole. -> Maps to Z_g (NGP).
        // Mesh X (u=0) -> Maps to Galactic Center. -> Map to X_g.
        // Mesh Z (u=90) -> Maps to Galactic East. -> Map to Y_g.
        
        // Matrix columns:
        // 0: -X_g (Mesh X maps to Anti-Center now to rotate texture 180deg)
        // 1: Z_g  (Mesh Y maps to NGP)
        // 2: -Y_g (Mesh Z maps to -Galactic East)
        // 3: 0, 0, 0, 1
        
        mRotationMatrix = com.google.android.stardroid.math.Matrix4x4(
            floatArrayOf(
                -x_g.x, -x_g.y, -x_g.z, 0f,
                z_g.x, z_g.y, z_g.z, 0f, // Column 2 in matrix memory (Mesh Y)
                -y_g.x, -y_g.y, -y_g.z, 0f,
                0f, 0f, 0f, 1f
            )
        )
        Log.d("MilkyWayManager", "Galactic Rotation Matrix Initialized")
    }

    companion object {
        // Higher resolution than SkyBox for smoother texture mapping
        private const val NUM_VERTEX_BANDS = 32
        private const val NUM_STEPS_IN_BAND = 32
        private const val EPSILON = 1e-3f
    }
}

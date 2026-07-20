package com.google.android.stardroid.renderer

import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.Matrix4x4
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.util.IndexBuffer
import com.google.android.stardroid.renderer.util.TexCoordBuffer
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.VertexBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import org.cosmosmataro.skymap.R

class GroundOverlayManager(layer: Int, textureManager: TextureManager) :
    RendererObjectManager(layer, textureManager) {

    private val mVertexBuffer = VertexBuffer(false)
    private val mTexCoordBuffer = TexCoordBuffer(false)
    private val mIndexBuffer = IndexBuffer(false)

    private var mTextureID = -1
    private var mRotationMatrix: Matrix4x4? = null

    // Matrices for rendering
    private val mViewMatrix = FloatArray(16)
    private val mProjectionMatrix = FloatArray(16)
    
    // Orientation vectors (J2000)
    private var mZenith = Vector3(0f, 0f, 1f)
    private var mNorth = Vector3(1f, 0f, 0f)

    fun setCamera(viewM: FloatArray, projM: FloatArray) {
        System.arraycopy(viewM, 0, mViewMatrix, 0, 16)
        System.arraycopy(projM, 0, mProjectionMatrix, 0, 16)
    }
    
    fun setOrientation(zenith: Vector3, north: Vector3) {
        mZenith = zenith
        mNorth = north
        updateRotationMatrix()
    }

    init {
        // Generate Hemisphere Mesh (Bottom half: Phi 0 to -90)
        // Similar to MilkyWayManager but covering the southern hemisphere of the local frame
        val numVertices = NUM_VERTEX_BANDS * NUM_STEPS_IN_BAND
        val numIndices = (NUM_VERTEX_BANDS - 1) * NUM_STEPS_IN_BAND * 6
        mVertexBuffer.reset(numVertices)
        mTexCoordBuffer.reset(numVertices)
        mIndexBuffer.reset(numIndices)

        val sinAngles = FloatArray(NUM_STEPS_IN_BAND)
        val cosAngles = FloatArray(NUM_STEPS_IN_BAND)

        var angleInBand = 0f
        val dAngle = (2.0 * PI / (NUM_STEPS_IN_BAND - 1)).toFloat()
        
        for (i in 0 until NUM_STEPS_IN_BAND) {
            sinAngles[i] = MathUtils.sin(angleInBand)
            cosAngles[i] = MathUtils.cos(angleInBand)
            angleInBand += dAngle
        }

        // Phi goes from 0 (Horizon) to -PI/2 (Nadir)
        for (band in 0 until NUM_VERTEX_BANDS) {
            val v = band.toFloat() / (NUM_VERTEX_BANDS - 1)
            // Linear mapping for phi: 0 to -PI/2
            val phi = - (v * (PI.toFloat() / 2f)) 
            
            // Spherical coordinates (Radius 1)
            // Z is Up. We want Z = sin(phi) (goes 0 to -1)
            // Radius of ring = cos(phi)
            val z = MathUtils.sin(phi)
            val radius = MathUtils.cos(phi)

            for (i in 0 until NUM_STEPS_IN_BAND) {
                val x = cosAngles[i] * radius
                val y = sinAngles[i] * radius
                
                mVertexBuffer.addPoint(x, y, z)
                // Texcoords don't strictly matter for solid color, but good for completeness
                mTexCoordBuffer.addTexCoords(i.toFloat() / NUM_STEPS_IN_BAND, v)
            }
        }

        // Indices
        val ib = mIndexBuffer
        var topBandStart: Short = 0
        var bottomBandStart = NUM_STEPS_IN_BAND.toShort()
        for (band in 0 until NUM_VERTEX_BANDS - 1) {
            for (offset in 0 until NUM_STEPS_IN_BAND - 1) {
                val topLeft = (topBandStart + offset).toShort()
                val topRight = (topLeft + 1).toShort()
                val bottomLeft = (bottomBandStart + offset).toShort()
                val bottomRight = (bottomLeft + 1).toShort()

                ib.addIndex(topLeft)
                ib.addIndex(bottomRight)
                ib.addIndex(bottomLeft)
                
                ib.addIndex(topRight)
                ib.addIndex(bottomRight)
                ib.addIndex(topLeft)
            }
            topBandStart = (topBandStart + NUM_STEPS_IN_BAND).toShort()
            bottomBandStart = (bottomBandStart + NUM_STEPS_IN_BAND).toShort()
        }
        
        updateRotationMatrix()
    }
    
    private fun updateRotationMatrix() {
        // Construct Basis:
        // Local Z (Up) -> Mapped to Zenith (J2000)
        // Local X (Northish) -> Mapped to North (J2000)
        // Local Y (East) -> Mapped to East (North x Zenith)
        
        // Ensure orthogonality
        // Z_world = Zenith
        val z_w = mZenith.normalizedCopy()
        
        // Y_world = North x Zenith (East) ? No, standard is East = North x Zenith? 
        // Wait, North vector in J2000 might not be perpendicular to Zenith.
        // We need the local tangent plane.
        // North_tangent = (North_pole - (North_pole . Zenith)*Zenith).normalized
        // But mNorth passed in is usually the North Point on horizon... 
        // Let's assume the caller passes orthogonal vectors or we ortho-normalize.
        
        // Actually, HorizonLayer uses:
        // z = zenith
        // n = north
        // e = east
        // These are already orthogonal basis vectors of the Horizon system expressed in J2000.
        // So we can just use them directly as columns.
        
        // Matrix maps Local (x,y,z) to World (X,Y,Z).
        // R * [0,0,1]^T = Zenith. -> Col 2 = Zenith.
        // R * [1,0,0]^T = North. -> Col 0 = North.
        // R * [0,1,0]^T = East. -> Col 1 = East.
        
        // But in my mesh generation:
        // x = cos(theta) * radius
        // y = sin(theta) * radius
        // z = sin(phi)
        
        // Matches typical Gl usage: X=Right, Y=Up? No.
        // I used Z as Up in the mesh generation (z = sin(phi)).
        // So Z axis corresponds to Zenith.
        // X axis (theta=0) corresponds to... arbitrary start of ring.
        // If I use North as X, then theta=0 is North.
        // Y axis is East.
        
        // Cross check: East = North x Zenith?
        // Let's compute East locally.
        val e_w = (mNorth * mZenith).normalizedCopy() // North x Zenith = East?
        // Right Hand Rule: Index(North) x Middle(Zenith) = Thumb(East)?
        // Yes, if North is X, Zenith is Z, East is -Y?
        // X x Z = -Y.
        // So if I want Y to be East, I should need X x Y = Z.
        // North x East = Zenith.
        // East = Zenith x North.
        
        val east = (mZenith * mNorth).normalizedCopy()
        val north = (east * mZenith).normalizedCopy() // Re-orthogonalize North
        
        // So Col0=North, Col1=East, Col2=Zenith
        
        mRotationMatrix = Matrix4x4(
            floatArrayOf(
                north.x, north.y, north.z, 0f,
                east.x, east.y, east.z, 0f,
                z_w.x, z_w.y, z_w.z, 0f,
                0f, 0f, 0f, 1f
            )
        )
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        mVertexBuffer.reload()
        mTexCoordBuffer.reload()
        mIndexBuffer.reload()
        if (fullReload) {
            mTextureID = -1
        }
    }

    private var shaderProgram: com.google.android.stardroid.renderer.shader.TextureShaderProgram? = null

    fun setShaderProgram(program: com.google.android.stardroid.renderer.shader.TextureShaderProgram) {
        this.shaderProgram = program
    }

    override fun drawInternal(gl: GL10?) {
        if (shaderProgram == null) return
        val shader = shaderProgram!!
        shader.useProgram()
        
        // Reset buffers
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ARRAY_BUFFER, 0)
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        // Disable legacy clients
        gl?.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        gl?.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl?.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)

        val posLoc = shader.aPositionLocation
        val texLoc = shader.aTexCoordLocation
        if (posLoc == -1 || texLoc == -1) return

        // Load "Blank" texture for solid fill
        if (mTextureID == -1) {
            if (gl != null) {
                // Create a 1x1 White Opaque Texture programmatically
                val ref = textureManager.createTexture(gl)
                mTextureID = ref.textureId
                
                ref.bind(gl)
                val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                
                android.opengl.GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)
                
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST.toFloat())
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST.toFloat())
                
                bitmap.recycle()
                android.util.Log.d("GroundOverlayManager", "Created White Texture: $mTextureID")
            }
        }

        // Rotation
        if (mRotationMatrix == null) updateRotationMatrix()

        // Bind VBOs
        val vb = mVertexBuffer.positionBuffer
        if (vb != null) {
            vb.position(0)
            android.opengl.GLES20.glVertexAttribPointer(posLoc, 3, android.opengl.GLES20.GL_FLOAT, false, 0, vb)
            android.opengl.GLES20.glEnableVertexAttribArray(posLoc)
        }
        val tb = mTexCoordBuffer.texCoordBuffer
        if (tb != null) {
            tb.position(0)
            android.opengl.GLES20.glVertexAttribPointer(texLoc, 2, android.opengl.GLES20.GL_FLOAT, false, 0, tb)
            android.opengl.GLES20.glEnableVertexAttribArray(texLoc)
        }

        // MVP
        val viewProj = FloatArray(16)
        android.opengl.Matrix.multiplyMM(viewProj, 0, mProjectionMatrix, 0, mViewMatrix, 0)
        val mvp = FloatArray(16)
        val model = mRotationMatrix!!.floatArray
        android.opengl.Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)

        // Uniforms: Black, Alpha 0.5 (Semi-transparent)
        // (r, g, b, a) -> Multiplied by texture.
        shader.setUniforms(mvp, mTextureID, 0f, 0f, 0f, 0.5f) 
        
        // Disable Cull Face to ensure visibility from inside
        android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_CULL_FACE)

        // Draw
        android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_BLEND)
        android.opengl.GLES20.glBlendFunc(android.opengl.GLES20.GL_SRC_ALPHA, android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA)
        android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_CULL_FACE)
        android.opengl.GLES20.glDepthMask(false) // Transparent rendering

        val ib = mIndexBuffer.indexBuffer
        if (ib != null) {
            ib.position(0)
            android.opengl.GLES20.glDrawElements(android.opengl.GLES20.GL_TRIANGLES, mIndexBuffer.size(), android.opengl.GLES20.GL_UNSIGNED_SHORT, ib)
        }

        // Cleanup
        android.opengl.GLES20.glDepthMask(true)
        android.opengl.GLES20.glDisableVertexAttribArray(posLoc)
        android.opengl.GLES20.glDisableVertexAttribArray(texLoc)
    }

    companion object {
        private const val NUM_VERTEX_BANDS = 16
        private const val NUM_STEPS_IN_BAND = 24
    }
}

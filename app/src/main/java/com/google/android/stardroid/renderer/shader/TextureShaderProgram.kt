package com.google.android.stardroid.renderer.shader

import android.content.res.Resources
import android.opengl.GLES20
import android.util.Log
import com.google.android.stardroid.renderer.util.ShaderUtils
import org.cosmosmataro.skymap.R

class TextureShaderProgram(resources: Resources) {
    var programId: Int = 0
        private set

    // Uniform locations
    private var uMVPMatrixLocation: Int = 0
    private var uTextureLocation: Int = 0
    private var uTextColorLocation: Int = 0

    // Attribute locations
    var aPositionLocation: Int = 0
        private set
    var aTexCoordLocation: Int = 0
        private set

    private var uIsNightModeLocation = -1

    init {
        val vertexShaderSource = ShaderUtils.readShaderFromResource(resources, R.raw.texture_vertex_shader)
        val fragmentShaderSource = ShaderUtils.readShaderFromResource(resources, R.raw.texture_fragment_shader)
        programId = ShaderUtils.buildProgram(vertexShaderSource, fragmentShaderSource)

        uMVPMatrixLocation = GLES20.glGetUniformLocation(programId, "u_MVPMatrix")
        uTextureLocation = GLES20.glGetUniformLocation(programId, "u_Texture")
        uTextColorLocation = GLES20.glGetUniformLocation(programId, "u_TextColor")
        
        aPositionLocation = GLES20.glGetAttribLocation(programId, "a_Position")
        aTexCoordLocation = GLES20.glGetAttribLocation(programId, "a_TexCoord")
        
        uIsNightModeLocation = GLES20.glGetUniformLocation(programId, "u_isNightMode")

        Log.d("TextureShaderProgram", "Attributes: pos=$aPositionLocation, tex=$aTexCoordLocation")
    }

    fun useProgram() {
        GLES20.glUseProgram(programId)
    }

    fun setUniforms(mvpMatrix: FloatArray, textureId: Int, r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLocation, 0)
        GLES20.glUniform4f(uTextColorLocation, r, g, b, a)
    }

    fun setNightMode(isNightMode: Boolean) {
        if (uIsNightModeLocation != -1) {
            GLES20.glUniform1f(uIsNightModeLocation, if (isNightMode) 1.0f else 0.0f)
        }
    }
}

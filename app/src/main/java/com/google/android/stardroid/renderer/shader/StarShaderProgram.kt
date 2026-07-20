package com.google.android.stardroid.renderer.shader

import android.content.res.Resources
import android.opengl.GLES20
import android.util.Log
import com.google.android.stardroid.renderer.util.ShaderUtils
import org.cosmosmataro.skymap.R

class StarShaderProgram(resources: Resources) {
    var programId: Int = 0
        private set

    // Uniform locations
    private var uMVPMatrixLocation: Int = 0
    private var uTextureLocation: Int = 0

    // Attribute locations
    var aPositionLocation: Int = 0
        private set
    var aPointSizeLocation: Int = 0
        private set
    var aTexOffsetLocation: Int = 0
        private set
    var aColorLocation: Int = 0
        private set

    private var uIsNightModeLocation = -1

    init {
        val vertexShaderSource = ShaderUtils.readShaderFromResource(resources, R.raw.star_vertex_shader)
        val fragmentShaderSource = ShaderUtils.readShaderFromResource(resources, R.raw.star_fragment_shader)
        programId = ShaderUtils.buildProgram(vertexShaderSource, fragmentShaderSource)

        uMVPMatrixLocation = GLES20.glGetUniformLocation(programId, "u_MVPMatrix")
        uTextureLocation = GLES20.glGetUniformLocation(programId, "u_Texture")
        aPositionLocation = GLES20.glGetAttribLocation(programId, "a_Position")
        aPointSizeLocation = GLES20.glGetAttribLocation(programId, "a_PointSize")
        aTexOffsetLocation = GLES20.glGetAttribLocation(programId, "a_TexOffset")
        aColorLocation = GLES20.glGetAttribLocation(programId, "a_Color")
        Log.d("StarShaderProgram", "Attributes: pos=$aPositionLocation, size=$aPointSizeLocation, tex=$aTexOffsetLocation, color=$aColorLocation")
        
        uIsNightModeLocation = GLES20.glGetUniformLocation(programId, "u_isNightMode")
    }

    fun useProgram() {
        GLES20.glUseProgram(programId)
    }

    fun setUniforms(mvpMatrix: FloatArray, textureId: Int) {
        Log.d("StarShaderProgram", "Setting uniforms. MVP: " + mvpMatrix[0] + ", " + mvpMatrix[5] + ", " + mvpMatrix[10] + ", " + mvpMatrix[15])
        GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLocation, 0)
    }

    fun setNightMode(isNightMode: Boolean) {
        if (uIsNightModeLocation != -1) {
            GLES20.glUniform1f(uIsNightModeLocation, if (isNightMode) 1.0f else 0.0f)
        }
    }
}

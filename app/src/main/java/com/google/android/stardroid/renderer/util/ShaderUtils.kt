package com.google.android.stardroid.renderer.util

import android.content.Context
import android.content.res.Resources
import android.opengl.GLES20
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object ShaderUtils {
    private const val TAG = "ShaderUtils"

    fun compileVertexShader(shaderCode: String): Int {
        return compileShader(GLES20.GL_VERTEX_SHADER, shaderCode)
    }

    fun compileFragmentShader(shaderCode: String): Int {
        return compileShader(GLES20.GL_FRAGMENT_SHADER, shaderCode)
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shaderObjectId = GLES20.glCreateShader(type)
        if (shaderObjectId == 0) {
            Log.w(TAG, "Could not create new shader.")
            return 0
        }
        GLES20.glShaderSource(shaderObjectId, shaderCode)
        GLES20.glCompileShader(shaderObjectId)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shaderObjectId, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.w(TAG, "Compilation of shader failed.")
            Log.w(TAG, GLES20.glGetShaderInfoLog(shaderObjectId))
            GLES20.glDeleteShader(shaderObjectId)
            return 0
        }
        return shaderObjectId
    }

    fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
        val programObjectId = GLES20.glCreateProgram()
        if (programObjectId == 0) {
            Log.w(TAG, "Could not create new program")
            return 0
        }
        GLES20.glAttachShader(programObjectId, vertexShaderId)
        GLES20.glAttachShader(programObjectId, fragmentShaderId)
        GLES20.glLinkProgram(programObjectId)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programObjectId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.w(TAG, "Linking of program failed.")
            Log.w(TAG, GLES20.glGetProgramInfoLog(programObjectId))
            GLES20.glDeleteProgram(programObjectId)
            return 0
        }
        return programObjectId
    }

    fun validateProgram(programObjectId: Int): Boolean {
        GLES20.glValidateProgram(programObjectId)
        val validateStatus = IntArray(1)
        GLES20.glGetProgramiv(programObjectId, GLES20.GL_VALIDATE_STATUS, validateStatus, 0)
        Log.v(TAG, "Results of validating program: " + validateStatus[0] + "\nLog:" + GLES20.glGetProgramInfoLog(programObjectId))
        return validateStatus[0] != 0
    }

    fun buildProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = compileVertexShader(vertexShaderSource)
        val fragmentShader = compileFragmentShader(fragmentShaderSource)
        val programId = linkProgram(vertexShader, fragmentShader)
        Log.d(TAG, "buildProgram: vertexId=$vertexShader, fragmentId=$fragmentShader, programId=$programId")
        return programId
    }

    fun readShaderFromResource(resources: Resources, resourceId: Int): String {
        val body = StringBuilder()
        try {
            val inputStream = resources.openRawResource(resourceId)
            val inputStreamReader = InputStreamReader(inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            var nextLine: String?
            while (bufferedReader.readLine().also { nextLine = it } != null) {
                body.append(nextLine)
                body.append('\n')
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not read resource: $resourceId", e)
            throw RuntimeException("Could not read resource: $resourceId", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error reading resource: $resourceId", e)
            throw RuntimeException("Could not read resource: $resourceId", e)
        }
        val source = body.toString()
        Log.d(TAG, "Read shader resource $resourceId, length=${source.length}")
        return source
    }
}

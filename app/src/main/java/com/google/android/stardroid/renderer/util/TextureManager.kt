package com.google.android.stardroid.renderer.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import android.opengl.GLUtils
import java.io.PrintWriter
import java.io.StringWriter
import javax.microedition.khronos.opengles.GL10

/**
 * Manages all textures used by the application. Useful to make sure that we
 * don't accidentally use deleted textures and don't leak textures, and that we don't create
 * multiple instances of the same texture.
 */
class TextureManager(private val res: Resources) {
    private val resourceIdToTextureMap = HashMap<Int, TextureData>()
    private val allTextures = ArrayList<TextureReferenceImpl>()

    fun createTexture(gl: GL10): TextureReference {
        return createTextureInternal(gl)
    }

    fun getTextureFromResource(gl: GL10, resourceID: Int): TextureReference {
        // If the texture already exists, return it.
        val texData = resourceIdToTextureMap[resourceID]
        if (texData != null) {
            // Increment the reference count
            texData.refCount++
            return texData.ref!!
        }

        val tex = createTextureFromResource(gl, resourceID)

        // Add it to the map.
        val data = TextureData()
        data.ref = tex
        data.refCount = 1
        resourceIdToTextureMap[resourceID] = data

        return tex
    }

    fun reset() {
        resourceIdToTextureMap.clear()
        for (ref in allTextures) {
            ref.invalidate()
        }
        allTextures.clear()
    }

    private class TextureReferenceImpl(override val textureId: Int) : TextureReference {
        private var valid = true

        override fun bind(gl: GL10) {
            checkValid()
            gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId)
        }

        override fun delete(gl: GL10) {
            checkValid()
            gl.glDeleteTextures(1, intArrayOf(textureId), 0)
            invalidate()
        }

        fun invalidate() {
            valid = false
        }

        private fun checkValid() {
            if (!valid) {
                Log.e("TextureManager", "Setting invalidated texture ID: $textureId")
                val writer = StringWriter()
                Throwable().printStackTrace(PrintWriter(writer))
                Log.e("TextureManager", writer.toString())
            }
        }
    }

    private class TextureData {
        var ref: TextureReferenceImpl? = null
        var refCount = 0
    }

    private fun createTextureFromResource(gl: GL10, resourceID: Int): TextureReferenceImpl {
        // The texture hasn't been loaded yet, so load it.
        val tex = createTextureInternal(gl)
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        var bmp = BitmapFactory.decodeResource(res, resourceID, opts)

        if (bmp == null) {
            // Fallback for VectorDrawables or other drawables that BitmapFactory can't decode
            try {
                val drawable = res.getDrawable(resourceID, null)
                if (drawable != null) {
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48

                    bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(0) // Ensure background is transparent
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    Log.d("TextureManager", "Supported drawable loaded for resource: $resourceID")
                }
            } catch (e: Exception) {
                Log.e("TextureManager", "Failed to load drawable resource: $resourceID", e)
            }
        }

        if (bmp == null) {
            Log.e("TextureManager", "Failed to decode resource: $resourceID")
            // Return the texture anyway, it will just be empty/black probably
            return tex
        }

        tex.bind(gl)
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE.toFloat())
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE.toFloat())

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0)

        bmp.recycle()
        return tex
    }

    private fun createTextureInternal(gl: GL10): TextureReferenceImpl {
        // The texture hasn't been loaded yet, so load it.
        val texID = IntArray(1)
        gl.glGenTextures(1, texID, 0)
        val tex = TextureReferenceImpl(texID[0])
        allTextures.add(tex)
        return tex
    }
}

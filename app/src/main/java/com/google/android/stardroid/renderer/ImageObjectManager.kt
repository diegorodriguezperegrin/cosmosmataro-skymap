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

import android.graphics.Bitmap
import android.opengl.GLUtils
import android.util.Log
import com.google.android.stardroid.renderables.ImagePrimitive
import com.google.android.stardroid.renderer.shader.TextureShaderProgram
import com.google.android.stardroid.renderer.util.TexCoordBuffer
import com.google.android.stardroid.renderer.util.TextureManager
import com.google.android.stardroid.renderer.util.TextureReference
import com.google.android.stardroid.renderer.util.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.EnumSet
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

class ImageObjectManager(layer: Int, manager: TextureManager) :
    RendererObjectManager(layer, manager) {
    private val mVertexBuffer = VertexBuffer(false)
    private val mTexCoordBuffer = TexCoordBuffer(false)
    private var mImages = arrayOfNulls<Image>(0)
    private var mTextures = arrayOfNulls<TextureReference>(0)
    private var mRedTextures = arrayOfNulls<TextureReference>(0)

    private var shaderProgram: TextureShaderProgram? = null

    fun setShaderProgram(shaderProgram: TextureShaderProgram) {
        this.shaderProgram = shaderProgram
    }

    var mUpdates: EnumSet<UpdateType> = EnumSet.noneOf(UpdateType::class.java)

    fun updateObjects(imageSources: List<ImagePrimitive>, type: EnumSet<UpdateType>) {
        val safeImages = ArrayList(imageSources)
        if (!type.contains(UpdateType.Reset) && safeImages.size != mImages.size) {
            logUpdateMismatch(
                "ImageObjectManager",
                safeImages.size,
                mImages.size,
                type
            )
            return
        }
        mUpdates.addAll(type)

        val numVertices = safeImages.size * 4
        val vertexBuffer = mVertexBuffer
        vertexBuffer.reset(numVertices)

        val texCoordBuffer = mTexCoordBuffer
        texCoordBuffer.reset(numVertices)

        val images: Array<Image?>
        val reset = type.contains(UpdateType.Reset) || type.contains(UpdateType.UpdateImages)
        if (reset) {
            images = arrayOfNulls(safeImages.size)
            for (i in safeImages.indices) {
                val `is` = safeImages[i]

                images[i] = Image()
                // TODO(brent): Fix this method.
                images[i]!!.name = "no url"
                images[i]!!.useBlending = false
                images[i]!!.bitmap = `is`.image
            }
        } else {
            images = mImages
        }

        // Update the positions in the position and tex coord buffers.
        if (reset || type.contains(UpdateType.UpdatePositions)) {
            for (i in safeImages.indices) {
                val `is` = safeImages[i]
                val xyz = `is`.location
                val px = xyz.x
                val py = xyz.y
                val pz = xyz.z

                val u = `is`.horizontalCorner
                val ux = u[0]
                val uy = u[1]
                val uz = u[2]

                val v = `is`.verticalCorner
                val vx = v[0]
                val vy = v[1]
                val vz = v[2]

                // lower left
                vertexBuffer.addPoint(px - ux - vx, py - uy - vy, pz - uz - vz)
                texCoordBuffer.addTexCoords(0f, 1f)

                // upper left
                vertexBuffer.addPoint(px - ux + vx, py - uy + vy, pz - uz + vz)
                texCoordBuffer.addTexCoords(0f, 0f)

                // lower right
                vertexBuffer.addPoint(px + ux - vx, py + uy - vy, pz + uz - vz)
                texCoordBuffer.addTexCoords(1f, 1f)

                // upper right
                vertexBuffer.addPoint(px + ux + vx, py + uy + vy, pz + uz + vz)
                texCoordBuffer.addTexCoords(1f, 0f)
            }
        }

        // We already set the image in reset, so only set them here if we're
        // not doing a reset.
        if (type.contains(UpdateType.UpdateImages)) {
            for (i in safeImages.indices) {
                val `is` = safeImages[i]
                images[i]!!.bitmap = `is`.image
            }
        }

        mImages = images
        queueForReload(false)
    }

    override fun reload(gl: GL10?, fullReload: Boolean) {
        val images = mImages
        var reloadBuffers = false
        var reloadImages = false

        if (fullReload) {
            reloadBuffers = true
            reloadImages = true
            // If this is a full reload, all the textures were automatically deleted,
            // so just create new arrays so we won't try to delete the old ones again.
            mTextures = arrayOfNulls(images.size)
            mRedTextures = arrayOfNulls(images.size)
        } else {
            // Process any queued updates.
            val reset = mUpdates.contains(UpdateType.Reset)
            reloadBuffers = reloadBuffers or (reset || mUpdates.contains(UpdateType.UpdatePositions))
            reloadImages = reloadImages or (reset || mUpdates.contains(UpdateType.UpdateImages))
            mUpdates.clear()
        }

        if (reloadBuffers) {
            mVertexBuffer.reload()
            mTexCoordBuffer.reload()
        }
        if (reloadImages) {
            // 1. Delete all existing textures
            for (i in mTextures.indices) {
                if (mTextures[i] != null) {
                    mTextures[i]!!.delete(gl!!)
                }
                if (mRedTextures[i] != null) {
                    mRedTextures[i]!!.delete(gl!!)
                }
            }

            // 2. Resize texture arrays if the number of images has changed
            if (mTextures.size != images.size) {
                mTextures = arrayOfNulls(images.size)
                mRedTextures = arrayOfNulls(images.size)
            }

            // 3. Create new textures for the current images
            for (i in images.indices) {
                val bmp = images[i]!!.bitmap
                mTextures[i] = textureManager.createTexture(gl!!)
                mTextures[i]!!.bind(gl)
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
                gl.glTexParameterf(
                    GL10.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_WRAP_S,
                    GL10.GL_CLAMP_TO_EDGE.toFloat()
                )
                gl.glTexParameterf(
                    GL10.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_WRAP_T,
                    GL10.GL_CLAMP_TO_EDGE.toFloat()
                )
                GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0)

                val redPixels = createRedImage(bmp!!)
                mRedTextures[i] = textureManager.createTexture(gl)
                mRedTextures[i]!!.bind(gl)
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
                gl.glTexParameterf(
                    GL10.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_WRAP_S,
                    GL10.GL_CLAMP_TO_EDGE.toFloat()
                )
                gl.glTexParameterf(
                    GL10.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_WRAP_T,
                    GL10.GL_CLAMP_TO_EDGE.toFloat()
                )
                gl.glTexImage2D(
                    GL10.GL_TEXTURE_2D, 0, GL10.GL_RGBA, bmp.width, bmp.height,
                    0, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, redPixels
                )
            }
        }
    }

    override fun drawInternal(gl: GL10?) {
        if (mVertexBuffer.size() == 0 || shaderProgram == null) {
            return
        }

        shaderProgram!!.useProgram()

        // Ensure GL_ARRAY_BUFFER is unbound
        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ARRAY_BUFFER, 0)

        // gl.glEnable(GL10.GL_TEXTURE_2D); // Invalid in GLES 2.0

        // Set Attributes
        mVertexBuffer.bindToAttribute(gl as GL11, shaderProgram!!.aPositionLocation)
        mTexCoordBuffer.bindToAttribute(gl, shaderProgram!!.aTexCoordLocation)

        // Set Common Uniforms (MVP)
        if (renderState!!.transformToDeviceMatrix != null) {
            val mvp = renderState!!.transformToDeviceMatrix!!.floatArray

            val textures = mTextures
            val redTextures = mRedTextures

            // Always use blending in GLES 2.0 for transparency if we lack discard
            android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_BLEND)
            android.opengl.GLES20.glBlendFunc(
                android.opengl.GLES20.GL_SRC_ALPHA,
                android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA
            )
            
            // Disable culling to ensure image is visible from inside the sphere
            android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_CULL_FACE)

            for (i in textures.indices) {
                val texToUse: TextureReference?
                if (renderState!!.nightVisionMode) {
                    texToUse = redTextures[i]
                } else {
                    texToUse = textures[i]
                }

                if (texToUse != null) {
                    // Bind texture to unit 0
                    shaderProgram!!.setUniforms(mvp, texToUse.textureId, 1f, 1f, 1f, 1f)
                    shaderProgram!!.setNightMode(renderState!!.nightVisionMode)

                    // Draw
                    android.opengl.GLES20.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 4 * i, 4)
                }
            }
            android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_CULL_FACE)
            android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_BLEND)
        }

        // gl.glDisable(GL10.GL_TEXTURE_2D); // Invalid

        // Disable attributes? VertexBuffer does not enable/disable, it just calls
        // glVertexAttribPointer
        // But we should clean up if we want to be safe, though SkyRenderer re-sets
        // anyway.
        android.opengl.GLES20.glDisableVertexAttribArray(shaderProgram!!.aPositionLocation)
        android.opengl.GLES20.glDisableVertexAttribArray(shaderProgram!!.aTexCoordLocation)
    }

    private fun createRedImage(bmp: Bitmap): IntBuffer {
        val width = bmp.width
        val height = bmp.height
        val numPixels = width * height
        val pixels = IntArray(numPixels)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        val redPixelsBB = ByteBuffer.allocateDirect(4 * numPixels)
        val redPixels = redPixelsBB.order(ByteOrder.nativeOrder()).asIntBuffer()
        for (j in 0 until numPixels) {
            val r = pixels[j] and 0xff
            val g = pixels[j] shr 8 and 0xff
            val b = pixels[j] shr 16 and 0xff
            val alphaMask = pixels[j] and -0x1000000

            redPixels.put(alphaMask or (r + g + b) / 3)
        }

        redPixels.position(0)
        return redPixels
    }

    private class Image {
        var name: String? = null
        var bitmap: Bitmap? = null
        var textureID: Int = 0
        var useBlending: Boolean = false
    }
}

package com.google.android.stardroid.renderer.util

import android.content.res.Resources
import org.easymock.EasyMock.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.microedition.khronos.opengles.GL10
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TextureManagerTest {
    private lateinit var resources: Resources
    private lateinit var gl: GL10
    private lateinit var textureManager: TextureManager

    @Before
    fun setUp() {
        resources = RuntimeEnvironment.getApplication().resources
        gl = createMock(GL10::class.java)
        textureManager = TextureManager(resources)
    }

    @Test
    fun testResourceTextureReferenceCounting() {
        // First request should trigger glGenTextures
        gl.glGenTextures(eq(1), anyObject(IntArray::class.java), eq(0))
        expectLastCall<Any?>().andAnswer {
            val args = getCurrentArguments()
            val ids = args[1] as IntArray
            ids[0] = 77
            null
        }

        replay(gl)

        // 1. Get texture first time (uses resource ID 100 which is invalid, falls back gracefully)
        val texture1 = textureManager.getTextureFromResource(gl, 100)
        assertEquals(77, texture1.textureId)

        verify(gl)

        // Reset mock for subsequent calls
        reset(gl)
        replay(gl)

        // 2. Get texture second time (should return same instance, increment ref count)
        val texture2 = textureManager.getTextureFromResource(gl, 100)
        assertSame(texture1, texture2)

        verify(gl)

        // Reset mock to check release behaviors
        reset(gl)
        replay(gl)

        // 3. Release first reference (should NOT trigger glDeleteTextures yet as ref count is 1)
        textureManager.releaseTexture(gl, 100)
        verify(gl)

        // Reset mock to assert final deletion
        reset(gl)
        
        // 4. Release final reference (should trigger glDeleteTextures)
        gl.glDeleteTextures(eq(1), anyObject(IntArray::class.java), eq(0))
        expectLastCall<Any?>().andAnswer {
            val args = getCurrentArguments()
            val ids = args[1] as IntArray
            assertEquals(77, ids[0])
            null
        }

        replay(gl)

        textureManager.releaseTexture(gl, 100)
        verify(gl)
    }
}

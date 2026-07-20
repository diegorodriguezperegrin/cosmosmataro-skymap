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

package com.google.android.stardroid.renderables

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords

/**
 * A celestial object represented by an image, such as a planet or a
 * galaxy.
 */
class ImagePrimitive(
    coords: Vector3,
    protected val resources: Resources,
    id: Int,
    upVec: Vector3 = up,
    imageScale: Float = 1.0f
) : AbstractPrimitive(coords, Color.WHITE) {

    // These two vectors, along with Source.xyz, determine the position of the
    // image object. The corners are as follows
    //
    // xyz-u+v xyz+u+v
    // +---------+ ^
    // | xyz | | v
    // | . | .
    // | |
    // +---------+
    // xyz-u-v xyz+u-v
    //
    // .--->
    // u
    @JvmField var ux: Float = 0f
    @JvmField var uy: Float = 0f
    @JvmField var uz: Float = 0f
    @JvmField var vx: Float = 0f
    @JvmField var vy: Float = 0f
    @JvmField var vz: Float = 0f

    var image: Bitmap? = null
    var requiresBlending = false

    var imageScale: Float = imageScale
        set(value) {
            field = value
            setUpVector(up)
        }

    constructor(ra: Float, dec: Float, res: Resources, id: Int) : this(
         getGeocentricCoords(ra, dec),
        res,
        id,
        up,
        1.0f
    )

    constructor(ra: Float, dec: Float, res: Resources, id: Int, upVec: Vector3) : this(
        getGeocentricCoords(ra, dec),
        res,
        id,
        upVec,
        1.0f
    )

    constructor(
        ra: Float,
        dec: Float,
        res: Resources,
        id: Int,
        upVec: Vector3,
        imageScale: Float
    ) : this(getGeocentricCoords(ra, dec), res, id, upVec, imageScale)


    constructor(
        ra: Float,
        dec: Float,
        resources: Resources,
        bitmap: Bitmap,
        upVec: Vector3,
        imageScale: Float
    ) : this(getGeocentricCoords(ra, dec), resources, 0, upVec, imageScale) {
        this.image = bitmap
    }

    init {
        setUpVector(upVec)
        if (id != 0) {
            setImageId(id)
        }
    }

    fun setImageId(imageId: Int) {
        val opts = BitmapFactory.Options()
        opts.inScaled = false

        var loadedBitmap = BitmapFactory.decodeResource(resources, imageId, opts)
        
        if (loadedBitmap == null) {
             Log.d("ImagePrimitive", "BitmapFactory failed for id $imageId, trying drawable fallback")
            // Fallback for VectorDrawables
            try {
                val drawable = resources.getDrawable(imageId, null)
                if (drawable != null) {
                    var width = drawable.intrinsicWidth
                    var height = drawable.intrinsicHeight
                    // Default to 256x256 to ensure high quality VectorDrawable rendering
                    if (width <= 0) width = 256
                    if (height <= 0) height = 256

                    loadedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    val canvas = Canvas(loadedBitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            } catch (e: Exception) {
                Log.e("ImagePrimitive", "Failed to load drawable resource: $imageId", e)
            }
        } else {
             // Bitmap loaded successfully from resource
        }

        if (loadedBitmap == null) {
            Log.e("ImagePrimitive", "Could not decode image $imageId")
            this.image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            this.image = loadedBitmap
        }
    }

    val horizontalCorner: FloatArray
        get() = floatArrayOf(ux, uy, uz)

    val verticalCorner: FloatArray
        get() = floatArrayOf(vx, vy, vz)

    fun setUpVector(upVec: Vector3) {
        val p = this.location
        val u = p.times(upVec).normalizedCopy().unaryMinus()
        val v = u.times(p)

        v.timesAssign(imageScale)
        u.timesAssign(imageScale)

        // TODO(serafini): Can we replace these with a float[]?
        ux = u.x
        uy = u.y
        uz = u.z

        vx = v.x
        vy = v.y
        vz = v.z
    }


    companion object {
        val up = Vector3(0.0f, 1.0f, 0.0f)
    }
}



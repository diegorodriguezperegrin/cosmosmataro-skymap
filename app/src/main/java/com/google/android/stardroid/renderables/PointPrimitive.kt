// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.renderables

import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.math.Vector3

/**
 * This class represents a astronomical point source, such as a star, or a distant galaxy.
 *
 * @author Brent Bryan
 */
class PointPrimitive(
    coords: Vector3,
    color: Int,
    val size: Int,
    val pointShape: Shape = Shape.CIRCLE
) : AbstractPrimitive(coords, color) {

    constructor(ra: Float, dec: Float, color: Int, size: Int) : this(
        getGeocentricCoords(ra, dec),
        color,
        size
    )

    enum class Shape(val imageIndex: Int) {
        CIRCLE(0),
        STAR(1),
        ELLIPTICAL_GALAXY(2),
        SPIRAL_GALAXY(3),
        IRREGULAR_GALAXY(4),
        LENTICULAR_GALAXY(3),
        GLOBULAR_CLUSTER(5),
        OPEN_CLUSTER(6),
        NEBULA(7),
        HUBBLE_DEEP_FIELD(8);
    }
}

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

package com.google.android.stardroid.control

import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import java.util.Date

/**
 * The interface to AstronomerModelImpl.  It is not expected that there
 * will be multiple subclasses of this interface - it is purely for easy of
 * testing.
 *
 * @author John Taylor
 */
interface AstronomerModel {
    /**
     * A POJO to hold the user's view direction.
     *
     * @author John Taylor
     */
    class Pointing(
        lineOfSight: Vector3,
        perpendicular: Vector3
    ) {
        // Geocentric coordinates
        private val _lineOfSight: Vector3 = lineOfSight.copy()
        private val _perpendicular: Vector3 = perpendicular.copy()

        constructor() : this(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f))

        /**
         * Gets the line of sight component of the pointing.
         * Warning: creates a copy - if you can reuse your own
         * GeocentricCoordinates object it might be more efficient to
         * use [.getLineOfSightX] etc.
         */
        val lineOfSight: Vector3
            get() = _lineOfSight.copy()

        /**
         * Gets the perpendicular component of the pointing.
         * Warning: creates a copy - if you can reuse your own
         * GeocentricCoordinates object it might be more efficient to
         * use [.getLineOfSightX] etc.
         */
        val perpendicular: Vector3
            get() = _perpendicular.copy()

        val lineOfSightX: Float
            get() = _lineOfSight.x
        val lineOfSightY: Float
            get() = _lineOfSight.y
        val lineOfSightZ: Float
            get() = _lineOfSight.z
        val perpendicularX: Float
            get() = _perpendicular.x
        val perpendicularY: Float
            get() = _perpendicular.y
        val perpendicularZ: Float
            get() = _perpendicular.z

        /**
         * Only the AstronomerModel should change this.
         */
        fun updatePerpendicular(newPerpendicular: Vector3) {
            _perpendicular.assign(newPerpendicular)
        }

        /**
         * Only the AstronomerModel should change this.
         */
        fun updateLineOfSight(newLineOfSight: Vector3) {
            _lineOfSight.assign(newLineOfSight)
        }
    }

    /**
     * If set to false, will not update the pointing automatically.
     */
    var autoUpdatePointing: Boolean

    /**
     * Gets or sets the field of view in degrees.
     */
    var fieldOfView: Float

    enum class ViewDirectionMode {
        STANDARD, ROTATE90, TELESCOPE
    }

    fun setViewDirectionMode(value: ViewDirectionMode)

    val magneticCorrection: Float

    /**
     * Returns the time, as UTC.
     */
    val time: Date

    /**
     * Sets the clock that provides the time.
     */
    fun setClock(clock: Clock)

    /**
     * Gets or sets the astronomer's current location on Earth.
     */
    var location: LatLong

    /**
     * Gets the user's direction of view.
     */
    val pointing: Pointing

    /**
     * Sets the user's direction of view.
     */
    fun setPointing(lineOfSight: Vector3, perpendicular: Vector3)

    /**
     * Gets the acceleration vector in the phone frame of reference.
     *
     * The returned object should not be modified.
     */
    val phoneUpDirection: Vector3

    /**
     * Sets the acceleration and magnetic field in the phone frame.
     *
     * The phone frame has x along the short side of the phone increasing to
     * the right, y along the long side increasing towards the top of the phone,
     * and z coming perpendicularly out of the phone increasing towards the user.
     */
    fun setPhoneSensorValues(acceleration: Vector3, magneticField: Vector3)

    /**
     * Sets the phone's rotation vector from the fused gyro/mag field/accelerometer.
     * Alternative to [.setPhoneSensorValues]
     */
    fun setPhoneSensorValues(rotationVector: FloatArray)

    /**
     * Returns the user's North in celestial coordinates.
     */
    val north: Vector3

    /**
     * Returns the user's South in celestial coordinates.
     */
    val south: Vector3

    /**
     * Returns the user's Zenith in celestial coordinates.
     */
    val zenith: Vector3

    /**
     * Returns the user's Nadir in celestial coordinates.
     */
    val nadir: Vector3

    /**
     * Returns the user's East in celestial coordinates.
     */
    val east: Vector3

    /**
     * Returns the user's West in celestial coordinates.
     */
    val west: Vector3

    fun setMagneticDeclinationCalculator(calculator: MagneticDeclinationCalculator)

    val timeMillis: Long
}

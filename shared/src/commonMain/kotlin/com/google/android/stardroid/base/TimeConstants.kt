// Copyright 2010 Google Inc.
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

package com.google.android.stardroid.base

/**
 * Constants for times.
 *
 * @author Brent Bryan
 */
object TimeConstants {
    const val MILLISECONDS_PER_SECOND = 1000L
    const val MILLISECONDS_PER_MINUTE = 60000L
    const val MILLISECONDS_PER_HOUR = 3600000L
    const val MILLISECONDS_PER_DAY = 86400000L
    const val MILLISECONDS_PER_WEEK = 604800000L
    const val SECONDS_PER_SECOND = 1L
    const val SECONDS_PER_MINUTE = 60L
    const val SECONDS_PER_10MINUTE = 600L
    const val SECONDS_PER_HOUR = 3600L
    const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
    const val SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY
    const val SECONDS_PER_SIDERIAL_DAY = 86164.0905
    const val MILLISECONDS_PER_SIDEREAL_DAY = MILLISECONDS_PER_SECOND * SECONDS_PER_SIDERIAL_DAY
    const val SECONDS_PER_SIDERIAL_WEEK = 7 * 86164.0905
}

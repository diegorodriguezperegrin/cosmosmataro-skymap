package com.google.android.stardroid.ephemeris

import org.cosmosmataro.skymap.R

// Extension properties to provide Android resources for SolarSystemBody
// These were previously in the enum constructor but moved out for KMP compatibility.

val SolarSystemBody.imageResourceId: Int
    get() = when (this) {
        SolarSystemBody.Pluto -> R.drawable.pluto
        SolarSystemBody.Neptune -> R.drawable.neptune
        SolarSystemBody.Uranus -> R.drawable.uranus
        SolarSystemBody.Saturn -> R.drawable.saturn
        SolarSystemBody.Jupiter -> R.drawable.jupiter
        SolarSystemBody.Mars -> R.drawable.mars
        SolarSystemBody.Sun -> R.drawable.sun
        SolarSystemBody.Mercury -> R.drawable.mercury
        SolarSystemBody.Venus -> R.drawable.venus
        SolarSystemBody.Moon -> R.drawable.moon4
        SolarSystemBody.Earth -> R.drawable.earth
    }

val SolarSystemBody.nameResourceId: Int
    get() = when (this) {
        SolarSystemBody.Pluto -> R.string.pluto
        SolarSystemBody.Neptune -> R.string.neptune
        SolarSystemBody.Uranus -> R.string.uranus
        SolarSystemBody.Saturn -> R.string.saturn
        SolarSystemBody.Jupiter -> R.string.jupiter
        SolarSystemBody.Mars -> R.string.mars
        SolarSystemBody.Sun -> R.string.sun
        SolarSystemBody.Mercury -> R.string.mercury
        SolarSystemBody.Venus -> R.string.venus
        SolarSystemBody.Moon -> R.string.moon
        SolarSystemBody.Earth -> R.string.earth
    }

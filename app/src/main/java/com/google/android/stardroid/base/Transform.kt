package com.google.android.stardroid.base

/**
 * This interface defines a function which transforms one object into another.
 *
 * @author Brent Bryan
 */
fun interface Transform<E, F> {
    fun transform(e: E): F
}

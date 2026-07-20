package com.google.android.stardroid.base

/**
 * An interface for determining whether or not an object should be included in a collection.
 *
 * @author Brent Bryan
 */
interface Filter<E> {
    /** Returns true if the given object should be included in the collection. */
    fun accept(obj: E): Boolean
}

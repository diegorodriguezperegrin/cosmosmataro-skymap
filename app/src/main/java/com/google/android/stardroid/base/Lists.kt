package com.google.android.stardroid.base

import java.util.ArrayList
import java.util.Arrays

/**
 * Utility methods for easily dealing with Lists.
 *
 * @author Brent Bryan
 */
object Lists {
    /**
     * Transforms each element in the given Iterable and returns the result as a
     * List. Does not change the given Iterable, or the items stored therein.
     */
    @JvmStatic
    fun <E, F> transform(iterable: Iterable<E>, transform: Transform<E, F>): List<F> {
        val result = ArrayList<F>()
        for (e in iterable) {
            result.add(transform.transform(e))
        }
        return result
    }

    /**
     * Returns the given Iterable as a List. If the current Iterable is already a
     * List, then the Iterable is returned directly. Otherwise a new List is
     * created with the same elements as the given Iterable.
     */
    @JvmStatic
    fun <E> asList(iterable: Iterable<E>): List<E> {
        if (iterable is List<*>) {
            @Suppress("UNCHECKED_CAST")
            return iterable as List<E>
        }

        val result = ArrayList<E>()
        for (e in iterable) {
            result.add(e)
        }
        return result
    }

    /**
     * Converts a user specified set of objects into a [List] of that type.
     */
    @SafeVarargs
    @JvmStatic
    fun <E> asList(vararg objects: E): List<E> {
        return Arrays.asList(*objects)
    }
}

package com.google.android.stardroid.base

/**
 * An object capable of providing instances of types T.
 *
 * @param <T> type of object to be provided
 *
 * @author Brent Bryan
 */
fun interface Provider<T> {
    /**
     * Provides an instance of type T. Implementors may choose to either return a
     * new instance upon every call, or provide the same instance for all calls.
     */
    fun get(): T
}

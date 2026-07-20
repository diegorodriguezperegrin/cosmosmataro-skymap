package com.google.android.stardroid.base

import java.util.Comparator

/**
 * A simple object which contains a pair of values. This object can be stored and returned when
 * references to two objects are required.
 *
 * @author Brent Bryan
 */
class Pair<E, F>(var first: E?, var second: F?) {

    companion object {
        @JvmStatic
        fun <S, T> of(first: S, second: T): Pair<S, T> {
            return Pair(first, second)
        }

        /**
         * Returns a new comparator which compares the first object in a set of pairs using the
         * specified Comparator.
         */
        @JvmStatic
        fun <S> comparatorOfFirsts(comparator: Comparator<S>): Comparator<Pair<S, *>> {
            return FirstComparator(comparator)
        }

        private class FirstComparator<E>(private val comparator: Comparator<E>) : Comparator<Pair<E, *>> {
            override fun compare(object1: Pair<E, *>, object2: Pair<E, *>): Int {
                return comparator.compare(object1.first, object2.first)
            }
        }
    }
}

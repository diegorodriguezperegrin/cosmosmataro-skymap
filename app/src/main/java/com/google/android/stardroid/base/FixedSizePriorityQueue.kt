// Copyright 2009 Google Inc.
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

package com.google.android.stardroid.base

import java.util.Comparator
import java.util.PriorityQueue

/**
 * A Priority Queue implementation which holds no more than a specified number
 * of elements. When the queue size exceeds this specified size, the root is
 * removed to ensure that the resulting size remains fixed.
 *
 * @param <E> type of object contained in the queue
 *
 * @author Brent Bryan
 */
class FixedSizePriorityQueue<E>(
    maxQueueSize: Int,
    comparator: Comparator<in E>
) : PriorityQueue<E>(maxQueueSize, comparator) {

    /** Maximum number of elements stored in this queue. */
    private val maxSize = maxQueueSize

    /**
     * Filter used to reject some objects without even checking the number of
     * objects or priorities of those objects in the queue.
     */
    var filter: Filter<in E>? = null

    override fun add(element: E): Boolean {
        if (filter?.accept(element) == false) {
            return false
        }

        if (!isFull) {
            super.add(element)
            return true
        }

        // Compare with the head of the queue (peek()).
        // If the new element is "greater" (according to comparator) than the head,
        // we remove the head and add the new element.
        // Note: The comparator likely orders such that the "smallest" (least interesting) element is at the head.
        if (comparator()!!.compare(element, peek()) > 0) {
            poll()
            super.add(element)
            return true
        }
        return false
    }

    override fun addAll(elements: Collection<E>): Boolean {
        var changed = false
        for (e in elements) {
            changed = changed or add(e)
        }
        return changed
    }

    val isFull: Boolean
        get() = size == maxSize

    companion object {
        private const val serialVersionUID = 3959389634971824728L
    }
}

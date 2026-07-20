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

package com.google.android.stardroid.util

import java.util.AbstractSet
import java.util.WeakHashMap

/**
 * This is to WeakHashMap as HashSet is to HashMap.  Basically, entries are held
 * with weak references and can be gc'd when other clients have finished with
 * them.
 */
class WeakHashSet<E> : AbstractSet<E>(), MutableSet<E> {
    private val hashMap = WeakHashMap<E, Boolean>()

    override fun iterator(): MutableIterator<E> {
        return hashMap.keys.iterator()
    }

    override fun add(element: E): Boolean {
        return hashMap.put(element, true) == null
    }

    override val size: Int
        get() = hashMap.size
}

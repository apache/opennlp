/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.util;

import java.util.Iterator;

/** Interface for interacting with a Heap data structure.
 * This implementation extract objects from smallest to largest based on either
 * their natural ordering or the comparator provided to an implementation.
 * While this is a typical of a heap it allows this objects natural ordering to
 * match that of other sorted collections.
 * */
public interface Heap<E>  {

  /**
   * Removes the smallest element from the heap and returns it.
   * @return The smallest element from the heap.
   */
  E extract();

  /**
   * Returns the smallest element of the heap.
   * @return The top element of the heap.
   */
  E first();

  /**
   * Returns the largest element of the heap.
   * @return The largest element of the heap.
   */
  E last();

  /**
   * Adds the specified object to the heap.
   * @param o The object to add to the heap.
   */
  void add(E o);

  /**
   * Returns the size of the heap.
   * @return The size of the heap.
   */
  int size();

  /**
   * Returns whether the heap is empty.
   * @return true if the heap is empty; false otherwise.
   */
  boolean isEmpty();

  /**
   * Returns an iterator over the elements of the heap.  No specific ordering of these
   * elements is guaranteed.
   * @return An iterator over the elements of the heap.
   */
  Iterator<E> iterator();

  /**
   * Clears the contents of the heap.
   */
  void clear();
}

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * This class implements the heap interface using a {@link java.util.List} as the underlying
 * data structure.  This heap allows values which are equals to be inserted.  The heap will
 * return the top K values which have been added where K is specified by the size passed to
 * the constructor. K+1 values are not gaurenteed to be kept in the heap or returned in a
 * particular order.
 *
 * This is now deprecated and will be removed in Release 1.8.1
 */
@Deprecated
public class ListHeap<E extends Comparable<E>> implements Heap<E> {
  private List<E> list;

  private Comparator<E> comp;

  private int size;

  private E max = null;

  /**
   * Creates a new heap with the specified size using the sorted based on the
   * specified comparator.
   * @param sz The size of the heap.
   * @param c The comparator to be used to sort heap elements.
   */
  public ListHeap(int sz, Comparator<E> c) {
    size = sz;
    comp = c;
    list = new ArrayList<>(sz);
  }

  /**
   * Creates a new heap of the specified size.
   * @param sz The size of the new heap.
   */
  public ListHeap(int sz) {
    this(sz, null);
  }

  private int parent(int i) {
    return (i - 1) / 2;
  }

  private int left(int i) {
    return (i + 1) * 2 - 1;
  }

  private int right(int i) {
    return (i + 1) * 2;
  }

  public int size() {
    return list.size();
  }

  private void swap(int x, int y) {
    E ox = list.get(x);
    E oy = list.get(y);

    list.set(y, ox);
    list.set(x, oy);
  }

  private boolean lt(E o1, E o2) {
    if (comp != null) {
      return comp.compare(o1, o2) < 0;
    }
    else {
      return o1.compareTo(o2) < 0;
    }
  }

  private boolean gt(E o1, E o2) {
    if (comp != null) {
      return comp.compare(o1, o2) > 0;
    }
    else {
      return o1.compareTo(o2) > 0;
    }
  }

  private void heapify(int i) {
    while (true) {
      int l = left(i);
      int r = right(i);
      int smallest;

      if (l < list.size() && lt(list.get(l), list.get(i)))
        smallest = l;
      else
        smallest = i;

      if (r < list.size() && lt(list.get(r), list.get(smallest)))
        smallest = r;

      if (smallest != i) {
        swap(smallest, i);
        i = smallest;
      }
      else
        break;
    }
  }

  public E extract() {
    if (list.size() == 0) {
      throw new RuntimeException("Heap Underflow");
    }
    E top = list.get(0);
    int last = list.size() - 1;
    if (last != 0) {
      list.set(0, list.remove(last));
      heapify(0);
    }
    else {
      list.remove(last);
    }

    return top;
  }

  public E first() {
    if (list.size() == 0) {
      throw new RuntimeException("Heap Underflow");
    }
    return list.get(0);
  }

  public E last() {
    if (list.size() == 0) {
      throw new RuntimeException("Heap Underflow");
    }
    return max;
  }

  public void add(E o) {
    /* keep track of max to prevent unnecessary insertion */
    if (max == null) {
      max = o;
    }
    else if (gt(o, max)) {
      if (list.size() < size) {
        max = o;
      }
      else {
        return;
      }
    }
    list.add(o);

    int i = list.size() - 1;

    //percolate new node to correct position in heap.
    while (i > 0 && gt(list.get(parent(i)), o)) {
      list.set(i, list.get(parent(i)));
      i = parent(i);
    }

    list.set(i, o);
  }

  public void clear() {
    list.clear();
  }

  public Iterator<E> iterator() {
    return list.iterator();
  }

  public boolean isEmpty() {
    return this.list.isEmpty();
  }
}

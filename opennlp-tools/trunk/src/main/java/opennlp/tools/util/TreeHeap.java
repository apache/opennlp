/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An implementation of the Heap interface based on {@link java.util.SortedSet}.
 * This implementation will not allow multiple objects which are equal to be added to the heap.
 * Only use this implementation when object in the heap can be totally ordered (no duplicates).
 * 
 * @deprecated not used anymore, when there is need for a heap use ListHeap instead
 */
@Deprecated
public class TreeHeap<E> implements Heap<E> {

  private SortedSet<E> tree;

  /**
   * Creates a new tree heap.
   */
  public TreeHeap() {
    tree = new TreeSet<E>();
  }

  /**
   * Creates a new tree heap of the specified size.
   * @param size The size of the new tree heap.
   */
  public TreeHeap(int size) {
    tree = new TreeSet<E>();
  }

  public E extract() {
    E rv = tree.first();
    tree.remove(rv);
    return rv;
  }

  public E first() {
    return tree.first();
  }

  public E last() {
    return tree.last();
  }

  public Iterator<E> iterator() {
    return tree.iterator();
  }

  public void add(E o) {
    tree.add(o);
  }

  public int size() {
    return tree.size();
  }

  public void clear() {
    tree.clear();
  }

  public boolean isEmpty(){
    return this.tree.isEmpty();
  }

  public static void main(String[] args) {
    Heap<Integer> heap = new TreeHeap<Integer>(5);
    for (int ai=0;ai<args.length;ai++){
      heap.add(Integer.valueOf(Integer.parseInt(args[ai])));
    }
    while (!heap.isEmpty()) {
      System.out.print(heap.extract()+" ");
    }
    System.out.println();
   }
}

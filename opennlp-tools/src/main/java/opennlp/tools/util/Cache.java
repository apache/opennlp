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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides fixed size, pre-allocated, least recently used replacement cache.
 */
@SuppressWarnings("unchecked")
public class Cache implements Map {

  /** The element in the linked list which was most recently used. **/
  private DoubleLinkedListElement first;
  /** The element in the linked list which was least recently used. **/
  private DoubleLinkedListElement last;
  /** Temporary holder of the key of the least-recently-used element. */
  private Object lastKey;
  /** Temporary value used in swap. */
  private ObjectWrapper temp;
  /** Holds the object wrappers which the keys are mapped to. */
  private ObjectWrapper[] wrappers;
  /** Map which stores the keys and values of the cache. */
  private Map map;
  /** The size of the cache. */
  private int size;

  /**
   * Creates a new cache of the specified size.
   * @param size The size of the cache.
   */
  public Cache(int size) {
    map = new HashMap(size);
    wrappers = new ObjectWrapper[size];
    this.size=size;
    Object o = new Object();
    first = new DoubleLinkedListElement(null, null, o);
    map.put(o, new ObjectWrapper(null, first));
    wrappers[0] = new ObjectWrapper(null, first);

    DoubleLinkedListElement e = first;
    for(int i=1; i<size; i++) {
      o = new Object();
      e = new DoubleLinkedListElement(e, null, o);
      wrappers[i] = new ObjectWrapper(null, e);
      map.put(o, wrappers[i]);
      e.prev.next = e;
    }
    last = e;
  }

  public void clear() {
    map.clear();
    DoubleLinkedListElement e = first;
    for (int oi=0;oi<size;oi++) {
      wrappers[oi].object=null;
      Object o = new Object();
      map.put(o,wrappers[oi]);
      e.object = o;
      e = e.next;
    }
  }

  public Object put(Object key, Object value) {
    ObjectWrapper o = (ObjectWrapper) map.get(key);
    if (o != null) {
      /*
       * this should never be the case, we only do a put on a cache miss which
       * means the current value wasn't in the cache. However if the user screws
       * up or wants to use this as a fixed size hash and puts the same thing in
       * the list twice then we update the value and more the key to the front of the
       * most recently used list.
       */

      // Move o's partner in the list to front
      DoubleLinkedListElement e = o.listItem;

      //move to front
      if (e != first) {
        //remove list item
        e.prev.next = e.next;
        if (e.next != null) {
          e.next.prev = e.prev;
        }
        else { //were moving last
          last = e.prev;
        }

        //put list item in front
        e.next = first;
        first.prev = e;
        e.prev = null;

        //update first
        first = e;
      }
      return o.object;
    }
    // Put o in the front and remove the last one
    lastKey = last.object; // store key to remove from hash later
    last.object = key; //update list element with new key

    // connect list item to front of list
    last.next = first;
    first.prev = last;

    // update first and last value
    first = last;
    last = last.prev;
    first.prev = null;
    last.next = null;

    // remove old value from cache
    temp = (ObjectWrapper) map.remove(lastKey);
    //update wrapper
    temp.object = value;
    temp.listItem = first;

    map.put(key, temp);
    return null;
  }

  public Object get(Object key) {
    ObjectWrapper o = (ObjectWrapper) map.get(key);
    if (o != null) {
      // Move it to the front
      DoubleLinkedListElement e = o.listItem;

      //move to front
      if (e != first) {
        //remove list item
        e.prev.next = e.next;
        if (e.next != null) {
          e.next.prev = e.prev;
        }
        else { //were moving last
          last = e.prev;
        }
        //put list item in front
        e.next = first;
        first.prev = e;
        e.prev = null;

        //update first
        first = e;
      }
      return o.object;
    }
    else {
      return null;
    }
  }


  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  public Set entrySet() {
    return map.entrySet();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public Set keySet() {
    return map.keySet();
  }

  public void putAll(Map t) {
    map.putAll(t);
  }

  public Object remove(Object key) {
    return map.remove(key);
  }

  public int size() {
    return map.size();
  }

  public Collection values() {
    return map.values();
  }
}

class ObjectWrapper {

  public Object object;
  public DoubleLinkedListElement listItem;

  public ObjectWrapper(Object o,DoubleLinkedListElement li) {
    object = o;
    listItem = li;
  }

  public Object getObject() {
    return object;
  }

  public DoubleLinkedListElement getListItem() {
    return listItem;
  }

  public void setObject(Object o) {
    object = o;
  }

  public void setListItem(DoubleLinkedListElement li) {
    listItem = li;
  }

  public boolean eqauls(Object o) {
    return object.equals(o);
  }
}

class DoubleLinkedListElement {

  public DoubleLinkedListElement prev;
  public DoubleLinkedListElement next;
  public Object object;

  public DoubleLinkedListElement(DoubleLinkedListElement p,
				   DoubleLinkedListElement n,
				   Object o) {
	prev = p;
	next = n;
	object = o;

	if (p != null) {
	    p.next = this;
	}

	if (n != null) {
	    n.prev = this;
	}
  }
}

class DoubleLinkedList {

  DoubleLinkedListElement first;
  DoubleLinkedListElement last;
  DoubleLinkedListElement current;

  public DoubleLinkedList() {
	first = null;
	last = null;
	current = null;
  }

  public void addFirst(Object o) {
	first = new DoubleLinkedListElement(null, first, o);

	if (current.next == null) {
	    last = current;
	}
  }

  public void addLast(Object o) {
	last = new DoubleLinkedListElement(last, null, o);

	if (current.prev == null) {
	    first = current;
	}
  }

  public void insert(Object o) {
	if (current == null) {
	    current = new DoubleLinkedListElement(null, null, o);
	}
	else {
	    current = new DoubleLinkedListElement(current.prev, current, o);
	}

	if (current.prev == null) {
	    first = current;
	}

	if (current.next == null) {
	    last = current;
	}
  }

  public DoubleLinkedListElement getFirst() {
	current = first;
	return first;
  }

  public DoubleLinkedListElement getLast() {
	current = last;
	return last;
  }

  public DoubleLinkedListElement getCurrent() {
	return current;
  }

  public DoubleLinkedListElement next() {
	if (current.next != null) {
	    current = current.next;
	}
	return current;
  }

  public DoubleLinkedListElement prev() {
	if (current.prev != null) {
	    current = current.prev;
	}
	return current;
  }

  @Override
  public String toString() {
	DoubleLinkedListElement e = first;
	String s = "[" + e.object.toString();

	e = e.next;

	while (e != null) {
	    s = s + ", " + e.object.toString();
	    e = e.next;
	}

	s = s +	"]";

	return s;
  }
}

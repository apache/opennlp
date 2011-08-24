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

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Set which counts the number of times a values are added to it.
 * This value can be accessed with the #getCount method.
 */
public class CountedSet<E> implements Set<E> {

  private Map<E, Integer> cset;

  /**
   * Creates a new counted set.
   */
  public CountedSet() {
    cset = new HashMap<E, Integer>();
  }

  /**
   * Creates a new counted set of the specified initial size.
   *
   * @param size The initial size of this set.
   */
  public CountedSet(int size) {
    cset = new HashMap<E, Integer>(size);

  }

  public boolean add(E o) {
    Integer count = cset.get(o);
    if ( count == null ) {
      cset.put(o, 1);
      return true;
    }
    else {
      cset.put(o, Integer.valueOf(count.intValue()+1));
      return false;
    }
  }

  /**
   * Reduces the count associated with this object by 1.  If this causes the count
   * to become 0, then the object is removed form the set.
   *
   * @param o The object whose count is being reduced.
   */
  public void subtract(E o) {
    Integer count = cset.get(o);
    if ( count != null ) {
      int c = count.intValue()-1;
      if (c == 0) {
        cset.remove(o);
      }
      else {
        cset.put(o, Integer.valueOf(c));
      }
    }
  }

  /**
   * Assigns the specified object the specified count in the set.
   *
   * @param o The object to be added or updated in the set.
   * @param c The count of the specified object.
   */
  public void setCount(E o, int c) {
    cset.put(o, Integer.valueOf(c));
  }

  /**
   * Return the count of the specified object.
   *
   * @param o the object whose count needs to be determined.
   * @return the count of the specified object.
   */
  public int getCount(E o) {
    Integer count = cset.get(o);
    if ( count == null ) {
      return 0;
    }
    else {
      return count.intValue();
    }
  }

  /**
   * This methods is deprecated use opennlp.toolsdictionary.serialization
   * package for writing a {@link CountedSet}.
   *
   * @param fileName
   * @param countCutoff
   */
  @Deprecated
  public void write(String fileName,int countCutoff) {
    write(fileName,countCutoff," ");
  }

  /**
   * This methods is deprecated use opennlp.toolsdictionary.serialization
   * package for writing a {@link CountedSet}.
   *
   * @param fileName
   * @param countCutoff
   * @param delim
   */
  @Deprecated
  public void write(String fileName,int countCutoff,String delim) {
    write(fileName,countCutoff,delim,null);
  }

  /**
   * This methods is deprecated use opennlp.toolsdictionary.serialization
   * package for writing a {@link CountedSet}.
   *
   * @param fileName
   * @param countCutoff
   * @param delim
   * @param encoding
   */
  @Deprecated
  public void write(String fileName,int countCutoff,String delim,String encoding) {
    PrintWriter out = null;
    try{
      if (encoding != null) {
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileName),encoding));
      }
      else {
        out = new PrintWriter(new FileWriter(fileName));
      }

      for (Iterator<E> e = cset.keySet().iterator();  e.hasNext();) {
        E key = e.next();
        int count = this.getCount(key);
        if ( count >= countCutoff ) {
          out.println(count + delim + key);
        }
      }
      out.close();
    }
    catch (IOException e) {
      System.err.println(e);
    }
  }

  public boolean addAll(Collection<? extends E> c) {
    boolean changed =  false;

    for (E element : c) {
      changed = changed || add(element);
    }

    return changed;
  }

  public void clear() {
    cset.clear();
  }

  public boolean contains(Object o) {
    return cset.keySet().contains(o);
  }

  public boolean containsAll(Collection<?> c) {
    return cset.keySet().containsAll(c);
  }

  public boolean isEmpty() {
    return cset.isEmpty();
  }

  public Iterator<E> iterator() {
    return cset.keySet().iterator();
  }

  public boolean remove(Object o) {
    return cset.remove(o) != null;
  }

  public boolean removeAll(Collection<?> c) {
    boolean changed =false;
    for (Iterator<E> ki = cset.keySet().iterator(); ki.hasNext();) {
      changed = changed || cset.remove(ki.next()) != null;
    }
    return changed;
  }

  public boolean retainAll(Collection<?> c) {
    boolean changed = false;
    for (Iterator<E> ki = cset.keySet().iterator();ki.hasNext();) {
      Object key = ki.next();
      if (!c.contains(key)) {
        cset.remove(key);
        changed = true;
      }
    }
    return changed;
  }

  public int size() {
    return cset.size();
  }

  public Object[] toArray() {
    return cset.keySet().toArray();
  }

  public <T> T[] toArray(T[] a) {
    return cset.keySet().toArray(a);
  }
}

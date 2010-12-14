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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/** 
 * Class which creates mapping between keys and a list of values.  
 */
public class HashList<K, V> extends HashMap<K, List<V>> {

  private static final long serialVersionUID = 1;
  
  public HashList() {
  }
  
  public V get(K key, int index) {
    if (get(key) != null) {
      return get(key).get(index);
    }
    else {
      return null;
    }
  }

  public Object putAll(K key, Collection<V> values) {
    List<V> o = get(key);

    if (o == null) {
      o = new ArrayList<V>();
      super.put(key, o);
    }

    o.addAll(values);

    if (o.size() == values.size())
      return null;
    else
      return o;
  }

  public List<V> put(K key, V value) {
    List<V> o = get(key);
    
    if (o == null) {
      o = new ArrayList<V>();
      super.put(key, o);
    } 

    o.add(value);

    if(o.size() == 1)
      return null;
    else
      return o;
  }

  public boolean remove(K key, V value) {
    List<V> l = get(key);
    if (l == null) {
      return false;
    }
    else {
      boolean r = l.remove(value);
      if (l.size() == 0) {
	remove(key);
      }
      return r;
    }
  }
}
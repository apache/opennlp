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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Class which creates mapping between keys and a list of values.
 */
@SuppressWarnings("unchecked")
public class HashList extends HashMap {

  private static final long serialVersionUID = 1;

  public HashList() {
  }

  public Object get(Object key, int index) {
    if (get(key) != null) {
      return ((List) get(key)).get(index);
    }
    else {
      return null;
    }
  }

  public Object putAll(Object key, Collection values) {
    List o = (List) get(key);

    if (o == null) {
      o = new ArrayList();
      super.put(key, o);
    }

    o.addAll(values);

    if (o.size() == values.size())
      return null;
    else
      return o;
  }

  @Override
  public List put(Object key, Object value) {
    List o = (List) get(key);

    if (o == null) {
      o = new ArrayList();
      super.put(key, o);
    }

    o.add(value);

    if (o.size() == 1)
      return null;
    else
      return o;
  }

  public boolean remove(Object key, Object value) {
    List l = (List) get(key);
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

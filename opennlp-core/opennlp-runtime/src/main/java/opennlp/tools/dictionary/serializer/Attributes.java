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

package opennlp.tools.dictionary.serializer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * The {@link Attributes} class stores name value pairs.
 * <p>
 * Problem: If a {@link HashMap} is used storing name-value pairs this results
 * in a very high memory footprint, replace it.
 */
public class Attributes {

  private final Map<String, String> mNameValueMap = new HashMap<>();

  /**
   * Retrieves the value for the given key or {@code null} if attribute it not set.
   *
   * @param key The key to get the desired value for.
   *
   * @return The value for the given key or {@code null}
   */
  public  String getValue(String key) {
    return mNameValueMap.get(key);
  }

  /**
   * Sets a key/value pair.
   *
   * @param key The key that uniquely identifies the specified {@code value}.
   *            Must not be {@code null}.
   * @param value The value that shall be retrievable via its {@code key}.
   *              Must not be {@code null}.
   */
  public void setValue(String key, String value) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(value, "value must not be null");

    mNameValueMap.put(key, value);
  }

  /**
   * Iterates over the keys.
   *
   * @return Retrieves a key-based {@link Iterator}.
   */
  public Iterator<String> iterator() {
    return mNameValueMap.keySet().iterator();
  }
}

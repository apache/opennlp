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

package opennlp.uima.normalizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.dictionary.serializer.DictionaryEntryPersistor;
import opennlp.tools.dictionary.serializer.Entry;
import opennlp.tools.util.StringList;

public class StringDictionary {

  private final Map<StringList, String> entries = new HashMap<>();

  public StringDictionary() {
  }

  /**
   * Initializes {@link StringDictionary} via a specified {@link InputStream}.
   *
   * @param in A valid, open {@link InputStream} to initialize with.
   * @throws IOException Thrown if IO errors occurred.
   */
  public StringDictionary(InputStream in) throws IOException {
    DictionaryEntryPersistor.create(in, entry -> {
      String valueString = entry.attributes().getValue("value");
      put(entry.tokens(), valueString);
    });
  }

  /**
   * Retrieves a value from a dictionary via its {@code key}.
   * 
   * @param key The {@link StringList key} to get value with.
   *            
   * @return Retrieves a corresponding String value or {@code null} if not found.
   */
  public String get(StringList key) {
    return entries.get(key);
  }

  /**
   * Adds a new entry to the dictionary.
   *
   * @param key The {@link StringList key} under which to put the {@code value}.
   * @param value The value to put.
   */
  public void put(StringList key, String value) {
    entries.put(key, value);
  }

  Iterator<StringList> iterator() {
    return entries.keySet().iterator();
  }

  /**
   * Writes the dictionary to the given {@link OutputStream}.
   *
   * @param out A valid, open {@link OutputStream} to serialize to.
   *            
   * @throws IOException Thrown if IO errors occurred during serialization.
   */
  public void serialize(OutputStream out) throws IOException {
    Iterator<Entry> entryIterator = new Iterator<>() {
      private final Iterator<StringList> mDictionaryIterator = StringDictionary.this.iterator();

      @Override
      public boolean hasNext() {
        return mDictionaryIterator.hasNext();
      }

      @Override
      public Entry next() {

        StringList tokens = mDictionaryIterator.next();

        Attributes attributes = new Attributes();

        attributes.setValue("value", get(tokens));

        return new Entry(tokens, attributes);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };

    DictionaryEntryPersistor.serialize(out, entryIterator, true);
  }
}

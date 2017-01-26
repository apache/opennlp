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

// lookup a string for given token list
public class StringDictionary {

  private Map<StringList, String> entries = new HashMap<>();

  public StringDictionary() {
  }

  /**
   * Initializes the current instance.
   *
   * @param in
   * @throws IOException
   */
  public StringDictionary(InputStream in) throws IOException {
    DictionaryEntryPersistor.create(in, entry -> {
      String valueString = entry.getAttributes().getValue("value");
      put(entry.getTokens(), valueString);
    });
  }

  public String get(StringList key) {
    return entries.get(key);
  }

  public void put(StringList key, String value) {
    entries.put(key, value);
  }

  Iterator<StringList> iterator() {
    return entries.keySet().iterator();
  }

  /**
   * Writes the ngram instance to the given {@link OutputStream}.
   *
   * @param out
   * @throws IOException
   *           if an I/O Error during writing occures
   */
  public void serialize(OutputStream out) throws IOException {
    Iterator<Entry> entryIterator = new Iterator<Entry>() {
      private Iterator<StringList> mDictionaryIterator = StringDictionary.this.iterator();

      public boolean hasNext() {
        return mDictionaryIterator.hasNext();
      }

      public Entry next() {

        StringList tokens = mDictionaryIterator.next();

        Attributes attributes = new Attributes();

        attributes.setValue("value", get(tokens));

        return new Entry(tokens, attributes);
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };

    DictionaryEntryPersistor.serialize(out, entryIterator, true);
  }
}
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

package opennlp.tools.postag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.dictionary.serializer.DictionaryEntryPersistor;
import opennlp.tools.dictionary.serializer.Entry;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;

/**
 * Provides a means of determining which tags are valid for a particular word
 * based on a tag dictionary read from a file.
 */
public class POSDictionary implements Iterable<String>, MutableTagDictionary {

  private Map<String, String[]> dictionary;

  private boolean caseSensitive = true;

  /**
   * Initializes an empty case sensitive {@link POSDictionary}.
   */
  public POSDictionary() {
    this(true);
  }

  /**
   * Initializes an empty {@link POSDictionary}.
   * @param caseSensitive the {@link POSDictionary} case sensitivity
   */
  public POSDictionary(boolean caseSensitive) {
    dictionary = new HashMap<>();
    this.caseSensitive = caseSensitive;
  }

  /**
   * Returns a list of valid tags for the specified word.
   *
   * @param word The word.
   *
   * @return A list of valid tags for the specified word or
   *     null if no information is available for that word.
   */
  public String[] getTags(String word) {
    if (caseSensitive) {
      return dictionary.get(word);
    }
    else {
      return dictionary.get(StringUtil.toLowerCase(word));
    }
  }

  /**
   * Associates the specified tags with the specified word. If the dictionary
   * previously contained the word, the old tags are replaced by the specified
   * ones.
   *
   * @param word
   *          The word to be added to the dictionary.
   * @param tags
   *          The set of tags associated with the specified word.
   *
   * @deprecated Use {@link #put(String, String[])} instead
   */
  void addTags(String word, String... tags) {
    put(word, tags);
  }

  /**
   * Retrieves an iterator over all words in the dictionary.
   */
  public Iterator<String> iterator() {
    return dictionary.keySet().iterator();
  }

  private static String tagsToString(String tags[]) {

    StringBuilder tagString = new StringBuilder();

    for (String tag : tags) {
      tagString.append(tag);
      tagString.append(' ');
    }

    // remove last space
    if (tagString.length() > 0) {
      tagString.setLength(tagString.length() - 1);
    }

    return tagString.toString();
  }

  /**
   * Writes the {@link POSDictionary} to the given {@link OutputStream};
   *
   * After the serialization is finished the provided
   * {@link OutputStream} remains open.
   *
   * @param out
   *            the {@link OutputStream} to write the dictionary into.
   *
   * @throws IOException
   *             if writing to the {@link OutputStream} fails
   */
  public void serialize(OutputStream out) throws IOException {
    Iterator<Entry> entries = new Iterator<Entry>() {

      Iterator<String> iterator = dictionary.keySet().iterator();

      public boolean hasNext() {
        return iterator.hasNext();
      }

      public Entry next() {

        String word = iterator.next();

        Attributes tagAttribute = new Attributes();
        tagAttribute.setValue("tags", tagsToString(getTags(word)));

        return new Entry(new StringList(word), tagAttribute);
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };

    DictionaryEntryPersistor.serialize(out, entries, caseSensitive);
  }

  @Override
  public int hashCode() {

    int[] keyHashes = new int[dictionary.size()];
    int[] valueHashes = new int[dictionary.size()];

    int i = 0;

    for (String word : this) {
      keyHashes[i] = word.hashCode();
      valueHashes[i] = Arrays.hashCode(getTags(word));
      i++;
    }

    Arrays.sort(keyHashes);
    Arrays.sort(valueHashes);

    return Objects.hash(Arrays.hashCode(keyHashes), Arrays.hashCode(valueHashes));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof POSDictionary) {
      POSDictionary posDictionary = (POSDictionary) obj;

      if (this.dictionary.size() == posDictionary.dictionary.size()) {
        for (String word : this) {
          if (!Arrays.equals(getTags(word), posDictionary.getTags(word))) {
            return false;
          }
        }

        return true;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    // it is time consuming to output the dictionary entries.
    // will output something meaningful for debugging, like
    // POSDictionary{size=100, caseSensitive=true}

    return "POSDictionary{size=" + dictionary.size() + ", caseSensitive="
        + this.caseSensitive + "}";
  }

  /**
   * Creates a new {@link POSDictionary} from a provided {@link InputStream}.
   *
   * After creation is finished the provided {@link InputStream} is closed.
   *
   * @param in
   *
   * @return the pos dictionary
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  public static POSDictionary create(InputStream in) throws IOException {

    final POSDictionary newPosDict = new POSDictionary();

    boolean isCaseSensitive = DictionaryEntryPersistor.create(in, entry -> {

      String tagString = entry.getAttributes().getValue("tags");

      String[] tags = tagString.split(" ");

      StringList word = entry.getTokens();

      if (word.size() != 1)
        throw new InvalidFormatException("Each entry must have exactly one token! " + word);

      newPosDict.dictionary.put(word.getToken(0), tags);
    });

    newPosDict.caseSensitive = isCaseSensitive;

    // TODO: The dictionary API needs to be improved to do this better!
    if (!isCaseSensitive) {
      Map<String, String[]> lowerCasedDictionary = new HashMap<>();

      for (Map.Entry<String, String[]> entry : newPosDict.dictionary.entrySet()) {
        lowerCasedDictionary.put(StringUtil.toLowerCase(entry.getKey()), entry.getValue());
      }

      newPosDict.dictionary = lowerCasedDictionary;
    }

    return newPosDict;
  }

  public String[] put(String word, String... tags) {
    if (this.caseSensitive) {
      return dictionary.put(word, tags);
    } else {
      return dictionary.put(StringUtil.toLowerCase(word), tags);
    }
  }

  public boolean isCaseSensitive() {
    return this.caseSensitive;
  }
}

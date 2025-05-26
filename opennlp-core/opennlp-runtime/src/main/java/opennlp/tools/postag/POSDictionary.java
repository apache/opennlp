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
import opennlp.tools.util.model.SerializableArtifact;

/**
 * Provides a means of determining which tags are valid for a particular word
 * based on a {@link TagDictionary} read from a file.
 */
public class POSDictionary implements Iterable<String>, MutableTagDictionary, SerializableArtifact {

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
   * 
   * @param caseSensitive {@code true} if the {@link POSDictionary} is case sensitive,
   *                      {@code false} otherwise.
   */
  public POSDictionary(boolean caseSensitive) {
    dictionary = new HashMap<>();
    this.caseSensitive = caseSensitive;
  }

  /**
   * Returns a list of valid tags for the specified {@code word}.
   *
   * @param word The word.
   *
   * @return An array of valid tags for the specified word or
   *         {@code null} if no information is available for that word.
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
   * Retrieves an {@link Iterator} over all words in the dictionary.
   */
  public Iterator<String> iterator() {
    return dictionary.keySet().iterator();
  }

  private static String tagsToString(String[] tags) {

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
   * <p>
   * After the serialization is finished the provided
   * {@link OutputStream} remains open.
   *
   * @param out
   *            the {@link OutputStream} to write the dictionary into.
   *
   * @throws IOException
   *             Throw if writing to the {@link OutputStream} fails
   */
  public void serialize(OutputStream out) throws IOException {
    Iterator<Entry> entries = new Iterator<>() {

      final Iterator<String> iterator = dictionary.keySet().iterator();

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

    if (obj instanceof POSDictionary posDictionary) {

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
    // it is time-consuming to output the dictionary entries.
    // will output something meaningful for debugging, like
    // POSDictionary{size=100, caseSensitive=true}

    return "POSDictionary{size=" + dictionary.size() + ", caseSensitive="
        + this.caseSensitive + "}";
  }

  /**
   * Creates a new {@link POSDictionary} from an {@link InputStream}.
   * <p>
   * After creation is finished the provided {@link InputStream} is closed.
   *
   * @param in The {@link InputStream} used for creating the {@link POSDictionary}.
   *           The stream must be open and have bytes available to read from.
   *
   * @return A valid {@link POSDictionary} instance.
   *
   * @throws IOException Thrown if IO errors occurred during creation.
   * @throws InvalidFormatException Thrown if the entries don't have exactly one token.
   */
  public static POSDictionary create(InputStream in) throws IOException {

    final POSDictionary newPosDict = new POSDictionary();

    boolean isCaseSensitive = DictionaryEntryPersistor.create(in, entry -> {

      String tagString = entry.attributes().getValue("tags");
      String[] tags = tagString.split(" ");
      StringList word = entry.tokens();

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

  @Override
  public String[] put(String word, String... tags) {
    if (this.caseSensitive) {
      return dictionary.put(word, tags);
    } else {
      return dictionary.put(StringUtil.toLowerCase(word), tags);
    }
  }

  @Override
  public boolean isCaseSensitive() {
    return this.caseSensitive;
  }

  @Override
  public Class<?> getArtifactSerializerClass() {
    return POSTaggerFactory.POSDictionarySerializer.class;
  }
}

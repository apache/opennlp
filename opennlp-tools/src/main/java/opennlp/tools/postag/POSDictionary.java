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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.dictionary.serializer.DictionarySerializer;
import opennlp.tools.dictionary.serializer.Entry;
import opennlp.tools.dictionary.serializer.EntryInserter;
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
    dictionary = new HashMap<String, String[]>();
    this.caseSensitive = caseSensitive;
  }

  /**
   * Creates a tag dictionary with contents of specified file.
   *
   * @param file The file name for the tag dictionary.
   *
   * @throws IOException when the specified file can not be read.
   * 
   * @deprecated Use {@link POSDictionary#create(InputStream)} instead, old format might removed.
   */
  @Deprecated
  public POSDictionary(String file) throws IOException {
    this(file, null, true);
  }

  /**
   * Creates a tag dictionary with contents of specified file and using specified
   * case to determine how to access entries in the tag dictionary.
   *
   * @param file The file name for the tag dictionary.
   * @param caseSensitive Specifies whether the tag dictionary is case sensitive or not.
   *
   * @throws IOException when the specified file can not be read.
   * 
   * @deprecated Use {@link POSDictionary#create(InputStream)} instead, old format might removed.
   */
  @Deprecated
  public POSDictionary(String file, boolean caseSensitive) throws IOException {
    this(file, null, caseSensitive);
  }


  /**
   * Creates a tag dictionary with contents of specified file and using specified case to determine how to access entries in the tag dictionary.
   *
   * @param file The file name for the tag dictionary.
   * @param encoding The encoding of the tag dictionary file.
   * @param caseSensitive Specifies whether the tag dictionary is case sensitive or not.
   *
   * @throws IOException when the specified file can not be read.
   * 
   * @deprecated Use {@link POSDictionary#create(InputStream)} instead, old format might removed.
   */
  @Deprecated
  public POSDictionary(String file, String encoding, boolean caseSensitive) throws IOException {
    this(new BufferedReader(encoding == null ? new FileReader(file) : new InputStreamReader(new FileInputStream(file),encoding)), caseSensitive);
  }

  /**
   * Create tag dictionary object with contents of specified file and using specified case to determine how to access entries in the tag dictionary.
   *
   * @param reader A reader for the tag dictionary.
   * @param caseSensitive Specifies whether the tag dictionary is case sensitive or not.
   *
   * @throws IOException when the specified file can not be read.
   * 
   * @deprecated Use {@link POSDictionary#create(InputStream)} instead, old format might removed.
   */
  @Deprecated
  public POSDictionary(BufferedReader reader, boolean caseSensitive) throws IOException {
    dictionary = new HashMap<String, String[]>();
    this.caseSensitive = caseSensitive;
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      String[] parts = line.split(" ");
      String[] tags = new String[parts.length - 1];
      for (int ti = 0, tl = parts.length - 1; ti < tl; ti++) {
        tags[ti] = parts[ti + 1];
      }
      if (caseSensitive) {
        dictionary.put(parts[0], tags);
      }
      else {
        dictionary.put(StringUtil.toLowerCase(parts[0]), tags);
      }
    }
  }

  /**
   * Returns a list of valid tags for the specified word.
   *
   * @param word The word.
   *
   * @return A list of valid tags for the specified word or
   * null if no information is available for that word.
   */
  public String[] getTags(String word) {
    if (caseSensitive) {
      return dictionary.get(word);
    }
    else {
      return dictionary.get(word.toLowerCase());
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

    DictionarySerializer.serialize(out, entries, caseSensitive);
  }

  @Override
  public boolean equals(Object o) {

    if (o == this) {
      return true;
    }
    else if (o instanceof POSDictionary) {
      POSDictionary dictionary = (POSDictionary) o;

      if (this.dictionary.size() == dictionary.dictionary.size()) {

        for (String word : this) {

          String aTags[] = getTags(word);
          String bTags[] = dictionary.getTags(word);

          if (!Arrays.equals(aTags, bTags)) {
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
  public static POSDictionary create(InputStream in) throws IOException, InvalidFormatException {

    final POSDictionary newPosDict = new POSDictionary();

    boolean isCaseSensitive = DictionarySerializer.create(in, new EntryInserter() {
      public void insert(Entry entry) throws InvalidFormatException {

        String tagString = entry.getAttributes().getValue("tags");

        String[] tags = tagString.split(" ");

        StringList word = entry.getTokens();

        if (word.size() != 1)
          throw new InvalidFormatException("Each entry must have exactly one token! "+word);

        newPosDict.dictionary.put(word.getToken(0), tags);
      }});

    newPosDict.caseSensitive = isCaseSensitive;
    
    // TODO: The dictionary API needs to be improved to do this better!
    if (!isCaseSensitive) {
      Map<String, String[]> lowerCasedDictionary = new HashMap<String, String[]>();
      
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
      return dictionary.put(word.toLowerCase(), tags);
    }
  }

  public boolean isCaseSensitive() {
    return this.caseSensitive;
  }
}

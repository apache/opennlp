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

package opennlp.tools.ngram;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.dictionary.serializer.DictionaryEntryPersistor;
import opennlp.tools.dictionary.serializer.Entry;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;

/**
 * The {@link NGramModel} can be used to crate ngrams and character ngrams.
 *
 * @see StringList
 */
public class NGramModel implements Iterable<StringList> {

  protected static final String COUNT = "count";

  private Map<StringList, Integer> mNGrams = new HashMap<>();

  /**
   * Initializes an empty instance.
   */
  public NGramModel() {
  }

  /**
   * Initializes the current instance.
   *
   * @param in the serialized model stream
   * @throws IOException
   */
  public NGramModel(InputStream in) throws IOException {
    DictionaryEntryPersistor.create(in, entry -> {

      int count;
      String countValueString = null;

      try {
        countValueString = entry.getAttributes().getValue(COUNT);

        if (countValueString == null) {
          throw new InvalidFormatException(
              "The count attribute must be set!");
        }

        count = Integer.parseInt(countValueString);
      } catch (NumberFormatException e) {
        throw new InvalidFormatException("The count attribute '" + countValueString
            + "' must be a number!", e);
      }

      add(entry.getTokens());
      setCount(entry.getTokens(), count);
    });
  }

  /**
   * Retrieves the count of the given ngram.
   *
   * @param ngram an ngram
   * @return count of the ngram or 0 if it is not contained
   *
   */
  public int getCount(StringList ngram) {

    Integer count = mNGrams.get(ngram);

    if (count == null) {
      return 0;
    }

    return count;
  }

  /**
   * Sets the count of an existing ngram.
   *
   * @param ngram
   * @param count
   */
  public void setCount(StringList ngram, int count) {

    Integer oldCount = mNGrams.put(ngram, count);

    if (oldCount == null) {
      mNGrams.remove(ngram);
      throw new NoSuchElementException();
    }
  }

  /**
   * Adds one NGram, if it already exists the count increase by one.
   *
   * @param ngram
   */
  public void add(StringList ngram) {
    if (contains(ngram)) {
      setCount(ngram, getCount(ngram) + 1);
    } else {
      mNGrams.put(ngram, 1);
    }
  }

  /**
   * Adds NGrams up to the specified length to the current instance.
   *
   * @param ngram the tokens to build the uni-grams, bi-grams, tri-grams, ..
   *     from.
   * @param minLength - minimal length
   * @param maxLength - maximal length
   */
  public void add(StringList ngram, int minLength, int maxLength) {

    if (minLength < 1 || maxLength < 1)
      throw new IllegalArgumentException("minLength and maxLength param must be at least 1. " +
          "minLength=" + minLength + ", maxLength= " + maxLength);

    if (minLength > maxLength)
      throw new IllegalArgumentException("minLength param must not be larger than " +
          "maxLength param. minLength=" + minLength + ", maxLength= " + maxLength);

    for (int lengthIndex = minLength; lengthIndex < maxLength + 1; lengthIndex++) {
      for (int textIndex = 0;
          textIndex + lengthIndex - 1 < ngram.size(); textIndex++) {

        String[] grams = new String[lengthIndex];

        for (int i = textIndex; i < textIndex + lengthIndex; i++) {
          grams[i - textIndex] = ngram.getToken(i);
        }

        add(new StringList(grams));
      }
    }
  }

  /**
   * Adds character NGrams to the current instance.
   *
   * @param chars
   * @param minLength
   * @param maxLength
   */
  public void add(String chars, int minLength, int maxLength) {

    for (int lengthIndex = minLength; lengthIndex < maxLength + 1; lengthIndex++) {
      for (int textIndex = 0;
          textIndex + lengthIndex - 1 < chars.length(); textIndex++) {

        String gram = StringUtil.toLowerCase(
            chars.substring(textIndex, textIndex + lengthIndex));

        add(new StringList(new String[]{gram}));
      }
    }
  }

  /**
   * Removes the specified tokens form the NGram model, they are just dropped.
   *
   * @param tokens
   */
  public void remove(StringList tokens) {
    mNGrams.remove(tokens);
  }

  /**
   * Checks fit he given tokens are contained by the current instance.
   *
   * @param tokens
   *
   * @return true if the ngram is contained
   */
  public boolean contains(StringList tokens) {
    return mNGrams.containsKey(tokens);
  }

  /**
   * Retrieves the number of {@link StringList} entries in the current instance.
   *
   * @return number of different grams
   */
  public int size() {
    return mNGrams.size();
  }

  /**
   * Retrieves an {@link Iterator} over all {@link StringList} entries.
   *
   * @return iterator over all grams
   */
  @Override
  public Iterator<StringList> iterator() {
    return mNGrams.keySet().iterator();
  }

  /**
   * Retrieves the total count of all Ngrams.
   *
   * @return total count of all ngrams
   */
  public int numberOfGrams() {
    int counter = 0;

    for (StringList ngram : this) {
      counter += getCount(ngram);
    }

    return counter;
  }

  /**
   * Deletes all ngram which do appear less than the cutoffUnder value
   * and more often than the cutoffOver value.
   *
   * @param cutoffUnder
   * @param cutoffOver
   */
  public void cutoff(int cutoffUnder, int cutoffOver) {

    if (cutoffUnder > 0 || cutoffOver < Integer.MAX_VALUE) {

      for (Iterator<StringList> it = iterator(); it.hasNext(); ) {

        StringList ngram = it.next();

        int count = getCount(ngram);

        if (count < cutoffUnder ||
            count > cutoffOver) {
          it.remove();
        }
      }
    }
  }

  /**
   * Creates a dictionary which contain all {@link StringList} which
   * are in the current {@link NGramModel}.
   *
   * Entries which are only different in the case are merged into one.
   *
   * Calling this method is the same as calling {@link #toDictionary(boolean)} with true.
   *
   * @return a dictionary of the ngrams
   */
  public Dictionary toDictionary() {
    return toDictionary(false);
  }

  /**
   * Creates a dictionary which contains all {@link StringList}s which
   * are in the current {@link NGramModel}.
   *
   * @param caseSensitive Specifies whether case distinctions should be kept
   *                      in the creation of the dictionary.
   *
   * @return a dictionary of the ngrams
   */
  public Dictionary toDictionary(boolean caseSensitive) {

    Dictionary dict = new Dictionary(caseSensitive);

    for (StringList stringList : this) {
      dict.put(stringList);
    }

    return dict;
  }

  /**
   * Writes the ngram instance to the given {@link OutputStream}.
   *
   * @param out
   *
   * @throws IOException if an I/O Error during writing occurs
   */
  public void serialize(OutputStream out) throws IOException {
    Iterator<Entry> entryIterator = new Iterator<Entry>()
    {
      private Iterator<StringList> mDictionaryIterator = NGramModel.this.iterator();

      @Override
      public boolean hasNext() {
        return mDictionaryIterator.hasNext();
      }

      @Override
      public Entry next() {

        StringList tokens = mDictionaryIterator.next();

        Attributes attributes = new Attributes();

        attributes.setValue(COUNT, Integer.toString(getCount(tokens)));
        
        return new Entry(tokens, attributes);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

    };

    DictionaryEntryPersistor.serialize(out, entryIterator, false);
  }

  @Override
  public boolean equals(Object obj) {
    boolean result;

    if (obj == this) {
      result = true;
    }
    else if (obj instanceof NGramModel) {
      NGramModel model  = (NGramModel) obj;

      result = mNGrams.equals(model.mNGrams);
    }
    else {
      result = false;
    }

    return result;
  }

  @Override
  public String toString() {
    return "Size: " + size();
  }

  @Override
  public int hashCode() {
    return mNGrams.hashCode();
  }
}

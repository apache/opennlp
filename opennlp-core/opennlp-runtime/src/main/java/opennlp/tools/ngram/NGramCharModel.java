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

import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import opennlp.tools.util.StringUtil;

/**
 * The {@link NGramCharModel} can be used to create character ngrams.
 *
 * @see NGramModel
 */
public class NGramCharModel implements Iterable<CharSequence> {

  protected static final String COUNT = "count";

  private final Map<CharSequence, Integer> mNGrams = new HashMap<>();

  /**
   * Initializes an empty instance.
   */
  public NGramCharModel() {
  }

  /**
   * Retrieves the count of the given {@link CharSequence ngram}.
   *
   * @param ngram The {@code ngram} to get the count for.
   * @return Count of the {@code ngram} or {@code 0} if it is not contained at all.
   */
  public int getCount(CharSequence ngram) {
    Integer count;
    if (ngram instanceof CharBuffer) {
      count = mNGrams.get(ngram);
    } else {
      count = mNGrams.get(CharBuffer.wrap(ngram));
    }

    if (count == null) {
      return 0;
    }

    return count;
  }

  /**
   * Sets the count of an existing {@link CharSequence ngram}.
   *
   * @param ngram The {@code ngram} to get the count for.
   * @param count The count of the {@code ngram} to set.
   */
  public void setCount(CharSequence ngram, int count) {

    Integer oldCount = mNGrams.put(ngram, count);

    if (oldCount == null) {
      mNGrams.remove(ngram);
      throw new NoSuchElementException();
    }
  }

  /**
   * Adds an {@code ngram}. If it already exists the count increase by one.
   *
   * @param ngram The {@link CharSequence} to be added.
   */
  public void add(CharSequence ngram) {
    if (contains(ngram)) {
      setCount(ngram, getCount(ngram) + 1);
    } else {
      mNGrams.put(CharBuffer.wrap(ngram), 1);
    }
  }

  /**
   * Adds a {@link CharSequence} that will be ngrammed into chars.
   *
   * @param chars The {@link CharSequence} to be ngrammed.
   * @param minLength The minimal length for {@code 'n'} to populate ngrams with.
   * @param maxLength The maximum length for {@code 'n'} to populate ngrams with.
   */
  public void add(CharSequence chars, int minLength, int maxLength) {
    CharBuffer cb = StringUtil.toLowerCaseCharBuffer(chars);
    for (int lengthIndex = minLength; lengthIndex < maxLength + 1; lengthIndex++) {
      for (int textIndex = 0; textIndex + lengthIndex - 1 < cb.length(); textIndex++) {
        CharSequence gram = cb.subSequence(textIndex, textIndex + lengthIndex);
        add(gram);
      }
    }
  }

  /**
   * Removes the specified {@code ngram} is from a {@link NGramCharModel}.
   *
   * @param ngram The {@code ngram} to remove. If {@code null}, the model keeps its state.
   */
  public void remove(CharSequence ngram) {
    if (ngram instanceof CharBuffer) {
      mNGrams.remove(ngram);
    } else {
      if (ngram != null) {
        mNGrams.remove(CharBuffer.wrap(ngram));
      }
    }
  }

  /**
   * Checks if the given {@code ngram} is contained in a {@link NGramCharModel}.
   *
   * @param ngram The {@code ngram} to check. If {@code null}, the model keeps its state.
   *
   * @return {@code true} if the ngram is contained, {@code false} otherwise.
   */
  public boolean contains(CharSequence ngram) {
    if (ngram instanceof CharBuffer) {
      return mNGrams.containsKey(ngram);
    } else {
      if (ngram != null) {
        return mNGrams.containsKey(CharBuffer.wrap(ngram));
      }
      return false;
    }
  }

  /**
   * Retrieves the number of {@link CharSequence entries} in a {@link NGramCharModel}.
   *
   * @return Number of different grams or {@code 0} if the model is empty.
   */
  public int size() {
    return mNGrams.size();
  }

  /**
   * Retrieves an {@link Iterator} over all {@link CharSequence entries}.
   *
   * @return iterator over all ngrams
   */
  @Override
  public Iterator<CharSequence> iterator() {
    return mNGrams.keySet().iterator();
  }

  /**
   * Retrieves the total count of all Ngrams.
   *
   * @return total count of all ngrams
   */
  public int numberOfGrams() {
    int counter = 0;

    for (CharSequence ngram : this) {
      counter += getCount(ngram);
    }

    return counter;
  }

  /**
   * Deletes all ngram which do appear less than the {@code cutoffUnder} value
   * and more often than the {@code cutoffOver} value.
   *
   * @param cutoffUnder The lower boundary to use for deletions.
   *                    Must be greater than {@code 0}.
   * @param cutoffOver The upper boundary to use for deletions.
   *                   Must be greater than {@code 0}
   */
  public void cutoff(int cutoffUnder, int cutoffOver) {

    if (cutoffUnder > 0 || cutoffOver < Integer.MAX_VALUE) {

      for (Iterator<CharSequence> it = iterator(); it.hasNext(); ) {

        CharSequence ngram = it.next();

        int count = getCount(ngram);

        if (count < cutoffUnder ||
            count > cutoffOver) {
          it.remove();
        }
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    boolean result;

    if (obj == this) {
      result = true;
    }
    else if (obj instanceof NGramCharModel model) {

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

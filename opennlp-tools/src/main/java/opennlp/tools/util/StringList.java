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

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import opennlp.tools.util.jvm.StringInterners;

/**
 * A {@link StringList} is an immutable list of {@link String}s.
 */
public class StringList implements Iterable<String> {

  private final String[] tokens;

  /**
   * Initializes a {@link StringList} instance.
   * <p>
   * Note: <br>
   * Token String will be interened via {@link StringInterners}.
   *
   * @param singleToken One single token
   */
  public StringList(String singleToken) {
    tokens = new String[]{StringInterners.intern(singleToken)};
  }

  /**
   * Initializes a {@link StringList} instance.
   * <p>
   * Note: <br>
   * Token Strings will be interened via {@link StringInterners}.
   *
   * @param tokens The string parts of the new {@link StringList}.
   *               Must not be an empty tokens array or {@code null}.
   *               
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public StringList(String... tokens) {

    Objects.requireNonNull(tokens, "tokens must not be null");

    if (tokens.length == 0) {
      throw new IllegalArgumentException("tokens must not be empty");
    }

    this.tokens = new String[tokens.length];

    for (int i = 0; i < tokens.length; i++) {
      this.tokens[i] = StringInterners.intern(tokens[i]);
    }
  }

  /**
   * @param index The index to get a token from.
   *
   * @return Retrieves a token from the given {@code index}.
   */
  public String getToken(int index) {
    return tokens[index];
  }

  /**
   * @return Retrieves the number of tokens inside this list.
   */
  public int size() {
    return tokens.length;
  }

  /**
   * @return Retrieves an {@link Iterator} over all tokens.
   */
  @Override
  public Iterator<String> iterator() {
    return new Iterator<>() {

      private int index;

      @Override
      public boolean hasNext() {
        return index < size();
      }

      @Override
      public String next() {

        if (hasNext()) {
          return getToken(index++);
        }
        else {
          throw new NoSuchElementException();
        }
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

    };
  }

  /**
   * Compares to {@link StringList token list} and ignores the case of the tokens.
   * Note: This can cause problems with some locales.
   *
   * @param tokens The {@link StringList tokens} used for comparison.
   *
   * @return {@code true} if identically with ignore the case, {@code false} otherwise.
   */
  public boolean compareToIgnoreCase(StringList tokens) {

    if (size() == tokens.size()) {
      for (int i = 0; i < size(); i++) {

        if (getToken(i).compareToIgnoreCase(
            tokens.getToken(i)) != 0) {
          return false;
        }
      }
    }
    else {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(tokens);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof StringList tokenList) {

      return Arrays.equals(tokens, tokenList.tokens);
    }

    return false;
  }

  /**
   * @return A human-readable representation of this {@link Span}.
   */
  @Override
  public String toString() {
    StringBuilder string = new StringBuilder();

    string.append('[');

    for (int i = 0; i < size(); i++) {
      string.append(getToken(i));

      if (i < size() - 1) {
        string.append(',');
      }
    }

    string.append(']');

    return string.toString();
  }
}

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

/**
 * The {@link StringList} is an immutable list of {@link String}s.
 */
public class StringList implements Iterable<String> {

  private String tokens[];

  /**
   * Initializes the current instance.
   *
   * Note: <br>
   * Token String will be replaced by identical internal String object.
   *
   * @param singleToken one single token
   */
  public StringList(String singleToken) {
    tokens = new String[]{singleToken.intern()};
  }

  /**
   * Initializes the current instance.
   *
   * Note: <br>
   * Token Strings will be replaced by identical internal String object.
   *
   * @param tokens the string parts of the new {@link StringList}, an empty
   *     tokens array or null is not permitted.
   */
  public StringList(String... tokens) {

    Objects.requireNonNull(tokens, "tokens must not be null");

    if (tokens.length == 0) {
      throw new IllegalArgumentException("tokens must not be empty");
    }

    this.tokens = new String[tokens.length];

    for (int i = 0; i < tokens.length; i++) {
      this.tokens[i] = tokens[i].intern();
    }
  }

  /**
   * Retrieves a token from the given index.
   *
   * @param index
   *
   * @return token at the given index
   */
  public String getToken(int index) {
    return tokens[index];
  }

  /**
   * Retrieves the number of tokens inside this list.
   *
   * @return number of tokens
   */
  public int size() {
    return tokens.length;
  }

  /**
   * Retrieves an {@link Iterator} over all tokens.
   *
   * @return iterator over tokens
   */
  public Iterator<String> iterator() {
    return new Iterator<String>() {

      private int index;

      public boolean hasNext() {
        return index < size();
      }

      public String next() {

        if (hasNext()) {
          return getToken(index++);
        }
        else {
          throw new NoSuchElementException();
        }
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }

    };
  }

  /**
   * Compares to tokens list and ignores the case of the tokens.
   *
   * Note: This can cause problems with some locals.
   *
   * @param tokens
   *
   * @return true if identically with ignore the case otherwise false
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

    if (obj instanceof StringList) {
      StringList tokenList = (StringList) obj;

      return Arrays.equals(tokens, tokenList.tokens);
    }

    return false;
  }

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

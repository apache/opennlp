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
package opennlp.tools.stemmer.light;

import opennlp.tools.stemmer.Stemmer;

/**
 * Base class of the light and minimal stemmers. It implements the public
 * {@link Stemmer#stem(CharSequence)} contract once: the word is validated, copied into a
 * {@code char[]} buffer, stemmed in place by the concrete algorithm via {@link #stem(char[], int)},
 * and returned as a {@link String} of the resulting length.
 */
abstract class AbstractCharArrayStemmer implements Stemmer {

  /** {@inheritDoc} */
  @Override
  public CharSequence stem(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    final int length = word.length();
    final char[] buffer = new char[length];
    for (int i = 0; i < length; i++) {
      buffer[i] = word.charAt(i);
    }
    final int stemmedLength = stem(buffer, length);
    return new String(buffer, 0, stemmedLength);
  }

  /**
   * Stems the buffer prefix in place.
   *
   * @param s   The buffer holding the word; the algorithm may overwrite characters in place.
   * @param len The filled length of the buffer.
   * @return The length of the stem within {@code s}.
   */
  abstract int stem(char[] s, int len);
}

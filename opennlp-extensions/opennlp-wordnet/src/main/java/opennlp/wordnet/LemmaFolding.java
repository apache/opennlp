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
package opennlp.wordnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The single home of the lemma fold and the space-separated field split this package relies on.
 * {@link MorphyExceptions} keys, the {@link InMemoryWordNetLexicon.LemmaKey sense-index keys},
 * and every query must fold through {@link #fold(String)} so their canonical forms agree.
 */
final class LemmaFolding {

  /** Not instantiable. */
  private LemmaFolding() {
  }

  /**
   * Folds a written form into its canonical shape: lowercase with the root locale, with the
   * underscore some formats store in multiword lemmas treated as a space.
   *
   * @param writtenForm The form as written in a source file or query. Must not be {@code null}.
   * @return The folded form.
   * @throws IllegalArgumentException Thrown if {@code writtenForm} is {@code null}.
   */
  static String fold(String writtenForm) {
    if (writtenForm == null) {
      throw new IllegalArgumentException("writtenForm must not be null");
    }
    return writtenForm.replace('_', ' ').toLowerCase(Locale.ROOT);
  }

  /**
   * Splits a space-separated field list, collapsing runs of spaces.
   *
   * @param value The field list. Must not be {@code null}.
   * @return The non-empty fields in order, never {@code null}.
   */
  static List<String> splitOnSpaces(String value) {
    final List<String> parts = new ArrayList<>(4);
    int start = 0;
    while (start < value.length()) {
      final int space = value.indexOf(' ', start);
      if (space < 0) {
        parts.add(value.substring(start));
        break;
      }
      if (space > start) {
        parts.add(value.substring(start, space));
      }
      start = space + 1;
    }
    return parts;
  }
}

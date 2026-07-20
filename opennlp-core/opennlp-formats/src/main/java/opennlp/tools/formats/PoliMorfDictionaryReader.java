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

package opennlp.tools.formats;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.util.StringUtil;

/**
 * Builds a {@link DictionaryLemmatizer} from a morphological dictionary laid out as one
 * tab-separated {@code surfaceForm\tlemma\ttag} row per entry.
 *
 * <p>This is the layout published by PoliMorf, the successor grammatical dictionary of Polish
 * (BSD 2-Clause), and emitted by exporting a morfologik dictionary to text; it is otherwise
 * language-agnostic. {@link DictionaryLemmatizer} expects the opposite column order,
 * {@code word\tpostag\tlemma}, and a single row per {@code (word, postag)} key with alternative
 * lemmas joined by {@code #}. This reader performs that adaptation: it re-orders the columns and
 * merges every lemma seen for the same form and tag into one entry, preserving first-seen order.
 * The bundled dictionary data itself is never shipped; callers supply it.</p>
 *
 * <p>Surface forms are lower-cased on load because {@link DictionaryLemmatizer} lower-cases the
 * queried token before lookup, so an entry keyed on a mixed-case form would otherwise be
 * unreachable. Tags are kept verbatim and must match the tags the caller's tagger emits.</p>
 *
 * <p>Thread safety is implementation specific.</p>
 */
public final class PoliMorfDictionaryReader {

  private static final String FIELD_SEPARATOR = "\t";
  private static final String LEMMA_SEPARATOR = "#";
  private static final int MIN_FIELDS = 3;

  private PoliMorfDictionaryReader() {
  }

  /**
   * Reads a UTF-8 {@code surfaceForm\tlemma\ttag} dictionary into a {@link DictionaryLemmatizer}.
   *
   * @param dictionary The dictionary referenced by an open {@link InputStream}. Must not be
   *                   {@code null}.
   * @return A {@link DictionaryLemmatizer} over the adapted entries.
   * @throws IllegalArgumentException if {@code dictionary} is {@code null}.
   * @throws IOException Thrown if IO errors occur while reading, or a non-blank line carries
   *                     fewer than three tab-separated fields.
   */
  public static DictionaryLemmatizer read(InputStream dictionary) throws IOException {
    return read(dictionary, StandardCharsets.UTF_8);
  }

  /**
   * Reads a {@code surfaceForm\tlemma\ttag} dictionary into a {@link DictionaryLemmatizer}.
   *
   * @param dictionary The dictionary referenced by an open {@link InputStream}. Must not be
   *                   {@code null}.
   * @param charset    The character encoding of the dictionary. Must not be {@code null}.
   * @return A {@link DictionaryLemmatizer} over the adapted entries.
   * @throws IllegalArgumentException if {@code dictionary} or {@code charset} is {@code null}.
   * @throws IOException Thrown if IO errors occur while reading, or a non-blank line carries
   *                     fewer than three tab-separated fields.
   */
  public static DictionaryLemmatizer read(InputStream dictionary, Charset charset)
      throws IOException {
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    if (charset == null) {
      throw new IllegalArgumentException("charset must not be null");
    }

    // key "form\ttag" -> alternative lemmas, both maps ordered so the output is deterministic.
    final Map<String, LinkedHashSet<String>> entries = new LinkedHashMap<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(dictionary, charset))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (StringUtil.isBlank(line)) {
          continue;
        }
        final String[] fields = line.split(FIELD_SEPARATOR, -1);
        if (fields.length < MIN_FIELDS) {
          throw new IOException("PoliMorf line " + lineNumber
              + " has fewer than " + MIN_FIELDS + " tab-separated fields: " + line);
        }
        final String form = fields[0].toLowerCase();
        final String lemma = fields[1];
        final String tag = fields[2];
        entries.computeIfAbsent(form + FIELD_SEPARATOR + tag, key -> new LinkedHashSet<>())
            .add(lemma);
      }
    }

    final StringBuilder adapted = new StringBuilder();
    for (final Map.Entry<String, LinkedHashSet<String>> entry : entries.entrySet()) {
      adapted.append(entry.getKey())
          .append(FIELD_SEPARATOR)
          .append(String.join(LEMMA_SEPARATOR, entry.getValue()))
          .append('\n');
    }

    final byte[] bytes = adapted.toString().getBytes(StandardCharsets.UTF_8);
    return new DictionaryLemmatizer(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
  }
}

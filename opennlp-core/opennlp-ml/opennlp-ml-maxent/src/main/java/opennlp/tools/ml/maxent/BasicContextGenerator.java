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

package opennlp.tools.ml.maxent;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.StringUtil;

/**
 * A {@link ContextGenerator} implementation for maxent decisions, assuming that the input
 * given to the {@link #getContext(String)} method is a String containing contextual
 * predicates separated by whitespace, for instance:
 * <p>
 * {@code cp_1 cp_2 ... cp_n}
 * </p>
 * A different separator can be given, which is taken as written. A predicate is not empty:
 * a leading, repeated, or trailing separator does not produce one.
 * <p>
 * Since 3.0.0 the separator is taken as written, not as a regular expression (OPENNLP-1929).
 * </p>
 */
public class BasicContextGenerator implements ContextGenerator<String> {

  private static final String[] NO_PREDICATES = new String[0];

  /**
   * The separator, or {@code null} to split on whitespace.
   */
  private final String separator;

  /**
   * Initializes a {@link BasicContextGenerator} that splits on runs of whitespace under the
   * Unicode {@code White_Space} property, see {@link StringUtil#isUnicodeWhitespace(int)}.
   * That definition is fixed and does not depend on the {@code opennlp.whitespace.mode}
   * system property, see {@link opennlp.tools.util.WhitespaceMode}.
   */
  public BasicContextGenerator() {
    separator = null;
  }

  /**
   * Initializes a {@link BasicContextGenerator} that splits on {@code sep} only. Other
   * whitespace is part of the predicates.
   *
   * @param sep The separator, taken as written and not as a regular expression.
   *            Must not be {@code null} or empty, and must be well-formed text: an unpaired
   *            surrogate is not a character and could split a code point of the input.
   *            A backslash is rejected, so a separator escaped for the regular expression
   *            engine, such as {@code "\\|"}, fails here instead of splitting nothing.
   * @throws IllegalArgumentException If {@code sep} is {@code null}, empty, contains a
   *                                  backslash, or contains an unpaired surrogate.
   */
  public BasicContextGenerator(String sep) {
    if (sep == null || sep.isEmpty()) {
      throw new IllegalArgumentException("sep must not be null or empty");
    }
    if (sep.indexOf('\\') >= 0) {
      throw new IllegalArgumentException(
          "sep is taken as written and must not contain a backslash: " + sep);
    }
    if (sep.codePoints().anyMatch(this::isUnpairedSurrogate)) {
      throw new IllegalArgumentException("sep must not contain an unpaired surrogate");
    }
    separator = sep;
  }

  /**
   * Tests whether a code point read from a string is a surrogate on its own rather than
   * the start of a supplementary character.
   *
   * @param codePoint A code point as returned by {@link String#codePoints()}.
   * @return {@code true} if it is a lone surrogate code unit.
   */
  private boolean isUnpairedSurrogate(int codePoint) {
    return codePoint <= Character.MAX_VALUE && Character.isSurrogate((char) codePoint);
  }

  /**
   * {@inheritDoc}
   * Splits {@code o} at each occurrence of the separator and leaves out empty parts.
   *
   * @throws IllegalArgumentException If {@code o} is {@code null}.
   */
  @Override
  public String[] getContext(String o) {
    if (o == null) {
      throw new IllegalArgumentException("o must not be null");
    }
    if (separator == null) {
      return StringUtil.splitOnUnicodeWhitespace(o);
    }
    int next = o.indexOf(separator);
    if (next == -1) {
      return o.isEmpty() ? NO_PREDICATES : new String[] {o};
    }
    final List<String> contexts = new ArrayList<>();
    int start = 0;
    do {
      if (next > start) {
        contexts.add(o.substring(start, next));
      }
      start = next + separator.length();
      next = o.indexOf(separator, start);
    } while (next != -1);
    if (start < o.length()) {
      contexts.add(o.substring(start));
    }
    return contexts.toArray(NO_PREDICATES);
  }

}

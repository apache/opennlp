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

import opennlp.tools.tokenize.WhitespaceTokenizer;

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
 * Since 3.0.0 the separator is not a regular expression and the default splits on each run
 * of whitespace (OPENNLP-1929).
 * </p>
 */
public class BasicContextGenerator implements ContextGenerator<String> {

  /**
   * The separator, or {@code null} to split on whitespace.
   */
  private final String separator;

  /**
   * Initializes a {@link BasicContextGenerator} that splits on whitespace, as defined by
   * {@link WhitespaceTokenizer}.
   */
  public BasicContextGenerator() {
    separator = null;
  }

  /**
   * Initializes a {@link BasicContextGenerator} that splits on {@code sep} only. Other
   * whitespace is part of the predicates.
   *
   * @param sep The separator, taken as written and not as a regular expression.
   *            Must not be {@code null} or empty.
   * @throws IllegalArgumentException If {@code sep} is {@code null} or empty.
   */
  public BasicContextGenerator(String sep) {
    if (sep == null || sep.isEmpty()) {
      throw new IllegalArgumentException("sep must not be null or empty");
    }
    separator = sep;
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
      return WhitespaceTokenizer.INSTANCE.tokenize(o);
    }
    final List<String> contexts = new ArrayList<>();
    int start = 0;
    int next;
    while ((next = o.indexOf(separator, start)) != -1) {
      if (next > start) {
        contexts.add(o.substring(start, next));
      }
      start = next + separator.length();
    }
    if (start < o.length()) {
      contexts.add(o.substring(start));
    }
    return contexts.toArray(new String[0]);
  }

}

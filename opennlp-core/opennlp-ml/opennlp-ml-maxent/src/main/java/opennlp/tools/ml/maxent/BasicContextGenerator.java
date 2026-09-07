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

/**
 * A {@link ContextGenerator} implementation for maxent decisions, assuming that the input
 * given to the {@link #getContext(String)} method is a String containing contextual
 * predicates separated by spaces, for instance:
 * <p>
 * {@code cp_1 cp_2 ... cp_n}
 * </p>
 */
public class BasicContextGenerator implements ContextGenerator<String> {

  private static final String DEFAULT_SEPARATOR = " ";

  private final String separator;

  public BasicContextGenerator() {
    this(DEFAULT_SEPARATOR);
  }

  /**
   * Initializes a {@link BasicContextGenerator} with a different separator.
   * This overwrites the default single space.
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
   * Splits at each occurrence of the separator with the result {@code String.split} gives for
   * a literal: a leading occurrence gives an empty first element, trailing empty elements
   * are removed.
   */
  @Override
  public String[] getContext(String o) {
    final List<String> contexts = new ArrayList<>();
    int start = 0;
    int next;
    while ((next = o.indexOf(separator, start)) != -1) {
      contexts.add(o.substring(start, next));
      start = next + separator.length();
    }
    contexts.add(o.substring(start));
    int end = contexts.size();
    while (end > 0 && contexts.get(end - 1).isEmpty()) {
      end--;
    }
    if (end == 0 && o.isEmpty()) {
      return new String[] {""};
    }
    return contexts.subList(0, end).toArray(new String[0]);
  }

}

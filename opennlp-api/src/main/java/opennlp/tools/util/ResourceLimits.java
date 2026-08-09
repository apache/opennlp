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

/**
 * Shared upper bounds for counts read from user-supplied resources, so a crafted
 * file cannot force an outsized allocation before validation completes.
 */
public final class ResourceLimits {

  /**
   * System property for overriding {@link #MAX_ENTRIES}.
   * Set at JVM startup, e.g. {@code -DOPENNLP_MAX_ENTRIES=5000000}.
   * Falls back to {@code 10_000_000} if absent or invalid.
   */
  public static final String MAX_ENTRIES_PROPERTY = "OPENNLP_MAX_ENTRIES";

  /**
   * Upper bound on count fields and resource sizes that drive allocations
   * (matrix dimensions, lexicon entries, model outcome counts, and similar).
   * Configurable via {@link #MAX_ENTRIES_PROPERTY}.
   */
  public static final int MAX_ENTRIES = initLimit(MAX_ENTRIES_PROPERTY, 10_000_000);

  /**
   * System property for overriding {@link #MAX_MATRIX_CELLS}.
   * Set at JVM startup, e.g. {@code -DOPENNLP_MAX_MATRIX_CELLS=20000000}.
   * Falls back to {@code 134_217_728} if absent or invalid.
   */
  public static final String MAX_MATRIX_CELLS_PROPERTY = "OPENNLP_MAX_MATRIX_CELLS";

  /**
   * Upper bound on the cell count of a two-dimensional cost table, whose entries
   * are far smaller than the record-sized entries {@link #MAX_ENTRIES} bounds.
   * The default of 2^27 cells caps a 16-bit cost matrix at 256 MiB, which admits
   * every published MeCab-format distribution (mecab-ko-dic 2.1.1 alone declares
   * 3822 x 2693, above {@link #MAX_ENTRIES}) while still refusing the roughly
   * 4 GiB allocation a crafted {@code 46340 46340} header would force.
   * Configurable via {@link #MAX_MATRIX_CELLS_PROPERTY}.
   */
  public static final int MAX_MATRIX_CELLS =
      initLimit(MAX_MATRIX_CELLS_PROPERTY, 134_217_728);

  private ResourceLimits() {
  }

  /**
   * Reads a positive integer limit from the given system property.
   *
   * @param property The system property name. Must not be {@code null}.
   * @param defaultValue The value used when the property is absent or invalid.
   * @return The configured limit, or {@code defaultValue}.
   */
  private static int initLimit(String property, int defaultValue) {
    final String prop = System.getProperty(property, "").trim();
    if (!prop.isEmpty()) {
      try {
        final int val = Integer.parseInt(prop);
        if (val > 0) {
          return val;
        }
      } catch (NumberFormatException ignore) {
        // Fall through to the default.
      }
    }
    return defaultValue;
  }
}

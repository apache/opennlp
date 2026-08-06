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
  public static final int MAX_ENTRIES = initMaxEntries();

  private ResourceLimits() {
  }

  private static int initMaxEntries() {
    final String prop = System.getProperty(MAX_ENTRIES_PROPERTY, "").trim();
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
    return 10_000_000;
  }
}

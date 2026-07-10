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
package opennlp.tools.geo;

import opennlp.tools.commons.ThreadSafe;

/**
 * One provenance-tagged attribute value of a {@link GazetteerEntry}.
 *
 * <p>Every dataset-specific value carries the identifier of the dataset or judgment it came
 * from, so a consumer can always tell bundled facts, derived facts, and joined facts apart, and
 * mixed-source entries stay auditable. A value with no source cannot be constructed; a project
 * judgment with no dataset behind it is marked {@link #SOURCE_UNSPECIFIED} explicitly.</p>
 *
 * <p>Instances are immutable and thread-safe.</p>
 *
 * @param value  The attribute value. Must not be {@code null} or empty.
 * @param source The provenance tag, for example {@code naturalearth} or
 *               {@link #SOURCE_UNSPECIFIED} (the explicit marker for a project judgment). Must
 *               not be {@code null} or empty.
 * @param notes  Free-text notes, for example the upstream field a value was derived from. Must
 *               not be {@code null}; may be empty.
 */
@ThreadSafe
public record AttributeValue(String value, String source, String notes) {

  /** The explicit provenance marker of a project judgment with no dataset behind it. */
  public static final String SOURCE_UNSPECIFIED = "UNSPECIFIED";

  /**
   * Creates an attribute value.
   *
   * @throws IllegalArgumentException Thrown if {@code value} or {@code source} is {@code null} or
   *     empty, or if {@code notes} is {@code null}.
   */
  public AttributeValue {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("Value must not be null or empty");
    }
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("Source must not be null or empty");
    }
    if (notes == null) {
      throw new IllegalArgumentException("Notes must not be null");
    }
  }
}

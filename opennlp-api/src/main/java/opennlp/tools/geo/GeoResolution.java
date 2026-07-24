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
import opennlp.tools.util.Span;

/**
 * One resolved location mention: the mention's span in the document, the winning
 * {@link GazetteerEntry}, and the resolver's confidence.
 *
 * <p>The {@link #mention() mention} span is in original document coordinates, the same coordinate
 * space as Name Finder output. Instances are immutable and thread-safe.</p>
 *
 * @param mention    The location mention's span, in original document coordinates. Must not be
 *                   {@code null}.
 * @param entry      The winning candidate. Must not be {@code null}.
 * @param confidence The resolver-defined confidence, in {@code [0, 1]}; how it is computed is the
 *                   {@link Geocoder} implementation's documented choice.
 */
@ThreadSafe
public record GeoResolution(Span mention, GazetteerEntry entry, double confidence) {

  /**
   * Creates a resolution.
   *
   * @throws IllegalArgumentException Thrown if {@code mention} or {@code entry} is {@code null},
   *     or {@code confidence} is not in {@code [0, 1]} (including {@code NaN}).
   */
  public GeoResolution {
    if (mention == null) {
      throw new IllegalArgumentException("Mention must not be null");
    }
    if (entry == null) {
      throw new IllegalArgumentException("Entry must not be null");
    }
    if (!(confidence >= 0.0 && confidence <= 1.0)) {
      throw new IllegalArgumentException("Confidence must be in [0, 1], got: " + confidence);
    }
  }
}

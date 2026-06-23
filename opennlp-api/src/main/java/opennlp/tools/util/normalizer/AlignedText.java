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
package opennlp.tools.util.normalizer;

import opennlp.tools.util.Span;

/**
 * The result of a normalization that keeps the original text alongside the normalized form and a
 * full {@link Alignment} between them.
 *
 * <p>The original is the source of truth, the normalized form is the derived view tuned for
 * matching and search, and the alignment maps spans between them through deletions, collapses, and
 * expansions. Use
 * {@link #toOriginalSpan(int, int)} to report a match found in the normalized form against the
 * original.</p>
 *
 * @param original   The untouched source text.
 * @param normalized The normalized text.
 * @param alignment  The alignment between the normalized and original text.
 */
public record AlignedText(CharSequence original, String normalized, Alignment alignment) {

  /**
   * Maps a span of the normalized text back to the tightest span of the original text.
   *
   * @param normalizedStart The inclusive start offset in the normalized text.
   * @param normalizedEnd   The exclusive end offset in the normalized text.
   * @return The corresponding original span.
   */
  public Span toOriginalSpan(int normalizedStart, int normalizedEnd) {
    return alignment.toOriginalSpan(normalizedStart, normalizedEnd);
  }

  /**
   * Maps a span of the original text forward to the normalized text.
   *
   * @param originalStart The inclusive start offset in the original text.
   * @param originalEnd   The exclusive end offset in the original text.
   * @return The corresponding normalized span.
   */
  public Span toNormalizedSpan(int originalStart, int originalEnd) {
    return alignment.toNormalizedSpan(originalStart, originalEnd);
  }
}

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

/**
 * The result of a normalization that keeps the original text alongside the normalized form.
 *
 * <p>The original is the source of truth (display, offsets, language-specific analysis); the
 * normalized form is a derived view tuned for matching and search. The {@link OffsetMap} ties the
 * two together so a position in the normalized text can be reported against the original.</p>
 *
 * @param original The untouched source text.
 * @param normalized The normalized text.
 * @param offsets The mapping between normalized and original character offsets.
 */
public record NormalizedText(CharSequence original, String normalized, OffsetMap offsets) {

  /**
   * Maps a normalized character offset back to the original text.
   *
   * @param normalizedOffset An offset in {@code [0, normalized().length()]}.
   * @return The corresponding original character offset.
   */
  public int toOriginalOffset(int normalizedOffset) {
    return offsets.toOriginalOffset(normalizedOffset);
  }

  /**
   * Maps an original character offset forward to the normalized text.
   *
   * @param originalOffset An offset in {@code [0, original().length()]}.
   * @return The corresponding normalized character offset.
   */
  public int toNormalizedOffset(int originalOffset) {
    return offsets.toNormalizedOffset(originalOffset);
  }
}

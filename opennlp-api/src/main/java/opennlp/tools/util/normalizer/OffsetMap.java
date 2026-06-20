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

import java.util.Arrays;

/**
 * A mapping between character offsets in a normalized string and the original text it came from.
 *
 * <p>Normalization that collapses runs or substitutes supplementary characters changes string
 * length, so an offset into the normalized form no longer lines up with the original. This map
 * records, for every normalized character, the original character offset it was produced from,
 * which lets a match found in the normalized form be reported in original coordinates.</p>
 *
 * <p>The internal mapping is non-decreasing, so {@link #toOriginalOffset(int)} is a direct array
 * read (O(1)) and {@link #toNormalizedOffset(int)} is a binary search (O(log n)). The map is
 * built in the same single cursor pass that produces the normalized text, via {@link Builder}.</p>
 */
public final class OffsetMap {

  // normalizedToOriginal[k] is the original char offset that produced normalized char k.
  // It has one extra trailing slot mapping the end of the normalized text to the end of the
  // original text, so offsets in [0, normalizedLength] are all valid.
  private final int[] normalizedToOriginal;
  private final int originalLength;

  private OffsetMap(int[] normalizedToOriginal, int originalLength) {
    this.normalizedToOriginal = normalizedToOriginal;
    this.originalLength = originalLength;
  }

  /**
   * Maps a normalized character offset back to the original text.
   *
   * @param normalizedOffset An offset in {@code [0, normalizedLength]}.
   * @return The corresponding original character offset.
   * @throws IndexOutOfBoundsException Thrown if {@code normalizedOffset} is out of range.
   */
  public int toOriginalOffset(int normalizedOffset) {
    if (normalizedOffset < 0 || normalizedOffset >= normalizedToOriginal.length) {
      throw new IndexOutOfBoundsException("normalized offset " + normalizedOffset
          + " is outside [0, " + normalizedLength() + "]");
    }
    return normalizedToOriginal[normalizedOffset];
  }

  /**
   * Maps an original character offset forward to the normalized text.
   *
   * <p>Returns the normalized offset of the character that {@code originalOffset} falls within: the
   * last normalized character whose source offset is at or before {@code originalOffset}. When
   * several original characters collapse to one normalized character, every offset in that run maps
   * to that single normalized offset. An offset that precedes the first retained character (for
   * example inside leading whitespace that was trimmed) maps to {@code 0}.</p>
   *
   * @param originalOffset An offset in {@code [0, originalLength]}.
   * @return The corresponding normalized character offset.
   * @throws IndexOutOfBoundsException Thrown if {@code originalOffset} is out of range.
   */
  public int toNormalizedOffset(int originalOffset) {
    if (originalOffset < 0 || originalOffset > originalLength) {
      throw new IndexOutOfBoundsException("original offset " + originalOffset
          + " is outside [0, " + originalLength + "]");
    }
    // Floor search: the rightmost normalized index whose source offset is <= originalOffset. This
    // keeps offsets that fall inside a collapsed run mapped to the run's single normalized
    // character rather than jumping to the following one. Clamped to 0 when nothing qualifies.
    int low = 0;
    int high = normalizedToOriginal.length - 1;
    int answer = 0;
    while (low <= high) {
      final int mid = (low + high) >>> 1;
      if (normalizedToOriginal[mid] <= originalOffset) {
        answer = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return answer;
  }

  /** {@return the length of the normalized text this map was built for} */
  public int normalizedLength() {
    return normalizedToOriginal.length - 1;
  }

  /** {@return the length of the original text this map was built for} */
  public int originalLength() {
    return originalLength;
  }

  /**
   * Builds an {@link OffsetMap} incrementally during a normalization pass. Call {@link #map(int)}
   * once for each character appended to the normalized output, then {@link #build(int)} once.
   */
  public static final class Builder {

    private int[] buffer = new int[16];
    private int length;

    /**
     * Records the original character offset that produced the next normalized character.
     *
     * @param originalOffset The source offset in the original text.
     */
    public void map(int originalOffset) {
      if (length == buffer.length) {
        buffer = Arrays.copyOf(buffer, buffer.length * 2);
      }
      buffer[length++] = originalOffset;
    }

    /**
     * Finalizes the map.
     *
     * @param originalLength The length of the original text (used as the trailing sentinel).
     * @return The immutable {@link OffsetMap}.
     */
    public OffsetMap build(int originalLength) {
      final int[] mapping = Arrays.copyOf(buffer, length + 1);
      mapping[length] = originalLength;
      return new OffsetMap(mapping, originalLength);
    }
  }
}

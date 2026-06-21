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

import opennlp.tools.util.Span;

/**
 * A bidirectional alignment between an original text and a normalized form of it.
 *
 * <p>Normalization edits text in ways that move character offsets: a run of whitespace collapses to
 * one space, a supplementary dash folds to a single ASCII hyphen, a case fold can grow text
 * (German {@code eszett} to {@code ss}), and trimming or stripping deletes characters outright. An
 * {@code Alignment} records those edits as a sequence of <em>equal</em> runs (text copied through
 * unchanged in length) and <em>replace</em> runs (a block of original characters that produced a
 * block of normalized characters), so any span in either form can be mapped to the other.</p>
 *
 * <p>Because it represents deletions as gaps and expansions as shared blocks (rather than storing a
 * single original offset per normalized character, which would assume the normalized text
 * contiguously covers the original), mapping is done
 * span to span ({@link #toOriginalSpan(int, int)} / {@link #toNormalizedSpan(int, int)}) so a match
 * that ends next to deleted text reports a tight span rather than over-covering the deletion. Two
 * alignments compose with {@link #andThen(Alignment)}, which is what lets a multi-stage
 * normalization pipeline still map a result all the way back to the original.</p>
 *
 * <p>Instances are immutable and thread-safe; build one with {@link Builder}.</p>
 */
public final class Alignment {

  // For normalized character k, originalStart[k]/originalEnd[k] are the half-open original range it
  // was produced from. Characters copied unchanged map one to one; characters from a collapse or
  // expansion share their run's whole original range (it cannot be subdivided); deleted original
  // characters appear as a gap that no normalized character covers.
  private final int[] originalStart;
  private final int[] originalEnd;
  private final int originalLength;

  private Alignment(int[] originalStart, int[] originalEnd, int originalLength) {
    this.originalStart = originalStart;
    this.originalEnd = originalEnd;
    this.originalLength = originalLength;
  }

  /** {@return the length of the normalized text this alignment was built for} */
  public int normalizedLength() {
    return originalStart.length;
  }

  /** {@return the length of the original text this alignment was built for} */
  public int originalLength() {
    return originalLength;
  }

  /**
   * Maps a half-open span of the normalized text to the tightest half-open span of the original
   * text that produced it.
   *
   * @param normalizedStart The inclusive start offset, in {@code [0, normalizedLength()]}.
   * @param normalizedEnd   The exclusive end offset, in {@code [normalizedStart, normalizedLength()]}.
   * @return The corresponding original span.
   * @throws IndexOutOfBoundsException Thrown if the offsets are out of range or inverted.
   */
  public Span toOriginalSpan(int normalizedStart, int normalizedEnd) {
    checkRange(normalizedStart, normalizedEnd, normalizedLength());
    if (normalizedStart == normalizedEnd) {
      final int at = normalizedStart < normalizedLength()
          ? originalStart[normalizedStart] : originalLength;
      return new Span(at, at);
    }
    return new Span(originalStart[normalizedStart], originalEnd[normalizedEnd - 1]);
  }

  /**
   * Maps a half-open span of the original text to the half-open span of the normalized text that
   * covers it. Original characters that were deleted map to an empty span at the point where they
   * were removed.
   *
   * @param originalStartOffset The inclusive start offset, in {@code [0, originalLength()]}.
   * @param originalEndOffset   The exclusive end offset, in {@code [originalStartOffset, originalLength()]}.
   * @return The corresponding normalized span.
   * @throws IndexOutOfBoundsException Thrown if the offsets are out of range or inverted.
   */
  public Span toNormalizedSpan(int originalStartOffset, int originalEndOffset) {
    checkRange(originalStartOffset, originalEndOffset, originalLength);
    final int start = firstIndexEndingAfter(originalStartOffset);
    final int end = firstIndexStartingAtOrAfter(originalEndOffset);
    return new Span(start, Math.max(start, end));
  }

  /**
   * Maps a normalized offset to the original offset where its character begins (start semantics).
   * Prefer {@link #toOriginalSpan(int, int)} for mapping a match, since a single offset cannot
   * distinguish the start and end of a span across a deletion.
   *
   * @param normalizedOffset An offset in {@code [0, normalizedLength()]}.
   * @return The corresponding original offset.
   * @throws IndexOutOfBoundsException Thrown if {@code normalizedOffset} is out of range.
   */
  public int toOriginalOffset(int normalizedOffset) {
    if (normalizedOffset < 0 || normalizedOffset > normalizedLength()) {
      throw new IndexOutOfBoundsException("normalized offset " + normalizedOffset
          + " is outside [0, " + normalizedLength() + "]");
    }
    return normalizedOffset < normalizedLength() ? originalStart[normalizedOffset] : originalLength;
  }

  /**
   * Composes this alignment with one that further normalizes this alignment's normalized text.
   *
   * <p>If this maps {@code original -> middle} and {@code next} maps {@code middle -> final}, the
   * result maps {@code original -> final} directly, so a span found in the final text can be mapped
   * straight back to the original without keeping the intermediate stages.</p>
   *
   * @param next The next stage, whose original side is this stage's normalized text.
   * @return The composed alignment.
   * @throws IllegalArgumentException Thrown if {@code next.originalLength()} does not equal this
   *     {@code normalizedLength()} (the stages do not line up).
   */
  public Alignment andThen(Alignment next) {
    if (next.originalLength != normalizedLength()) {
      throw new IllegalArgumentException("stages do not line up: this normalizedLength="
          + normalizedLength() + " but next originalLength=" + next.originalLength);
    }
    final int finalLength = next.normalizedLength();
    final int[] starts = new int[finalLength];
    final int[] ends = new int[finalLength];
    for (int f = 0; f < finalLength; f++) {
      final int middleStart = next.originalStart[f];
      final int middleEnd = next.originalEnd[f];
      final int start = middleStart < normalizedLength() ? originalStart[middleStart] : originalLength;
      final int end = middleEnd > 0 ? originalEnd[middleEnd - 1] : 0;
      starts[f] = start;
      ends[f] = Math.max(start, end);
    }
    return new Alignment(starts, ends, originalLength);
  }

  // First normalized index whose original coverage ends strictly after offset (so it covers or
  // follows offset); normalizedLength() when offset is at or past the last covered original char.
  private int firstIndexEndingAfter(int offset) {
    int low = 0;
    int high = originalEnd.length;
    while (low < high) {
      final int mid = (low + high) >>> 1;
      if (originalEnd[mid] > offset) {
        high = mid;
      } else {
        low = mid + 1;
      }
    }
    return low;
  }

  // First normalized index whose original coverage starts at or after offset.
  private int firstIndexStartingAtOrAfter(int offset) {
    int low = 0;
    int high = originalStart.length;
    while (low < high) {
      final int mid = (low + high) >>> 1;
      if (originalStart[mid] >= offset) {
        high = mid;
      } else {
        low = mid + 1;
      }
    }
    return low;
  }

  private static void checkRange(int start, int end, int length) {
    if (start < 0 || end > length || start > end) {
      throw new IndexOutOfBoundsException("span [" + start + ", " + end + ") is outside [0, "
          + length + "]");
    }
  }

  /**
   * Builds an {@link Alignment} as the normalized text is produced, by recording each edit in order.
   * Call {@link #equal(int)} for characters copied through unchanged and {@link #replace(int, int)}
   * for a block that was rewritten (including deletions and insertions), then {@link #build(int)}.
   */
  public static final class Builder {

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private int[] starts = new int[16];
    private int[] ends = new int[16];
    private int count;
    private int originalCursor;

    /**
     * Records {@code charCount} characters copied through unchanged (a one to one run).
     *
     * @param charCount The number of UTF-16 characters; must not be negative.
     * @return This builder.
     */
    public Builder equal(int charCount) {
      if (charCount < 0) {
        throw new IllegalArgumentException("charCount must not be negative: " + charCount);
      }
      for (int i = 0; i < charCount; i++) {
        append(originalCursor, originalCursor + 1);
        originalCursor++;
      }
      return this;
    }

    /**
     * Records a rewritten block: {@code originalCount} original characters that produced
     * {@code normalizedCount} normalized characters. Each produced character is attributed to the
     * whole original block, since a collapse or expansion cannot be subdivided. {@code 0} for
     * {@code normalizedCount} is a deletion; {@code 0} for {@code originalCount} is an insertion.
     *
     * @param originalCount   The number of original characters consumed; must not be negative.
     * @param normalizedCount The number of normalized characters produced; must not be negative.
     * @return This builder.
     */
    public Builder replace(int originalCount, int normalizedCount) {
      if (originalCount < 0 || normalizedCount < 0) {
        throw new IllegalArgumentException("counts must not be negative: " + originalCount
            + ", " + normalizedCount);
      }
      final int blockEnd = originalCursor + originalCount;
      for (int i = 0; i < normalizedCount; i++) {
        append(originalCursor, blockEnd);
      }
      originalCursor = blockEnd;
      return this;
    }

    /**
     * Finalizes the alignment.
     *
     * @param originalLength The full length of the original text.
     * @return The immutable {@link Alignment}.
     * @throws IllegalStateException Thrown if the recorded edits do not consume exactly
     *     {@code originalLength} original characters (a sign that some input was not accounted for).
     */
    public Alignment build(int originalLength) {
      if (originalCursor != originalLength) {
        throw new IllegalStateException("edits consumed " + originalCursor
            + " original characters but originalLength is " + originalLength);
      }
      return new Alignment(Arrays.copyOf(starts, count), Arrays.copyOf(ends, count), originalLength);
    }

    private void append(int start, int end) {
      if (count == starts.length) {
        grow();
      }
      starts[count] = start;
      ends[count] = end;
      count++;
    }

    // Overflow-aware 1.5x growth: never wraps to a negative capacity, degrades to a clean
    // OutOfMemoryError at the array-size ceiling instead of NegativeArraySizeException.
    private void grow() {
      int newCapacity = starts.length + (starts.length >> 1);
      if (newCapacity < 0 || newCapacity > MAX_ARRAY_SIZE) {
        newCapacity = MAX_ARRAY_SIZE;
      }
      if (newCapacity <= count) {
        throw new OutOfMemoryError("Alignment exceeds maximum size");
      }
      starts = Arrays.copyOf(starts, newCapacity);
      ends = Arrays.copyOf(ends, newCapacity);
    }
  }
}

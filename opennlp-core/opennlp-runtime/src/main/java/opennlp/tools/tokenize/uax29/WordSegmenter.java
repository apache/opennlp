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
package opennlp.tools.tokenize.uax29;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.util.Span;

/**
 * Finds word boundaries in text using the Unicode Text Segmentation algorithm
 * (<a href="https://www.unicode.org/reports/tr29/">UAX #29</a>), rules WB1 through WB999.
 *
 * <p>The implementation is a single forward cursor pass with O(1) {@link WordBreakProperty}
 * lookups and no regular expression. It decodes each code point once, keeps only a constant amount
 * of state, and allocates nothing per character. It implements the "ignore" semantics of WB4 (a
 * base character absorbs following {@code Extend}, {@code Format}, and {@code ZWJ}), the look-ahead
 * rules WB6/WB7/WB7b/WB12, the Hebrew quote rules WB7a-WB7c, the emoji zero-width-joiner rule WB3c,
 * and regional-indicator pairing WB15/WB16. The look-ahead for the WB6/WB7b/WB12 rules is resolved
 * lazily and only at mid-word punctuation, so the common case never scans ahead.</p>
 *
 * <p>{@link #forEachSegment(CharSequence, SegmentConsumer)} streams the segments with no
 * allocation; {@link #boundaries(CharSequence)} returns every boundary offset (always including
 * {@code 0} and the text length); {@link #segments(CharSequence)} returns the spans between
 * them.</p>
 */
public final class WordSegmenter {

  /** Receives each word segment as the half-open character range {@code [start, end)}. */
  @FunctionalInterface
  public interface SegmentConsumer {
    /**
     * Accepts one segment.
     *
     * @param start The inclusive start character offset.
     * @param end   The exclusive end character offset.
     */
    void accept(int start, int end);
  }

  // Decisions for the WB5-WB999 rules. NO_BREAK/BREAK are final; CONSULT marks a (last, current)
  // pair whose decision also depends on look-ahead or regional-indicator parity, so the full rule
  // cascade must be consulted. GO_SLOW appears only in the FAST table (never in TRANSITION) and
  // marks a current class that can trigger a WB3-family or WB4 rule.
  private static final byte NO_BREAK = 0;
  private static final byte BREAK = 1;
  private static final byte CONSULT = 2;
  private static final byte GO_SLOW = 3;

  private static final WordBreak[] CLASSES = WordBreak.values();
  private static final int CLASS_COUNT = CLASSES.length;

  // TRANSITION[last * CLASS_COUNT + current] holds the WB5-WB999 decision for a (last, current)
  // pair: NO_BREAK or BREAK when the decision is the same for every secondLast, next significant
  // value, and parity, or CONSULT otherwise. The table is derived from afterPrefix(...) at
  // class-load, so it is equivalent to the rule cascade by construction; only the hot path reads it.
  private static final byte[] TRANSITION = buildTransitionTable();

  // Ordinals of the Word_Break classes that the WB3 family and WB4 examine. The hot loop works with
  // ordinals to avoid materializing a WordBreak enum per character.
  private static final int OTHER_ORDINAL = WordBreak.OTHER.ordinal();
  private static final int CR_ORDINAL = WordBreak.CR.ordinal();
  private static final int LF_ORDINAL = WordBreak.LF.ordinal();
  private static final int NEWLINE_ORDINAL = WordBreak.NEWLINE.ordinal();
  private static final int ZWJ_ORDINAL = WordBreak.ZWJ.ordinal();
  private static final int WSEG_SPACE_ORDINAL = WordBreak.WSEG_SPACE.ordinal();
  private static final int EXTEND_ORDINAL = WordBreak.EXTEND.ordinal();
  private static final int FORMAT_ORDINAL = WordBreak.FORMAT.ordinal();
  private static final int REGIONAL_INDICATOR_ORDINAL = WordBreak.REGIONAL_INDICATOR.ordinal();

  // SPECIAL[ordinal] is true for the classes that can trigger a WB3-family or WB4 rule (the
  // newline, ZWJ, word-segment-space, and ignorable classes). When neither the previous nor the
  // current class is special, those rules cannot fire and the hot loop goes straight to the
  // transition table.
  private static final boolean[] SPECIAL = buildSpecialTable();

  // FAST[last * CLASS_COUNT + current] is the hot-loop table: the TRANSITION decision when the
  // current class is ordinary, or GO_SLOW when it is special. One read decides the common case and
  // detects a special current class, so the loop never reloads SPECIAL[current].
  private static final byte[] FAST = buildFastTable();

  private WordSegmenter() {
  }

  private static boolean[] buildSpecialTable() {
    final boolean[] special = new boolean[CLASS_COUNT];
    special[CR_ORDINAL] = true;
    special[LF_ORDINAL] = true;
    special[NEWLINE_ORDINAL] = true;
    special[ZWJ_ORDINAL] = true;
    special[WSEG_SPACE_ORDINAL] = true;
    special[EXTEND_ORDINAL] = true;
    special[FORMAT_ORDINAL] = true;
    return special;
  }

  private static byte[] buildFastTable() {
    final byte[] fast = new byte[CLASS_COUNT * CLASS_COUNT];
    for (int last = 0; last < CLASS_COUNT; last++) {
      for (int current = 0; current < CLASS_COUNT; current++) {
        final int index = last * CLASS_COUNT + current;
        fast[index] = SPECIAL[current] ? GO_SLOW : TRANSITION[index];
      }
    }
    return fast;
  }

  private static byte[] buildTransitionTable() {
    final byte[] table = new byte[CLASS_COUNT * CLASS_COUNT];
    for (final WordBreak last : CLASSES) {
      for (final WordBreak current : CLASSES) {
        table[last.ordinal() * CLASS_COUNT + current.ordinal()] = deriveDecision(last, current);
      }
    }
    return table;
  }

  // Returns the constant WB5-WB999 decision for a (last, current) pair, or CONSULT if afterPrefix
  // gives different answers for different secondLast, next, or parity values.
  private static byte deriveDecision(WordBreak last, WordBreak current) {
    Boolean constant = null;
    for (final WordBreak secondLast : CLASSES) {
      for (final WordBreak next : CLASSES) {
        for (int parity = 0; parity <= 1; parity++) {
          final boolean decision = afterPrefix(current, last, secondLast, next, parity);
          if (constant == null) {
            constant = decision;
          } else if (constant != decision) {
            return CONSULT;
          }
        }
      }
    }
    return constant ? BREAK : NO_BREAK;
  }

  /**
   * Streams the word segments of {@code text} to {@code consumer} in order, allocating nothing.
   * Each segment is delivered as the half-open character range {@code [start, end)}; the segments
   * are contiguous and together cover the whole text.
   *
   * @param text     The text to segment.
   * @param consumer The receiver of the segment ranges.
   */
  public static void forEachSegment(CharSequence text, SegmentConsumer consumer) {
    final int length = text.length();
    if (length == 0) {
      return;
    }

    final int firstCp = Character.codePointAt(text, 0);
    int prev = WordBreakProperty.ordinalOf(firstCp);
    boolean prevSpecial = SPECIAL[prev];
    int last = OTHER_ORDINAL;
    int secondLast = OTHER_ORDINAL;
    int regionalIndicatorRun = 0;
    if (!isIgnorable(prev)) {
      last = prev;
      regionalIndicatorRun = prev == REGIONAL_INDICATOR_ORDINAL ? 1 : 0;
    }

    int segmentStart = 0;
    int i = Character.charCount(firstCp);
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final int charCount = Character.charCount(codePoint);
      final int current = WordBreakProperty.ordinalOf(codePoint);

      // One table read per character. It is the decision for the common case and, as GO_SLOW, the
      // "current is special" flag; combined with the carried prevSpecial it avoids the two SPECIAL
      // look-ups the rules would otherwise need.
      final byte action = FAST[last * CLASS_COUNT + current];
      final boolean currentSpecial = action == GO_SLOW;
      final boolean breakHere;
      if (prevSpecial || currentSpecial) {
        breakHere = breakAtSpecial(prev, current, codePoint, last, secondLast,
            regionalIndicatorRun, text, i + charCount, length);
      } else {
        breakHere = action == CONSULT
            ? consult(text, i + charCount, length, current, last, secondLast, regionalIndicatorRun)
            : action == BREAK;
      }

      if (breakHere) {
        consumer.accept(segmentStart, i);
        segmentStart = i;
      }

      if (!isIgnorable(current)) {
        secondLast = last;
        last = current;
        regionalIndicatorRun = current == REGIONAL_INDICATOR_ORDINAL ? regionalIndicatorRun + 1 : 0;
      }
      prev = current;
      prevSpecial = currentSpecial;
      i += charCount;
    }
    consumer.accept(segmentStart, length);
  }

  // Handles a position where the previous or current class is special: applies the WB3 family and
  // WB4 (which depend on the immediately preceding code point), then falls back to the transition
  // table for the WB5-WB999 rules.
  private static boolean breakAtSpecial(int prev, int current, int codePoint, int last,
      int secondLast, int regionalIndicatorRun, CharSequence text, int nextFrom, int length) {
    if (prev == CR_ORDINAL && current == LF_ORDINAL) {
      return false;                                                       // WB3
    }
    if (prev == CR_ORDINAL || prev == LF_ORDINAL || prev == NEWLINE_ORDINAL) {
      return true;                                                        // WB3a
    }
    if (current == CR_ORDINAL || current == LF_ORDINAL || current == NEWLINE_ORDINAL) {
      return true;                                                        // WB3b
    }
    if (prev == ZWJ_ORDINAL && ExtendedPictographic.is(codePoint)) {
      return false;                                                       // WB3c
    }
    if (prev == WSEG_SPACE_ORDINAL && current == WSEG_SPACE_ORDINAL) {
      return false;                                                       // WB3d
    }
    if (current == EXTEND_ORDINAL || current == FORMAT_ORDINAL || current == ZWJ_ORDINAL) {
      return false;                                                       // WB4
    }
    final byte action = TRANSITION[last * CLASS_COUNT + current];
    return action == CONSULT
        ? consult(text, nextFrom, length, current, last, secondLast, regionalIndicatorRun)
        : action == BREAK;
  }

  // Resolves a CONSULT cell: a look-ahead (WB6/WB7b/WB12) or parity (WB15/WB16) rule applies, so
  // the next significant value is read (the only place it is needed) and the full cascade is run.
  private static boolean consult(CharSequence text, int nextFrom, int length, int current,
      int last, int secondLast, int regionalIndicatorRun) {
    final WordBreak next = nextSignificant(text, nextFrom, length);
    return afterPrefix(CLASSES[current], CLASSES[last], CLASSES[secondLast], next,
        regionalIndicatorRun);
  }

  /**
   * Returns the word boundary character offsets in {@code text}, in ascending order, including the
   * boundaries at {@code 0} and {@code text.length()}.
   *
   * @param text The text to segment.
   * @return The boundary offsets; for empty text, {@code [0]}.
   */
  public static int[] boundaries(CharSequence text) {
    if (text.length() == 0) {
      return new int[] {0};
    }
    final IntList offsets = new IntList();
    offsets.add(0); // WB1: break at start of text.
    // Boundaries are 0 followed by every segment end; the last end is the text length (WB2).
    forEachSegment(text, (start, end) -> offsets.add(end));
    return offsets.toArray();
  }

  /**
   * Returns the word segments of {@code text} as spans between consecutive boundaries.
   *
   * @param text The text to segment.
   * @return The segment spans, in order.
   */
  public static List<Span> segments(CharSequence text) {
    final List<Span> spans = new ArrayList<>();
    forEachSegment(text, (start, end) -> spans.add(new Span(start, end)));
    return spans;
  }

  // The Word_Break value of the next non-ignorable code point at or after "from" (else OTHER).
  private static WordBreak nextSignificant(CharSequence text, int from, int length) {
    for (int j = from; j < length; ) {
      final int codePoint = Character.codePointAt(text, j);
      final WordBreak value = WordBreakProperty.of(codePoint);
      if (!isIgnorable(value)) {
        return value;
      }
      j += Character.charCount(codePoint);
    }
    return WordBreak.OTHER;
  }

  // Applies WB5 through WB999. These rules depend only on the last two significant values, the
  // current value, the next significant value, and the regional-indicator parity, never on the
  // immediately preceding code point; that is what lets them be captured by the transition table.
  // "last"/"secondLast" skip the WB4-absorbed characters.
  private static boolean afterPrefix(WordBreak current, WordBreak last, WordBreak secondLast,
      WordBreak next, int regionalIndicatorRun) {
    if (isAhLetter(last) && isAhLetter(current)) {
      return false;                                                       // WB5
    }
    if (isAhLetter(last) && isMidLetter(current) && isAhLetter(next)) {
      return false;                                                       // WB6
    }
    if (isAhLetter(secondLast) && isMidLetter(last) && isAhLetter(current)) {
      return false;                                                       // WB7
    }
    if (last == WordBreak.HEBREW_LETTER && current == WordBreak.SINGLE_QUOTE) {
      return false;                                                       // WB7a
    }
    if (last == WordBreak.HEBREW_LETTER && current == WordBreak.DOUBLE_QUOTE
        && next == WordBreak.HEBREW_LETTER) {
      return false;                                                       // WB7b
    }
    if (secondLast == WordBreak.HEBREW_LETTER && last == WordBreak.DOUBLE_QUOTE
        && current == WordBreak.HEBREW_LETTER) {
      return false;                                                       // WB7c
    }
    if (last == WordBreak.NUMERIC && current == WordBreak.NUMERIC) {
      return false;                                                       // WB8
    }
    if (isAhLetter(last) && current == WordBreak.NUMERIC) {
      return false;                                                       // WB9
    }
    if (last == WordBreak.NUMERIC && isAhLetter(current)) {
      return false;                                                       // WB10
    }
    if (secondLast == WordBreak.NUMERIC && isMidNumber(last) && current == WordBreak.NUMERIC) {
      return false;                                                       // WB11
    }
    if (last == WordBreak.NUMERIC && isMidNumber(current) && next == WordBreak.NUMERIC) {
      return false;                                                       // WB12
    }
    if (last == WordBreak.KATAKANA && current == WordBreak.KATAKANA) {
      return false;                                                       // WB13
    }
    if ((isAhLetter(last) || last == WordBreak.NUMERIC || last == WordBreak.KATAKANA
        || last == WordBreak.EXTEND_NUM_LET) && current == WordBreak.EXTEND_NUM_LET) {
      return false;                                                       // WB13a
    }
    if (last == WordBreak.EXTEND_NUM_LET && (isAhLetter(current) || current == WordBreak.NUMERIC
        || current == WordBreak.KATAKANA)) {
      return false;                                                       // WB13b
    }
    if (current == WordBreak.REGIONAL_INDICATOR && last == WordBreak.REGIONAL_INDICATOR
        && (regionalIndicatorRun & 1) == 1) {
      return false;                                                       // WB15 / WB16
    }
    return true;                                                          // WB999
  }

  private static boolean isIgnorable(WordBreak value) {
    return value == WordBreak.EXTEND || value == WordBreak.FORMAT || value == WordBreak.ZWJ;
  }

  private static boolean isIgnorable(int ordinal) {
    return ordinal == EXTEND_ORDINAL || ordinal == FORMAT_ORDINAL || ordinal == ZWJ_ORDINAL;
  }

  private static boolean isAhLetter(WordBreak value) {
    return value == WordBreak.ALETTER || value == WordBreak.HEBREW_LETTER;
  }

  // MidLetter | MidNumLet | Single_Quote (the "MidLetterQ" set used by WB6 and WB7).
  private static boolean isMidLetter(WordBreak value) {
    return value == WordBreak.MID_LETTER || value == WordBreak.MID_NUM_LET
        || value == WordBreak.SINGLE_QUOTE;
  }

  // MidNum | MidNumLet | Single_Quote (the set used by WB11 and WB12).
  private static boolean isMidNumber(WordBreak value) {
    return value == WordBreak.MID_NUM || value == WordBreak.MID_NUM_LET
        || value == WordBreak.SINGLE_QUOTE;
  }

  // A minimal growable int array, so boundaries() makes one backing allocation instead of one per
  // boundary (an ArrayList<Integer> would box every offset).
  private static final class IntList {
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private int[] values = new int[16];
    private int size;

    void add(int value) {
      if (size == values.length) {
        // Overflow-aware 1.5x growth so a very large boundary count never wraps to a negative
        // capacity (NegativeArraySizeException); it degrades to a clean OutOfMemoryError instead.
        int newCapacity = values.length + (values.length >> 1);
        if (newCapacity < 0 || newCapacity > MAX_ARRAY_SIZE) {
          newCapacity = MAX_ARRAY_SIZE;
        }
        values = Arrays.copyOf(values, newCapacity);
      }
      values[size++] = value;
    }

    int[] toArray() {
      return Arrays.copyOf(values, size);
    }
  }
}

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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.BitSet;

import opennlp.tools.util.normalizer.BundledUnicodeData;
import opennlp.tools.util.normalizer.HexCodePoints;

/**
 * Checks the Unicode {@code Extended_Pictographic} property of a code point.
 *
 * <p>This is the one extra property the word boundary algorithm needs (rule WB3c), to keep emoji
 * zero-width-joiner sequences together. The data is loaded on first use from the bundled
 * {@code ExtendedPictographic.txt} resource, the {@code Extended_Pictographic} lines extracted
 * from the Unicode <a href="https://www.unicode.org/Public/UCD/latest/ucd/emoji/emoji-data.txt">
 * {@code emoji-data.txt}</a>, and stored in a {@link BitSet}, so membership is an O(1) bit
 * check.</p>
 */
public final class ExtendedPictographic {

  private static final String RESOURCE = "ExtendedPictographic.txt";

  private static final BundledUnicodeData.Lazy<BitSet> MEMBERS =
      new BundledUnicodeData.Lazy<>(ExtendedPictographic::load);

  private ExtendedPictographic() {
  }

  /**
   * {@return the resolved member bit set} Package-visible so a per-pass caller can resolve the set
   * once (see {@link #is(BitSet, int)}) rather than once per code point.
   *
   * @throws IllegalStateException Thrown if the bundled data resource is missing.
   * @throws UncheckedIOException Thrown if the bundled data resource cannot be read.
   * @throws IllegalArgumentException Thrown if the bundled data is malformed.
   */
  static BitSet members() {
    return MEMBERS.get();
  }

  /**
   * {@return the member set parsed from the bundled {@code ExtendedPictographic.txt} resource}
   *
   * @throws IllegalStateException Thrown if the resource is missing.
   * @throws UncheckedIOException Thrown if the resource cannot be read.
   * @throws IllegalArgumentException Thrown if the data is malformed.
   */
  private static BitSet load() {
    return BundledUnicodeData.load(ExtendedPictographic.class, RESOURCE, "Extended_Pictographic",
        in -> {
          final BitSet set = new BitSet();
          parse(in, set);
          return set;
        });
  }

  /**
   * Parses {@code Extended_Pictographic} definition lines into {@code set}. Package-visible so the
   * malformed-data handling can be exercised without the bundled resource.
   *
   * @param in  The definition lines to read.
   * @param set The bit set receiving the member code points.
   * @throws IOException Thrown if reading {@code in} fails.
   * @throws IllegalArgumentException Thrown if a definition line is malformed.
   */
  static void parse(InputStream in, BitSet set) throws IOException {
    BundledUnicodeData.forEachContentLine(in, (content, lineNumber) -> {
      // Only the code-point column is needed; the property value after ';' is implicit (this is a
      // filtered single-property file), so a line with no ';' is taken whole -- unlike
      // WordBreakProperty, whose value column is required.
      final int semicolon = content.indexOf(';');
      final String codePoints = (semicolon < 0 ? content : content.substring(0, semicolon)).strip();
      final int[] range;
      try {
        range = HexCodePoints.parseRange(codePoints);
      } catch (IllegalArgumentException e) {
        // Fail loud naming the bad line, the same way the sibling loaders do.
        throw new IllegalArgumentException(
            "Malformed Extended_Pictographic data in " + RESOURCE + ": " + content, e);
      }
      set.set(range[0], range[1] + 1);
    });
  }

  /**
   * {@return whether a code point has the {@code Extended_Pictographic} property}
   *
   * @param codePoint The code point. Values outside {@code [0, U+10FFFF]} return {@code false}.
   */
  public static boolean is(int codePoint) {
    return is(members(), codePoint);
  }

  /**
   * Like {@link #is(int)} but against an already-resolved set, so a caller that checks many code
   * points in one pass ({@link WordSegmenter}, {@link WordType}) pays the volatile read behind
   * {@link #members()} once for the whole pass rather than once per code point.
   *
   * @param resolved  The resolved member set from {@link #members()}.
   * @param codePoint The code point. Values outside {@code [0, U+10FFFF]} return {@code false}.
   * @return Whether the code point has the {@code Extended_Pictographic} property.
   */
  static boolean is(BitSet resolved, int codePoint) {
    return codePoint >= 0 && codePoint <= Character.MAX_CODE_POINT && resolved.get(codePoint);
  }
}

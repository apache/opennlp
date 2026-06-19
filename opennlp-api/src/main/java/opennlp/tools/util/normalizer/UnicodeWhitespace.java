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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reference data for Unicode whitespace, plus O(1) membership lookups.
 *
 * <p>This is a static, immutable table of the {@code 25} code points that carry the Unicode
 * {@code White_Space} property, and the related {@code 6} code points that are commonly mistaken
 * for whitespace but carry {@code White_Space=no} (zero-width and other format characters).
 * The data mirrors the tables in
 * <a href="https://en.wikipedia.org/wiki/Whitespace_character">Whitespace character</a>
 * and the Unicode Character Database ({@code PropList.txt}).</p>
 *
 * <p>The membership test is deliberately built from this explicit table rather than from
 * {@link Character#isWhitespace(int)} or {@link Character#isSpaceChar(int)}, both of which
 * disagree with the Unicode {@code White_Space} property. {@code Character.isWhitespace}
 * excludes the non-breaking spaces and {@code NEL} but includes the information-separator
 * controls {@code U+001C}-{@code U+001F}; {@code Character.isSpaceChar} excludes tab, newline,
 * and the other line breaks. {@link #isWhitespace(int)} matches the standard exactly.</p>
 */
public final class UnicodeWhitespace {

  /** Unicode general category for a whitespace or related code point. */
  public enum Category {
    /** {@code Cc} - control. */
    Cc,
    /** {@code Zs} - space separator. */
    Zs,
    /** {@code Zl} - line separator. */
    Zl,
    /** {@code Zp} - paragraph separator. */
    Zp,
    /** {@code Cf} - format (the related, non-whitespace code points). */
    Cf
  }

  /** Line-breaking behavior, mirroring the "Notes" column of the reference table. */
  public enum Breaking {
    /** A break opportunity, but not a forced line break (e.g. {@code SPACE}). */
    MAY_BREAK,
    /** A forced line or paragraph break (e.g. {@code LF}, {@code LINE SEPARATOR}). */
    LINE_BREAK,
    /** A space that suppresses line breaking (e.g. {@code NO-BREAK SPACE}). */
    NON_BREAKING
  }

  /**
   * One Unicode whitespace code point and its reference attributes.
   *
   * @param codePoint The Unicode code point.
   * @param name The Unicode character name, lower cased as in the reference table.
   * @param abbreviation The common abbreviation (for example {@code NBSP}), or {@code ""} if none.
   * @param category The Unicode general {@link Category category}.
   * @param breaking The line-{@link Breaking breaking} behavior.
   */
  public record WhitespaceCharacter(int codePoint, String name, String abbreviation,
                                    Category category, Breaking breaking) {

    /** {@return whether this code point forces a line or paragraph break} */
    public boolean isLineBreak() {
      return breaking == Breaking.LINE_BREAK;
    }

    /** {@return whether this is a non-breaking space} */
    public boolean isNonBreaking() {
      return breaking == Breaking.NON_BREAKING;
    }

    /** {@return the {@code U+XXXX} notation for this code point} */
    public String toUnicodeNotation() {
      return String.format("U+%04X", codePoint);
    }
  }

  /**
   * One related code point that is commonly confused with whitespace but is not
   * ({@code White_Space=no}). These are format characters and must not be treated as, or
   * normalized like, whitespace.
   *
   * @param codePoint The Unicode code point.
   * @param name The Unicode character name, lower cased as in the reference table.
   * @param abbreviation The common abbreviation (for example {@code BOM}), or {@code ""} if none.
   * @param note A short description of what the character actually does.
   */
  public record RelatedCharacter(int codePoint, String name, String abbreviation, String note) {

    /** {@return the {@code U+XXXX} notation for this code point} */
    public String toUnicodeNotation() {
      return String.format("U+%04X", codePoint);
    }
  }

  private static final List<WhitespaceCharacter> WHITESPACE = List.of(
      new WhitespaceCharacter(0x0009, "character tabulation", "HT", Category.Cc, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x000A, "line feed", "LF", Category.Cc, Breaking.LINE_BREAK),
      new WhitespaceCharacter(0x000B, "line tabulation", "VT", Category.Cc, Breaking.LINE_BREAK),
      new WhitespaceCharacter(0x000C, "form feed", "FF", Category.Cc, Breaking.LINE_BREAK),
      new WhitespaceCharacter(0x000D, "carriage return", "CR", Category.Cc, Breaking.LINE_BREAK),
      new WhitespaceCharacter(0x0020, "space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x0085, "next line", "NEL", Category.Cc, Breaking.LINE_BREAK),
      new WhitespaceCharacter(0x00A0, "no-break space", "NBSP", Category.Zs, Breaking.NON_BREAKING),
      new WhitespaceCharacter(0x1680, "ogham space mark", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2000, "en quad", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2001, "em quad", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2002, "en space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2003, "em space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2004, "three-per-em space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2005, "four-per-em space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2006, "six-per-em space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2007, "figure space", "", Category.Zs, Breaking.NON_BREAKING),
      new WhitespaceCharacter(0x2008, "punctuation space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2009, "thin space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x200A, "hair space", "", Category.Zs, Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x2028, "line separator", "", Category.Zl, Breaking.LINE_BREAK),
      new WhitespaceCharacter(0x2029, "paragraph separator", "", Category.Zp, Breaking.LINE_BREAK),
      new WhitespaceCharacter(0x202F, "narrow no-break space", "NNBSP", Category.Zs,
          Breaking.NON_BREAKING),
      new WhitespaceCharacter(0x205F, "medium mathematical space", "MMSP", Category.Zs,
          Breaking.MAY_BREAK),
      new WhitespaceCharacter(0x3000, "ideographic space", "", Category.Zs, Breaking.MAY_BREAK));

  private static final List<RelatedCharacter> LOOKALIKES = List.of(
      new RelatedCharacter(0x180E, "mongolian vowel separator", "MVS",
          "format character; narrow space for Mongolian"),
      new RelatedCharacter(0x200B, "zero width space", "ZWSP",
          "format; word boundary indicator, no visible width"),
      new RelatedCharacter(0x200C, "zero width non-joiner", "ZWNJ",
          "format; prevents character connection"),
      new RelatedCharacter(0x200D, "zero width joiner", "ZWJ",
          "format; enables character connection"),
      new RelatedCharacter(0x2060, "word joiner", "WJ",
          "format; non-breaking, no line break point"),
      new RelatedCharacter(0xFEFF, "zero width no-break space", "BOM",
          "format; byte order mark"));

  private static final Map<Integer, WhitespaceCharacter> BY_CODE_POINT = new HashMap<>();
  private static final BitSet MEMBERSHIP = new BitSet();
  private static final BitSet LOOKALIKE_MEMBERSHIP = new BitSet();
  private static final int[] CODE_POINTS = new int[WHITESPACE.size()];
  private static final List<WhitespaceCharacter> LINE_BREAKS = new ArrayList<>();
  private static final List<WhitespaceCharacter> NON_BREAKING = new ArrayList<>();

  static {
    for (int i = 0; i < WHITESPACE.size(); i++) {
      final WhitespaceCharacter ws = WHITESPACE.get(i);
      BY_CODE_POINT.put(ws.codePoint(), ws);
      MEMBERSHIP.set(ws.codePoint());
      CODE_POINTS[i] = ws.codePoint();
      if (ws.isLineBreak()) {
        LINE_BREAKS.add(ws);
      }
      if (ws.isNonBreaking()) {
        NON_BREAKING.add(ws);
      }
    }
    for (final RelatedCharacter related : LOOKALIKES) {
      LOOKALIKE_MEMBERSHIP.set(related.codePoint());
    }
  }

  private UnicodeWhitespace() {
  }

  /**
   * Tests whether a code point carries the Unicode {@code White_Space} property.
   *
   * @param codePoint The code point to test. Out-of-range values (negative or beyond
   *     {@link Character#MAX_CODE_POINT}) simply return {@code false}.
   * @return {@code true} if the code point is one of the {@code 25} Unicode whitespace characters.
   */
  public static boolean isWhitespace(int codePoint) {
    return codePoint >= 0 && codePoint <= Character.MAX_CODE_POINT && MEMBERSHIP.get(codePoint);
  }

  /**
   * Tests whether a code point is one of the related, non-whitespace look-alike format characters.
   *
   * @param codePoint The code point to test.
   * @return {@code true} if the code point is in the {@link #lookalikes() look-alike} set.
   */
  public static boolean isLookalike(int codePoint) {
    return codePoint >= 0 && codePoint <= Character.MAX_CODE_POINT
        && LOOKALIKE_MEMBERSHIP.get(codePoint);
  }

  /**
   * Looks up the reference entry for a whitespace code point.
   *
   * @param codePoint The code point.
   * @return The {@link WhitespaceCharacter}, or {@link Optional#empty()} if it is not whitespace.
   */
  public static Optional<WhitespaceCharacter> byCodePoint(int codePoint) {
    return Optional.ofNullable(BY_CODE_POINT.get(codePoint));
  }

  /** {@return the {@code 25} Unicode whitespace characters, in ascending code point order} */
  public static List<WhitespaceCharacter> all() {
    return WHITESPACE;
  }

  /** {@return the related, non-whitespace look-alike format characters} */
  public static List<RelatedCharacter> lookalikes() {
    return LOOKALIKES;
  }

  /** {@return the whitespace characters that force a line or paragraph break} */
  public static List<WhitespaceCharacter> lineBreaks() {
    return List.copyOf(LINE_BREAKS);
  }

  /** {@return the non-breaking whitespace characters} */
  public static List<WhitespaceCharacter> nonBreaking() {
    return List.copyOf(NON_BREAKING);
  }

  /** {@return the whitespace code points, in ascending order} */
  public static int[] codePoints() {
    return CODE_POINTS.clone();
  }
}

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
 * Reference data for Unicode dashes, plus O(1) membership lookups.
 *
 * <p>This is a static, immutable table of every code point that carries the Unicode {@code Dash}
 * property (Unicode Character Database, {@code PropList.txt}). The set is broader than the
 * {@code Pd} (dash punctuation) general category: it also includes the swung dash ({@code Po})
 * and the mathematical minus signs ({@code Sm}). Java offers no {@code Dash} predicate and
 * {@code \p{Pd}} would miss the {@code Sm} and {@code Po} members, which is why the set is kept
 * here explicitly.</p>
 *
 * <p>Two distinctions matter for normalization:</p>
 * <ul>
 *   <li>The three mathematical minus signs ({@code U+207B}, {@code U+208B}, {@code U+2212}, all
 *   category {@code Sm}) are excluded from {@link #defaultDashCodePoints()} because flattening
 *   them to {@code U+002D} can change mathematical meaning. They remain available through
 *   {@link #codePoints()} for callers that opt in.</li>
 *   <li>{@code U+00AD} SOFT HYPHEN is deliberately absent: it is a format character
 *   ({@code White_Space=no}, {@code Dash=no}), an invisible line-break hint, and must not be
 *   turned into a visible hyphen.</li>
 * </ul>
 */
public final class UnicodeDash {

  /** The canonical ASCII dash that dashes are normalized to: {@code U+002D} HYPHEN-MINUS. */
  public static final int HYPHEN_MINUS = 0x002D;

  /** The Unicode general category of a dash code point. */
  public enum Category {
    /** {@code Pd} - dash punctuation. */
    Pd,
    /** {@code Po} - other punctuation (the swung dash). */
    Po,
    /** {@code Sm} - math symbol (the minus signs). */
    Sm
  }

  /**
   * One Unicode dash code point and its reference attributes.
   *
   * @param codePoint The Unicode code point.
   * @param name The Unicode character name, lower cased.
   * @param category The Unicode general {@link Category category}.
   */
  public record DashCharacter(int codePoint, String name, Category category) {

    /** {@return whether this is a mathematical minus sign (category {@code Sm})} */
    public boolean isMathematical() {
      return category == Category.Sm;
    }

    /** {@return whether this code point is outside the Basic Multilingual Plane} */
    public boolean isSupplementary() {
      return codePoint > 0xFFFF;
    }

    /** {@return the {@code U+XXXX} notation for this code point} */
    public String toUnicodeNotation() {
      return String.format("U+%04X", codePoint);
    }
  }

  private static final List<DashCharacter> DASHES = List.of(
      new DashCharacter(0x002D, "hyphen-minus", Category.Pd),
      new DashCharacter(0x058A, "armenian hyphen", Category.Pd),
      new DashCharacter(0x05BE, "hebrew punctuation maqaf", Category.Pd),
      new DashCharacter(0x1400, "canadian syllabics hyphen", Category.Pd),
      new DashCharacter(0x1806, "mongolian todo soft hyphen", Category.Pd),
      new DashCharacter(0x2010, "hyphen", Category.Pd),
      new DashCharacter(0x2011, "non-breaking hyphen", Category.Pd),
      new DashCharacter(0x2012, "figure dash", Category.Pd),
      new DashCharacter(0x2013, "en dash", Category.Pd),
      new DashCharacter(0x2014, "em dash", Category.Pd),
      new DashCharacter(0x2015, "horizontal bar", Category.Pd),
      new DashCharacter(0x2053, "swung dash", Category.Po),
      new DashCharacter(0x207B, "superscript minus", Category.Sm),
      new DashCharacter(0x208B, "subscript minus", Category.Sm),
      new DashCharacter(0x2212, "minus sign", Category.Sm),
      new DashCharacter(0x2E17, "double oblique hyphen", Category.Pd),
      new DashCharacter(0x2E1A, "hyphen with diaeresis", Category.Pd),
      new DashCharacter(0x2E3A, "two-em dash", Category.Pd),
      new DashCharacter(0x2E3B, "three-em dash", Category.Pd),
      new DashCharacter(0x2E40, "double hyphen", Category.Pd),
      new DashCharacter(0x2E5D, "oblique hyphen", Category.Pd),
      new DashCharacter(0x301C, "wave dash", Category.Pd),
      new DashCharacter(0x3030, "wavy dash", Category.Pd),
      new DashCharacter(0x30A0, "katakana-hiragana double hyphen", Category.Pd),
      new DashCharacter(0xFE31, "presentation form for vertical em dash", Category.Pd),
      new DashCharacter(0xFE32, "presentation form for vertical en dash", Category.Pd),
      new DashCharacter(0xFE58, "small em dash", Category.Pd),
      new DashCharacter(0xFE63, "small hyphen-minus", Category.Pd),
      new DashCharacter(0xFF0D, "fullwidth hyphen-minus", Category.Pd),
      new DashCharacter(0x10D6E, "garay hyphen", Category.Pd),
      new DashCharacter(0x10EAD, "yezidi hyphenation mark", Category.Pd));

  private static final Map<Integer, DashCharacter> BY_CODE_POINT = new HashMap<>();
  private static final BitSet MEMBERSHIP = new BitSet();
  private static final int[] CODE_POINTS = new int[DASHES.size()];
  private static final List<DashCharacter> MATHEMATICAL = new ArrayList<>();
  private static final int[] DEFAULT_CODE_POINTS;

  static {
    final List<Integer> defaults = new ArrayList<>();
    for (int i = 0; i < DASHES.size(); i++) {
      final DashCharacter dash = DASHES.get(i);
      BY_CODE_POINT.put(dash.codePoint(), dash);
      MEMBERSHIP.set(dash.codePoint());
      CODE_POINTS[i] = dash.codePoint();
      if (dash.isMathematical()) {
        MATHEMATICAL.add(dash);
      } else {
        defaults.add(dash.codePoint());
      }
    }
    DEFAULT_CODE_POINTS = defaults.stream().mapToInt(Integer::intValue).toArray();
  }

  private UnicodeDash() {
  }

  /**
   * Tests whether a code point carries the Unicode {@code Dash} property.
   *
   * @param codePoint The code point to test. Out-of-range values return {@code false}.
   * @return {@code true} if the code point is one of the Unicode dash characters.
   */
  public static boolean isDash(int codePoint) {
    return codePoint >= 0 && codePoint <= Character.MAX_CODE_POINT && MEMBERSHIP.get(codePoint);
  }

  /**
   * Looks up the reference entry for a dash code point.
   *
   * @param codePoint The code point.
   * @return The {@link DashCharacter}, or {@link Optional#empty()} if it is not a dash.
   */
  public static Optional<DashCharacter> byCodePoint(int codePoint) {
    return Optional.ofNullable(BY_CODE_POINT.get(codePoint));
  }

  /** {@return all Unicode dash characters, in ascending code point order} */
  public static List<DashCharacter> all() {
    return DASHES;
  }

  /** {@return the mathematical minus signs, excluded from the default normalization set} */
  public static List<DashCharacter> mathematical() {
    return List.copyOf(MATHEMATICAL);
  }

  /** {@return all dash code points, in ascending order, including the mathematical minus signs} */
  public static int[] codePoints() {
    return CODE_POINTS.clone();
  }

  /**
   * {@return the dash code points used for normalization by default, in ascending order}
   *
   * <p>This is every dash except the mathematical minus signs, so flattening to
   * {@link #HYPHEN_MINUS} does not silently rewrite mathematics.</p>
   */
  public static int[] defaultDashCodePoints() {
    return DEFAULT_CODE_POINTS.clone();
  }
}

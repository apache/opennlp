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

import java.util.BitSet;

/**
 * The category of a {@linkplain WordTokenizer word token}. {@link #ALPHANUMERIC} and
 * {@link #NUMERIC} cover letter and digit words; the remaining categories identify scripts and
 * emoji that benefit from script-specific handling. The boundaries themselves follow the Unicode
 * release shipped with {@link WordSegmenter}.
 */
public enum WordType {

  /** A token that contains at least one letter (optionally mixed with digits and connectors). */
  ALPHANUMERIC,

  /** A token made up entirely of digits and numeric connectors. */
  NUMERIC,

  /** A token containing a Han ideograph (one ideograph per token under UAX #29 segmentation). */
  IDEOGRAPHIC,

  /** A Hiragana token. */
  HIRAGANA,

  /** A Katakana token. */
  KATAKANA,

  /** A Hangul token. */
  HANGUL,

  /** A token in a Southeast Asian script that requires dictionary segmentation (Thai, Lao, ...). */
  SOUTHEAST_ASIAN,

  /** An emoji, emoji sequence, or regional-indicator flag. */
  EMOJI;

  private static final int REGIONAL_INDICATOR_FIRST = 0x1F1E6;
  private static final int REGIONAL_INDICATOR_LAST = 0x1F1FF;

  // No code point below this can belong to a script-specific category (the lowest is Thai, U+0E00),
  // so Latin, Greek, Cyrillic, and ASCII text skips the relatively costly script lookup entirely.
  private static final int LOWEST_SCRIPT_CODE_POINT = 0x0E00;

  // ASCII kind: 0 = neither, 1 = letter, 2 = digit. No ASCII code point is pictographic or in a
  // script-specific category, so ASCII characters skip those tests and the Character.isLetter /
  // isDigit general-category look-ups entirely.
  private static final byte[] ASCII_KIND = buildAsciiKind();

  private static byte[] buildAsciiKind() {
    final byte[] kind = new byte[0x80];
    for (int c = '0'; c <= '9'; c++) {
      kind[c] = 2;
    }
    for (int c = 'A'; c <= 'Z'; c++) {
      kind[c] = 1;
    }
    for (int c = 'a'; c <= 'z'; c++) {
      kind[c] = 1;
    }
    return kind;
  }

  // Classifies the code points in text over [start, end) as a word token type, or returns null
  // when the range is not a word (pure whitespace, punctuation, or symbols). Emoji win over
  // scripts, scripts over the generic alphanumeric/numeric split. The script category is taken from
  // the first script code point in the range; UAX #29 word segments are single-script in practice, so
  // for an unusual mixed-script run this reports the leading script, not a per-character determination.
  static WordType of(CharSequence text, int start, int end) {
    boolean hasLetter = false;
    boolean hasDigit = false;
    WordType script = null;
    // Resolved once per call rather than once per code point in the loop below, so the volatile read
    // behind ExtendedPictographic.members() is not repeated for every non-ASCII character of a token.
    final BitSet pictographs = ExtendedPictographic.members();
    for (int i = start; i < end; ) {
      final int codePoint = Character.codePointAt(text, i);
      i += Character.charCount(codePoint);
      if (codePoint < 0x80) {
        final int kind = ASCII_KIND[codePoint];
        if (kind == 1) {
          hasLetter = true;
        } else if (kind == 2) {
          hasDigit = true;
        }
        continue;
      }
      if (ExtendedPictographic.is(pictographs, codePoint) || isRegionalIndicator(codePoint)) {
        return EMOJI;
      }
      if (codePoint >= LOWEST_SCRIPT_CODE_POINT && script == null) {
        script = scriptType(codePoint);
      }
      if (Character.isLetter(codePoint)) {
        hasLetter = true;
      } else if (Character.isDigit(codePoint)) {
        hasDigit = true;
      }
    }
    if (script != null) {
      return script;
    }
    if (hasLetter) {
      return ALPHANUMERIC;
    }
    if (hasDigit) {
      return NUMERIC;
    }
    return null;
  }

  private static boolean isRegionalIndicator(int codePoint) {
    return codePoint >= REGIONAL_INDICATOR_FIRST && codePoint <= REGIONAL_INDICATOR_LAST;
  }

  // Maps a code point to a script-specific token type, or null for scripts (Latin, Greek, ...) that
  // fall through to the generic alphanumeric category.
  private static WordType scriptType(int codePoint) {
    switch (Character.UnicodeScript.of(codePoint)) {
      case HAN:
        return IDEOGRAPHIC;
      case HIRAGANA:
        return HIRAGANA;
      case KATAKANA:
        return KATAKANA;
      case HANGUL:
        return HANGUL;
      case THAI:
      case LAO:
      case MYANMAR:
      case KHMER:
      case TAI_LE:
      case NEW_TAI_LUE:
      case TAI_VIET:
        return SOUTHEAST_ASIAN;
      default:
        return null;
    }
  }
}

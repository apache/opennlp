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

import java.util.Optional;

/**
 * The derived-facts layer of the emoji annotation record store: decodes flag emoji to ISO 3166
 * codes purely from the code point sequence, with no data file. An emoji flag sequence is two
 * regional indicator symbols encoding an ISO 3166-1 alpha-2 code (U+1F1E9 U+1F1EA decodes to
 * {@code DE}), and an emoji tag sequence over U+1F3F4 WAVING BLACK FLAG encodes an ISO 3166-2
 * subdivision code in tag characters (the England flag decodes to {@code GB-ENG}); both mechanisms
 * are defined by <a href="https://www.unicode.org/reports/tr51/">UTS&#160;#51</a>.
 *
 * <p>Decoding is mechanical and deliberately does not check <em>assignment</em>: U+1F1FD U+1F1FD
 * decodes to {@code XX} even though no such region is assigned, because validity against the
 * region registry is a join-time question for whatever gazetteer the user has installed (see the
 * joined-facts layer), not a bundled fact. This is also why no per-flag rows exist in
 * {@code emoji-annotations.txt}: the region code is fully determined by the sequence itself.</p>
 *
 * <p>{@link #isoRegion(CharSequence)} is the strict decoder: it fails loud on a sequence that is
 * flag-shaped but malformed, such as a lone regional indicator or an unterminated tag sequence.
 * {@link #isFlag(CharSequence)} is the total predicate for bulk callers that must never throw on
 * degenerate real-world text. The expected input is one symbol, for example one
 * {@link Term#original()} token; the UAX&#160;#29 word tokenizer already segments a run of
 * adjacent flags into regional indicator pairs.</p>
 */
public final class EmojiFlags {

  private static final int FIRST_REGIONAL_INDICATOR = 0x1F1E6; // REGIONAL INDICATOR SYMBOL LETTER A
  private static final int LAST_REGIONAL_INDICATOR = 0x1F1FF;  // REGIONAL INDICATOR SYMBOL LETTER Z
  private static final int WAVING_BLACK_FLAG = 0x1F3F4;
  private static final int CANCEL_TAG = 0xE007F;
  // A tag character maps to the ASCII character at (code point - TAG_OFFSET).
  private static final int TAG_OFFSET = 0xE0000;

  private EmojiFlags() {
  }

  /**
   * {@return whether {@code codePoint} is one of the 26 regional indicator symbols}
   *
   * @param codePoint The code point to test.
   */
  public static boolean isRegionalIndicator(int codePoint) {
    return codePoint >= FIRST_REGIONAL_INDICATOR && codePoint <= LAST_REGIONAL_INDICATOR;
  }

  /**
   * {@return whether {@code symbol} is exactly one well-formed flag emoji} True only for a
   * regional indicator pair or a terminated subdivision tag sequence; unlike
   * {@link #isoRegion(CharSequence)} this never throws on malformed sequences, so bulk callers
   * (per-token annotation of arbitrary text) can probe safely.
   *
   * @param symbol The code point sequence of one symbol. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code symbol} is {@code null}.
   */
  public static boolean isFlag(CharSequence symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("Symbol must not be null");
    }
    return decode(symbol, true) != null;
  }

  /**
   * Decodes one flag emoji to its ISO 3166 code: a regional indicator pair to the ISO 3166-1
   * alpha-2 code ({@code DE}) and a subdivision tag sequence to the ISO 3166-2 code
   * ({@code GB-ENG}).
   *
   * @param symbol The code point sequence of one symbol. Must not be {@code null}.
   * @return The ISO 3166 code, or empty when {@code symbol} is not flag-shaped at all (no leading
   *     regional indicator and no tag sequence over U+1F3F4; a lone U+1F3F4 and the ZWJ pirate
   *     flag are not region flags).
   * @throws IllegalArgumentException if {@code symbol} is {@code null}, or if it is flag-shaped
   *     but malformed: a lone regional indicator, more or fewer than exactly two regional
   *     indicators, or a tag sequence that is unterminated, too short for a subdivision code, not
   *     letter-led, or followed by trailing content.
   */
  public static Optional<String> isoRegion(CharSequence symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("Symbol must not be null");
    }
    return Optional.ofNullable(decode(symbol, false));
  }

  // Returns the ISO code, or null when symbol is not flag-shaped. A flag-shaped but malformed
  // sequence returns null when lenient, otherwise fails loud.
  private static String decode(CharSequence symbol, boolean lenient) {
    if (symbol.isEmpty()) {
      return null;
    }
    final int first = Character.codePointAt(symbol, 0);
    if (isRegionalIndicator(first)) {
      return decodeRegionalIndicators(symbol, first, lenient);
    }
    if (first == WAVING_BLACK_FLAG && symbol.length() > 2
        && isTagBlock(Character.codePointAt(symbol, 2))) {
      return decodeTagSequence(symbol, lenient);
    }
    return null;
  }

  // An emoji flag sequence is exactly two regional indicators; anything else that starts with one
  // (a lone indicator, an odd run, adjacent flags passed as one symbol) is malformed.
  private static String decodeRegionalIndicators(CharSequence symbol, int first, boolean lenient) {
    final int firstCount = Character.charCount(first);
    if (symbol.length() < firstCount + 2) {
      return malformed(lenient, "Malformed regional indicator sequence: a flag is a regional"
          + " indicator pair, got a lone indicator in: " + symbol);
    }
    final int second = Character.codePointAt(symbol, firstCount);
    if (!isRegionalIndicator(second)) {
      return malformed(lenient, "Malformed regional indicator sequence: expected a second"
          + " regional indicator in: " + symbol);
    }
    if (symbol.length() > firstCount + Character.charCount(second)) {
      return malformed(lenient, "Malformed regional indicator sequence: expected exactly one"
          + " regional indicator pair in: " + symbol);
    }
    return new String(new char[] {
        (char) ('A' + first - FIRST_REGIONAL_INDICATOR),
        (char) ('A' + second - FIRST_REGIONAL_INDICATOR)});
  }

  // An emoji tag sequence: U+1F3F4, tag letters/digits spelling the ISO 3166-2 code (lowercase,
  // no separator, for example gbeng), then CANCEL TAG. The caller verified the first tag
  // character, so the sequence is tag-shaped and any violation below is malformed.
  private static String decodeTagSequence(CharSequence symbol, boolean lenient) {
    final StringBuilder decoded = new StringBuilder();
    int i = 2; // past U+1F3F4
    boolean terminated = false;
    while (i < symbol.length()) {
      final int codePoint = Character.codePointAt(symbol, i);
      i += Character.charCount(codePoint);
      if (codePoint == CANCEL_TAG) {
        terminated = true;
        break;
      }
      if (!isTagCharacter(codePoint)) {
        return malformed(lenient, "Malformed emoji tag sequence: expected a tag character or"
            + " CANCEL TAG, got U+" + Integer.toHexString(codePoint).toUpperCase() + " in: "
            + symbol);
      }
      decoded.append((char) (codePoint - TAG_OFFSET));
    }
    if (!terminated) {
      return malformed(lenient,
          "Malformed emoji tag sequence: missing the CANCEL TAG terminator in: " + symbol);
    }
    if (i < symbol.length()) {
      return malformed(lenient,
          "Malformed emoji tag sequence: content after the CANCEL TAG terminator in: " + symbol);
    }
    if (decoded.length() < 3) {
      return malformed(lenient, "Malformed emoji tag sequence: an ISO 3166-2 code is a two-letter"
          + " region and a subdivision suffix, got '" + decoded + "' in: " + symbol);
    }
    if (!isTagLetter(decoded.charAt(0)) || !isTagLetter(decoded.charAt(1))) {
      return malformed(lenient, "Malformed emoji tag sequence: the region part of '" + decoded
          + "' is not two letters in: " + symbol);
    }
    // ISO 3166-2 format: uppercase region, hyphen-minus, uppercase subdivision suffix.
    final StringBuilder iso = new StringBuilder(decoded.length() + 1);
    for (int k = 0; k < decoded.length(); k++) {
      if (k == 2) {
        iso.append('-');
      }
      final char c = decoded.charAt(k);
      iso.append(isTagLetter(c) ? (char) (c - ('a' - 'A')) : c);
    }
    return iso.toString();
  }

  // Any character of the TAG block; a U+1F3F4 followed by one of these is a tag sequence attempt,
  // so its violations are malformed rather than "not a flag" (unlike, say, the ZWJ pirate flag).
  private static boolean isTagBlock(int codePoint) {
    return codePoint >= TAG_OFFSET && codePoint <= CANCEL_TAG;
  }

  // Tag characters restricted to what an ISO 3166-2 code can contain: tag digits and tag small
  // letters. (The full TAG block also carries punctuation tags; those are not valid here.)
  private static boolean isTagCharacter(int codePoint) {
    final int ascii = codePoint - TAG_OFFSET;
    return (ascii >= '0' && ascii <= '9') || (ascii >= 'a' && ascii <= 'z');
  }

  private static boolean isTagLetter(char decoded) {
    return decoded >= 'a' && decoded <= 'z';
  }

  private static String malformed(boolean lenient, String message) {
    if (lenient) {
      return null;
    }
    throw new IllegalArgumentException(message);
  }
}

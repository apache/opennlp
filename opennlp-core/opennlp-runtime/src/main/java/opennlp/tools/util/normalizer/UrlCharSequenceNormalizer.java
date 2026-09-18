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

import opennlp.tools.util.CompatibilityMode;
import opennlp.tools.util.StringUtil;

/**
 * A {@link CharSequenceNormalizer} implementation that replaces each {@code http} or
 * {@code https} URL and each email address with one space.
 *
 * <p>A URL is a scheme, {@code ://}, and a body. The scheme is {@code http} or {@code https}
 * in any letter case and starts the text or follows a code point that is neither a letter,
 * a digit, nor one of {@code _ + - . :}, so the {@code https} inside {@code git+https://}
 * or {@code blob:https://} is not a URL of its own. The body runs to the end of the text or
 * to the first Unicode whitespace, control character, unpaired surrogate, typographic
 * quotation mark, or one of {@code < > "}; it therefore includes a port, userinfo, percent
 * escapes, a bracketed IPv6 host, and non-ASCII host names and paths. Trailing sentence
 * punctuation ({@code . , ; : ! ?}), a trailing apostrophe, and a closing bracket without its
 * opening bracket inside the body are not part of the URL. A URL with another scheme is kept
 * as it is, and no email address is matched inside it.</p>
 *
 * <p>An email address is a maximal run of the local-part set {@code [-+_.0-9A-Za-z]} whose
 * left neighbor is outside that set, an {@code @}, and a domain run out of
 * {@code [-.0-9A-Za-z]} that must not start with a dot and must span at least two chars.</p>
 *
 * <p>Since 3.0.0 URLs are removed as a whole. Under {@link CompatibilityMode#LEGACY} the
 * earlier output is produced for language detector models trained with it: a URL is a
 * lowercase {@code http://} or {@code https://} followed by a run of
 * {@code [-_.?&~;+=/#0-9A-Za-z]}, and email addresses are then matched in the result, also
 * inside URLs with another scheme.</p>
 *
 * @see <a href="https://url.spec.whatwg.org/">WHATWG URL Standard</a>
 */
public class UrlCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 2023145028634552389L;

  private static final String SCHEME_SEPARATOR = "://";
  private static final String HTTP = "http";
  private static final String HTTPS = "https";

  /** The characters a scheme continues with after its first letter, per RFC 3986. */
  private static final CodePointSet SCHEME_BODY =
      AsciiChars.ALPHANUMERIC.union(CodePointSet.of('+', '-', '.'));

  /** Code points that, before the scheme, make it part of a larger token. */
  private static final CodePointSet SCHEME_NEIGHBORS = CodePointSet.of('_', '+', '-', '.', ':');

  /** Delimiters that end a URL body besides whitespace and control characters. */
  private static final CodePointSet BODY_DELIMITERS = CodePointSet.of('<', '>', '"');

  /** Punctuation given back when it trails a URL. */
  private static final CodePointSet TRAILING_PUNCTUATION =
      CodePointSet.of('.', ',', ';', ':', '!', '?', '\'');

  /** The URL body set of the legacy output: {@code [-_.?&~;+=/#0-9A-Za-z]}. */
  private static final CodePointSet LEGACY_URL_BODY = AsciiChars.ALPHANUMERIC
      .union(CodePointSet.of('-', '_', '.', '?', '&', '~', ';', '+', '=', '/', '#'));

  private static final UrlCharSequenceNormalizer INSTANCE = new UrlCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static UrlCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   * URLs and email addresses are found in one pass, so an address inside a URL of any
   * scheme is never matched on its own.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    if (CompatibilityMode.current() == CompatibilityMode.LEGACY) {
      return MailAddressScan.removeAll(removeLegacyUrls(text));
    }
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int bodyStart = schemeEnd(text, i);
      if (bodyStart != -1) {
        final int end = bodyEnd(text, bodyStart);
        if (end > bodyStart) {
          if (out == null) {
            out = new StringBuilder(length).append(text, 0, i);
          }
          if (isHttpScheme(text, i, bodyStart - SCHEME_SEPARATOR.length())) {
            out.append(' ');
          } else {
            out.append(text, i, end);
          }
          i = end;
          continue;
        }
      }
      final int mailEnd = MailAddressScan.matchEnd(text, i);
      if (mailEnd > i) {
        if (out == null) {
          out = new StringBuilder(length).append(text, 0, i);
        }
        out.append(' ');
        i = mailEnd;
        continue;
      }
      final int codePoint = Character.codePointAt(text, i);
      if (out != null) {
        out.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return out == null ? text : out.toString();
  }

  /**
   * Finds the end of a scheme and its separator that start at an index.
   *
   * @param text The text. Must not be {@code null}.
   * @param start The index the scheme must start at, inside the text.
   * @return The index after {@code ://}, or {@code -1} if no scheme with a left boundary
   *         starts there.
   */
  private int schemeEnd(CharSequence text, int start) {
    if (start > 0) {
      final int before = Character.codePointBefore(text, start);
      if (Character.isLetterOrDigit(before) || SCHEME_NEIGHBORS.contains(before)) {
        return -1;
      }
    }
    final int length = text.length();
    if (!StringUtil.isAsciiLetter(text.charAt(start))) {
      return -1;
    }
    int at = start + 1;
    while (at < length && SCHEME_BODY.contains(text.charAt(at))) {
      at++;
    }
    final int end = at + SCHEME_SEPARATOR.length();
    if (end > length) {
      return -1;
    }
    for (int k = 0; k < SCHEME_SEPARATOR.length(); k++) {
      if (text.charAt(at + k) != SCHEME_SEPARATOR.charAt(k)) {
        return -1;
      }
    }
    return end;
  }

  /**
   * Finds the end of a URL body that starts at an index.
   *
   * @param text The text. Must not be {@code null}.
   * @param bodyStart The index after the scheme separator.
   * @return The exclusive end of the body, or {@code bodyStart} if the body is empty.
   */
  private int bodyEnd(CharSequence text, int bodyStart) {
    final int length = text.length();
    int end = bodyStart;
    while (end < length) {
      final int codePoint = Character.codePointAt(text, end);
      if (endsBody(codePoint)) {
        break;
      }
      end += Character.charCount(codePoint);
    }
    return trimTrailing(text, bodyStart, end);
  }

  /**
   * Tests whether a code point ends a URL body.
   *
   * @param codePoint The code point; an unpaired surrogate is passed as its own value.
   * @return {@code true} for whitespace, a control character, an unpaired surrogate, a
   *         typographic quotation mark, or a delimiter.
   */
  private boolean endsBody(int codePoint) {
    final int type = Character.getType(codePoint);
    return StringUtil.isUnicodeWhitespace(codePoint)
        || type == Character.CONTROL
        || type == Character.INITIAL_QUOTE_PUNCTUATION
        || type == Character.FINAL_QUOTE_PUNCTUATION
        || (codePoint <= Character.MAX_VALUE && Character.isSurrogate((char) codePoint))
        || BODY_DELIMITERS.contains(codePoint);
  }

  /**
   * Gives back trailing punctuation and closing brackets that have no opening bracket inside
   * the body.
   *
   * @param text The text. Must not be {@code null}.
   * @param bodyStart The index the body starts at.
   * @param end The exclusive end of the body as scanned.
   * @return The exclusive end of the URL.
   */
  private int trimTrailing(CharSequence text, int bodyStart, int end) {
    int roundBalance = 0;
    int squareBalance = 0;
    int curlyBalance = 0;
    for (int i = bodyStart; i < end; i++) {
      switch (text.charAt(i)) {
        case '(' -> roundBalance++;
        case ')' -> roundBalance--;
        case '[' -> squareBalance++;
        case ']' -> squareBalance--;
        case '{' -> curlyBalance++;
        case '}' -> curlyBalance--;
        default -> { }
      }
    }
    while (end > bodyStart) {
      final char last = text.charAt(end - 1);
      if (TRAILING_PUNCTUATION.contains(last)) {
        end--;
      } else if (last == ')' && roundBalance < 0) {
        roundBalance++;
        end--;
      } else if (last == ']' && squareBalance < 0) {
        squareBalance++;
        end--;
      } else if (last == '}' && curlyBalance < 0) {
        curlyBalance++;
        end--;
      } else {
        break;
      }
    }
    return end;
  }

  /**
   * Tests whether the scheme between two indexes is {@code http} or {@code https} in any
   * letter case.
   *
   * @param text The text. Must not be {@code null}.
   * @param start The index the scheme starts at.
   * @param end The index after the scheme, where {@code ://} starts.
   * @return {@code true} if the scheme is {@code http} or {@code https}.
   */
  private boolean isHttpScheme(CharSequence text, int start, int end) {
    final int schemeLength = end - start;
    final String expected;
    if (schemeLength == HTTP.length()) {
      expected = HTTP;
    } else if (schemeLength == HTTPS.length()) {
      expected = HTTPS;
    } else {
      return false;
    }
    for (int k = 0; k < schemeLength; k++) {
      if (AsciiChars.toLower(text.charAt(start + k)) != expected.charAt(k)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Replaces each URL of the legacy output with one space.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence removeLegacyUrls(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int end = matchLegacyUrlEnd(text, i);
      if (end > i) {
        if (out == null) {
          out = new StringBuilder(length).append(text, 0, i);
        }
        out.append(' ');
        i = end;
      } else {
        if (out != null) {
          out.append(text.charAt(i));
        }
        i++;
      }
    }
    return out == null ? text : out.toString();
  }

  /**
   * {@return the exclusive end of a legacy URL match starting at {@code start}, or {@code -1}
   * if there is none}
   *
   * @param text  The text to look into; never null.
   * @param start The index the match must start at.
   */
  private int matchLegacyUrlEnd(CharSequence text, int start) {
    final int length = text.length();
    if (!regionEquals(text, start, HTTP)) {
      return -1;
    }
    int bodyStart = start + HTTP.length();
    if (bodyStart < length && text.charAt(bodyStart) == 's'
        && regionEquals(text, bodyStart + 1, SCHEME_SEPARATOR)) {
      bodyStart += 1 + SCHEME_SEPARATOR.length();
    } else if (regionEquals(text, bodyStart, SCHEME_SEPARATOR)) {
      bodyStart += SCHEME_SEPARATOR.length();
    } else {
      return -1;
    }
    if (bodyStart >= length || !LEGACY_URL_BODY.contains(text.charAt(bodyStart))) {
      return -1;
    }
    int end = bodyStart + 1;
    while (end < length && LEGACY_URL_BODY.contains(text.charAt(end))) {
      end++;
    }
    return end;
  }

  /**
   * {@return whether the chars at {@code at} equal {@code literal} exactly}
   *
   * @param text    The text to look into; never null.
   * @param at      The index the comparison starts at.
   * @param literal The chars to compare against; never null.
   */
  private boolean regionEquals(CharSequence text, int at, String literal) {
    if (at + literal.length() > text.length()) {
      return false;
    }
    for (int k = 0; k < literal.length(); k++) {
      if (text.charAt(at + k) != literal.charAt(k)) {
        return false;
      }
    }
    return true;
  }
}

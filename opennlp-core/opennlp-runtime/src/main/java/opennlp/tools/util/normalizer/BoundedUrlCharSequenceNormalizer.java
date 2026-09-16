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

import opennlp.tools.util.StringUtil;

/**
 * A {@link CharSequenceNormalizer} that replaces each {@code http} or {@code https} URL and
 * each email address with one space, taking a URL as a whole the way it is bounded in
 * running text.
 *
 * <p>A URL is a scheme, {@code ://}, and a body. The scheme is {@code http} or {@code https}
 * in any letter case and starts the text or follows a code point that is neither a letter,
 * a digit, nor one of {@code _ + - . :}, so the {@code https} inside {@code git+https://}
 * or {@code blob:https://} is not a URL of its own. The body runs to the end of the text or
 * to the first Unicode whitespace, control character, unpaired surrogate, typographic
 * quotation mark, or one of {@code < > "}; it therefore includes a port, userinfo, percent
 * escapes, a bracketed IPv6 host, and non-ASCII host names and paths. Trailing sentence punctuation
 * ({@code . , ; : ! ?}) and a closing bracket without its opening bracket inside the body
 * are not part of the URL. A URL with another scheme is kept as it is, and no email address
 * is matched inside it. This differs from {@link UrlCharSequenceNormalizer}, the normalizer
 * existing language detector models were trained with; a model trained with one normalizer
 * must be decoded with the same one.
 *
 * <p>Email addresses are matched as in {@link UrlCharSequenceNormalizer}.
 *
 * @see <a href="https://url.spec.whatwg.org/">WHATWG URL Standard</a>
 * @since 3.0.0
 */
public final class BoundedUrlCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = -1024195904855005L;

  private static final String SCHEME_SEPARATOR = "://";
  private static final String HTTP = "http";
  private static final String HTTPS = "https";

  /** The characters a scheme continues with after its first letter, per RFC 3986. */
  private static final CodePointSet SCHEME_BODY =
      MailAddressScan.ASCII_ALNUM.union(CodePointSet.of('+', '-', '.'));

  /** Code points that, before the scheme, make it part of a larger token. */
  private static final CodePointSet SCHEME_NEIGHBORS = CodePointSet.of('_', '+', '-', '.', ':');

  /** Delimiters that end a URL body besides whitespace and control characters. */
  private static final CodePointSet BODY_DELIMITERS = CodePointSet.of('<', '>', '"');

  /** Sentence punctuation given back when it trails a URL. */
  private static final CodePointSet TRAILING_PUNCTUATION =
      CodePointSet.of('.', ',', ';', ':', '!', '?');

  private static final BoundedUrlCharSequenceNormalizer INSTANCE =
      new BoundedUrlCharSequenceNormalizer();

  private BoundedUrlCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static BoundedUrlCharSequenceNormalizer getInstance() {
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
    if (!isAsciiLetter(text.charAt(start))) {
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
   * @param codePoint The code point, an unpaired surrogate read as its own code point included.
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
   * Gives back trailing sentence punctuation and closing brackets that have no opening
   * bracket inside the body.
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
      if (Character.toLowerCase(text.charAt(start + k)) != expected.charAt(k)) {
        return false;
      }
    }
    return true;
  }

  private boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private Object readResolve() {
    return INSTANCE;
  }
}

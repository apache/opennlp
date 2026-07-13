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

/**
 * A {@link CharSequenceNormalizer} implementation that normalizes text
 * in terms of URls and email addresses. Every encounter will be replaced by a whitespace.
 *
 * <p>Normalization runs in two passes:</p>
 * <ol>
 *   <li>URLs: a lowercase {@code http://} or {@code https://} scheme followed by at least one
 *       character out of the body set {@code [-_.?&~;+=/#0-9A-Za-z]} becomes one space; the
 *       match ends at the first character outside that set, so a colon (port), a percent
 *       escape, an at sign (userinfo), or a non-ASCII label cuts it short.</li>
 *   <li>Email addresses: a maximal run of the local-part set {@code [-+_.0-9A-Za-z]} whose
 *       left neighbor is outside that set, an {@code @}, and a domain run out of
 *       {@code [-.0-9A-Za-z]} that must not start with a dot and must span at least two
 *       chars, become one space.</li>
 * </ol>
 */
public class UrlCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 2023145028634552389L;

  private static final CodePointSet ASCII_ALNUM = CodePointSet.ofRange('0', '9')
      .union(CodePointSet.ofRange('A', 'Z'))
      .union(CodePointSet.ofRange('a', 'z'));

  /** The URL body set: {@code [-_.?&~;+=/#0-9A-Za-z]}. */
  private static final CodePointSet URL_BODY =
      ASCII_ALNUM.union(CodePointSet.of('-', '_', '.', '?', '&', '~', ';', '+', '=', '/', '#'));

  /** The mail local-part set, also the left-neighbor exclusion set: {@code [-+_.0-9A-Za-z]}. */
  private static final CodePointSet MAIL_LOCAL =
      ASCII_ALNUM.union(CodePointSet.of('-', '+', '_', '.'));

  /** The set a domain may start with: {@code [-0-9A-Za-z]}. */
  private static final CodePointSet MAIL_DOMAIN_START = ASCII_ALNUM.union(CodePointSet.of('-'));

  /** The set a domain continues with: {@code [-.0-9A-Za-z]}. */
  private static final CodePointSet MAIL_DOMAIN = MAIL_DOMAIN_START.union(CodePointSet.of('.'));

  private static final UrlCharSequenceNormalizer INSTANCE = new UrlCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static UrlCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    return removeMailAddresses(removeUrls(text));
  }

  /**
   * Replaces each URL with one space.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence removeUrls(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int end = matchUrlEnd(text, i);
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
   * {@return the exclusive end of a URL match starting at {@code start}, or {@code -1} if
   * there is none}
   *
   * @param text  The text to look into; never null.
   * @param start The index the match must start at.
   */
  private int matchUrlEnd(CharSequence text, int start) {
    final int length = text.length();
    if (!regionEquals(text, start, "http")) {
      return -1;
    }
    int bodyStart = start + 4;
    if (bodyStart < length && text.charAt(bodyStart) == 's'
        && regionEquals(text, bodyStart + 1, "://")) {
      bodyStart += 4;
    } else if (regionEquals(text, bodyStart, "://")) {
      bodyStart += 3;
    } else {
      return -1;
    }
    if (bodyStart >= length || !URL_BODY.contains(text.charAt(bodyStart))) {
      return -1;
    }
    int end = bodyStart + 1;
    while (end < length && URL_BODY.contains(text.charAt(end))) {
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

  /**
   * Replaces each email address with one space.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence removeMailAddresses(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int end = matchMailEnd(text, i);
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
   * {@return the exclusive end of an email match starting at {@code start}, or {@code -1} if
   * there is none}
   *
   * @param text  The text to look into; never null.
   * @param start The index the match must start at.
   */
  private int matchMailEnd(CharSequence text, int start) {
    if (start > 0 && MAIL_LOCAL.contains(text.charAt(start - 1))) {
      return -1; // a match never starts inside a local-part run
    }
    if (!MAIL_LOCAL.contains(text.charAt(start))) {
      return -1;
    }
    final int length = text.length();
    int at = start + 1;
    while (at < length && MAIL_LOCAL.contains(text.charAt(at))) {
      at++;
    }
    if (at >= length || text.charAt(at) != '@') {
      return -1;
    }
    final int domainStart = at + 1;
    if (domainStart >= length || !MAIL_DOMAIN_START.contains(text.charAt(domainStart))) {
      return -1;
    }
    int end = domainStart + 1;
    while (end < length && MAIL_DOMAIN.contains(text.charAt(end))) {
      end++;
    }
    if (end - domainStart < 2) {
      return -1;
    }
    return end;
  }
}

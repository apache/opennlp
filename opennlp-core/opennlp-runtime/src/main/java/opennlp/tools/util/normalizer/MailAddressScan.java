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
 * Finds email addresses in text for the URL normalizers. An address is a maximal run of the
 * local-part set {@code [-+_.0-9A-Za-z]} with a left neighbor outside that set, an
 * {@code @}, and a domain run out of {@code [-.0-9A-Za-z]} that does not start with a dot
 * and spans at least two chars.
 */
final class MailAddressScan {

  /** The ASCII letters and digits. */
  static final CodePointSet ASCII_ALNUM = CodePointSet.ofRange('0', '9')
      .union(CodePointSet.ofRange('A', 'Z'))
      .union(CodePointSet.ofRange('a', 'z'));

  /** The mail local-part set, also the left-neighbor exclusion set: {@code [-+_.0-9A-Za-z]}. */
  private static final CodePointSet MAIL_LOCAL =
      ASCII_ALNUM.union(CodePointSet.of('-', '+', '_', '.'));

  /** The set a domain may start with: {@code [-0-9A-Za-z]}. */
  private static final CodePointSet MAIL_DOMAIN_START = ASCII_ALNUM.union(CodePointSet.of('-'));

  /** The set a domain continues with: {@code [-.0-9A-Za-z]}. */
  private static final CodePointSet MAIL_DOMAIN = MAIL_DOMAIN_START.union(CodePointSet.of('.'));

  private static final char AT = '@';
  private static final int MIN_DOMAIN_LENGTH = 2;

  private MailAddressScan() {
  }

  /**
   * Replaces each email address with one space.
   *
   * @param text The text to scan. Must not be {@code null}.
   * @return The input itself when no address matched, otherwise the normalized copy.
   */
  static CharSequence removeAll(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int end = matchEnd(text, i);
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
   * Finds the end of an email address that starts at an index.
   *
   * @param text The text to look into. Must not be {@code null}.
   * @param start The index the address must start at, inside the text.
   * @return The exclusive end of the address, or {@code -1} if none starts there.
   */
  static int matchEnd(CharSequence text, int start) {
    if (start > 0 && MAIL_LOCAL.contains(text.charAt(start - 1))) {
      return -1;
    }
    if (!MAIL_LOCAL.contains(text.charAt(start))) {
      return -1;
    }
    final int length = text.length();
    int at = start + 1;
    while (at < length && MAIL_LOCAL.contains(text.charAt(at))) {
      at++;
    }
    if (at >= length || text.charAt(at) != AT) {
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
    if (end - domainStart < MIN_DOMAIN_LENGTH) {
      return -1;
    }
    return end;
  }
}

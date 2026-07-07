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
 * A {@link ShrinkCharSequenceNormalizer} implementation that shrinks repeated spaces / chars
 * in text.
 *
 * <p>Two forward cursor passes reproduce, byte for byte, the output of the former regex
 * implementation ({@code "\\s{2,}"} replaced by one space, then {@code "(.)\\1{2,}"} with
 * {@code CASE_INSENSITIVE} replaced by {@code "$1$1"}, then {@link String#trim()}):</p>
 * <ol>
 *   <li>Each run of two or more ASCII whitespace characters (the six characters the regex
 *       {@code \s} class matches; this legacy rung predates the Unicode {@code White_Space}
 *       based {@link CharClass#whitespace()}) collapses to a single space. A lone whitespace
 *       character is kept as it is.</li>
 *   <li>Each run of three or more repeats of one code point shrinks to two copies of its first
 *       occurrence. Repeats compare case-insensitively over ASCII only, and a run never starts
 *       on a regex line terminator, because the former {@code .} did not match one.</li>
 * </ol>
 * <p>The result is finally trimmed with {@link String#trim()}, preserving the legacy contract
 * that every leading or trailing char up to {@code U+0020} is dropped.</p>
 */
public class ShrinkCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = -4511969661556543048L;

  // The six characters the former regex "\s" class matched (ASCII only, no UNICODE flags).
  private static final CodePointSet ASCII_WHITESPACE =
      CodePointSet.ofRange(0x0009, 0x000D).union(CodePointSet.of(0x0020));

  // The code points the former "." (without DOTALL) refused to match: the regex line terminators.
  private static final CodePointSet REGEX_LINE_TERMINATORS =
      CodePointSet.of(0x000A, 0x000D, 0x0085, 0x2028, 0x2029);

  private static final ShrinkCharSequenceNormalizer INSTANCE = new ShrinkCharSequenceNormalizer();

  public static ShrinkCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    return shrinkRepeatedCodePoints(shrinkWhitespace(text)).trim();
  }

  // Replaces each run of two or more ASCII whitespace characters with a single space; a lone
  // whitespace character stays as it is (the former "\s{2,}" never matched a run of one).
  private static String shrinkWhitespace(CharSequence text) {
    final int length = text.length();
    final StringBuilder out = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (ASCII_WHITESPACE.contains(codePoint)) {
        int runEnd = i + 1; // members are single-char ASCII
        while (runEnd < length && ASCII_WHITESPACE.contains(text.charAt(runEnd))) {
          runEnd++;
        }
        if (runEnd - i >= 2) {
          out.append(' ');
        } else {
          out.append(text.charAt(i));
        }
        i = runEnd;
      } else {
        out.appendCodePoint(codePoint);
        i += Character.charCount(codePoint);
      }
    }
    return out.toString();
  }

  // Shrinks each run of three or more repeats of one code point to two copies of its first
  // occurrence, exactly as the former "(.)\1{2,}" -> "$1$1" pass did: the run's first code point
  // must not be a regex line terminator, every repeat advances by the first occurrence's char
  // count while comparing whole code points ASCII case-insensitively (mirroring the engine's
  // backreference matching), and a failed attempt resumes at the next char, like the regex scan.
  private static String shrinkRepeatedCodePoints(String text) {
    final int length = text.length();
    final StringBuilder out = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (!REGEX_LINE_TERMINATORS.contains(codePoint)) {
        final int charCount = Character.charCount(codePoint);
        int repeats = 0;
        int end = i + charCount;
        while (end + charCount <= length
            && asciiCaseInsensitiveEquals(Character.codePointAt(text, end), codePoint)) {
          end += charCount;
          repeats++;
        }
        if (repeats >= 2) {
          out.append(text, i, i + charCount).append(text, i, i + charCount);
          i = end;
          continue;
        }
      }
      out.append(text.charAt(i));
      i++;
    }
    return out.toString();
  }

  private static boolean asciiCaseInsensitiveEquals(int a, int b) {
    return asciiToLower(a) == asciiToLower(b);
  }

  private static int asciiToLower(int codePoint) {
    return codePoint >= 'A' && codePoint <= 'Z' ? codePoint + 0x20 : codePoint;
  }
}

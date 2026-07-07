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
 * A {@link TwitterCharSequenceNormalizer} implementation that normalizes text
 * in terms of Twitter character patterns. Every encounter will be replaced by a whitespace.
 *
 * <p>Four forward cursor passes reproduce, byte for byte, the output of the former regex
 * implementation:</p>
 * <ol>
 *   <li>a {@code #} or {@code @} followed by at least one non-whitespace character, together
 *       with the whole following non-whitespace run, becomes one space (the whitespace class is
 *       the six ASCII characters the former {@code \S} complemented);</li>
 *   <li>each maximal sequence of {@code rt} units (case-insensitive, each followed by a space or
 *       colon) becomes one space when it starts on a word boundary as the JDK regex engine
 *       defined it for {@code \b};</li>
 *   <li>each emoticon, eyes {@code :}, {@code ;} or {@code x}, an optional {@code -} nose, and a
 *       mouth out of {@code ( ) d o p} (case-insensitive), becomes one space, including inside
 *       words;</li>
 *   <li>laughter, a run of {@code h}/{@code j}, a run of vowels, and at least one repetition of
 *       the two runs' last characters, shrinks to those two characters twice
 *       ({@code "hahaha"} to {@code "haha"}), comparing ASCII case-insensitively.</li>
 * </ol>
 */
public class TwitterCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = -8155452559337913929L;

  // The six characters the former regex "\s" class matched; "\S" was their complement.
  private static final CodePointSet ASCII_WHITESPACE =
      CodePointSet.ofRange(0x0009, 0x000D).union(CodePointSet.of(0x0020));

  private static final TwitterCharSequenceNormalizer INSTANCE = new TwitterCharSequenceNormalizer();

  public static TwitterCharSequenceNormalizer getInstance() {
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
    return shrinkLaughter(removeEmoticons(removeRetweetMarkers(removeTagsAndHandles(text))));
  }

  // "[#@]\S+" -> " ": a hash or at sign starts a match only if a non-whitespace char follows;
  // the match then swallows the whole non-whitespace run (including further # and @).
  private static String removeTagsAndHandles(CharSequence text) {
    final int length = text.length();
    final StringBuilder out = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      if ((c == '#' || c == '@')
          && i + 1 < length && !ASCII_WHITESPACE.contains(text.charAt(i + 1))) {
        int end = i + 2;
        while (end < length && !ASCII_WHITESPACE.contains(text.charAt(end))) {
          end++;
        }
        out.append(' ');
        i = end;
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  // "\b(rt[ :])+" (case-insensitive) -> " ": one or more three-char units of r, t, and a space
  // or colon, starting where the JDK engine put a word boundary.
  private static String removeRetweetMarkers(String text) {
    final int length = text.length();
    final StringBuilder out = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      if ((c == 'r' || c == 'R') && !wordBeforeBlocksBoundary(text, i)) {
        int end = i;
        while (end + 3 <= length && isRetweetUnit(text, end)) {
          end += 3;
        }
        if (end > i) {
          out.append(' ');
          i = end;
          continue;
        }
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  private static boolean isRetweetUnit(String text, int at) {
    final char r = text.charAt(at);
    final char t = text.charAt(at + 1);
    final char separator = text.charAt(at + 2);
    return (r == 'r' || r == 'R') && (t == 't' || t == 'T')
        && (separator == ' ' || separator == ':');
  }

  // Mirrors how the JDK regex engine decided whether "\b" held before a word character at
  // index (java.util.regex.Pattern.Bound without UNICODE_CHARACTER_CLASS): the boundary is
  // blocked if the preceding code point is an ASCII word character, or is a non-spacing mark
  // with a base character. The base-character scan steps backwards one char at a time and reads
  // Character.codePointAt at each position, exactly like the engine, so it stops on the low
  // surrogate of a supplementary-plane mark.
  private static boolean wordBeforeBlocksBoundary(CharSequence text, int index) {
    if (index <= 0) {
      return false;
    }
    final int before = Character.codePointBefore(text, index);
    if (isAsciiWord(before)) {
      return true;
    }
    if (Character.getType(before) == Character.NON_SPACING_MARK) {
      for (int x = index - 1; x >= 0; x--) {
        final int codePoint = Character.codePointAt(text, x);
        if (Character.isLetterOrDigit(codePoint)) {
          return true;
        }
        if (Character.getType(codePoint) != Character.NON_SPACING_MARK) {
          return false;
        }
      }
    }
    return false;
  }

  private static boolean isAsciiWord(int codePoint) {
    return codePoint >= 'a' && codePoint <= 'z'
        || codePoint >= 'A' && codePoint <= 'Z'
        || codePoint >= '0' && codePoint <= '9'
        || codePoint == '_';
  }

  // "[:;x]-?[()dop]" (case-insensitive) -> " ": eyes, an optional nose, and a mouth. The
  // optional nose is tried first, like the greedy "-?"; a bare "-" is never a mouth.
  private static String removeEmoticons(String text) {
    final int length = text.length();
    final StringBuilder out = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      if (isEmoticonEyes(c)) {
        if (i + 2 < length && text.charAt(i + 1) == '-' && isEmoticonMouth(text.charAt(i + 2))) {
          out.append(' ');
          i += 3;
          continue;
        }
        if (i + 1 < length && isEmoticonMouth(text.charAt(i + 1))) {
          out.append(' ');
          i += 2;
          continue;
        }
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  private static boolean isEmoticonEyes(char c) {
    return c == ':' || c == ';' || c == 'x' || c == 'X';
  }

  private static boolean isEmoticonMouth(char c) {
    return c == '(' || c == ')' || c == 'd' || c == 'D'
        || c == 'o' || c == 'O' || c == 'p' || c == 'P';
  }

  // "([hj])+([aieou])+(\1+\2+)+" (case-insensitive) -> "$1$2$1$2": a run of h/j, a run of
  // vowels, and at least one complete repetition of runs of the two captured characters (the
  // last consonant and the last vowel, which is what the repeated groups held). The four
  // character sets involved are pairwise disjoint, so the greedy runs never backtrack into
  // another viable split; a failed attempt resumes at the next char, like the regex scan.
  private static String shrinkLaughter(String text) {
    final int length = text.length();
    final StringBuilder out = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      if (isLaughConsonant(c)) {
        int p = i + 1;
        while (p < length && isLaughConsonant(text.charAt(p))) {
          p++;
        }
        if (p < length && isLaughVowel(text.charAt(p))) {
          final char consonant = text.charAt(p - 1);
          int q = p + 1;
          while (q < length && isLaughVowel(text.charAt(q))) {
            q++;
          }
          final char vowel = text.charAt(q - 1);
          int end = q;
          while (true) {
            int consonantRunEnd = end;
            while (consonantRunEnd < length
                && asciiCaseInsensitiveEquals(text.charAt(consonantRunEnd), consonant)) {
              consonantRunEnd++;
            }
            if (consonantRunEnd == end) {
              break;
            }
            int vowelRunEnd = consonantRunEnd;
            while (vowelRunEnd < length
                && asciiCaseInsensitiveEquals(text.charAt(vowelRunEnd), vowel)) {
              vowelRunEnd++;
            }
            if (vowelRunEnd == consonantRunEnd) {
              break;
            }
            end = vowelRunEnd;
          }
          if (end > q) {
            out.append(consonant).append(vowel).append(consonant).append(vowel);
            i = end;
            continue;
          }
        }
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  private static boolean isLaughConsonant(char c) {
    return c == 'h' || c == 'H' || c == 'j' || c == 'J';
  }

  private static boolean isLaughVowel(char c) {
    return c == 'a' || c == 'i' || c == 'e' || c == 'o' || c == 'u'
        || c == 'A' || c == 'I' || c == 'E' || c == 'O' || c == 'U';
  }

  private static boolean asciiCaseInsensitiveEquals(char a, char b) {
    return asciiToLower(a) == asciiToLower(b);
  }

  private static char asciiToLower(char c) {
    return c >= 'A' && c <= 'Z' ? (char) (c + 0x20) : c;
  }
}

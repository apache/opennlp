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
 * in terms of Twitter character patterns. Every encounter will be replaced by a whitespace.
 *
 * <p>Normalization runs in four passes:</p>
 * <ol>
 *   <li>hashtags and handles: a {@code #} or {@code @} followed by at least one non-whitespace
 *       character, together with the whole following non-whitespace run, becomes one space
 *       (whitespace here means tab, line feed, vertical tab, form feed, carriage return, or
 *       space);</li>
 *   <li>retweet markers: each sequence of {@code rt} units (case-insensitive, each followed by
 *       a space or colon) becomes one space when it starts on a word boundary;</li>
 *   <li>emoticons: eyes {@code :}, {@code ;} or {@code x}, an optional {@code -} nose, and a
 *       mouth out of {@code ( ) d o p} (case-insensitive), become one space, including inside
 *       words;</li>
 *   <li>laughter: a run of {@code h}/{@code j}, a run of vowels, and at least one repetition of
 *       the two runs' last characters, shrinks to those two characters twice
 *       ({@code "hahaha"} to {@code "haha"}), comparing ASCII case-insensitively.</li>
 * </ol>
 */
public class TwitterCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 3921004098714878226L;

  private static final TwitterCharSequenceNormalizer INSTANCE = new TwitterCharSequenceNormalizer();

  public static TwitterCharSequenceNormalizer getInstance() {
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
    return shrinkLaughter(removeEmoticons(removeRetweetMarkers(removeTagsAndHandles(text))));
  }

  /**
   * Replaces each hashtag or handle with one space: a {@code #} or {@code @} matches only if a
   * non-whitespace char follows, and the match then swallows the whole non-whitespace run,
   * including further {@code #} and {@code @}.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence removeTagsAndHandles(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      if ((c == '#' || c == '@')
          && i + 1 < length && !AsciiChars.WHITESPACE.contains(text.charAt(i + 1))) {
        int end = i + 2;
        while (end < length && !AsciiChars.WHITESPACE.contains(text.charAt(end))) {
          end++;
        }
        if (out == null) {
          out = new StringBuilder(length).append(text, 0, i);
        }
        out.append(' ');
        i = end;
      } else {
        if (out != null) {
          out.append(c);
        }
        i++;
      }
    }
    return out == null ? text : out.toString();
  }

  /**
   * Replaces each sequence of one or more retweet units with one space when the sequence
   * starts on a word boundary.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence removeRetweetMarkers(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      if ((c == 'r' || c == 'R') && !wordBeforeBlocksBoundary(text, i)) {
        int end = i;
        while (end + 3 <= length && isRetweetUnit(text, end)) {
          end += 3;
        }
        if (end > i) {
          if (out == null) {
            out = new StringBuilder(length).append(text, 0, i);
          }
          out.append(' ');
          i = end;
          continue;
        }
      }
      if (out != null) {
        out.append(c);
      }
      i++;
    }
    return out == null ? text : out.toString();
  }

  /**
   * {@return whether the three chars starting at {@code at} form one retweet unit:
   * {@code r} or {@code R}, {@code t} or {@code T}, then a space or colon}
   *
   * @param text The text to look into; never null.
   * @param at   The index of the unit's first char; at least three chars must remain.
   */
  private boolean isRetweetUnit(CharSequence text, int at) {
    final char r = text.charAt(at);
    final char t = text.charAt(at + 1);
    final char separator = text.charAt(at + 2);
    return (r == 'r' || r == 'R') && (t == 't' || t == 'T')
        && (separator == ' ' || separator == ':');
  }

  /**
   * {@return whether the word boundary before {@code index} is blocked} The boundary is
   * blocked if the preceding code point is an ASCII word character, or is a non-spacing mark
   * whose backwards base-character scan reaches a letter or digit. This matches the word
   * boundary decision of the JDK 21 regular-expression engine; the backwards scan reads one
   * char at a time, so it intentionally stops on the low surrogate of a
   * supplementary-plane mark, exactly like that engine.
   *
   * @param text  The text to look into; never null.
   * @param index The index the boundary is checked before.
   */
  private boolean wordBeforeBlocksBoundary(CharSequence text, int index) {
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

  /** {@return whether {@code codePoint} is an ASCII letter, digit, or underscore} */
  private boolean isAsciiWord(int codePoint) {
    return codePoint >= 'a' && codePoint <= 'z'
        || codePoint >= 'A' && codePoint <= 'Z'
        || codePoint >= '0' && codePoint <= '9'
        || codePoint == '_';
  }

  /**
   * Replaces each emoticon with one space, including inside words: eyes, then an optional
   * {@code -} nose (tried first when present), then a mouth. A bare {@code -} is never a
   * mouth.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence removeEmoticons(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final char c = text.charAt(i);
      if (isEmoticonEyes(c)) {
        if (i + 2 < length && text.charAt(i + 1) == '-' && isEmoticonMouth(text.charAt(i + 2))) {
          if (out == null) {
            out = new StringBuilder(length).append(text, 0, i);
          }
          out.append(' ');
          i += 3;
          continue;
        }
        if (i + 1 < length && isEmoticonMouth(text.charAt(i + 1))) {
          if (out == null) {
            out = new StringBuilder(length).append(text, 0, i);
          }
          out.append(' ');
          i += 2;
          continue;
        }
      }
      if (out != null) {
        out.append(c);
      }
      i++;
    }
    return out == null ? text : out.toString();
  }

  /** {@return whether {@code c} is an emoticon's eyes: {@code :}, {@code ;}, or {@code x}} */
  private boolean isEmoticonEyes(char c) {
    return c == ':' || c == ';' || c == 'x' || c == 'X';
  }

  /** {@return whether {@code c} is an emoticon's mouth: one of {@code ( ) d o p}, either case} */
  private boolean isEmoticonMouth(char c) {
    return c == '(' || c == ')' || c == 'd' || c == 'D'
        || c == 'o' || c == 'O' || c == 'p' || c == 'P';
  }

  /**
   * Shrinks laughter: a run of {@code h}/{@code j}, a run of vowels, and at least one complete
   * repetition of runs of the two last characters of those runs, shrink to those two
   * characters twice. The character sets involved are pairwise disjoint, so the greedy runs
   * never overlap ambiguously; a failed attempt resumes at the next char.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence shrinkLaughter(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
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
                && AsciiChars.caseInsensitiveEquals(text.charAt(consonantRunEnd), consonant)) {
              consonantRunEnd++;
            }
            if (consonantRunEnd == end) {
              break;
            }
            int vowelRunEnd = consonantRunEnd;
            while (vowelRunEnd < length
                && AsciiChars.caseInsensitiveEquals(text.charAt(vowelRunEnd), vowel)) {
              vowelRunEnd++;
            }
            if (vowelRunEnd == consonantRunEnd) {
              break;
            }
            end = vowelRunEnd;
          }
          if (end > q) {
            if (out == null) {
              out = new StringBuilder(length).append(text, 0, i);
            }
            out.append(consonant).append(vowel).append(consonant).append(vowel);
            i = end;
            continue;
          }
        }
      }
      if (out != null) {
        out.append(c);
      }
      i++;
    }
    return out == null ? text : out.toString();
  }

  /** {@return whether {@code c} is a laughter consonant: {@code h} or {@code j}, either case} */
  private boolean isLaughConsonant(char c) {
    return c == 'h' || c == 'H' || c == 'j' || c == 'J';
  }

  /** {@return whether {@code c} is a laughter vowel: {@code a e i o u}, either case} */
  private boolean isLaughVowel(char c) {
    return c == 'a' || c == 'i' || c == 'e' || c == 'o' || c == 'u'
        || c == 'A' || c == 'I' || c == 'E' || c == 'O' || c == 'U';
  }

}

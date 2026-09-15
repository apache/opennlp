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

/**
 * A {@link CharSequenceNormalizer} that replaces every run of supplementary-plane code points,
 * {@code U+10000} and above, with a single space. Emoji outside the Basic Multilingual Plane are
 * replaced along with every other supplementary character, CJK Extension B ideographs included;
 * BMP characters and unpaired surrogates are kept. Since 3.0.0 hyphens and BMP characters are no
 * longer replaced (OPENNLP-1928). Under {@link CompatibilityMode#LEGACY} the output of the
 * 1.x/2.x releases is produced instead: a run of hyphens, of code points from {@code U+D83C}
 * to {@code U+10FC00}, unpaired surrogates in that range included, becomes one space, and
 * code points above {@code U+10FC00} are kept.
 *
 * @deprecated Replaces every supplementary-plane code point with a space, not only emoji. Use
 *     {@link EmojiToEmoticonCharSequenceNormalizer} instead.
 */
@Deprecated(since = "3.0.0", forRemoval = true)
public class EmojiCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 4553401197981667914L;

  private static final int HYPHEN = '-';
  private static final int LEGACY_RANGE_FIRST = 0xD83C;
  private static final int LEGACY_RANGE_LAST = 0x10FC00;

  private static final EmojiCharSequenceNormalizer INSTANCE = new EmojiCharSequenceNormalizer();

  public static EmojiCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   * Every run of replaced code points becomes one space; text without one is returned as
   * it is. Which code points are replaced depends on {@link CompatibilityMode#current()}.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    final boolean legacy = CompatibilityMode.current() == CompatibilityMode.LEGACY;
    int i = indexOfReplaced(text, legacy);
    if (i == -1) {
      return text;
    }
    StringBuilder normalized = new StringBuilder(text.length()).append(text, 0, i);
    boolean inRun = false;
    while (i < text.length()) {
      int cp = Character.codePointAt(text, i);
      if (isReplaced(cp, legacy)) {
        if (!inRun) {
          normalized.append(' ');
          inRun = true;
        }
      }
      else {
        normalized.appendCodePoint(cp);
        inRun = false;
      }
      i += Character.charCount(cp);
    }
    return normalized.toString();
  }

  /**
   * Tells whether a code point is replaced by a space.
   *
   * @param codePoint The code point, an unpaired surrogate read as its own code point included.
   * @param legacy {@code true} for the output of the 1.x/2.x releases.
   * @return {@code true} if the code point is replaced.
   */
  private boolean isReplaced(int codePoint, boolean legacy) {
    if (legacy) {
      return codePoint == HYPHEN
          || (codePoint >= LEGACY_RANGE_FIRST && codePoint <= LEGACY_RANGE_LAST);
    }
    return Character.isSupplementaryCodePoint(codePoint);
  }

  /**
   * Finds the first replaced code point.
   *
   * @param text The text.
   * @param legacy {@code true} for the output of the 1.x/2.x releases.
   * @return The offset of its first char, or {@code -1} if there is none.
   */
  private int indexOfReplaced(CharSequence text, boolean legacy) {
    for (int i = 0; i < text.length();) {
      int cp = Character.codePointAt(text, i);
      if (isReplaced(cp, legacy)) {
        return i;
      }
      i += Character.charCount(cp);
    }
    return -1;
  }

  private Object readResolve() {
    return INSTANCE;
  }
}

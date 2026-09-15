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
 * A {@link CharSequenceNormalizer} that replaces every run of supplementary-plane code points,
 * {@code U+10000} and above, with a single space. Emoji outside the Basic Multilingual Plane are
 * replaced along with every other supplementary character, CJK Extension B ideographs included;
 * BMP characters and unpaired surrogates are kept. Since 3.0.0 hyphens and BMP characters are no
 * longer replaced (OPENNLP-1928).
 *
 * @deprecated Replaces every supplementary-plane code point with a space, not only emoji. Use
 *     {@link EmojiToEmoticonCharSequenceNormalizer} instead.
 */
@Deprecated(since = "3.0.0", forRemoval = true)
public class EmojiCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 4553401197981667914L;

  private static final EmojiCharSequenceNormalizer INSTANCE = new EmojiCharSequenceNormalizer();

  public static EmojiCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   * Every run of supplementary-plane code points becomes one space; text without one is
   * returned as it is.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    int i = indexOfSupplementary(text);
    if (i == -1) {
      return text;
    }
    StringBuilder normalized = new StringBuilder(text.length()).append(text, 0, i);
    boolean inRun = false;
    while (i < text.length()) {
      int cp = Character.codePointAt(text, i);
      if (Character.isSupplementaryCodePoint(cp)) {
        if (!inRun) {
          normalized.append(' ');
          inRun = true;
        }
        i += 2;
      }
      else {
        normalized.append((char) cp);
        inRun = false;
        i++;
      }
    }
    return normalized.toString();
  }

  /**
   * Finds the first supplementary-plane code point.
   *
   * @param text The text.
   * @return The offset of its high surrogate, or {@code -1} if there is none.
   */
  private int indexOfSupplementary(CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      if (Character.isHighSurrogate(text.charAt(i)) && i + 1 < text.length()
          && Character.isLowSurrogate(text.charAt(i + 1))) {
        return i;
      }
    }
    return -1;
  }

  private Object readResolve() {
    return INSTANCE;
  }
}

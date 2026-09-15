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
 * A {@link CharSequenceNormalizer} that replaces every maximal run of supplementary-plane code
 * points, {@code U+10000} to {@code U+10FFFF}, with a single space. Emoji live in that range, and
 * so do other scripts and symbols; nothing in the Basic Multilingual Plane is touched, so
 * fullwidth and halfwidth forms, compatibility ideographs, presentation forms, and the private
 * use area pass through, and so do the BMP characters of an emoji sequence such as the zero
 * width joiner {@code U+200D} and the variation selector {@code U+FE0F}. An unpaired surrogate
 * is not a code point in that range and is kept as it is.
 *
 * <p>Since 3.0.0 only supplementary-plane code points are replaced. Earlier releases also
 * blanked some BMP characters and ASCII hyphens (OPENNLP-1928). The default
 * {@link opennlp.tools.langdetect.LanguageDetectorFactory} chain uses this normalizer.
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
   * Every maximal run of supplementary-plane code points becomes one space.
   */
  @Override
  public CharSequence normalize (CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    StringBuilder normalized = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      int cp = Character.codePointAt(text, i);
      if (Character.isSupplementaryCodePoint(cp)) {
        while (i < text.length() && Character.isSupplementaryCodePoint(Character.codePointAt(text, i))) {
          i += 2;
        }
        normalized.append(' ');
      }
      else {
        normalized.append(text.charAt(i));
        i++;
      }
    }
    return normalized.toString();
  }
}

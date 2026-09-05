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
 * A {@link EmojiCharSequenceNormalizer} implementation that normalizes text
 * in terms of emojis. Every encounter will be replaced by a whitespace.
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

  /** {@inheritDoc} */
  @Override
  public CharSequence normalize (CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    StringBuilder normalized = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      int cp = Character.codePointAt(text, i);
      if (cp >= LOWER_CODE_POINT && cp <= UPPER_CODE_POINT) {
        i += Character.charCount(cp);
        while (i < text.length()) {
          int next = Character.codePointAt(text, i);
          if (next < LOWER_CODE_POINT || next > UPPER_CODE_POINT) {
            break;
          }
          i += Character.charCount(next);
        }
        normalized.append(' ');
      }
      else {
        normalized.appendCodePoint(cp);
        i += Character.charCount(cp);
      }
    }
    return normalized.toString();
  }

  // The replaced pattern "[\uD83C-\uDBFF\uDC00-\uDFFF]+" contains a high surrogate
  // range, so the regex engine matches whole code points in the flattened range
  // [\uD83C, U+10FC00]: BMP chars from U+D83C up and supplementary code points
  // up to U+10FC00, collapsing each maximal run into a single space.
  private static final int LOWER_CODE_POINT = 0xD83C;
  private static final int UPPER_CODE_POINT = 0x10FC00;
}

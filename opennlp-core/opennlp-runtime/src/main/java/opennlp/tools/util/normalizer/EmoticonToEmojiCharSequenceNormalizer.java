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
 * A {@link CharSequenceNormalizer} that folds ASCII emoticons to emoji, using the bundled
 * {@code emoji-emoticons.txt} mapping (for example {@code :-)} to U+1F642 SLIGHTLY SMILING FACE).
 *
 * <p>An emoticon folds only when it stands alone as a whitespace-delimited unit (the text boundary
 * or Unicode {@code White_Space} on both sides), so sequences inside ordinary text such as the
 * {@code :/} in {@code https://} are never touched. Matching is longest first at each position.
 * Apply this normalizer before tokenization if emoticons should survive as single tokens. This is
 * an offset-changing transform: {@link #normalizeAligned(CharSequence)} reports the
 * {@link Alignment} from the folded text back to the input. The reverse direction is
 * {@link EmojiToEmoticonCharSequenceNormalizer}.</p>
 */
public final class EmoticonToEmojiCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 4425475084975880520L;

  private static final EmoticonToEmojiCharSequenceNormalizer INSTANCE =
      new EmoticonToEmojiCharSequenceNormalizer();

  /** Instantiated once for {@link #getInstance()}. */
  private EmoticonToEmojiCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static EmoticonToEmojiCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code text} is {@code null}.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    return EmojiEmoticons.getInstance().emoticonToEmoji(text);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code text} is {@code null}.
   */
  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    return EmojiEmoticons.getInstance().emoticonToEmojiAligned(text);
  }
}

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

import java.util.Objects;

/**
 * A {@link CharSequenceNormalizer} that folds emoji to ASCII emoticons, using the bundled
 * {@code emoji-emoticons.txt} mapping (for example U+1F642 SLIGHTLY SMILING FACE to {@code :)}).
 *
 * <p>A mapped pictograph inside a larger ZWJ sequence or followed by U+FE0E VARIATION SELECTOR-15
 * is left untouched; a trailing U+FE0F VARIATION SELECTOR-16 after any mapped pictograph is
 * absorbed into the fold, so no dangling variation selector is left behind. This is an expanding,
 * offset-changing transform: {@link #normalizeAligned(CharSequence)} reports the {@link Alignment}
 * from the folded text back to the input. The reverse direction is
 * {@link EmoticonToEmojiCharSequenceNormalizer}.</p>
 */
public final class EmojiToEmoticonCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 7118395871093868025L;

  private static final EmojiToEmoticonCharSequenceNormalizer INSTANCE =
      new EmojiToEmoticonCharSequenceNormalizer();

  /** Instantiated once for {@link #getInstance()}. */
  private EmojiToEmoticonCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static EmojiToEmoticonCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   *
   * @throws NullPointerException if {@code text} is {@code null}.
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    Objects.requireNonNull(text, "text must not be null");
    return EmojiEmoticons.getInstance().emojiToEmoticon(text);
  }

  /**
   * {@inheritDoc}
   *
   * @throws NullPointerException if {@code text} is {@code null}.
   */
  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    Objects.requireNonNull(text, "text must not be null");
    return EmojiEmoticons.getInstance().emojiToEmoticonAligned(text);
  }
}

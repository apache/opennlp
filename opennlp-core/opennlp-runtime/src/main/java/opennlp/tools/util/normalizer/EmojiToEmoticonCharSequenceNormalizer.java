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
 * A {@link CharSequenceNormalizer} that folds emoji to ASCII emoticons, using the bundled,
 * project-authored {@code emoji-emoticons.txt} mapping (for example U+1F642 SLIGHTLY SMILING FACE
 * to {@code :)}).
 *
 * <p>Only a pictograph with a mapped emoticon is folded; everything else is copied through, so the
 * fold rewrites the signal into ASCII instead of deleting it the way the deprecated
 * {@link EmojiCharSequenceNormalizer} does. A trailing U+FE0F VARIATION SELECTOR-16 after any
 * mapped pictograph is absorbed into the fold, so no dangling variation selector is left behind.
 * A mapped pictograph inside a larger ZWJ sequence (HEART ON FIRE, the family emoji) or followed
 * by U+FE0E VARIATION SELECTOR-15, which requests text presentation, is left untouched; folding a
 * fragment of a distinct emoji would corrupt it. The mapping is many to one (the grinning-face
 * family folds to {@code :D}); the
 * reverse direction is {@link EmoticonToEmojiCharSequenceNormalizer}, and a round trip through both
 * converges on the canonical forms rather than restoring every variant.</p>
 *
 * <p>This is an offset-changing transform (a one-character pictograph such as U+263A expands to the
 * two-character {@code :)}), so it is offset-aware: {@link #normalizeAligned(CharSequence)} reports
 * the {@link Alignment} from the folded text back to the input. A single cursor pass with no
 * regular expression.</p>
 */
public final class EmojiToEmoticonCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 7118395871093868025L;

  private static final EmojiToEmoticonCharSequenceNormalizer INSTANCE =
      new EmojiToEmoticonCharSequenceNormalizer();

  private EmojiToEmoticonCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static EmojiToEmoticonCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return EmojiEmoticons.substitute(text, EmojiEmoticons.emojiToEmoticon(), false);
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    return EmojiEmoticons.substituteAligned(text, EmojiEmoticons.emojiToEmoticon(), false);
  }
}

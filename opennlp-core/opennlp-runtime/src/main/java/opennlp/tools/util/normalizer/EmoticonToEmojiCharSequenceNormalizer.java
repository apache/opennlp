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
 * A {@link CharSequenceNormalizer} that folds ASCII emoticons to emoji, using the bundled,
 * project-authored {@code emoji-emoticons.txt} mapping (for example {@code :-)} to U+1F642
 * SLIGHTLY SMILING FACE).
 *
 * <p>An emoticon folds only when it stands alone as a whitespace-delimited unit (the text boundary
 * or Unicode {@code White_Space} on both sides), because emoticon character sequences also occur
 * inside ordinary text where folding would corrupt it: the {@code :/} in {@code https://} is not an
 * emoticon, and a missed fold costs nothing while a false fold is irreversible. Matching is longest
 * first at each position, so {@code :-)} folds as one unit rather than {@code :)} claiming a prefix
 * and leaving a stray character.</p>
 *
 * <p>Folding emoticons before tokenization is what makes emoticons and emoji one class downstream:
 * the UAX&#160;#29 word tokenizer keeps a pictograph as an {@code EMOJI} token but splits an
 * unfolded emoticon into punctuation, so applying this rung first lets {@code :)} survive
 * tokenization as a single token. For the same reason there is no per-token {@link Dimension} for
 * this direction; the reverse, {@link EmojiToEmoticonCharSequenceNormalizer}, has
 * {@link Dimension#EMOJI_FOLD}.</p>
 *
 * <p>This is an offset-changing transform (the three-character {@code :-)} contracts to one
 * surrogate-pair pictograph), so it is offset-aware: {@link #normalizeAligned(CharSequence)}
 * reports the {@link Alignment} from the folded text back to the input. A single cursor pass with
 * no regular expression.</p>
 */
public final class EmoticonToEmojiCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 3520486210777693086L;

  private static final EmoticonToEmojiCharSequenceNormalizer INSTANCE =
      new EmoticonToEmojiCharSequenceNormalizer();

  private EmoticonToEmojiCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static EmoticonToEmojiCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return EmojiEmoticons.substitute(text, EmojiEmoticons.emoticonToEmoji(), true);
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    return EmojiEmoticons.substituteAligned(text, EmojiEmoticons.emoticonToEmoji(), true);
  }
}

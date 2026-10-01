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
 * A {@link CharSequenceNormalizer} implementation that replaces each run of complete emoji with
 * one space.
 *
 * <p>An emoji is a fully-qualified or minimally-qualified sequence of Unicode Emoji 17.0 in the
 * sense of <a href="https://www.unicode.org/reports/tr51/">UTS #51</a>: a single emoji, an emoji
 * with U+FE0F, a flag, a keycap, a skin tone modifier sequence, a ZWJ sequence or a tag
 * sequence, as listed in {@code emoji-test.txt}. A minimally-qualified ZWJ sequence lacks U+FE0F
 * after a later element only, such as a woman raising hand written without U+FE0F after
 * U+2640. Adjacent emoji form one run and become one space; one redundant U+FE0F right after an
 * emoji is removed with it. Everything else is kept: a symbol with text presentation such as
 * U+2764 without U+FE0F, an unqualified sequence starting with such a symbol, letters and
 * ideographs from other planes,
 * private use characters, unpaired surrogates, and an emoji with a stray joiner, modifier,
 * selector, flag letter or tag next to it, which is kept as a whole instead of leaving a
 * fragment behind.</p>
 *
 * <p>Since 3.0.0 only complete emoji sequences are removed. Under
 * {@link CompatibilityMode#LEGACY} the earlier output is produced for language detector models
 * trained with it: each maximal run of ASCII hyphens, characters from U+E000 to U+FFFF (private
 * use characters, CJK compatibility ideographs, Arabic and Hebrew presentation forms, fullwidth
 * and halfwidth forms, variation selectors), supplementary characters up to U+10FC00 whether
 * they are emoji or not, and unpaired surrogates from U+D83C on becomes one space, while emoji
 * in the Basic Multilingual Plane such as U+231A are kept.</p>
 */
public class EmojiCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = -723015318244958736L;

  /**
   * The first code point of the range removed under {@link CompatibilityMode#LEGACY}: the high
   * surrogate U+D83C. Before 3.0.0 this class used the regex
   * {@code [\uD83C-\uDBFF\uDC00-\uDFFF]+}, whose character class Java reads as U+D83C to
   * U+10FC00 (the adjacent escapes {@code \uDBFF\uDC00} form one supplementary code point),
   * plus a literal hyphen and U+DFFF.
   */
  private static final int LEGACY_RANGE_FIRST = 0xD83C;

  /** The last code point of the range removed under {@link CompatibilityMode#LEGACY}. */
  private static final int LEGACY_RANGE_LAST = 0x10FC00;

  /** The hyphen removed under {@link CompatibilityMode#LEGACY}. */
  private static final int HYPHEN = '-';

  private static final EmojiCharSequenceNormalizer INSTANCE = new EmojiCharSequenceNormalizer();

  /** {@return the shared, stateless instance} */
  public static EmojiCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    if (CompatibilityMode.current() == CompatibilityMode.LEGACY) {
      return removeLegacyRuns(text);
    }
    UnicodeEmojiSequences sequences = UnicodeEmojiSequences.getInstance();
    StringBuilder normalized = null;
    int copiedThrough = 0;
    for (int i = 0; i < text.length();) {
      final int codePoint = Character.codePointAt(text, i);
      if (!sequences.isCandidateStart(codePoint)) {
        i += Character.charCount(codePoint);
        continue;
      }
      UnicodeEmojiSequences.Candidate candidate = sequences.candidateAt(text, i);
      if (candidate == null) {
        i += Character.charCount(codePoint);
      } else if (candidate.valid()) {
        if (normalized == null) {
          normalized = new StringBuilder(text.length());
        }
        normalized.append(text, copiedThrough, i).append(' ');
        i = candidate.end();
        copiedThrough = i;
      } else {
        i = candidate.end();
      }
    }
    if (normalized == null) {
      return text;
    }
    return normalized.append(text, copiedThrough, text.length()).toString();
  }

  /**
   * Replaces each maximal run of code points of the legacy set with one space.
   *
   * @param text The text to scan; never null.
   * @return The input itself when nothing matched, otherwise the normalized copy.
   */
  private CharSequence removeLegacyRuns(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final int next = i + Character.charCount(codePoint);
      if (isLegacyMember(codePoint)) {
        if (out == null) {
          out = new StringBuilder(length).append(text, 0, i);
        }
        out.append(' ');
        i = legacyRunEnd(text, next);
      } else {
        if (out != null) {
          out.append(text, i, next);
        }
        i = next;
      }
    }
    return out == null ? text : out.toString();
  }

  /**
   * {@return the exclusive end of the run of legacy set members that starts at {@code from}}
   *
   * @param text The text to scan; never null.
   * @param from The index to scan from.
   */
  private int legacyRunEnd(CharSequence text, int from) {
    final int length = text.length();
    int i = from;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (!isLegacyMember(codePoint)) {
        break;
      }
      i += Character.charCount(codePoint);
    }
    return i;
  }

  /**
   * {@return whether {@code codePoint} is removed under {@link CompatibilityMode#LEGACY}} An
   * unpaired surrogate is passed as its own value, the way
   * {@link Character#codePointAt(CharSequence, int)} reads it.
   *
   * @param codePoint The code point to test.
   */
  private boolean isLegacyMember(int codePoint) {
    return codePoint == HYPHEN
        || (codePoint >= LEGACY_RANGE_FIRST && codePoint <= LEGACY_RANGE_LAST);
  }

  /** {@return the shared instance, so deserialization keeps the singleton} */
  private Object readResolve() {
    return INSTANCE;
  }
}

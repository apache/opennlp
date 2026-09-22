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
 * Replaces complete, fully-qualified Unicode Emoji 18.0 sequences with whitespace.
 * Adjacent sequences form one run and become one space. Text-presentation characters and
 * structurally connected malformed emoji candidates are preserved.
 *
 * @deprecated Use {@link EmojiToEmoticonCharSequenceNormalizer} to retain emoji as text signal.
 */
@Deprecated(since = "3.0.0", forRemoval = true)
public class EmojiCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 4553401197981667914L;
  
  private static final EmojiCharSequenceNormalizer INSTANCE = new EmojiCharSequenceNormalizer();

  public static EmojiCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /** {@inheritDoc} */
  @Override public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    UnicodeEmojiSequences sequences = UnicodeEmojiSequences.getInstance();
    StringBuilder normalized = null;
    int copiedThrough = 0;
    for (int i = 0; i < text.length();) {
      UnicodeEmojiSequences.Candidate candidate = sequences.candidateAt(text, i);
      if (candidate != null && candidate.valid()) {
        if (normalized == null) {
          normalized = new StringBuilder(text.length());
        }
        normalized.append(text, copiedThrough, i).append(' ');
        i = candidate.end();
        copiedThrough = i;
      }
      else {
        i = candidate == null ? i + Character.charCount(Character.codePointAt(text, i))
            : candidate.end();
      }
    }
    if (normalized == null) {
      return text;
    }
    return normalized.append(text, copiedThrough, text.length()).toString();
  }

  private Object readResolve() {
    return INSTANCE;
  }
}

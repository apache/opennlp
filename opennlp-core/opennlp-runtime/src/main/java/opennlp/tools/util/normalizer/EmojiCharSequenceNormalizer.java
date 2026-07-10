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

import java.util.regex.Pattern;

/**
 * A {@link EmojiCharSequenceNormalizer} implementation that normalizes text
 * in terms of emojis. Every encounter will be replaced by a whitespace.
 *
 * @deprecated The surrogate-block pattern replaces every supplementary-plane code point with a
 *     space, not only emoji (a Deseret or Gothic letter is blanked just like a pictograph), while
 *     BMP pictographs such as U+2764 HEAVY BLACK HEART pass through untouched; deleting the symbol
 *     also destroys its signal for downstream components. Use
 *     {@link EmojiToEmoticonCharSequenceNormalizer} to fold emoji to ASCII emoticons, or
 *     {@link EmoticonToEmojiCharSequenceNormalizer} for the reverse direction. The default
 *     language detector chain intentionally keeps this normalizer: its models were trained with
 *     pictographs blanked, and folding to ASCII emoticons would feed them n-grams they never saw.
 */
@Deprecated(since = "3.0.0")
public class EmojiCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 4553401197981667914L;
  
  private static final EmojiCharSequenceNormalizer INSTANCE = new EmojiCharSequenceNormalizer();

  public static EmojiCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  private static final Pattern EMOJI_REGEX =
      Pattern.compile("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+");

  @Override
  public CharSequence normalize (CharSequence text) {
    return EMOJI_REGEX.matcher(text).replaceAll(" ");
  }
}

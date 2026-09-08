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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class EmojiCharSequenceNormalizerTest {

  public EmojiCharSequenceNormalizer normalizer = EmojiCharSequenceNormalizer.getInstance();

  @Test
  void normalizeEmoji() {

    String s = new StringBuilder()
        .append("Any funny text goes here ")
        .appendCodePoint(0x1F606)
        .appendCodePoint(0x1F606)
        .appendCodePoint(0x1F606)
        .append(" ")
        .appendCodePoint(0x1F61B)
        .toString();
    Assertions.assertEquals(
        "Any funny text goes here    ", normalizer.normalize(s));
  }

  @Test
  void normalizeUnpairedSurrogates() {
    // a lone high surrogate and a lone low surrogate are matched individually
    String s = "a" + '\uD83C' + "b" + '\uDC00' + "c";
    Assertions.assertEquals("a b c", normalizer.normalize(s));

    // adjacent surrogates, paired or not, collapse into a single space
    StringBuilder sb = new StringBuilder();
    sb.append("x").append('\uD83C').append('\uDC00').append("y");
    Assertions.assertEquals("x y", normalizer.normalize(sb));
  }

  @Test
  void normalizeMatchesCodePointsNotOnlyEmoji() {
    // the matched code point range is [U+D83C, U+10FC00], so BMP characters
    // from U+D83C up are replaced as well
    Assertions.assertEquals("a b", normalizer.normalize("a" + '\uE000' + "b"));

    // supplementary code points beyond U+10FC00 are kept verbatim
    StringBuilder sb = new StringBuilder();
    sb.append("a").appendCodePoint(0x10FFFF).append("b");
    Assertions.assertEquals("a" + new String(Character.toChars(0x10FFFF)) + "b",
        normalizer.normalize(sb));
  }

}

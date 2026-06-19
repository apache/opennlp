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

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextAnalyzerTest {

  private static final CharSequenceNormalizer LOWER = s -> s.toString().toLowerCase(Locale.ROOT);

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void testAnalyzePreservesSpansAndNormalizesTokens() {
    final String text = "Hello WORLD";
    final List<AnalyzedToken> tokens = TextAnalyzer.whitespace(LOWER).analyze(text);

    assertEquals(2, tokens.size());
    assertEquals(0, tokens.get(0).span().getStart());
    assertEquals(5, tokens.get(0).span().getEnd());
    assertEquals("Hello", tokens.get(0).original());
    assertEquals("hello", tokens.get(0).normalized());
    assertEquals("WORLD", tokens.get(1).original());
    assertEquals("world", tokens.get(1).normalized());
    assertEquals("Hello", tokens.get(0).span().getCoveredText(text).toString());
  }

  @Test
  void testSpanStaysCorrectWhenNormalizedLengthChanges() {
    final CharSequenceNormalizer bracket = s -> "[" + s + "]";
    final String text = "ab cd";
    final List<AnalyzedToken> tokens = TextAnalyzer.whitespace(bracket).analyze(text);

    assertEquals("[ab]", tokens.get(0).normalized());
    assertEquals(0, tokens.get(0).span().getStart());
    assertEquals(2, tokens.get(0).span().getEnd());
    assertEquals(3, tokens.get(1).span().getStart());
    assertEquals(5, tokens.get(1).span().getEnd());
  }

  @Test
  void testSplitsOnUnicodeWhitespace() {
    final String text = "alpha" + cp(0x00A0) + "beta";
    final List<AnalyzedToken> tokens = TextAnalyzer.whitespace(LOWER).analyze(text);

    assertEquals(2, tokens.size());
    assertEquals("alpha", tokens.get(0).normalized());
    assertEquals("beta", tokens.get(1).normalized());
  }

  @Test
  void testSupplementaryTokenIsKeptIntact() {
    final String emoji = cp(0x1F600);
    final String text = "a " + emoji + " b";
    final List<AnalyzedToken> tokens = TextAnalyzer.whitespace(LOWER).analyze(text);

    assertEquals(3, tokens.size());
    assertEquals(emoji, tokens.get(1).original());
    assertTrue(tokens.get(1).span().getEnd() - tokens.get(1).span().getStart() == emoji.length());
  }

  @Test
  void testTermsReturnsNormalizedFormsOnly() {
    assertEquals(List.of("a", "b", "c"), TextAnalyzer.whitespace(LOWER).terms("A B C"));
  }

  @Test
  void testEmptyAndWhitespaceOnlyYieldNoTokens() {
    assertEquals(List.of(), TextAnalyzer.whitespace(LOWER).analyze(""));
    assertEquals(List.of(), TextAnalyzer.whitespace(LOWER).analyze("   "));
  }

  @Test
  void testRejectsNullArguments() {
    assertThrows(NullPointerException.class, () -> new TextAnalyzer(null, LOWER));
    assertThrows(NullPointerException.class, () -> new TextAnalyzer(CharClass.whitespace(), null));
    assertThrows(NullPointerException.class, () -> TextAnalyzer.whitespace(LOWER).analyze(null));
  }
}

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
package opennlp.subword.sentencepiece;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.AlignedText;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@code OffsetAwareNormalizer} view: the model normalizer's output must align
 * back to the original text exactly, including through whitespace collapsing, character-map
 * replacements, and supplementary characters.
 */
class SentencePieceAlignmentTest {

  private static SentencePieceTokenizer unigram() {
    return SentencePieceParityTest.tokenizer("tiny-unigram");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Hello world", " Hello   world  ", "Hello world.\nSecond line",
      "3.14159 x 42", "family emoji", "  leading and trailing  ", "a", "", "   "})
  void testAlignedMatchesUnaligned(String input) {
    final AlignedText aligned = unigram().normalizeAligned(input);
    assertEquals(unigram().normalize(input).toString(), aligned.normalizedString());
    assertEquals(input, aligned.original().toString());
    assertEquals(aligned.normalizedString().length(), aligned.alignment().normalizedLength());
    assertEquals(input.length(), aligned.alignment().originalLength());
  }

  @Test
  void testWordMapsBackThroughCollapsedWhitespace() {
    final String input = " Hello   world  ";
    final AlignedText aligned = unigram().normalizeAligned(input);
    final String normalized = aligned.normalizedString();

    final int at = normalized.indexOf("world");
    final Span original = aligned.toOriginalSpan(at, at + "world".length());
    assertEquals("world", input.substring(original.getStart(), original.getEnd()));
  }

  @Test
  void testLigatureReplacementMapsToItsSourceCharacter() {
    // The character map expands the single ligature to two letters; both normalized letters
    // must map back to the one original character.
    final String input = cp(0xFB01) + "nancial";
    final AlignedText aligned = unigram().normalizeAligned(input);
    final String normalized = aligned.normalizedString();

    final int at = normalized.indexOf("fi");
    assertTrue(at >= 0, "the character map must expand the ligature, got " + normalized);
    final Span original = aligned.toOriginalSpan(at, at + 2);
    assertEquals(0, original.getStart());
    assertEquals(1, original.getEnd());
  }

  @Test
  void testSupplementaryCharacterSpansUseUtf16Units() {
    final String input = "I love " + new String(Character.toChars(0x1F355)) + " pizza";
    final List<SubwordPiece> pieces = unigram().encode(input);

    SubwordPiece pizzaSlice = null;
    for (final SubwordPiece piece : pieces) {
      if (piece.piece().contains(new String(Character.toChars(0x1F355)))) {
        pizzaSlice = piece;
      }
    }
    assertTrue(pizzaSlice != null, "the emoji must surface as a piece, got " + pieces);
    assertEquals(7, pizzaSlice.start());
    assertEquals(9, pizzaSlice.end());
    assertEquals(new String(Character.toChars(0x1F355)),
        input.substring(pizzaSlice.start(), pizzaSlice.end()));
  }

  @Test
  void testEverySpanIsWithinTheOriginalText() {
    final String input = "quotes " + cp(0x201C) + "fancy" + cp(0x201D) + " and "
        + cp(0x2018) + "single" + cp(0x2019) + " " + cp(0x2014) + " dash";
    for (final SubwordPiece piece : unigram().encode(input)) {
      assertTrue(piece.start() >= 0 && piece.end() <= input.length(),
          "span " + piece + " must lie inside the input");
      assertTrue(piece.start() <= piece.end(), "span " + piece + " must not be inverted");
    }
  }

  @Test
  void testSpansAreMonotonicAndAdjacent() {
    final String input = "The quick brown fox jumps over the lazy dog.";
    int previousEnd = 0;
    for (final SubwordPiece piece : unigram().encode(input)) {
      assertTrue(piece.start() >= previousEnd || piece.start() == piece.end(),
          "piece " + piece + " must not step back before " + previousEnd);
      previousEnd = Math.max(previousEnd, piece.end());
    }
    assertEquals(input.length(), previousEnd, "the last span must reach the end of the input");
  }

  @Test
  void testUnpairedSurrogateIsDeterministic() {
    final String input = "a" + (char) 0xD83C + "b";
    final List<SubwordPiece> first = unigram().encode(input);
    final List<SubwordPiece> second = unigram().encode(input);
    assertEquals(first, second);
    int covered = 0;
    for (final SubwordPiece piece : first) {
      covered = Math.max(covered, piece.end());
    }
    assertEquals(input.length(), covered);
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void testNullInputsFailLoudly() {
    assertThrows(IllegalArgumentException.class, () -> unigram().encode(null));
    assertThrows(IllegalArgumentException.class, () -> unigram().normalizeAligned(null));
    assertThrows(IllegalArgumentException.class, () -> unigram().normalize(null));
  }
}

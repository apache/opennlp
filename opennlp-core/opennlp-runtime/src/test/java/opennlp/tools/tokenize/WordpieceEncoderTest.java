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
package opennlp.tools.tokenize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The encoder held against {@link ReferenceBertPipeline} for piece-sequence parity (the encoder's
 * contract is "the same pipeline, plus ids and spans"), plus exact hand-computed span
 * assertions through every normalization step that changes, inserts, or removes characters.
 */
class WordpieceEncoderTest {

  // Ids are indices: [PAD]=0, [UNK]=1, [CLS]=2, [SEP]=3, hello=4, world=5, ##s=6, won=7,
  // ##der=8, ##ful=9, ca=10, ##fe=11, istanbul=12, U+4E2D=13, U+56FD=14, .=15, ,=16, !=17,
  // he=18, ##llo=19, Greek "sofos" with a final sigma=20.
  private static final List<String> VOCAB = List.of(
      "[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "world", "##s", "won", "##der", "##ful",
      "ca", "##fe", "istanbul", "\u4E2D", "\u56FD", ".", ",", "!", "he", "##llo",
      "\u03C3\u03BF\u03C6\u03BF\u03C2");

  private static WordpieceEncoder uncased() {
    return new WordpieceEncoder(VOCAB);
  }

  private static void assertPiece(SubwordPiece piece, String expectedPiece, int expectedId,
                                  int expectedStart, int expectedEnd) {
    assertEquals(expectedPiece, piece.piece());
    assertEquals(expectedId, piece.id());
    assertEquals(expectedStart, piece.start(), "start of " + piece);
    assertEquals(expectedEnd, piece.end(), "end of " + piece);
  }

  /**
   * The curated parity inputs, each exercising a normalization step of the pipeline.
   *
   * @return The inputs.
   */
  static Stream<String> curatedInputs() {
    return Stream.of(
        "",
        "   ",
        "Hello, WORLD!",
        "Wonderful",
        "hellos",
        // An accented e, stripped by NFD decomposition.
        "Caf\u00E9",
        // The Turkish dotted capital I: lower cases to two chars, then the dot strips away.
        "\u0130stanbul",
        // CJK ideographs are isolated into single-character tokens.
        "\u4E2D\u56FD is CJK",
        // Greek upper case: the trailing sigma takes the contextual final-sigma mapping.
        "\u03A3\u039F\u03A6\u039F\u03A3",
        // The NBSP is whitespace in the BERT sense.
        "hello\u00A0world",
        // NUL and the zero-width space are removed by the cleaning stage.
        "a\u0000b\u200Bc",
        // An emoji: unknown to the vocabulary, and a surrogate pair.
        "\uD83D\uDE00",
        "!!!",
        "a".repeat(101),
        "he said: \u00ABhello\u00BB.");
  }

  @ParameterizedTest
  @MethodSource("curatedInputs")
  void testPieceSequenceMatchesTheReferencePipelineOnCuratedInputs(String input) {
    final ReferenceBertPipeline reference = new ReferenceBertPipeline(new HashSet<>(VOCAB), true);
    final WordpieceEncoder encoder = uncased();
    assertArrayEquals(reference.tokenize(input), encoder.encodeToPieces(input),
        "parity broke on: " + input);
  }

  @Test
  void testPieceSequenceMatchesTheReferencePipelineOnRandomInputs() {
    final int[] pool = {'a', 'b', 'A', 'B', 'z', ' ', ' ', '\t', 0x00A0, '.', '!', ',',
        0x0301, 0x00E9, 0x0130, 0x03A3, 0x03C3, 0x03BF, 0x4E2D, 0xFFFD, 0x200B, 0x1F600, 0};
    final Random random = new Random(42);
    for (final boolean lowerCase : new boolean[] {true, false}) {
      final ReferenceBertPipeline reference =
          new ReferenceBertPipeline(new HashSet<>(VOCAB), lowerCase);
      final WordpieceEncoder encoder = new WordpieceEncoder(VOCAB, lowerCase);
      for (int round = 0; round < 400; round++) {
        final StringBuilder text = new StringBuilder();
        final int length = random.nextInt(25);
        for (int i = 0; i < length; i++) {
          text.appendCodePoint(pool[random.nextInt(pool.length)]);
        }
        final String input = text.toString();
        assertArrayEquals(reference.tokenize(input), encoder.encodeToPieces(input),
            "parity broke on: " + input);

        // Span invariants: within bounds and never moving backwards.
        int previousStart = 0;
        for (final SubwordPiece piece : encoder.encode(input)) {
          assertTrue(piece.start() >= previousStart && piece.end() <= input.length(),
              "span out of order or bounds in " + input + ": " + piece);
          previousStart = piece.start();
        }
      }
    }
  }

  @Test
  void testSpansSurvivePunctuationIsolationAndCaseFolding() {
    final List<SubwordPiece> pieces = uncased().encode("Hello, WORLD!");
    assertEquals(6, pieces.size());
    assertPiece(pieces.get(0), "[CLS]", 2, 0, 0);
    assertPiece(pieces.get(1), "hello", 4, 0, 5);
    assertPiece(pieces.get(2), ",", 16, 5, 6);
    assertPiece(pieces.get(3), "world", 5, 7, 12);
    assertPiece(pieces.get(4), "!", 17, 12, 13);
    assertPiece(pieces.get(5), "[SEP]", 3, 13, 13);
  }

  @Test
  void testSpansSurviveAccentStripping() {
    // The accent is stripped by NFD, yet ##fe still covers the accented surface.
    final List<SubwordPiece> pieces = uncased().encode("Caf\u00E9");
    assertEquals(4, pieces.size());
    assertPiece(pieces.get(1), "ca", 10, 0, 2);
    assertPiece(pieces.get(2), "##fe", 11, 2, 4);
  }

  @Test
  void testSpansSurviveLengthChangingLowerCasing() {
    // The Turkish dotted capital I lower cases to two chars before the combining dot strips;
    // the piece still covers the original eight chars.
    final List<SubwordPiece> pieces = uncased().encode("\u0130stanbul");
    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1), "istanbul", 12, 0, 8);
  }

  @Test
  void testCjkIsolationYieldsOnePieceAndSpanPerIdeograph() {
    final List<SubwordPiece> pieces = uncased().encode("\u4E2D\u56FD");
    assertEquals(4, pieces.size());
    assertPiece(pieces.get(1), "\u4E2D", 13, 0, 1);
    assertPiece(pieces.get(2), "\u56FD", 14, 1, 2);
  }

  @Test
  void testUnknownWordCoversItsWholeSurfaceIncludingRemovedChars() {
    // NUL and the zero-width space are removed by cleaning, so one word "abc" remains; it is
    // not representable and becomes the unknown piece spanning the full original surface.
    final List<SubwordPiece> pieces = uncased().encode("a\u0000b\u200Bc");
    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1), "[UNK]", 1, 0, 5);
  }

  @Test
  void testContextualCaseMappingFallsBackToWordWideSpans() {
    // Greek final sigma is a contextual mapping the per-char rerun cannot reproduce, so the
    // word's pieces fall back to spanning the whole word; content parity is asserted in the
    // differential tests above.
    final List<SubwordPiece> pieces =
        uncased().encode("\u03A3\u039F\u03A6\u039F\u03A3");
    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1),
        "\u03C3\u03BF\u03C6\u03BF\u03C2", 20, 0, 5);
  }

  @Test
  void testEncodeToIdsCarriesVocabularyLineNumbers() {
    assertArrayEquals(new int[] {2, 4, 5, 6, 3}, uncased().encodeToIds("Hello worldS"));
  }

  @Test
  void testCasedEncoderKeepsCase() {
    final List<String> vocabulary = new ArrayList<>(VOCAB);
    vocabulary.add("Hello");
    final WordpieceEncoder cased = new WordpieceEncoder(vocabulary, false);
    final List<SubwordPiece> pieces = cased.encode("Hello hello");
    assertPiece(pieces.get(1), "Hello", vocabulary.size() - 1, 0, 5);
    assertPiece(pieces.get(2), "hello", 4, 6, 11);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void testEmptyAndBlankTextEncodeToTheFramePiecesOnly(String input) {
    final List<SubwordPiece> pieces = uncased().encode(input);
    assertEquals(2, pieces.size(), "frame pieces broke on <" + input + ">");
    assertPiece(pieces.get(0), "[CLS]", 2, 0, 0);
    assertPiece(pieces.get(1), "[SEP]", 3, input.length(), input.length());
  }

  @Test
  void testValidationFailsLoudly() {
    assertThrows(IllegalArgumentException.class, () -> new WordpieceEncoder(null));
    assertThrows(IllegalArgumentException.class,
        () -> new WordpieceEncoder(List.of("[CLS]", "[SEP]")));
    assertThrows(IllegalArgumentException.class,
        () -> new WordpieceEncoder(List.of("[CLS]", "[SEP]", "[UNK]", "dup", "dup")));
    final List<String> withNull = new ArrayList<>(VOCAB);
    withNull.add(null);
    assertThrows(IllegalArgumentException.class, () -> new WordpieceEncoder(withNull));
    assertThrows(IllegalArgumentException.class, () -> uncased().encode(null));
  }
}

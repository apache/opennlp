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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks token ids and original-text offsets through each normalization stage. */
class WordpieceEncoderTest {

  private static final long REFERENCE_RANDOM_SEED = 1885L;
  private static final int REFERENCE_SAMPLE_COUNT = 500;
  private static final long SPAN_RANDOM_SEED = 42L;
  private static final int SPAN_SAMPLE_COUNT = 400;

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

  @Test
  void testGeneratedInputsMatchReferenceNormalizationOrder() {
    final int[] pool = {'a', 'B', ' ', '\t', '\n', 0x00A0, 0x00C9, 0x0130, 0x0301,
        0x0391, 0x03BF, 0x1715, 0x200B, 0x2028, 0x2029, 0x302E, 0x4E2D, 0x20000,
        '.', '\'', 0x10100, 0x1F600, 0xFFFD, 0};
    final Random random = new Random(REFERENCE_RANDOM_SEED);
    for (final boolean lowerCase : new boolean[] {true, false}) {
      for (int sampleIndex = 0; sampleIndex < REFERENCE_SAMPLE_COUNT; sampleIndex++) {
        final StringBuilder input = new StringBuilder();
        final int length = random.nextInt(24);
        for (int i = 0; i < length; i++) {
          input.appendCodePoint(pool[random.nextInt(pool.length)]);
        }

        final List<String> expectedWords = referenceBasicTokens(input, lowerCase);
        final Set<String> vocabulary = new LinkedHashSet<>(
            List.of("[UNK]", "[CLS]", "[SEP]"));
        vocabulary.addAll(expectedWords);
        final WordpieceEncoder encoder = new WordpieceEncoder(
            new ArrayList<>(vocabulary), lowerCase);

        final List<String> expected = new ArrayList<>(expectedWords.size() + 2);
        expected.add("[CLS]");
        expected.addAll(expectedWords);
        expected.add("[SEP]");
        assertEquals(expected, List.of(encoder.encodeToPieces(input)),
            "unexpected normalization for: " + codePointList(input));
      }
    }
  }

  private static List<String> codePointList(CharSequence text) {
    return text.codePoints()
        .mapToObj(codePoint -> "U+" + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT))
        .toList();
  }

  private static List<String> referenceBasicTokens(CharSequence input, boolean lowerCase) {
    final StringBuilder cleaned = new StringBuilder(input.length() + 16);
    input.codePoints().forEach(codePoint -> {
      if (codePoint == 0 || codePoint == 0xFFFD || BertNormalization.isControl(codePoint)) {
        return;
      }
      if (BertNormalization.isWhitespace(codePoint)) {
        cleaned.append(' ');
      } else if (BertNormalization.isCjk(codePoint)) {
        cleaned.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        cleaned.appendCodePoint(codePoint);
      }
    });

    final List<String> words = splitReferenceWhitespace(cleaned);
    final List<String> pieces = new ArrayList<>();
    for (String word : words) {
      if (lowerCase) {
        word = stripReferenceAccents(word.toLowerCase(Locale.ROOT));
      }
      final StringBuilder current = new StringBuilder();
      word.codePoints().forEach(codePoint -> {
        if (BertNormalization.isPunctuation(codePoint)) {
          addIfNotEmpty(pieces, current);
          pieces.add(new String(Character.toChars(codePoint)));
        } else {
          current.appendCodePoint(codePoint);
        }
      });
      addIfNotEmpty(pieces, current);
    }
    return pieces;
  }

  private static List<String> splitReferenceWhitespace(CharSequence text) {
    final List<String> words = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    text.codePoints().forEach(codePoint -> {
      if (codePoint == ' ' || codePoint == 0x2028 || codePoint == 0x2029) {
        addIfNotEmpty(words, current);
      } else {
        current.appendCodePoint(codePoint);
      }
    });
    addIfNotEmpty(words, current);
    return words;
  }

  private static String stripReferenceAccents(String text) {
    final StringBuilder stripped = new StringBuilder(text.length());
    Normalizer.normalize(text, Normalizer.Form.NFD).codePoints().forEach(codePoint -> {
      if (Character.getType(codePoint) != Character.NON_SPACING_MARK) {
        stripped.appendCodePoint(codePoint);
      }
    });
    return stripped.toString();
  }

  private static void addIfNotEmpty(List<String> words, StringBuilder current) {
    if (!current.isEmpty()) {
      words.add(current.toString());
      current.setLength(0);
    }
  }

  @Test
  void testGeneratedUnicodeProducesOrderedBoundedSpans() {
    final int[] pool = {'a', 'b', 'A', 'B', 'z', ' ', ' ', '\t', 0x00A0, '.', '!', ',',
        0x0301, 0x00E9, 0x0130, 0x03A3, 0x03C3, 0x03BF, 0x4E2D, 0xFFFD, 0x200B, 0x1F600, 0};
    final Random random = new Random(SPAN_RANDOM_SEED);
    for (final boolean lowerCase : new boolean[] {true, false}) {
      final WordpieceEncoder encoder = new WordpieceEncoder(VOCAB, lowerCase);
      for (int sampleIndex = 0; sampleIndex < SPAN_SAMPLE_COUNT; sampleIndex++) {
        final StringBuilder text = new StringBuilder();
        final int length = random.nextInt(25);
        for (int i = 0; i < length; i++) {
          text.appendCodePoint(pool[random.nextInt(pool.length)]);
        }
        final String input = text.toString();
        final List<SubwordPiece> pieces = encoder.encode(input);
        assertPiece(pieces.getFirst(), "[CLS]", 2, 0, 0);
        assertPiece(pieces.getLast(), "[SEP]", 3, input.length(), input.length());

        int previousStart = 0;
        for (final SubwordPiece piece : pieces) {
          assertTrue(piece.start() >= previousStart && piece.end() <= input.length(),
              "span out of order or bounds in " + input + ": " + piece);
          previousStart = piece.start();
        }
      }
    }
  }

  @Test
  void testSpansPreservePunctuationIsolationAndCaseMapping() {
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
  void testSpansPreserveAccentStripping() {
    // The accent is stripped by NFD, yet ##fe still covers the accented surface.
    final List<SubwordPiece> pieces = uncased().encode("Caf\u00E9");
    assertEquals(4, pieces.size());
    assertPiece(pieces.get(1), "ca", 10, 0, 2);
    assertPiece(pieces.get(2), "##fe", 11, 2, 4);
  }

  @Test
  void testSpansPreserveDecomposedAccentStripping() {
    final List<SubwordPiece> pieces = uncased().encode("Cafe\u0301");

    assertEquals(4, pieces.size());
    assertPiece(pieces.get(1), "ca", 10, 0, 2);
    assertPiece(pieces.get(2), "##fe", 11, 2, 5);
  }

  @Test
  void testNormalizationReordersCombiningMarksAcrossCodePoints() {
    final String normalized = "a\u1715\u302E";
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[UNK]", "[CLS]", "[SEP]", normalized));

    final List<SubwordPiece> pieces = encoder.encode("a\u302E\u1715");

    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1), normalized, 3, 0, 3);
  }

  @Test
  void testSpansPreserveLengthChangingLowerCasing() {
    // The Turkish dotted capital I normalizes to i; the piece still covers the full input range.
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
  void testSupplementaryCjkOffsetsUseUtf16Indices() {
    final String ideograph = new String(Character.toChars(0x20000));
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[UNK]", "[CLS]", "[SEP]", "a", ideograph, "b"));

    final List<SubwordPiece> pieces = encoder.encode("A" + ideograph + "B");

    assertPiece(pieces.get(1), "a", 3, 0, 1);
    assertPiece(pieces.get(2), ideograph, 4, 1, 3);
    assertPiece(pieces.get(3), "b", 5, 3, 4);
  }

  @Test
  void testLineAndParagraphSeparatorsSplitWords() {
    // Zl and Zp are not whitespace in the BERT _is_whitespace sense, but the reference
    // pipeline's whitespace_tokenize (Python's str.split()) breaks words on them.
    final List<SubwordPiece> pieces = uncased().encode("hello\u2028world\u2029hello");
    assertEquals(5, pieces.size());
    assertPiece(pieces.get(1), "hello", 4, 0, 5);
    assertPiece(pieces.get(2), "world", 5, 6, 11);
    assertPiece(pieces.get(3), "hello", 4, 12, 17);
  }

  @Test
  void testUnknownWordCoversCompleteSourceRangeIncludingRemovedChars() {
    // NUL and the zero-width space are removed by cleaning, so one word "abc" remains; it is
    // not representable and becomes the unknown piece spanning the full original surface.
    final List<SubwordPiece> pieces = uncased().encode("a\u0000b\u200Bc");
    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1), "[UNK]", 1, 0, 5);
  }

  @Test
  void testPartialWordpieceMatchBecomesOneUnknownPiece() {
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[UNK]", "[CLS]", "[SEP]", "won", "##der"));

    final List<SubwordPiece> pieces = encoder.encode("Wonderful");

    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1), "[UNK]", 0, 0, 9);
  }

  @Test
  void testFinalSigmaMappingPreservesSourceRange() {
    final List<SubwordPiece> pieces =
        uncased().encode("\u03A3\u039F\u03A6\u039F\u03A3");
    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1),
        "\u03C3\u03BF\u03C6\u03BF\u03C2", 20, 0, 5);
  }

  @Test
  void testEncodeToIdsReturnsVocabularyLineNumbers() {
    assertArrayEquals(new int[] {2, 4, 5, 6, 3}, uncased().encodeToIds("Hello worldS"));
  }

  @Test
  void testExplicitVocabularyIdsArePreserved() {
    final WordpieceEncoder encoder = new WordpieceEncoder(
        Map.of("<s>", 101, "</s>", 205, "<unk>", 999, "alice", 42), true,
        "<s>", "</s>", "<unk>");

    assertArrayEquals(new int[] {101, 42, 999, 205},
        encoder.encodeToIds("Alice rabbit"));
  }

  @Test
  void testCopiesVocabularyInputs() {
    final List<String> list = new ArrayList<>(VOCAB);
    final WordpieceEncoder fromList = new WordpieceEncoder(list);
    list.set(4, "changed");

    final Map<String, Integer> map = new HashMap<>(Map.of(
        "[CLS]", 10, "[SEP]", 11, "[UNK]", 12, "hello", 13));
    final WordpieceEncoder fromMap = new WordpieceEncoder(
        map, true, "[CLS]", "[SEP]", "[UNK]");
    map.remove("hello");

    assertArrayEquals(new String[] {"[CLS]", "hello", "[SEP]"},
        fromList.encodeToPieces("Hello"));
    assertArrayEquals(new int[] {10, 13, 11}, fromMap.encodeToIds("Hello"));
  }

  @Test
  void testAliceExamplePreservesOriginalOffsets() {
    final List<String> vocabulary = List.of(
        "[UNK]", "[CLS]", "[SEP]", "alice", "was", "begin", "##ning", "to", "get",
        "very", "tired", ".");
    final WordpieceEncoder encoder = new WordpieceEncoder(vocabulary);
    final String text = "Alice was beginning to get very tired.";

    final List<SubwordPiece> pieces = encoder.encode(text);

    assertArrayEquals(new String[] {
        "[CLS]", "alice", "was", "begin", "##ning", "to", "get", "very", "tired", ".",
        "[SEP]"}, encoder.encodeToPieces(text));
    assertArrayEquals(new int[] {1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 2},
        encoder.encodeToIds(text));
    assertPiece(pieces.get(1), "alice", 3, 0, 5);
    assertPiece(pieces.get(2), "was", 4, 6, 9);
    assertPiece(pieces.get(3), "begin", 5, 10, 15);
    assertPiece(pieces.get(4), "##ning", 6, 15, 19);
    assertPiece(pieces.get(8), "tired", 10, 32, 37);
    assertPiece(pieces.get(9), ".", 11, 37, 38);
  }

  @Test
  void testMaximumWordLengthCountsUnicodeCodePoints() {
    final String face = "\uD83D\uDE00";
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[UNK]", "[CLS]", "[SEP]", face, "##" + face), false);

    final String input = face.repeat(100);
    final List<SubwordPiece> pieces = encoder.encode(input);

    assertEquals(102, pieces.size());
    assertPiece(pieces.get(1), face, 3, 0, 2);
    assertPiece(pieces.get(100), "##" + face, 4, 198, 200);
  }

  @Test
  void testDoesNotSplitSupplementaryCodePoint() {
    final String face = "\uD83D\uDE00";
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[UNK]", "[CLS]", "[SEP]", "\uD83D", "##\uDE00"), false);

    final List<SubwordPiece> pieces = encoder.encode(face);

    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1), "[UNK]", 0, 0, 2);
  }

  @Test
  void testWordBeyondMaximumLengthBecomesUnknown() {
    final String face = "\uD83D\uDE00";
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[UNK]", "[CLS]", "[SEP]", face, "##" + face), false);

    final String input = face.repeat(101);
    final List<SubwordPiece> pieces = encoder.encode(input);

    assertEquals(3, pieces.size());
    assertPiece(pieces.get(1), "[UNK]", 0, 0, 202);
  }

  @Test
  void testCustomMaximumWordLength() {
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[UNK]", "[CLS]", "[SEP]", "a", "##a"), false, 2);

    assertArrayEquals(new String[] {"[CLS]", "a", "##a", "[SEP]"},
        encoder.encodeToPieces("aa"));
    assertArrayEquals(new String[] {"[CLS]", "[UNK]", "[SEP]"},
        encoder.encodeToPieces("aaa"));
  }

  @Test
  void testRejectsNegativeMaximumWordLength() {
    assertEquals("maxWordCodePoints must not be negative",
        assertThrows(IllegalArgumentException.class,
            () -> new WordpieceEncoder(VOCAB, true, -1)).getMessage());
  }

  @ParameterizedTest
  @MethodSource("invalidSubwordPieces")
  void testSubwordPieceRejectsInvalidFields(String expectedMessage, Executable constructor) {
    assertEquals(expectedMessage,
        assertThrows(IllegalArgumentException.class, constructor).getMessage());
  }

  private static Stream<Arguments> invalidSubwordPieces() {
    return Stream.of(
        Arguments.of("piece must not be null",
            (Executable) () -> new SubwordPiece(null, 0, 0, 0)),
        Arguments.of("piece must not be empty",
            (Executable) () -> new SubwordPiece("", 0, 0, 0)),
        Arguments.of("id must not be negative",
            (Executable) () -> new SubwordPiece("piece", -1, 0, 0)),
        Arguments.of("start must not be negative",
            (Executable) () -> new SubwordPiece("piece", 0, -1, 0)),
        Arguments.of("end must be at least start",
            (Executable) () -> new SubwordPiece("piece", 0, 2, 1)));
  }

  @Test
  void testCasedEncoderPreservesCase() {
    final List<String> vocabulary = new ArrayList<>(VOCAB);
    vocabulary.add("Hello");
    final WordpieceEncoder cased = new WordpieceEncoder(vocabulary, false);
    final List<SubwordPiece> pieces = cased.encode("Hello hello");
    assertPiece(pieces.get(1), "Hello", vocabulary.size() - 1, 0, 5);
    assertPiece(pieces.get(2), "hello", 4, 6, 11);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\u0000", "\u200B", "\u0301"})
  void testEmptyAndBlankTextEncodeToTheFramePiecesOnly(String input) {
    final List<SubwordPiece> pieces = uncased().encode(input);
    assertEquals(2, pieces.size(), "unexpected frame pieces for <" + input + ">");
    assertPiece(pieces.get(0), "[CLS]", 2, 0, 0);
    assertPiece(pieces.get(1), "[SEP]", 3, input.length(), input.length());
  }

  @ParameterizedTest
  @MethodSource("invalidListVocabularies")
  void testRejectsInvalidListVocabulary(String expectedMessage, Executable constructor) {
    assertEquals(expectedMessage,
        assertThrows(IllegalArgumentException.class, constructor).getMessage());
  }

  private static Stream<Arguments> invalidListVocabularies() {
    final List<String> withNull = new ArrayList<>(VOCAB);
    withNull.add(null);
    return Stream.of(
        Arguments.of("vocabulary must not be null",
            (Executable) () -> new WordpieceEncoder(null)),
        Arguments.of("vocabulary must not contain duplicate piece 'dup'",
            (Executable) () -> new WordpieceEncoder(
                List.of("[CLS]", "[SEP]", "[UNK]", "dup", "dup"))),
        Arguments.of("vocabulary must not contain an empty piece at index 3",
            (Executable) () -> new WordpieceEncoder(
                List.of("[CLS]", "[SEP]", "[UNK]", ""))),
        Arguments.of("vocabulary must not contain null at index " + (withNull.size() - 1),
            (Executable) () -> new WordpieceEncoder(withNull)));
  }

  @ParameterizedTest
  @MethodSource("invalidMapVocabularies")
  void testRejectsInvalidMapVocabulary(String expectedMessage, Executable constructor) {
    assertEquals(expectedMessage,
        assertThrows(IllegalArgumentException.class, constructor).getMessage());
  }

  private static Stream<Arguments> invalidMapVocabularies() {
    final Map<String, Integer> nullPiece = new HashMap<>();
    nullPiece.put("[CLS]", 0);
    nullPiece.put("[SEP]", 1);
    nullPiece.put("[UNK]", 2);
    nullPiece.put(null, 0);
    final Map<String, Integer> nullId = new HashMap<>();
    nullId.put("[SEP]", 1);
    nullId.put("[UNK]", 2);
    nullId.put("[CLS]", null);
    return Stream.of(
        Arguments.of("vocabularyIds must not be null",
            (Executable) () -> new WordpieceEncoder((Map<String, Integer>) null, true,
                "[CLS]", "[SEP]", "[UNK]")),
        Arguments.of("vocabularyIds must not contain a negative id for piece '[UNK]'",
            (Executable) () -> new WordpieceEncoder(
                Map.of("[CLS]", 0, "[SEP]", 1, "[UNK]", -1), true,
                "[CLS]", "[SEP]", "[UNK]")),
        Arguments.of("vocabularyIds must not contain an empty piece",
            (Executable) () -> new WordpieceEncoder(
                Map.of("[CLS]", 0, "[SEP]", 1, "[UNK]", 2, "", 3), true,
                "[CLS]", "[SEP]", "[UNK]")),
        Arguments.of("vocabularyIds must not contain null pieces or ids",
            (Executable) () -> new WordpieceEncoder(
                nullPiece, true, "[CLS]", "[SEP]", "[UNK]")),
        Arguments.of("vocabularyIds must not contain null pieces or ids",
            (Executable) () -> new WordpieceEncoder(
                nullId, true, "[CLS]", "[SEP]", "[UNK]")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"[CLS]", "[SEP]", "[UNK]"})
  void testRejectsMissingSpecialToken(String missingToken) {
    final List<String> vocabulary = new ArrayList<>(VOCAB);
    vocabulary.remove(missingToken);

    assertEquals("vocabulary must contain special token '" + missingToken + "'",
        assertThrows(IllegalArgumentException.class,
            () -> new WordpieceEncoder(vocabulary)).getMessage());
  }

  @ParameterizedTest
  @MethodSource("invalidSpecialTokens")
  void testRejectsNullAndEmptySpecialTokens(String expectedMessage, Executable constructor) {
    assertEquals(expectedMessage,
        assertThrows(IllegalArgumentException.class, constructor).getMessage());
  }

  private static Stream<Arguments> invalidSpecialTokens() {
    return Stream.of(
        Arguments.of("classificationToken must not be null",
            (Executable) () -> new WordpieceEncoder(VOCAB, true, null, "[SEP]", "[UNK]")),
        Arguments.of("separatorToken must not be null",
            (Executable) () -> new WordpieceEncoder(VOCAB, true, "[CLS]", null, "[UNK]")),
        Arguments.of("unknownToken must not be null",
            (Executable) () -> new WordpieceEncoder(VOCAB, true, "[CLS]", "[SEP]", null)),
        Arguments.of("classificationToken must not be empty",
            (Executable) () -> new WordpieceEncoder(VOCAB, true, "", "[SEP]", "[UNK]")),
        Arguments.of("separatorToken must not be empty",
            (Executable) () -> new WordpieceEncoder(VOCAB, true, "[CLS]", "", "[UNK]")),
        Arguments.of("unknownToken must not be empty",
            (Executable) () -> new WordpieceEncoder(VOCAB, true, "[CLS]", "[SEP]", "")));
  }

  @Test
  void testRejectsNullText() {
    assertEquals("text must not be null",
        assertThrows(IllegalArgumentException.class,
            () -> uncased().encode(null)).getMessage());
  }
}

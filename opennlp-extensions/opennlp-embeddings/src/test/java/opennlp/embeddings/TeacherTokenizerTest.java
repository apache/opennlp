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
package opennlp.embeddings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The teacher tokenizer cleaning mirrors Model2Vec: unused tokens and special added tokens other
 * than the unknown and pad tokens are dropped, the survivors are renumbered in their original id
 * order, and the rewritten {@code tokenizer.json} carries the pruned vocabulary, the remapped
 * unknown id, a null post-processor, and only the unknown/pad added tokens.
 */
class TeacherTokenizerTest {

  // A WordPiece teacher: the special tokens are added tokens, plus one [unused] row and one
  // content row pair. The post-processor wraps sequences in [CLS]/[SEP] (ids 2 and 3).
  private static final String WORDPIECE_TEACHER =
      "{\"version\":\"1.0\","
          + "\"normalizer\":{\"type\":\"BertNormalizer\",\"lowercase\":true},"
          + "\"added_tokens\":["
          + "{\"id\":0,\"content\":\"[PAD]\",\"special\":true},"
          + "{\"id\":1,\"content\":\"[UNK]\",\"special\":true},"
          + "{\"id\":2,\"content\":\"[CLS]\",\"special\":true},"
          + "{\"id\":3,\"content\":\"[SEP]\",\"special\":true},"
          + "{\"id\":4,\"content\":\"[MASK]\",\"special\":true}],"
          + "\"post_processor\":{\"type\":\"TemplateProcessing\","
          + "\"single\":[{\"SpecialToken\":{\"id\":\"[CLS]\",\"type_id\":0}},"
          + "{\"Sequence\":{\"id\":\"A\",\"type_id\":0}},"
          + "{\"SpecialToken\":{\"id\":\"[SEP]\",\"type_id\":0}}],"
          + "\"special_tokens\":{\"[CLS]\":{\"id\":\"[CLS]\",\"ids\":[2],\"tokens\":[\"[CLS]\"]},"
          + "\"[SEP]\":{\"id\":\"[SEP]\",\"ids\":[3],\"tokens\":[\"[SEP]\"]}}},"
          + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
          + "\"vocab\":{\"[PAD]\":0,\"[UNK]\":1,\"[CLS]\":2,\"[SEP]\":3,\"[MASK]\":4,"
          + "\"hello\":5,\"[unused1]\":6,\"world\":7}}}";

  // A Unigram teacher in the bge-m3 shape: <s>, <pad>, </s>, <unk> lead the vocabulary, <mask>
  // trails it; all five are special added tokens.
  private static final String UNIGRAM_TEACHER =
      "{\"version\":\"1.0\","
          + "\"added_tokens\":["
          + "{\"id\":0,\"content\":\"<s>\",\"special\":true},"
          + "{\"id\":1,\"content\":\"<pad>\",\"special\":true},"
          + "{\"id\":2,\"content\":\"</s>\",\"special\":true},"
          + "{\"id\":3,\"content\":\"<unk>\",\"special\":true},"
          + "{\"id\":6,\"content\":\"<mask>\",\"special\":true}],"
          + "\"post_processor\":null,"
          + "\"model\":{\"type\":\"Unigram\",\"unk_id\":3,\"byte_fallback\":false,"
          + "\"vocab\":[[\"<s>\",0.0],[\"<pad>\",0.0],[\"</s>\",0.0],[\"<unk>\",0.0],"
          + "[\"a\",-1.5],[\"b\",-2.5],[\"<mask>\",0.0]]}}";

  private static Path write(Path dir, String name, String content) throws IOException {
    final Path file = dir.resolve(name);
    Files.writeString(file, content);
    return file;
  }

  @Test
  void testWordpieceCleaningDropsSpecialsAndUnusedTokens(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", WORDPIECE_TEACHER);
    write(dir, "tokenizer_config.json", "{\"do_lower_case\":true,\"pad_token\":\"[PAD]\"}");

    final TeacherTokenizer tokenizer =
        TeacherTokenizer.read(tokenizerJson, dir.resolve("tokenizer_config.json"));

    assertEquals(TeacherTokenizer.WORDPIECE, tokenizer.modelType());
    assertEquals(4, tokenizer.vocabularySize());
    assertArrayEquals(new int[] {0, 1, 5, 7}, tokenizer.keptOriginalIds());
    assertEquals(0, tokenizer.padTokenId());
    assertEquals("[UNK]", tokenizer.unkToken());
    assertEquals("[PAD]", tokenizer.padToken());
    // Each row is fed to the teacher as [CLS, token, SEP].
    assertArrayEquals(new long[] {2, 5, 3}, tokenizer.inputSequence(2));
  }

  @Test
  void testWordpieceRewriteRenumbersTheSurvivors(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", WORDPIECE_TEACHER);
    write(dir, "tokenizer_config.json", "{\"pad_token\":\"[PAD]\"}");
    final TeacherTokenizer tokenizer =
        TeacherTokenizer.read(tokenizerJson, dir.resolve("tokenizer_config.json"));

    final Path cleaned = dir.resolve("cleaned.json");
    tokenizer.writeCleaned(cleaned);

    // The cleaned file parses again and names exactly the surviving rows in order (the pad
    // token needs its tokenizer_config to be recognized, as in the teacher).
    final TeacherTokenizer reread =
        TeacherTokenizer.read(cleaned, dir.resolve("tokenizer_config.json"));
    assertEquals(4, reread.vocabularySize());
    assertArrayEquals(new int[] {0, 1, 2, 3}, reread.keptOriginalIds());
    final String json = Files.readString(cleaned);
    assertTrue(json.contains("\"post_processor\":null"), json);
    assertTrue(json.contains("\"hello\":2"), json);
    assertTrue(json.contains("\"world\":3"), json);
    assertFalse(json.contains("[unused1]"), json);
    assertFalse(json.contains("[MASK]"), json);
    // The unk and pad added tokens remain, with Model2Vec's flag convention.
    assertTrue(json.contains("{\"id\":0,\"content\":\"[PAD]\",\"single_word\":true,"
        + "\"lstrip\":true,\"rstrip\":true,\"normalized\":true,\"special\":true}"), json);
    assertTrue(json.contains("{\"id\":1,\"content\":\"[UNK]\",\"single_word\":false,"
        + "\"lstrip\":false,\"rstrip\":false,\"normalized\":false,\"special\":true}"), json);
    // Untouched sections survive byte for byte.
    assertTrue(json.contains("\"normalizer\":{\"type\":\"BertNormalizer\",\"lowercase\":true}"),
        json);
  }

  @Test
  void testUnigramCleaningKeepsPadAndUnkOnly(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", UNIGRAM_TEACHER);
    write(dir, "tokenizer_config.json", "{\"pad_token\":\"<pad>\"}");

    final TeacherTokenizer tokenizer =
        TeacherTokenizer.read(tokenizerJson, dir.resolve("tokenizer_config.json"));

    assertEquals(TeacherTokenizer.UNIGRAM, tokenizer.modelType());
    assertEquals(4, tokenizer.vocabularySize());
    assertArrayEquals(new int[] {1, 3, 4, 5}, tokenizer.keptOriginalIds());
    assertEquals(1, tokenizer.padTokenId());
    assertEquals("<unk>", tokenizer.unkToken());
    // No post-processor, so the input sequence is the bare token.
    assertArrayEquals(new long[] {4}, tokenizer.inputSequence(2));
  }

  @Test
  void testUnigramRewriteRemapsUnkIdAndKeepsScores(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", UNIGRAM_TEACHER);
    write(dir, "tokenizer_config.json", "{\"pad_token\":\"<pad>\"}");
    final TeacherTokenizer tokenizer =
        TeacherTokenizer.read(tokenizerJson, dir.resolve("tokenizer_config.json"));

    final Path cleaned = dir.resolve("cleaned.json");
    tokenizer.writeCleaned(cleaned);

    // The loader's own Unigram reader must see the surviving rows in order.
    assertEquals(List.of("<pad>", "<unk>", "a", "b"), TokenizerJsonVocab.rows(cleaned));
    final String json = Files.readString(cleaned);
    assertTrue(json.contains("\"unk_id\":1"), json);
    assertTrue(json.contains("[\"a\",-1.5]"), json);
    assertTrue(json.contains("\"byte_fallback\":false"), json);
  }

  @Test
  void testUnigramWithoutPadTokenKeepsOnlyTheUnknownToken(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", UNIGRAM_TEACHER);

    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    assertNull(tokenizer.padToken());
    assertEquals(3, tokenizer.vocabularySize());
    assertArrayEquals(new int[] {3, 4, 5}, tokenizer.keptOriginalIds());
  }

  @Test
  void testRejectsAnUnsupportedModelType(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"model\":{\"type\":\"BPE\",\"vocab\":{\"a\":0}}}");

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> TeacherTokenizer.read(tokenizerJson, null));
    assertTrue(e.getMessage().contains("BPE"), e.getMessage());
  }

  @Test
  void testRejectsANullTokenizerJsonFile() {
    assertEquals("TokenizerJsonFile must not be null", assertThrows(
        IllegalArgumentException.class, () -> TeacherTokenizer.read(null, null)).getMessage());
  }

  @Test
  void testRejectsAMissingTokenizerJsonFile(@TempDir Path dir) {
    final Path missing = dir.resolve("tokenizer.json");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TeacherTokenizer.read(missing, null));
    assertEquals("File does not exist or is not a regular file: " + missing, e.getMessage());
  }

  /**
   * The teacher must be rejected, not half-read, when it cannot describe a distilled table. Each
   * case names the part of the contract it breaks and a fragment the message has to carry, so a
   * silently weakened check shows up as a failing row rather than as a corrupt model directory.
   */
  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
      "no model at all;{\"version\":\"1.0\"};has no model with a vocabulary",
      "a model without a vocabulary;{\"model\":{\"type\":\"WordPiece\"}};"
          + "has no model with a vocabulary",
      "no unknown token;{\"model\":{\"type\":\"WordPiece\",\"vocab\":{\"a\":0}}};"
          + "does not name an unknown token",
      "an unknown token outside the vocabulary;{\"model\":{\"type\":\"WordPiece\","
          + "\"unk_token\":\"[UNK]\",\"vocab\":{\"a\":0}}};it is not in the vocabulary",
      "a Unigram unk_id out of range;{\"model\":{\"type\":\"Unigram\",\"unk_id\":9,"
          + "\"vocab\":[[\"a\",0.0]]}};does not name an unknown token",
      "vocabulary ids with a gap;{\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"a\","
          + "\"vocab\":{\"a\":0,\"b\":2}}};not a gapless range",
      "an unsupported post-processor;{\"post_processor\":{\"type\":\"ByteLevel\"},"
          + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"a\",\"vocab\":{\"a\":0}}};"
          + "is not supported",
      "trailing content;{\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"a\","
          + "\"vocab\":{\"a\":0}}} junk;Trailing content"})
  void testRejectsATeacherItCannotDistill(String reason, String teacherJson, String messagePart,
                                          @TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", teacherJson);

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> TeacherTokenizer.read(tokenizerJson, null), reason);
    assertTrue(e.getMessage().contains(messagePart),
        "a teacher with " + reason + " reported: " + e.getMessage());
  }

  /**
   * The other shape a {@code TemplateProcessing} template takes: a single string, whose items are
   * separated by whitespace rather than being a list of objects.
   */
  @Test
  void testReadsAStringTemplatePostProcessor(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"post_processor\":{\"type\":\"TemplateProcessing\","
            + "\"single\":\"<s> $A </s>\","
            + "\"special_tokens\":{\"<s>\":{\"id\":\"<s>\",\"ids\":[0]},"
            + "\"</s>\":{\"id\":\"</s>\",\"ids\":[2]}}},"
            + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"<unk>\","
            + "\"vocab\":{\"<s>\":0,\"<pad>\":1,\"</s>\":2,\"<unk>\":3,\"a\":4}}}");

    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    // Row 4 is 'a'; the string template wraps it the same way the structured form would.
    assertArrayEquals(new long[] {0, 4, 2}, tokenizer.inputSequence(4));
  }

  /**
   * A {@code BertProcessing} post-processor carries its wrapper as {@code cls}/{@code sep} token
   * pairs instead of as a template, and the ids come straight from those pairs.
   */
  @Test
  void testReadsABertProcessingPostProcessor(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"post_processor\":{\"type\":\"BertProcessing\","
            + "\"cls\":[\"[CLS]\",2],\"sep\":[\"[SEP]\",3]},"
            + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
            + "\"vocab\":{\"[PAD]\":0,\"[UNK]\":1,\"[CLS]\":2,\"[SEP]\":3,\"hello\":4}}}");

    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    assertEquals(5, tokenizer.vocabularySize());
    assertArrayEquals(new long[] {2, 4, 3}, tokenizer.inputSequence(4));
  }

  /** A teacher without a post-processor feeds the bare token, with no wrapper ids. */
  @Test
  void testANullPostProcessorAddsNoWrapper(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"post_processor\":null,\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
            + "\"vocab\":{\"[UNK]\":0,\"hello\":1}}}");

    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    assertArrayEquals(new long[] {1}, tokenizer.inputSequence(1));
  }

  /**
   * A pad token the teacher's {@code tokenizer_config.json} names but the vocabulary does not have
   * is not a row; it must not be kept, and the pad id falls back to 0.
   */
  @Test
  void testAPadTokenOutsideTheVocabularyIsIgnored(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", UNIGRAM_TEACHER);
    write(dir, "tokenizer_config.json", "{\"pad_token\":\"<not-in-vocab>\"}");

    final TeacherTokenizer tokenizer =
        TeacherTokenizer.read(tokenizerJson, dir.resolve("tokenizer_config.json"));

    assertEquals(0, tokenizer.padTokenId());
    assertArrayEquals(new int[] {3, 4, 5}, tokenizer.keptOriginalIds());
  }

  /**
   * A template that names its special tokens without carrying a {@code special_tokens} table has to
   * resolve those names through the vocabulary instead.
   */
  @Test
  void testATemplateWithoutASpecialTokenTableResolvesThroughTheVocabulary(@TempDir Path dir)
      throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"post_processor\":{\"type\":\"TemplateProcessing\","
            + "\"single\":[{\"SpecialToken\":{\"id\":\"[CLS]\",\"type_id\":0}},"
            + "{\"Sequence\":{\"id\":\"A\",\"type_id\":0}},"
            + "{\"SpecialToken\":{\"id\":\"[SEP]\",\"type_id\":0}}]},"
            + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
            + "\"vocab\":{\"[PAD]\":0,\"[UNK]\":1,\"[CLS]\":2,\"[SEP]\":3,\"hello\":4}}}");

    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    assertArrayEquals(new long[] {2, 4, 3}, tokenizer.inputSequence(4));
  }

  @Test
  void testRejectsATemplateSpecialTokenThatResolvesNowhere(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"post_processor\":{\"type\":\"TemplateProcessing\","
            + "\"single\":[{\"SpecialToken\":{\"id\":\"[BOS]\",\"type_id\":0}},"
            + "{\"Sequence\":{\"id\":\"A\",\"type_id\":0}}]},"
            + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
            + "\"vocab\":{\"[UNK]\":0,\"hello\":1}}}");

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> TeacherTokenizer.read(tokenizerJson, null));
    assertTrue(e.getMessage().contains("[BOS]"), e.getMessage());
  }

  @Test
  void testRejectsAVocabularyIdUsedTwice(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"a\",\"vocab\":{\"a\":0,\"b\":0}}}");

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> TeacherTokenizer.read(tokenizerJson, null));
    assertTrue(e.getMessage().contains("assigned more than once"), e.getMessage());
  }

  @Test
  void testAnExplicitlyNullUnkIdCountsAsNoUnknownToken(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"model\":{\"type\":\"Unigram\",\"unk_id\":null,\"vocab\":[[\"a\",0.0]]}}");

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> TeacherTokenizer.read(tokenizerJson, null));
    assertTrue(e.getMessage().contains("does not name an unknown token"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   \n\t "})
  void testRejectsAnEmptyTokenizerJson(String content, @TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", content);

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> TeacherTokenizer.read(tokenizerJson, null));
    assertTrue(e.getMessage().contains("Unexpected end of input"), e.getMessage());
  }

  /**
   * The removal pattern is matched from the start of the token, so it drops a token that begins
   * with an {@code [unusedN]} marker and keeps everything else, including a marker without digits
   * and one that is not at the start.
   */
  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
      "[unused0];1",
      "[unused12];1",
      "[unused7]tail;1",
      "[unused];2",
      "[unusedx];2",
      "x[unused1];2",
      "[UNUSED1];2"})
  void testUnusedTokenRemovalMatchesFromTheStartOnly(String token, int expectedSize,
                                                     @TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
            + "\"vocab\":{\"[UNK]\":0,\"" + token + "\":1}}}");

    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    assertEquals(expectedSize, tokenizer.vocabularySize());
  }

  /**
   * Vocabulary entries are copied as raw spans, so a teacher's escapes reach the distilled file
   * untouched and still decode to the pieces the loader resolves matrix rows by.
   */
  @Test
  void testUnicodeVocabularyEntriesSurviveTheRewrite(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"version\":\"1.0\",\"post_processor\":null,"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,\"vocab\":[[\"<unk>\",0.0],"
            + "[\"caf\\u00e9\",-1.0],[\"e\\u0301\",-2.0],[\"\\ud83d\\ude00\",-3.0],"
            + "[\"a\",-4.0]]}}");
    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    final Path cleaned = dir.resolve("cleaned.json");
    tokenizer.writeCleaned(cleaned);

    assertEquals(5, tokenizer.vocabularySize());
    final String json = Files.readString(cleaned);
    assertTrue(json.contains("[\"caf\\u00e9\",-1.0]"), json);
    assertTrue(json.contains("[\"e\\u0301\",-2.0]"), json);
    assertTrue(json.contains("[\"\\ud83d\\ude00\",-3.0]"), json);
    // A precomposed letter, a base letter plus a combining acute, and a supplementary-plane
    // character all decode to what the teacher declared.
    assertEquals(List.of("<unk>", "caf\u00e9", "e\u0301", "\uD83D\uDE00", "a"),
        TokenizerJsonVocab.rows(cleaned));
  }

  /**
   * The added-token overlay is the only part of the rewrite that re-encodes a token string rather
   * than copying its raw span, so it has to escape what JSON requires.
   */
  @Test
  void testTheAddedTokenOverlayEscapesTheUnknownTokenContent(@TempDir Path dir) throws IOException {
    // The unknown token carries a backslash and a tab.
    final String rawToken = "\"<unk\\\\\\t>\"";
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"version\":\"1.0\","
            + "\"added_tokens\":[{\"id\":0,\"content\":" + rawToken + ",\"special\":true}],"
            + "\"post_processor\":null,"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,"
            + "\"vocab\":[[" + rawToken + ",0.0],[\"a\",-1.0]]}}");
    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    final Path cleaned = dir.resolve("cleaned.json");
    tokenizer.writeCleaned(cleaned);

    assertEquals(2, tokenizer.vocabularySize());
    final String json = Files.readString(cleaned);
    assertTrue(json.contains("[" + rawToken + ",0.0]"), json);
    assertTrue(json.contains("\"content\":\"<unk\\\\" + "\\" + "u0009>\""), json);
  }

  /** The rewrite emits only fields the teacher had, so an absent overlay stays absent. */
  @Test
  void testATeacherWithoutAnAddedTokensSectionWritesNoOverlay(@TempDir Path dir)
      throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"version\":\"1.0\",\"post_processor\":null,"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,"
            + "\"vocab\":[[\"<unk>\",0.0],[\"a\",-1.0]]}}");
    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    final Path cleaned = dir.resolve("cleaned.json");
    tokenizer.writeCleaned(cleaned);

    assertFalse(Files.readString(cleaned).contains("added_tokens"));
    assertEquals(List.of("<unk>", "a"), TokenizerJsonVocab.rows(cleaned));
  }

  /**
   * The overlay is pruned by token content alone: the {@code special} flag is never read, so a
   * plain vocabulary extension is dropped from the distilled table just like {@code [MASK]} is.
   */
  @Test
  void testEveryAddedTokenIsDroppedRegardlessOfItsSpecialFlag(@TempDir Path dir)
      throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json",
        "{\"version\":\"1.0\","
            + "\"added_tokens\":[{\"id\":1,\"content\":\"[UNK]\",\"special\":true},"
            + "{\"id\":2,\"content\":\"covid\",\"special\":false}],"
            + "\"post_processor\":null,"
            + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
            + "\"vocab\":{\"hello\":0,\"[UNK]\":1,\"covid\":2}}}");

    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    assertArrayEquals(new int[] {0, 1}, tokenizer.keptOriginalIds());
  }

  @Test
  void testWriteCleanedRejectsANullFile(@TempDir Path dir) throws IOException {
    final Path tokenizerJson = write(dir, "tokenizer.json", UNIGRAM_TEACHER);
    final TeacherTokenizer tokenizer = TeacherTokenizer.read(tokenizerJson, null);

    assertEquals("File must not be null", assertThrows(
        IllegalArgumentException.class, () -> tokenizer.writeCleaned(null)).getMessage());
  }

  @Test
  void testTermInputSequenceMapsPieceStringsToOriginalIds(@TempDir Path dir) throws IOException {
    final TeacherTokenizer tokenizer = TeacherTokenizer.read(
        write(dir, "tokenizer.json", WORDPIECE_TEACHER), null);

    // hello and world map to their original ids, an unmapped piece falls to the unknown id,
    // and the sequence is wrapped in the post-processor's [CLS]/[SEP] ids.
    assertArrayEquals(new long[] {2, 5, 1, 7, 3},
        tokenizer.inputSequence(List.of("hello", "nope", "world")));
    assertEquals("Pieces must not be null", assertThrows(IllegalArgumentException.class,
        () -> tokenizer.inputSequence((List<String>) null)).getMessage());
  }

  @Test
  void testReadsTheNormalizerLowercaseFlag(@TempDir Path dir) throws IOException {
    assertEquals(Boolean.TRUE, TeacherTokenizer.read(
        write(dir, "wordpiece.json", WORDPIECE_TEACHER), null).lowerCase());
    // The Unigram teacher states no normalizer, so the flag is unknown.
    assertNull(TeacherTokenizer.read(
        write(dir, "unigram.json", UNIGRAM_TEACHER), null).lowerCase());
  }
}

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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Model2VecUnigramTokenizerTest {

  private static final String NORMALIZER =
      "\"normalizer\":{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"}";
  private static final String PRE_TOKENIZER =
      "\"pre_tokenizer\":{\"type\":\"Metaspace\",\"replacement\":\"▁\","
          + "\"prepend_scheme\":\"always\",\"split\":false}";
  private static final String MODEL =
      "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,\"byte_fallback\":false,"
          + "\"vocab\":[[\"<unk>\",0.0],[\"▁a\",-1.0]]}";
  private static final String TOKENIZER = "{" + NORMALIZER + "," + PRE_TOKENIZER + ","
      + MODEL + "}";

  /** Verifies that normalization-added characters do not extend a piece past the source text. */
  @Test
  void testPieceOffsetsReferToTheOriginalText(@TempDir Path dir) throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer, TOKENIZER);

    final Model2VecUnigramTokenizer loaded = Model2VecUnigramTokenizer.load(tokenizer);

    assertEquals(1, loaded.encode("a").getFirst().end());
  }

  /** Verifies alignment through literal insertion, marker collapse, and marker stripping. */
  @Test
  void testPieceOffsetsSurvivePostNormalization(@TempDir Path dir) throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    final String sequence = "\"normalizer\":{\"type\":\"Sequence\",\"normalizers\":["
        + "{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"},"
        + "{\"type\":\"Replace\",\"pattern\":{\"String\":\"▁\"},"
        + "\"content\":\" ▁ \"},"
        + "{\"type\":\"Replace\",\"pattern\":{\"Regex\":\"\\\\s+\"},"
        + "\"content\":\" \"},"
        + "{\"type\":\"Replace\",\"pattern\":{\"String\":\"a\"},"
        + "\"content\":\" a \"},"
        + "{\"type\":\"Strip\",\"strip_left\":false,\"strip_right\":true}]}";
    Files.writeString(tokenizer, "{" + sequence + "," + PRE_TOKENIZER + "," + MODEL + "}");

    final Model2VecUnigramTokenizer loaded = Model2VecUnigramTokenizer.load(tokenizer);

    assertEquals(List.of(new SubwordPiece("▁a", 1, 0, 1)), loaded.encode("a"));
  }

  /** Verifies that two top-level model definitions are rejected as ambiguous. */
  @Test
  void testRejectsADuplicateTopLevelField(@TempDir Path dir) throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer, "{" + NORMALIZER + "," + PRE_TOKENIZER + "," + MODEL + ","
        + MODEL + "}");

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer));

    assertTrue(error.getMessage().contains("more than once"), error.getMessage());
  }

  /**
   * Supplies duplicate fields in each nested object parsed by the adapter.
   *
   * @return The object name and tokenizer JSON for each case.
   */
  private static Stream<Arguments> nestedDuplicateFields() {
    return Stream.of(
        Arguments.of("model", TOKENIZER.replace("\"unk_id\":0",
            "\"unk_id\":0,\"unk_id\":0")),
        Arguments.of("normalizer", TOKENIZER.replace("\"precompiled_charsmap\":\"\"",
            "\"precompiled_charsmap\":\"\",\"precompiled_charsmap\":\"\"")),
        Arguments.of("pre-tokenizer", TOKENIZER.replace("\"split\":false",
            "\"split\":false,\"split\":false")),
        Arguments.of("added token", "{\"added_tokens\":[{\"id\":1,\"id\":1,"
            + "\"content\":\"▁a\",\"special\":false}]," + NORMALIZER + ","
            + PRE_TOKENIZER + "," + MODEL + "}"));
  }

  /** Verifies duplicate fields are rejected in every nested tokenizer object. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("nestedDuplicateFields")
  void testRejectsDuplicateNestedFields(String object, String json, @TempDir Path dir)
      throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer, json);

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer), object);

    assertTrue(error.getMessage().contains("more than once"), error.getMessage());
  }

  /** Verifies that added-token metadata cannot assign one row more than once. */
  @Test
  void testRejectsDuplicateAddedTokenIds(@TempDir Path dir) throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer, "{\"added_tokens\":["
        + "{\"id\":1,\"content\":\"▁a\",\"special\":false},"
        + "{\"id\":1,\"content\":\"▁a\",\"special\":true}],"
        + NORMALIZER + "," + PRE_TOKENIZER + "," + MODEL + "}");

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer));

    assertTrue(error.getMessage().contains("added token id 1 occurs more than once"),
        error.getMessage());
  }

  /**
   * Supplies a trailing comma in each tokenizer structure parsed by the adapter.
   *
   * @return The structure name and malformed tokenizer JSON for each case.
   */
  private static Stream<Arguments> trailingCommaJson() {
    final String addedToken = "\"added_tokens\":[{\"id\":1,\"content\":\"▁a\","
        + "\"special\":false}]";
    return Stream.of(
        Arguments.of("model", TOKENIZER.replace("]]}", "]],}")),
        Arguments.of("vocabulary", TOKENIZER.replace("[\"▁a\",-1.0]]",
            "[\"▁a\",-1.0],]")),
        Arguments.of("normalizer", TOKENIZER.replace("charsmap\":\"\"}",
            "charsmap\":\"\",}")),
        Arguments.of("normalizer array", TOKENIZER.replace(
            "\"normalizer\":{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"}",
            "\"normalizer\":{\"type\":\"Sequence\",\"normalizers\":["
                + "{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"},]}")),
        Arguments.of("pre-tokenizer", TOKENIZER.replace("split\":false}",
            "split\":false,}")),
        Arguments.of("added-token object", "{" + addedToken.replace("false}", "false,}")
            + "," + NORMALIZER + "," + PRE_TOKENIZER + "," + MODEL + "}"),
        Arguments.of("added-token array", "{" + addedToken.replace("]", ",]")
            + "," + NORMALIZER + "," + PRE_TOKENIZER + "," + MODEL + "}"));
  }

  /** Verifies that the tokenizer adapter accepts only standard JSON array and object syntax. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("trailingCommaJson")
  void testRejectsTrailingCommas(String structure, String json, @TempDir Path dir)
      throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer, json);

    assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer), structure);
  }

  @Test
  void testReportsMissingVocabularyAsInvalidModelContent(@TempDir Path dir) throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer,
        "{\"normalizer\":{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"},"
            + "\"pre_tokenizer\":{\"type\":\"Metaspace\",\"replacement\":\"▁\","
            + "\"prepend_scheme\":\"always\",\"split\":false},"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0}} ");

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer));

    assertTrue(error.getMessage().contains("model.vocab"), error.getMessage());
  }

  @Test
  void testRejectsANormalizationStepBeforeThePrecompiledMap(@TempDir Path dir)
      throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer,
        "{\"normalizer\":{\"type\":\"Sequence\",\"normalizers\":["
            + "{\"type\":\"Replace\",\"pattern\":{\"Regex\":\"\\\\s+\"},"
            + "\"content\":\" \"},"
            + "{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"}]},"
            + "\"pre_tokenizer\":{\"type\":\"Metaspace\",\"replacement\":\"▁\","
            + "\"prepend_scheme\":\"always\",\"split\":false},"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,\"byte_fallback\":false,"
            + "\"vocab\":[[\"<unk>\",0.0],[\"▁a\",-1.0]]}}");

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer));

    assertTrue(error.getMessage().contains("Precompiled normalizer must precede"),
        error.getMessage());
  }

  @Test
  void testRejectsAnEmptyLiteralReplacement(@TempDir Path dir) throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    final String sequence = "\"normalizer\":{\"type\":\"Sequence\",\"normalizers\":["
        + "{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"},"
        + "{\"type\":\"Replace\",\"pattern\":{\"String\":\"\"},"
        + "\"content\":\"  \"}]}";
    Files.writeString(tokenizer, "{" + sequence + "," + PRE_TOKENIZER + "," + MODEL + "}");

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer));

    assertTrue(error.getMessage().contains("empty literal"), error.getMessage());
  }
}

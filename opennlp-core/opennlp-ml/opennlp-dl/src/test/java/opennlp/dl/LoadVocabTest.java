/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadVocabTest {

  /** A component without a model, to read vocabularies through. */
  private static final AbstractDL DL =
      new ModelFreeDL(Map.of("[CLS]", 0, "[SEP]", 1, "[UNK]", 2), true);

  /** A flat vocab.json. */
  private static final String JSON_VOCAB = "{\"[PAD]\":0,\"hello\":1,\"\\u0120x\":2}";

  /** A tokenizer.json of a WordPiece model whose model.vocab holds {@link #TOKENIZER_VOCAB}. */
  private static final String TOKENIZER_JSON = """
      {
        "version": "1.0",
        "truncation": null,
        "added_tokens": [
          {"id": 0, "content": "[PAD]", "special": true},
          {"id": 1, "content": "[UNK]", "special": true}
        ],
        "normalizer": {"type": "BertNormalizer", "lowercase": true},
        "model": {
          "type": "WordPiece",
          "unk_token": "[UNK]",
          "vocab_size": 5,
          "vocab": {"[PAD]": 0, "[UNK]": 1, "hello": 2, "##ing": 3, "\\u0120x": 4}
        }
      }
      """;

  private static final Map<String, Integer> TOKENIZER_VOCAB =
      Map.of("[PAD]", 0, "[UNK]", 1, "hello", 2, "##ing", 3, "\u0120x", 4);

  private static final String UNIGRAM_TOKENIZER_JSON = "{\"version\": \"1.0\", \"model\": {\"type\": "
      + "\"Unigram\", \"unk_id\": 0, \"vocab\": [[\"<unk>\", 0.0], [\"a\", -1.5]]}}";

  @TempDir
  private Path tempDir;

  private File getResource(String name) throws IOException {
    try (InputStream is = Objects.requireNonNull(
        getClass().getResourceAsStream("/opennlp/dl/" + name))) {
      final Path file = tempDir.resolve(name);
      Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
      return file.toFile();
    }
  }

  /**
   * Writes a vocabulary file into the per-test temporary directory.
   *
   * @param name The file name.
   * @param content The file content, written as UTF-8.
   * @return The written file.
   */
  private File vocabFile(String name, String content) throws IOException {
    return Files.writeString(tempDir.resolve(name), content).toFile();
  }

  @Test
  void testLoadPlainTextVocab() throws IOException {
    final Map<String, Integer> vocab = DL.loadVocab(getResource("vocab-plain.txt"));

    assertNotNull(vocab);
    assertEquals(6, vocab.size());
    assertEquals(0, vocab.get("[CLS]"));
    assertEquals(1, vocab.get("[SEP]"));
    assertEquals(2, vocab.get("[UNK]"));
    assertEquals(3, vocab.get("hello"));
    assertEquals(4, vocab.get("world"));
    assertEquals(5, vocab.get("##ing"));
  }

  @Test
  void testLoadJsonVocab() throws IOException {
    final Map<String, Integer> vocab = DL.loadVocab(getResource("vocab.json"));

    assertNotNull(vocab);
    assertEquals(6, vocab.size());
    assertEquals(0, vocab.get("[CLS]"));
    assertEquals(1, vocab.get("[SEP]"));
    assertEquals(2, vocab.get("[UNK]"));
    assertEquals(3, vocab.get("hello"));
    assertEquals(4, vocab.get("world"));
    assertEquals(5, vocab.get("##ing"));
  }

  @Test
  void testJsonVocabWithEscapedCharacters() throws IOException {
    final File tempFile = vocabFile("vocab-escaped.json", "{\"hello\\\"world\": 0, \"back\\\\slash\": 1}");

    final Map<String, Integer> vocab = DL.loadVocab(tempFile);

    assertNotNull(vocab);
    assertEquals(2, vocab.size());
    assertEquals(0, vocab.get("hello\"world"));
    assertEquals(1, vocab.get("back\\slash"));
  }

  @Test
  void testJsonVocabWithUnicodeEscapedCharacters() throws IOException {
    final File tempFile = vocabFile("vocab-unicode.json",
        "{\"\\u0120token\": 0, \"line\\rbreak\": 1, \"form\\ffeed\": 2}");

    final Map<String, Integer> vocab = DL.loadVocab(tempFile);

    assertNotNull(vocab);
    assertEquals(3, vocab.size());
    assertEquals(0, vocab.get("\u0120token"));
    assertEquals(1, vocab.get("line\rbreak"));
    assertEquals(2, vocab.get("form\ffeed"));
  }

  @Test
  void testLoadTokenizerJsonVocabFile() throws IOException {
    final File file = getResource("tokenizer.json");

    final Map<String, Integer> vocab = DL.loadVocab(file);

    // the 24 entries of model.vocab, and the added token opennlp that model.vocab lacks
    assertEquals(25, vocab.size());
    assertEquals(0, vocab.get("[PAD]"));
    assertEquals(1, vocab.get("[UNK]"));
    assertEquals(2, vocab.get("[CLS]"));
    assertEquals(3, vocab.get("[SEP]"));
    assertEquals(6, vocab.get("hello"));
    assertEquals(9, vocab.get("##ing"));
    assertEquals(23, vocab.get("##c"));
    assertEquals(24, vocab.get("opennlp"));
    assertEquals(Boolean.TRUE, DL.readVocabFile(file).lowercase());
  }

  /**
   * Sentences and the pieces and ids the fixture gives them, computed by hand from
   * {@code tokenizer.json}: the text is lower cased and stripped of accents as the normalizer
   * says, split at whitespace and punctuation, and each word is segmented into the longest
   * pieces of the vocabulary, all but the first carrying the {@code ##} prefix; a word that
   * cannot be segmented is {@code [UNK]}, and {@code [CLS]} and {@code [SEP]} frame the text.
   */
  static Stream<Arguments> tokenizerJsonEncodings() {
    return Stream.of(
        Arguments.of("Hello world!",
            List.of("[CLS]", "hello", "world", "!", "[SEP]"),
            new long[] {2, 6, 7, 19, 3}),
        // un ##play ##able; the added token opennlp is longer than the piece open
        Arguments.of("The unplayable OpenNLP plays, played.",
            List.of("[CLS]", "the", "un", "##play", "##able", "opennlp", "play", "##s", ",",
                "play", "##ed", ".", "[SEP]"),
            new long[] {2, 5, 12, 13, 14, 24, 8, 10, 18, 8, 11, 17, 3}),
        // c is only a continuation piece, so cab cannot start
        Arguments.of("abc cab",
            List.of("[CLS]", "a", "##b", "##c", "[UNK]", "[SEP]"),
            new long[] {2, 20, 22, 23, 1, 3}),
        // lowercase: true strips accents as well
        Arguments.of("H\u00e9llo W\u00f6rld!",
            List.of("[CLS]", "hello", "world", "!", "[SEP]"),
            new long[] {2, 6, 7, 19, 3}),
        Arguments.of("",
            List.of("[CLS]", "[SEP]"),
            new long[] {2, 3}));
  }

  @ParameterizedTest
  @MethodSource("tokenizerJsonEncodings")
  void testTokenizerJsonVocabularyEncodesAsComputedByHand(String text, List<String> pieces,
      long[] ids) throws IOException {
    final Map<String, Integer> vocab = DL.loadVocab(getResource("tokenizer.json"));
    final List<SubwordPiece> encoded = AbstractDL.createWordpieceEncoder(vocab, true).encode(text);

    assertEquals(pieces, encoded.stream().map(SubwordPiece::piece).toList());
    assertArrayEquals(ids, encoded.stream().mapToLong(SubwordPiece::id).toArray());
  }

  static Stream<Arguments> unsupportedTokenizerFiles() {
    return Stream.of(
        Arguments.of("tokenizer-unigram.json", UNIGRAM_TOKENIZER_JSON, "\"Unigram\""),
        Arguments.of("tokenizer-bpe.json", "{\"model\": {\"type\": \"BPE\", \"vocab\": {\"a\": 0},"
            + " \"merges\": []}}", "\"BPE\""),
        Arguments.of("tokenizer-prefix.json", "{\"model\": {\"type\": \"WordPiece\","
            + " \"continuing_subword_prefix\": \"@@\", \"vocab\": {\"a\": 0}}}", "continuing_subword_prefix"),
        Arguments.of("tokenizer-conflict.json", "{\"added_tokens\": [{\"id\": 1, \"content\": \"a\"}],"
            + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}}", "added token \"a\""));
  }

  @ParameterizedTest
  @MethodSource("unsupportedTokenizerFiles")
  void testUnsupportedTokenizerJsonFileIsRejectedNamingTheFile(String name, String json,
      String reason) throws IOException {
    final File tempFile = vocabFile(name, json);

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> DL.loadVocab(tempFile));
    assertTrue(e.getMessage().contains(tempFile.getName()), e.getMessage());
    assertTrue(e.getMessage().contains(reason), e.getMessage());
  }

  @Test
  void testTokenizerJsonFileWithAByteOrderMarkIsReadAsJson() throws IOException {
    final File tempFile = vocabFile("tokenizer-bom.json", "\uFEFF" + TOKENIZER_JSON);

    assertEquals(TOKENIZER_VOCAB, DL.loadVocab(tempFile));
  }

  @Test
  void testJsonAndPlainTextVocabProduceSameResult() throws IOException {
    final Map<String, Integer> plainVocab = DL.loadVocab(getResource("vocab-plain.txt"));
    final Map<String, Integer> jsonVocab = DL.loadVocab(getResource("vocab.json"));

    assertEquals(plainVocab, jsonVocab);
  }

  static Stream<Arguments> jsonVocabs() {
    return Stream.of(
        Arguments.of("{}", Map.of()),
        Arguments.of("{\"a\": 1, \"b\": 2}", Map.of("a", 1, "b", 2)),
        Arguments.of(" \t\r\n{\"a\"\n:\r\n  3\t}\n", Map.of("a", 3)),
        Arguments.of("{\"a\":0}", Map.of("a", 0)),
        Arguments.of("{\"a\": 2147483647}", Map.of("a", Integer.MAX_VALUE)),
        Arguments.of("{\"a\\\"b\": 1}", Map.of("a\"b", 1)),
        Arguments.of("{\"a\\\\\": 1}", Map.of("a\\", 1)),
        Arguments.of("{\"\\u0120x\": 7, \"\\u00e9\": 8}", Map.of("\u0120x", 7, "\u00E9", 8)),
        Arguments.of("{\"\uD83D\uDE00\": 1}", Map.of("\uD83D\uDE00", 1)),
        // a raw line break inside a token is content
        Arguments.of("{\"a\nb\": 1}", Map.of("a\nb", 1)),
        Arguments.of("{\"\": 1}", Map.of("", 1)),
        // a later entry for the same token, written or escaped, overwrites the earlier one
        Arguments.of("{\"a\": 1, \"a\": 2}", Map.of("a", 2)),
        Arguments.of("{\"a\": 1, \"\\u0061\": 2}", Map.of("a", 2)),
        // tokens named model and vocab with integer ids are a flat vocabulary
        Arguments.of("{\"model\":0,\"vocab\":1}", Map.of("model", 0, "vocab", 1)));
  }

  @ParameterizedTest
  @MethodSource("jsonVocabs")
  void testLoadJsonVocab(String json, Map<String, Integer> expected) {
    assertEquals(expected, DL.readJsonVocab(json).ids());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      // not one object
      "", " ", "[]", "\"a\"", "{\"a\": 1}{}", "{\"a\": 1} x",
      // malformed structure
      "{\"a\": 1", "{\"a\" 1}", "{\"a\": 1 \"b\": 2}", "{\"a\": 1,}", "{a: 1}", "\"a\":1\"b\":2",
      // whitespace outside strings is only the four RFC 8259 characters
      "{\"a\":\u00A05}", "{\"a\":\t4\u000B}",
      // values that are not non-negative integers
      "{\"a\": 1.5}", "{\"a\": -1}", "{\"a\": 12abc}", "{\"a\": \u0661}", "{\"a\": \"1\"}",
      "{\"a\": 99999999999}", "{\"a\": {\"b\": 1}}", "{\"a\": [1]}", "{\"a\": null}",
      // invalid escapes in a token
      "{\"a\\q\": 1}", "{\"\\\uD83D\uDE00\": 2}", "{\"\\u12\": 3}", "{\"\\u+123\": 3}",
      "{\"a\\\nb\": 1}"})
  void testLoadJsonVocabRejectsMalformedText(String json) {
    assertThrows(IllegalArgumentException.class, () -> DL.readJsonVocab(json).ids());
  }

  @Test
  void testLoadJsonVocabRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> DL.readJsonVocab(null).ids());
  }

  @Test
  void testLoadJsonVocabMessageNamesTheToken() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab("{\"ok\": 1, \"bad\": -1}").ids());
    assertTrue(e.getMessage().contains("\"bad\""), e.getMessage());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      // an int-valued top-level member makes the top-level object the vocabulary
      "{\"a\": 1, \"model\": {\"vocab\": {\"b\": 2}}} | \"model\"",
      // model missing or not an object
      "{\"model\": \"x\"} | \"model\"", "{\"vocab\": {\"a\": 1}} | \"vocab\"",
      // model.type missing or not a string: the file is not a tokenizer.json
      "{\"model\": {\"vocab\": {\"a\": 0}}} | \"model\"",
      "{\"a\": -1, \"model\": {\"vocab\": {\"b\": 2}}} | \"a\"",
      "{\"model\": {\"type\": 5, \"vocab\": {\"a\": 0}}} | \"model\"",
      // a config.json or a tokenizer_config.json handed over in place of the vocabulary
      "{\"architectures\": [\"BertModel\"], \"hidden_size\": 768} | \"architectures\"",
      "{\"do_lower_case\": true, \"tokenizer_class\": \"BertTokenizer\"} | \"do_lower_case\""})
  void testLoadJsonVocabRejectsOtherLayoutsNamingTheFirstMemberThatIsNoId(String json,
      String named) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains(named), e.getMessage());
  }

  @Test
  void testLoadJsonVocabNamesTheOffsetOfAMalformedAddedToken() {
    final String json = "{\"added_tokens\": [{\"id\": 0, \"content\": \"a\"},"
        + " {\"id\": \"7\", \"content\": \"b\"}],"
        + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}}";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains("\"id\" at offset " + json.indexOf("\"7\"")),
        e.getMessage());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      // model.vocab missing or not an object
      "{\"model\": {\"type\": \"WordPiece\"}} | model.vocab",
      "{\"model\": {\"type\": \"WordPiece\", \"vocab\": [\"a\"]}} | model.vocab",
      "{\"model\": {\"type\": \"WordPiece\", \"vocab\": \"a\"}} | model.vocab",
      // the same strict rules apply inside model.vocab, and the message names the token
      "{\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": \"1\"}}} | \"a\"",
      "{\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": -1}}} | \"a\""})
  void testLoadJsonVocabRejectsAWordPieceModelWithoutAUsableVocab(String json, String named) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains(named), e.getMessage());
  }

  static Stream<Arguments> addedTokens() {
    final String model = "\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0, \"b\": 2}}";
    return Stream.of(
        // a token absent from model.vocab is added with its id, before or after the model
        Arguments.of("{\"added_tokens\": [{\"id\": 5, \"content\": \"[NEW]\", \"special\": true}], "
            + model + "}", Map.of("a", 0, "b", 2, "[NEW]", 5)),
        Arguments.of("{" + model + ", \"added_tokens\": [{\"content\": \"c\", \"id\": 1}]}",
            Map.of("a", 0, "b", 2, "c", 1)),
        // a token listed with the id model.vocab gives it is an entry once
        Arguments.of("{\"added_tokens\": [{\"id\": 0, \"content\": \"a\"}, {\"id\": 2, \"content\": \"b\"}],"
            + model + "}", Map.of("a", 0, "b", 2)),
        // the same added token twice with one id, and content with escapes
        Arguments.of("{\"added_tokens\": [{\"id\": 3, \"content\": \"\\u0120x\"},"
            + " {\"id\": 3, \"content\": \"\\u0120x\"}], " + model + "}",
            Map.of("a", 0, "b", 2, "\u0120x", 3)),
        // an empty list adds nothing
        Arguments.of("{\"added_tokens\": [], " + model + "}", Map.of("a", 0, "b", 2)),
        // a later added_tokens list replaces an earlier one, as a later member does
        Arguments.of("{\"added_tokens\": [{\"id\": 9, \"content\": \"x\"}], " + model
            + ", \"added_tokens\": [{\"id\": 8, \"content\": \"y\"}]}", Map.of("a", 0, "b", 2, "y", 8)));
  }

  @ParameterizedTest
  @MethodSource("addedTokens")
  void testLoadJsonVocabAddsTheAddedTokensAbsentFromTheModelVocab(String json,
      Map<String, Integer> expected) {
    assertEquals(expected, DL.readJsonVocab(json).ids());
  }

  @Test
  void testLoadJsonVocabRejectsAnAddedTokenWhoseIdDiffersFromTheModelVocab() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab("{\"added_tokens\": [{\"id\": 7, \"content\": \"b\"}],"
            + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0, \"b\": 2}}}").ids());
    assertTrue(e.getMessage().contains("\"b\""), e.getMessage());
    assertTrue(e.getMessage().contains("7"), e.getMessage());
    assertTrue(e.getMessage().contains("2"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      // the id is that of a token of model.vocab
      "{\"added_tokens\": [{\"id\": 2, \"content\": \"[NEW]\"}],"
          + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0, \"b\": 2}}}",
      // the id is that of an earlier added token
      "{\"added_tokens\": [{\"id\": 5, \"content\": \"[NEW]\"}, {\"id\": 5, \"content\": \"b\"}],"
          + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}}"})
  void testLoadJsonVocabRejectsAnAddedTokenWhoseIdAnotherTokenHas(String json) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains("\"[NEW]\""), e.getMessage());
    assertTrue(e.getMessage().contains("\"b\""), e.getMessage());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      // not a list
      "{\"added_tokens\": null, MODEL} | added_tokens",
      "{\"added_tokens\": {\"id\": 1, \"content\": \"c\"}, MODEL} | added_tokens",
      // an entry that is not an object
      "{\"added_tokens\": [\"c\"], MODEL} | added_tokens[0]",
      "{\"added_tokens\": [[1, \"c\"]], MODEL} | added_tokens[0]",
      // id or content missing
      "{\"added_tokens\": [{\"content\": \"c\"}], MODEL} | added_tokens[0]",
      "{\"added_tokens\": [{\"id\": 1}], MODEL} | added_tokens[0]",
      // id or content of another type: the message names the member
      "{\"added_tokens\": [{\"id\": \"1\", \"content\": \"c\"}], MODEL} | \"id\"",
      "{\"added_tokens\": [{\"id\": -1, \"content\": \"c\"}], MODEL} | \"id\"",
      "{\"added_tokens\": [{\"id\": 1.0, \"content\": \"c\"}], MODEL} | \"id\"",
      "{\"added_tokens\": [{\"id\": 1, \"content\": null}], MODEL} | \"content\"",
      "{\"added_tokens\": [{\"id\": 1, \"content\": [\"c\"]}], MODEL} | \"content\""})
  void testLoadJsonVocabRejectsMalformedAddedTokens(String template, String named) {
    final String json = template.replace("MODEL",
        "\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}");
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains(named), e.getMessage());
  }

  static Stream<Arguments> lowercaseSettings() {
    final String model = "\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}";
    return Stream.of(
        Arguments.of(TOKENIZER_JSON, Boolean.TRUE),
        Arguments.of("{\"normalizer\": {\"type\": \"BertNormalizer\", \"lowercase\": false}, " + model
            + "}", Boolean.FALSE),
        Arguments.of("{" + model + ", \"normalizer\": {\"lowercase\": true}}", Boolean.TRUE),
        // a later normalizer, and a later lowercase, wins
        Arguments.of("{\"normalizer\": {\"lowercase\": true, \"lowercase\": false}, " + model
            + ", \"normalizer\": {\"lowercase\": false, \"lowercase\": true}}", Boolean.TRUE),
        // no normalizer, a normalizer without the setting, or one that is not an object
        Arguments.of("{" + model + "}", null),
        Arguments.of("{\"normalizer\": {\"type\": \"NFC\"}, " + model + "}", null),
        Arguments.of("{\"normalizer\": null, " + model + "}", null),
        // a vocab.json has no setting
        Arguments.of("{\"a\": 0, \"normalizer\": 1}", null));
  }

  @ParameterizedTest
  @MethodSource("lowercaseSettings")
  void testReadJsonVocabKeepsTheLowercaseSetting(String json, Boolean expected) {
    assertEquals(expected, DL.readJsonVocab(json).lowercase());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"true\"", "1", "null", "{}", "[true]", "NaN"})
  void testReadJsonVocabRejectsALowercaseSettingThatIsNotABoolean(String value) {
    final String json = "{\"normalizer\": {\"lowercase\": " + value + "},"
        + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}}";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json));
    assertTrue(e.getMessage().contains("\"lowercase\""), e.getMessage());
  }

  @Test
  void testReadVocabFileKeepsTheLowercaseSettingOfATokenizerJson() throws IOException {
    final File tempFile = vocabFile("tokenizer.json", TOKENIZER_JSON);

    final AbstractDL.Vocabulary vocabulary = DL.readVocabFile(tempFile);

    assertEquals(TOKENIZER_VOCAB, vocabulary.ids());
    assertEquals(Boolean.TRUE, vocabulary.lowercase());
    assertThrows(InvalidFormatException.class,
        () -> DL.requireLowerCase(tempFile, vocabulary, false));
    DL.requireLowerCase(tempFile, vocabulary, true);
  }

  @Test
  void testRequireLowerCaseNamesTheFileAndBothSettings() throws IOException {
    final File tempFile = vocabFile("tokenizer-cased.json", "{\"normalizer\": {\"lowercase\": false},"
        + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}}");
    final AbstractDL.Vocabulary vocabulary = DL.readVocabFile(tempFile);

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> DL.requireLowerCase(tempFile, vocabulary, true));
    assertTrue(e.getMessage().contains(tempFile.getName()), e.getMessage());
    assertTrue(e.getMessage().contains("normalizer.lowercase"), e.getMessage());
    assertTrue(e.getMessage().contains("false"), e.getMessage());
    assertTrue(e.getMessage().contains("lowerCase true"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testRequireLowerCaseAcceptsAVocabularyWithoutTheSetting(boolean lowerCase)
      throws IOException {
    final File plain = vocabFile("vocab.txt", "[CLS]\n[SEP]\n");
    DL.requireLowerCase(plain, DL.readVocabFile(plain), lowerCase);
    final File flat = vocabFile("vocab.json", "{\"[CLS]\": 0}");
    DL.requireLowerCase(flat, DL.readVocabFile(flat), lowerCase);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BPE", "Unigram", "WordLevel", "wordpiece", ""})
  void testLoadJsonVocabRejectsOtherModelTypes(String type) {
    final String json = "{\"model\": {\"type\": \"" + type + "\", \"vocab\": {\"a\": 0}}}";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains("WordPiece"), e.getMessage());
    assertTrue(e.getMessage().contains("\"" + type + "\""), e.getMessage());
  }

  @Test
  void testLoadJsonVocabRejectsAByteLevelBpeTokenizer() {
    // RoBERTa: without the type check this would load, and the <s> and </s> tokens would then
    // select the RoBERTa branch of the WordPiece encoder over a byte-level vocabulary
    final String json = "{\"model\": {\"type\": \"BPE\", \"vocab\": {\"<s>\": 0, \"</s>\": 2,"
        + " \"<unk>\": 3, \"\u0120the\": 4}, \"merges\": [\"\u0120 t\"]}}";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains("\"BPE\""), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"@@\"", "\"\"", "\"#\"", "null", "7"})
  void testLoadJsonVocabRejectsAnotherContinuingSubwordPrefix(String prefix) {
    final String json = "{\"model\": {\"type\": \"WordPiece\", \"continuing_subword_prefix\": "
        + prefix + ", \"vocab\": {\"a\": 0, \"##b\": 1}}}";
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains("continuing_subword_prefix"), e.getMessage());
    assertTrue(e.getMessage().contains("##"), e.getMessage());
  }

  @Test
  void testLoadJsonVocabSkipsALeadingByteOrderMark() {
    assertEquals(Map.of("a", 1), DL.readJsonVocab("\uFEFF{\"a\": 1}").ids());
    assertEquals(TOKENIZER_VOCAB, DL.readJsonVocab("\uFEFF" + TOKENIZER_JSON).ids());
  }

  @Test
  void testJsonVocabFileWithAByteOrderMarkIsReadAsJson() throws IOException {
    final File tempFile = vocabFile("vocab-bom.json", "\uFEFF{\"a\": 0, \"b\": 1}\n");

    assertEquals(Map.of("a", 0, "b", 1), DL.loadVocab(tempFile));
  }

  @Test
  void testPlainTextVocabFileWithAByteOrderMarkKeepsTheFirstToken() throws IOException {
    final File tempFile = vocabFile("vocab-bom.txt", "\uFEFF[CLS]\n[SEP]\n");

    assertEquals(Map.of("[CLS]", 0, "[SEP]", 1), DL.loadVocab(tempFile));
  }

  static Stream<Arguments> plainTextVocabWithEmptyLines() {
    return Stream.of(
        Arguments.of("[CLS]\n[SEP]\n\n", Map.of("[CLS]", 0, "[SEP]", 1)),
        Arguments.of("[CLS]\n\n[SEP]\n", Map.of("[CLS]", 0, "[SEP]", 2)),
        Arguments.of("\n[CLS]\r\n\r\n[SEP]\r\n", Map.of("[CLS]", 1, "[SEP]", 3)),
        Arguments.of("[CLS]\n \n[SEP]\n", Map.of("[CLS]", 0, " ", 1, "[SEP]", 2)),
        Arguments.of("\uD835\uDC00\n\n[SEP]\n", Map.of("\uD835\uDC00", 0, "[SEP]", 2)));
  }

  @ParameterizedTest
  @MethodSource("plainTextVocabWithEmptyLines")
  void testPlainTextVocabFileSkipsEmptyLinesAndKeepsLineIds(String content,
                                                           Map<String, Integer> expected)
      throws IOException {
    final File tempFile = vocabFile("vocab-empty-lines.txt", content);

    assertEquals(expected, DL.loadVocab(tempFile));
  }

  @Test
  void testPlainTextVocabFileWithATrailingEmptyLineBuildsAnEncoder() throws IOException {
    final File tempFile = vocabFile("vocab-trailing.txt", "[PAD]\n[UNK]\n[CLS]\n[SEP]\nhello\n\n");

    assertNotNull(AbstractDL.createWordpieceEncoder(DL.loadVocab(tempFile), true));
  }

  static Stream<Arguments> tokenizerLayouts() {
    return Stream.of(
        // model.vocab is the vocabulary; vocab_size and the other members are not entries
        Arguments.of(TOKENIZER_JSON, TOKENIZER_VOCAB),
        Arguments.of(TOKENIZER_JSON.replace("\n", "\r\n"), TOKENIZER_VOCAB),
        Arguments.of(TOKENIZER_JSON.replace("\n", "").replace("  ", ""), TOKENIZER_VOCAB),
        // added_tokens listed in model.vocab with the same id are entries once
        Arguments.of("{\"added_tokens\": [{\"id\": 2, \"content\": \"b\", \"special\": true}],"
            + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0, \"b\": 2}}}",
            Map.of("a", 0, "b", 2)),
        // an empty vocab object is an empty vocabulary
        Arguments.of("{\"model\": {\"type\": \"WordPiece\", \"vocab\": {}}}", Map.of()),
        // the continuing subword prefix of the WordPiece encoder is accepted
        Arguments.of("{\"model\": {\"type\": \"WordPiece\", \"continuing_subword_prefix\": \"##\","
            + " \"max_input_chars_per_word\": 100, \"vocab\": {\"a\": 0, \"##b\": 1}}}",
            Map.of("a", 0, "##b", 1)),
        // other top-level members, integers included, are not entries
        Arguments.of("{\"version\": \"1.0\", \"truncation\": {\"max_length\": 512},"
            + " \"padding\": null, \"vocab_size\": 1,"
            + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 0}}}", Map.of("a", 0)),
        // vocab objects elsewhere, at any depth, are not the vocabulary
        Arguments.of("{\"normalizer\": {\"a\": {\"b\": {\"c\": [[[{\"vocab\": {\"x\": 9}}]]]}}},"
            + " \"model\": {\"type\": \"WordPiece\", \"decoder\": {\"vocab\": {\"y\": 8}},"
            + " \"merges\": [[\"a\", \"b\"]], \"vocab\": {\"z\": 1}}}", Map.of("z", 1)),
        // a later model, a later vocab, or a later token wins
        Arguments.of("{\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 1}},"
            + " \"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 2}}}", Map.of("a", 2)),
        Arguments.of("{\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 1}, \"vocab\": {\"b\": 3}}}",
            Map.of("b", 3)),
        Arguments.of("{\"model\": {\"type\": \"WordPiece\","
            + " \"vocab\": {\"a\": 1, \"a\": 5, \"\\u0061\": 6}}}", Map.of("a", 6)),
        // the member names and the type may be written with escapes
        Arguments.of("{\"\\u006dodel\": {\"t\\u0079pe\": \"Word\\u0050iece\", \"voc\\u0061b\": {\"a\": 1}}}",
            Map.of("a", 1)),
        Arguments.of("{ \"model\"\t:\t{ \"type\" : \"WordPiece\" , \"vocab\"\r\n:\r\n{ \"a\" : 1 } } }",
            Map.of("a", 1)),
        Arguments.of("{\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 2147483647}}}",
            Map.of("a", Integer.MAX_VALUE)));
  }

  @ParameterizedTest
  @MethodSource("tokenizerLayouts")
  void testLoadJsonVocabTokenizerLayouts(String json, Map<String, Integer> expected) {
    assertEquals(expected, DL.readJsonVocab(json).ids());
  }

  @ParameterizedTest
  @ValueSource(strings = {"2147483648", "4294967296", "9223372036854775808",
      "12345678901234567890"})
  void testLoadJsonVocabNamesTheTokenOfAnIdThatDoesNotFit(String id) {
    for (String json : new String[] {"{\"big\": " + id + "}",
        "{\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"big\": " + id + "}}}"}) {
      final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
          () -> DL.readJsonVocab(json).ids(), json);
      assertTrue(e.getMessage().contains("\"big\""), e.getMessage());
      assertTrue(e.getMessage().contains("does not fit into an int"), e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"a\": 00}", "{\"a\": 0123}",
      "{\"model\": {\"type\": \"WordPiece\", \"vocab\": {\"a\": 01}}}"})
  void testLoadJsonVocabRejectsLeadingZeros(String json) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains("offset "), e.getMessage());
  }

  static Stream<Arguments> jsonVocabPrefixes() {
    return Stream.of(JSON_VOCAB, TOKENIZER_JSON.strip()).flatMap(text ->
        Stream.iterate(0, n -> n + 1).limit(text.length())
            .map(n -> Arguments.of(n, text.substring(0, n))));
  }

  @ParameterizedTest(name = "cut at {0}: {1}")
  @MethodSource("jsonVocabPrefixes")
  void testLoadJsonVocabRejectsATruncatedObject(int length, String prefix) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(prefix).ids());
    assertTrue(e.getMessage().contains("offset "), e.getMessage());
  }

  static Stream<Arguments> jsonVocabTrailers() {
    return Stream.of(JSON_VOCAB, TOKENIZER_JSON).flatMap(text ->
        Stream.of("{}", "x", "\uFEFF", ",", "\"vocab\"", "{\"a\":1}",
            "{\"model\": {\"type\": \"WordPiece\", \"vocab\": {}}}")
            .map(trailing -> Arguments.of(text, trailing)));
  }

  @ParameterizedTest
  @MethodSource("jsonVocabTrailers")
  void testLoadJsonVocabRejectsContentAfterTheObject(String text, String trailing) {
    final String json = text + trailing;
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(json).ids());
    assertTrue(e.getMessage().contains("offset " + text.length() + ","), e.getMessage());
    assertTrue(e.getMessage().contains("content after the object"), e.getMessage());
  }

  @Test
  void testJsonVocabFileWithWindowsLineEndings() throws IOException {
    final File tempFile = vocabFile("vocab-crlf.json", "{\r\n  \"a\": 0,\r\n  \"b\": 1\r\n}\r\n");

    assertEquals(Map.of("a", 0, "b", 1), DL.loadVocab(tempFile));
  }

  @Test
  void testPlainTextVocabFileWithWindowsLineEndings() throws IOException {
    final File tempFile = vocabFile("vocab-crlf.txt", "[CLS]\r\n[SEP]\r\nhello\r\n");

    assertEquals(Map.of("[CLS]", 0, "[SEP]", 1, "hello", 2), DL.loadVocab(tempFile));
  }

  @Test
  void testMalformedJsonVocabFileIsReportedAsAnInvalidFormat() throws IOException {
    final File tempFile = vocabFile("vocab-malformed.json", "{\"a\": 1, \"b\": }");

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> DL.loadVocab(tempFile));
    assertTrue(e.getMessage().contains(tempFile.getName()), e.getMessage());
    assertTrue(e.getMessage().contains("offset "), e.getMessage());
  }

  @Test
  void testJsonVocabFileWithAnInvalidEscapeIsReportedAsAnInvalidFormat() throws IOException {
    final File tempFile = vocabFile("vocab-invalid-escape.json", "{\"bad\\xescape\": 0}");

    assertThrows(InvalidFormatException.class, () -> DL.loadVocab(tempFile));
  }

  @Test
  void testLoadJsonVocabNamesTheUnsupportedUnigramModelType() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab(UNIGRAM_TOKENIZER_JSON).ids());
    assertTrue(e.getMessage().contains("WordPiece"), e.getMessage());
    assertTrue(e.getMessage().contains("\"Unigram\""), e.getMessage());
  }

  @Test
  void testLoadJsonVocabRejectsANonFiniteId() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DL.readJsonVocab("{\"ok\": 1, \"bad\": NaN}").ids());
    assertTrue(e.getMessage().contains("\"bad\""), e.getMessage());
  }
}

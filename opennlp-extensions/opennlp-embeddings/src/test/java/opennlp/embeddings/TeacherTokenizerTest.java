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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    assertTrue(!json.contains("[unused1]"), json);
    assertTrue(!json.contains("[MASK]"), json);
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

    final IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> TeacherTokenizer.read(tokenizerJson, null));
    assertTrue(e.getMessage().contains("BPE"), e.getMessage());
  }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code tokenizer.json} vocabulary contract: the Unigram {@code model.vocab} list order is
 * the row order, {@code added_tokens} append or must agree, everything else is skipped, and any
 * departure from the known shape fails loud.
 */
class TokenizerJsonVocabTest {

  @TempDir
  private Path dir;

  private Path write(String json) throws IOException {
    final Path file = dir.resolve("tokenizer.json");
    Files.writeString(file, json);
    return file;
  }

  @Test
  void testRejectsNullAndMissingFile() {
    assertThrows(IllegalArgumentException.class, () -> TokenizerJsonVocab.rows(null));
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(dir.resolve("absent.json")));
  }

  @Test
  void testVocabListOrderIsTheRowOrder() throws IOException {
    final Path file = write("{\"model\":{\"type\":\"Unigram\",\"unk_id\":1,"
        + "\"vocab\":[[\"<pad>\",0.0],[\"<unk>\",0.0],[\"\\u2581a\",-2.5],[\"b\",-3.0]]}}");

    assertEquals(List.of("<pad>", "<unk>", "\u2581a", "b"), TokenizerJsonVocab.rows(file));
  }

  @Test
  void testAddedTokenAtTheNextRowAppends() throws IOException {
    final Path file = write("{\"added_tokens\":[{\"id\":2,\"content\":\"<mask>\","
        + "\"special\":true}],"
        + "\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"a\",0.0],[\"b\",-1.0]]}}");

    assertEquals(List.of("a", "b", "<mask>"), TokenizerJsonVocab.rows(file));
  }

  @Test
  void testAddedTokenAtAnExistingRowMustAgree() throws IOException {
    final Path agreeing = write("{\"added_tokens\":[{\"id\":0,\"content\":\"<pad>\"}],"
        + "\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"<pad>\",0.0],[\"a\",-1.0]]}}");
    assertEquals(List.of("<pad>", "a"), TokenizerJsonVocab.rows(agreeing));

    final Path contradicting = write("{\"added_tokens\":[{\"id\":0,\"content\":\"<s>\"}],"
        + "\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"<pad>\",0.0],[\"a\",-1.0]]}}");
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(contradicting));
    assertTrue(e.getMessage().contains("contradicts"), e.getMessage());
  }

  @Test
  void testAddedTokenBeyondTheNextRowIsAGap() throws IOException {
    final Path file = write("{\"added_tokens\":[{\"id\":5,\"content\":\"<mask>\"}],"
        + "\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"a\",0.0]]}}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(file));
    assertTrue(e.getMessage().contains("gap"), e.getMessage());
  }

  @Test
  void testAddedTokensAreOverlaidInIdOrderNotListOrder() throws IOException {
    final Path file = write("{\"added_tokens\":[{\"id\":3,\"content\":\"y\"},"
        + "{\"id\":2,\"content\":\"x\"}],"
        + "\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"a\",0.0],[\"b\",-1.0]]}}");

    assertEquals(List.of("a", "b", "x", "y"), TokenizerJsonVocab.rows(file));
  }

  @Test
  void testSkipsUnrelatedSectionsAndDecodesEscapes() throws IOException {
    final Path file = write("{\"version\":\"1.0\",\"truncation\":null,"
        + "\"normalizer\":{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"AAAA\"},"
        + "\"pre_tokenizer\":[1,2,{\"a\":[true,false]}],"
        + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,"
        + "\"vocab\":[[\"\\\"quoted\\\"\",0.0],[\"tab\\there\",-1.0]]}}");

    assertEquals(List.of("\"quoted\"", "tab\there"), TokenizerJsonVocab.rows(file));
  }

  @Test
  void testRejectsANonUnigramModel() throws IOException {
    final Path file = write("{\"model\":{\"type\":\"BPE\",\"vocab\":[[\"a\",0.0]]}}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(file));
    assertTrue(e.getMessage().contains("BPE"), e.getMessage());
  }

  @Test
  void testRejectsAnObjectShapedVocab() throws IOException {
    // The WordPiece/BPE tokenizer.json layout stores vocab as {piece: id}; ids in that shape
    // are not list positions, so it must be refused rather than misread.
    final Path file = write("{\"model\":{\"type\":\"Unigram\",\"vocab\":{\"a\":0,\"b\":1}}}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(file));
    assertTrue(e.getMessage().contains("object"), e.getMessage());
  }

  @Test
  void testRejectsAMissingVocab() throws IOException {
    final Path noModel = write("{\"version\":\"1.0\"}");
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(noModel)).getMessage().contains("model.vocab"));

    final Path noVocab = write("{\"model\":{\"type\":\"Unigram\"}}");
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(noVocab)).getMessage().contains("model.vocab"));
  }

  @Test
  void testRejectsAnAddedTokenWithoutIdOrContent() throws IOException {
    final Path file = write("{\"added_tokens\":[{\"content\":\"<mask>\"}],"
        + "\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"a\",0.0]]}}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(file));
    assertTrue(e.getMessage().contains("id"), e.getMessage());
  }

  @Test
  void testRejectsDuplicateTopLevelSections() throws IOException {
    final Path file = write("{\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"a\",0.0]]},"
        + "\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"b\",0.0]]}}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TokenizerJsonVocab.rows(file));
    assertTrue(e.getMessage().contains("more than once"), e.getMessage());
  }

  @Test
  void testRejectsMalformedJson() throws IOException {
    final Path file = write("{\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"a\",0.0]");

    assertThrows(IllegalArgumentException.class, () -> TokenizerJsonVocab.rows(file));
  }

  @Test
  void testVocabularyEntryPointRejectsDuplicatePieces() throws IOException {
    final Path file = write("{\"model\":{\"type\":\"Unigram\","
        + "\"vocab\":[[\"a\",0.0],[\"a\",-1.0]]}}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> EmbeddingVocabulary.fromTokenizerJson(file));
    assertTrue(e.getMessage().contains("more than once"), e.getMessage());
  }
}

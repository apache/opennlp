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
 * The vocabulary contract: line number is the id, duplicates fail loud, the id lookup uses a
 * {@code -1} sentinel, and the reverse lookup enforces its bounds.
 */
class WordPieceVocabularyTest {

  @Test
  void testLineNumberIsTheTokenId() {
    final WordPieceVocabulary vocabulary =
        WordPieceVocabulary.fromLines(List.of("[CLS]", "[SEP]", "hello", "world"), "test");
    assertEquals(4, vocabulary.size());
    assertEquals(0, vocabulary.id("[CLS]"));
    assertEquals(2, vocabulary.id("hello"));
    assertEquals("world", vocabulary.token(3));
    assertTrue(vocabulary.tokens().contains("hello"));
  }

  @Test
  void testUnknownTokenIdIsTheSentinel() {
    final WordPieceVocabulary vocabulary =
        WordPieceVocabulary.fromLines(List.of("hello"), "test");
    assertEquals(-1, vocabulary.id("missing"));
    assertThrows(IllegalArgumentException.class, () -> vocabulary.id(null));
  }

  @Test
  void testDuplicateTokenFailsLoudlyNamingBothLines() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> WordPieceVocabulary.fromLines(List.of("hello", "world", "hello"), "test"));
    assertTrue(e.getMessage().contains("hello"), e.getMessage());
    assertTrue(e.getMessage().contains("0") && e.getMessage().contains("2"), e.getMessage());
  }

  @Test
  void testReverseLookupEnforcesBounds() {
    final WordPieceVocabulary vocabulary =
        WordPieceVocabulary.fromLines(List.of("hello"), "test");
    assertEquals("hello", vocabulary.token(0));
    assertThrows(IllegalArgumentException.class, () -> vocabulary.token(-1));
    assertThrows(IllegalArgumentException.class, () -> vocabulary.token(1));
  }

  @Test
  void testReadFromFileMatchesInMemoryLines(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    Files.write(file, List.of("[CLS]", "token"));
    final WordPieceVocabulary read = WordPieceVocabulary.read(file);
    assertEquals(2, read.size());
    assertEquals(1, read.id("token"));
  }
}

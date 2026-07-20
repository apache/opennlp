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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.tokenize.SubwordPiece;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cookbook path documented in {@code tokenizer.xml}: load a
 * {@link SentencePieceTokenizer} from a {@code .model} file, encode text to pieces with
 * original offsets, and obtain id arrays.
 */
public class SentencePieceUsageExampleTest {

  @Test
  void testLoadEncodeAndEncodeToIds(@TempDir Path dir) throws IOException {
    final Path modelFile = dir.resolve("spiece.model");
    try (InputStream in = SentencePieceUsageExampleTest.class
        .getResourceAsStream("tiny-unigram.model")) {
      assertNotNull(in, "missing test resource tiny-unigram.model");
      Files.copy(in, modelFile);
    }

    final SentencePieceTokenizer tokenizer = SentencePieceTokenizer.load(modelFile);
    final String text = "hello world";
    final List<SubwordPiece> pieces = tokenizer.encode(text);
    assertFalse(pieces.isEmpty());
    for (final SubwordPiece piece : pieces) {
      assertTrue(piece.id() >= 0);
      assertTrue(piece.start() >= 0);
      assertTrue(piece.end() <= text.length());
      // Control or whitespace pieces may report an empty span (start == end).
      assertTrue(piece.start() <= piece.end());
    }

    final int[] ids = tokenizer.encodeToIds(text);
    assertEquals(pieces.size(), ids.length);
    for (int i = 0; i < ids.length; i++) {
      assertEquals(pieces.get(i).id(), ids[i]);
    }
  }
}

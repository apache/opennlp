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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.SubwordPiece;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-loud behavior on malformed models, plus the concurrency guarantee: one loaded tokenizer
 * must produce identical results from many threads.
 */
class SentencePieceModelValidationTest {

  @Test
  void testNullAndEmptyInputFailLoudly() {
    assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load((Path) null));
    assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load((InputStream) null));
    assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(new byte[0])));
  }

  @Test
  void testGarbageBytesFailLoudly() {
    final byte[] garbage = "this is not a model file at all".getBytes(StandardCharsets.UTF_8);
    assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(garbage)));
  }

  @Test
  void testTruncatedModelFailsLoudly() throws IOException {
    final byte[] whole = readModel();
    final byte[] truncated = Arrays.copyOf(whole, whole.length / 3);
    assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(truncated)));
  }

  @Test
  void testUnsupportedModelTypeFailsLoudly() {
    // A minimal well-formed model claiming the WORD algorithm (model_type = 3).
    final byte[] model = minimalModel(3);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(model)));
    assertTrue(e.getMessage().contains("not supported"), e.getMessage());
  }

  @Test
  void testMissingUnknownPieceFailsLoudly() {
    final byte[] model = minimalModelWithoutUnk();
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(model)));
    assertTrue(e.getMessage().contains("unknown piece"), e.getMessage());
  }

  @Test
  void testConcurrentEncodingIsConsistent() throws Exception {
    final SentencePieceTokenizer tokenizer = SentencePieceParityTest.tokenizer("tiny-unigram");
    final String[] inputs = {
        "The quick brown fox jumps over the lazy dog.",
        "tokenization and segmentation",
        " Hello   world  ",
        "water running walked faster apple book work play"};
    final List<List<SubwordPiece>> expected = new ArrayList<>();
    for (final String input : inputs) {
      expected.add(tokenizer.encode(input));
    }

    final ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      final List<Future<Boolean>> futures = new ArrayList<>();
      for (int t = 0; t < 8; t++) {
        futures.add(pool.submit((Callable<Boolean>) () -> {
          for (int round = 0; round < 500; round++) {
            for (int i = 0; i < inputs.length; i++) {
              if (!expected.get(i).equals(tokenizer.encode(inputs[i]))) {
                return false;
              }
            }
          }
          return true;
        }));
      }
      for (final Future<Boolean> future : futures) {
        assertTrue(future.get(), "concurrent encoding must match single-threaded results");
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void testVocabularyAccessors() {
    final SentencePieceTokenizer tokenizer = SentencePieceParityTest.tokenizer("tiny-unigram");
    assertEquals(300, tokenizer.vocabularySize());
    assertEquals(SentencePieceTokenizer.Algorithm.UNIGRAM, tokenizer.algorithm());
    for (int id = 0; id < tokenizer.vocabularySize(); id++) {
      final String piece = tokenizer.idToPiece(id);
      if (!tokenizer.isUnknown(id) && !tokenizer.isControl(id)) {
        assertEquals(id, tokenizer.pieceToId(piece), "round trip of piece '" + piece + "'");
      }
    }
    assertEquals(tokenizer.unknownId(), tokenizer.pieceToId("definitely-not-in-the-vocabulary"));
    assertThrows(IllegalArgumentException.class, () -> tokenizer.idToPiece(-1));
    assertThrows(IllegalArgumentException.class,
        () -> tokenizer.idToPiece(tokenizer.vocabularySize()));
    assertThrows(IllegalArgumentException.class, () -> tokenizer.pieceToId(null));
  }

  private static byte[] readModel() throws IOException {
    try (InputStream in =
             SentencePieceModelValidationTest.class.getResourceAsStream("tiny-unigram.model")) {
      return in.readAllBytes();
    }
  }

  // Hand-encodes a minimal ModelProto: three pieces (<unk>, <s>, </s>) and a trainer spec with
  // the requested model type.
  private static byte[] minimalModel(int modelType) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    writePiece(out, "<unk>", 2);
    writePiece(out, "<s>", 3);
    writePiece(out, "</s>", 3);
    writePiece(out, "a", 1);
    // trainer_spec { model_type = <modelType> }
    out.write(0x12);
    out.write(2);
    out.write(0x18);
    out.write(modelType);
    return out.toByteArray();
  }

  private static byte[] minimalModelWithoutUnk() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    writePiece(out, "a", 1);
    writePiece(out, "b", 1);
    return out.toByteArray();
  }

  private static void writePiece(ByteArrayOutputStream out, String piece, int type) {
    final byte[] utf8 = piece.getBytes(StandardCharsets.UTF_8);
    // pieces { piece = <piece>; score = 0.0; type = <type> } as nested length-delimited field 1.
    final int inner = 2 + utf8.length + 2;
    out.write(0x0A);
    out.write(inner);
    out.write(0x0A);
    out.write(utf8.length);
    out.writeBytes(utf8);
    out.write(0x18);
    out.write(type);
  }
}

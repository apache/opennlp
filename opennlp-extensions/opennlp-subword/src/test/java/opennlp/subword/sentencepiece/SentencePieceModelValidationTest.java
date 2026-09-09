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
import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates malformed-model rejection and concurrent tokenizer use. */
class SentencePieceModelValidationTest {

  private static final String HEX_DIGITS = "0123456789ABCDEF";

  @Test
  void testRejectsNullAndEmptyInput() {
    assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load((Path) null));
    assertThrows(IllegalArgumentException.class,
        () -> SentencePieceTokenizer.load((InputStream) null));
    assertThrows(InvalidFormatException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(new byte[0])));
  }

  @Test
  void testRejectsGarbageBytes() {
    final byte[] garbage = "this is not a model file at all".getBytes(StandardCharsets.UTF_8);
    assertThrows(InvalidFormatException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(garbage)));
  }

  @Test
  void testRejectsTruncatedModel() throws IOException {
    final byte[] whole = readModel();
    final byte[] truncated = Arrays.copyOf(whole, whole.length / 3);
    assertThrows(InvalidFormatException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(truncated)));
  }

  @Test
  void testRejectsAPieceFieldThatCrossesItsMessageBoundary() {
    final byte[] model = {
        0x0A, 0x02,  // pieces sub-message with a two-byte payload
        0x0A, 0x04,  // piece string claiming four bytes outside that payload
        'a', 'b', 'c', 'd'};

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> ModelProtoReader.read(model));

    assertTrue(error.getMessage().contains("message boundary"), error.getMessage());
  }

  @Test
  void testRejectsMalformedUtf8InAPiece() {
    final byte[] model = {
        0x0A, 0x03,  // pieces sub-message
        0x0A, 0x01, (byte) 0xFF};

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> ModelProtoReader.read(model));

    assertTrue(error.getMessage().contains("UTF-8"), error.getMessage());
  }

  /** Verifies that the tenth byte of a 64-bit varint cannot carry more than one value bit. */
  @Test
  void testRejectsVarintLargerThan64Bits() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(minimalModel(ModelProtoReader.RawModel.MODEL_TYPE_UNIGRAM));
    out.write(0x28); // Unknown field 5 with the varint wire type.
    for (int i = 0; i < 9; i++) {
      out.write(0x80);
    }
    out.write(0x02);

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> ModelProtoReader.read(out.toByteArray()));

    assertTrue(error.getMessage().contains("64 bits"), error.getMessage());
  }

  @Test
  void testRejectsUnsupportedModelType() {
    // A minimal well-formed model claiming the WORD algorithm (model_type = 3).
    final byte[] model = minimalModel(3);
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(model)));
    assertTrue(e.getMessage().contains("not supported"), e.getMessage());
  }

  @Test
  void testRejectsMissingUnknownPiece() {
    final byte[] model = minimalModelWithoutUnk();
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(model)));
    assertTrue(e.getMessage().contains("unknown piece"), e.getMessage());
  }

  @Test
  void testRejectsMalformedPrecompiledCharsMap() {
    // A well-formed proto whose normalizer spec carries a truncated precompiled character map;
    // load() must report it as an invalid model, like every other malformed model content.
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    writePiece(out, "<unk>", 2);
    writePiece(out, "a", 1);
    // normalizer_spec { precompiled_charsmap = <3 bytes> }
    out.write(0x1A);
    out.write(5);
    out.write(0x12);
    out.write(3);
    out.writeBytes(new byte[] {1, 2, 3});
    final byte[] model = out.toByteArray();
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(model)));
    assertTrue(e.getMessage().contains("character map"), e.getMessage());
  }

  @Test
  void testConcurrentEncodingIsConsistent() throws Exception {
    final SentencePieceTokenizer tokenizer = SentencePieceFixtures.tokenizer("tiny-unigram");
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
    final SentencePieceTokenizer tokenizer = SentencePieceFixtures.tokenizer("tiny-unigram");
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

  @Test
  void testScoresAreFiniteAndRangeChecked() {
    final SentencePieceTokenizer tokenizer = SentencePieceFixtures.tokenizer("tiny-unigram");
    for (int id = 0; id < tokenizer.vocabularySize(); id++) {
      assertTrue(Float.isFinite(tokenizer.score(id)), "score of piece " + id);
    }
    assertThrows(IllegalArgumentException.class, () -> tokenizer.score(-1));
    assertThrows(IllegalArgumentException.class,
        () -> tokenizer.score(tokenizer.vocabularySize()));
  }

  @Test
  void testBytePiecesExistOnlyInByteFallbackModels() {
    final SentencePieceTokenizer byteFallback =
        SentencePieceFixtures.tokenizer("tiny-unigram-bytefb");
    int bytePieces = 0;
    for (int id = 0; id < byteFallback.vocabularySize(); id++) {
      if (byteFallback.isByte(id)) {
        bytePieces++;
        assertTrue(byteFallback.idToPiece(id).startsWith("<0x"),
            "byte piece " + id + " is " + byteFallback.idToPiece(id));
      }
    }
    assertEquals(256, bytePieces, "byte fallback defines one piece per byte value");

    final SentencePieceTokenizer plain = SentencePieceFixtures.tokenizer("tiny-unigram");
    for (int id = 0; id < plain.vocabularySize(); id++) {
      assertFalse(plain.isByte(id), "piece " + id + " must not be a byte piece");
    }
    assertThrows(IllegalArgumentException.class, () -> plain.isByte(-1));
  }

  @Test
  void testRejectsNonAsciiHexadecimalBytePiece() {
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> SentencePieceTokenizer.load(new ByteArrayInputStream(
            byteFallbackModel("<0x\uff26F>"))));

    assertTrue(error.getMessage().contains("invalid"), error.getMessage());
  }

  /**
   * Builds a byte-fallback model, replacing the {@code <0xFF>} piece with the supplied text.
   *
   * @param lastBytePiece The text of the piece assigned to byte {@code 0xFF}.
   * @return The encoded model.
   */
  private static byte[] byteFallbackModel(String lastBytePiece) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    writePiece(out, "<unk>", 2);
    for (int b = 0; b < 256; b++) {
      final String piece = "<0x" + HEX_DIGITS.charAt(b >>> 4)
          + HEX_DIGITS.charAt(b & 0x0f) + ">";
      writePiece(out, b == 255 ? lastBytePiece : piece, 6);
    }
    // trainer_spec { byte_fallback = true }
    out.write(0x12);
    out.write(3);
    out.write(0x98);
    out.write(0x02);
    out.write(1);
    return out.toByteArray();
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

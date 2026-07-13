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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.embeddings.cmdline.AssembleModelTool;
import opennlp.tools.cmdline.TerminateToolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assembler completes a distilled directory into a loadable one: it derives the WordPiece
 * {@code vocab.txt} and {@code tokenizer_config.json} from {@code tokenizer.json}, leaves existing
 * files alone, and reports the SentencePiece {@code .model} it cannot fabricate. The CLI tool wraps
 * it and turns failures into a {@link TerminateToolException}.
 */
class ModelAssemblerTest {

  // A WordPiece tokenizer.json with a five-entry vocab dictionary (no [CLS]/[SEP], as Model2Vec
  // ships) and a BERT normalizer that lower-cases. The dictionary is written out of id order to
  // prove the assembler sorts it.
  private static final String WORDPIECE_TOKENIZER_JSON =
      "{\"version\":\"1.0\","
          + "\"normalizer\":{\"type\":\"BertNormalizer\",\"strip_accents\":null,"
          + "\"lowercase\":true},"
          + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
          + "\"vocab\":{\"[PAD]\":0,\"hello\":2,\"[UNK]\":1,\"cat\":4,\"world\":3}}}";

  private static final float[][] ROWS = {
      {0f, 0f, 0f},       // [PAD]
      {1f, 10f, 100f},    // [UNK]
      {2f, 20f, 200f},    // hello
      {3f, 30f, 300f},    // world
      {4f, 40f, 400f},    // cat
  };

  private static Path writeWordpieceDistillation(Path dir) throws IOException {
    Files.writeString(dir.resolve("tokenizer.json"), WORDPIECE_TOKENIZER_JSON);
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false}");
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", ROWS));
    return dir;
  }

  @Test
  void testDerivesTheWordpieceVocabularyAndConfigInIdOrder(@TempDir Path dir) throws IOException {
    writeWordpieceDistillation(dir);

    final ModelAssembler.Result result = ModelAssembler.assemble(dir);

    assertEquals("WordPiece", result.family());
    assertEquals(3, result.dimension());
    assertEquals(5, result.vocabularySize());
    assertTrue(result.wroteVocabulary());
    assertTrue(result.wroteTokenizerConfig());
    // The vocab.txt must be the dictionary in id order, not the order it was written.
    assertEquals(List.of("[PAD]", "[UNK]", "hello", "world", "cat"),
        Files.readAllLines(dir.resolve("vocab.txt")));
    // The casing comes from the BERT normalizer's lowercase flag.
    assertTrue(Files.readString(dir.resolve("tokenizer_config.json")).contains("\"do_lower_case\": true"));
  }

  @Test
  void testAssembledDirectoryEmbeds(@TempDir Path dir) throws IOException {
    writeWordpieceDistillation(dir);
    ModelAssembler.assemble(dir);

    final StaticEmbeddingModel model = StaticEmbeddingModel.load(dir);
    // (hello[row 2] + world[row 3]) / 2 = (2 + 3) / 2 in the first component; the model has no
    // frame tokens, so only the two content pieces pool.
    assertEquals(2.5f, model.embed("hello world")[0], 1e-5f);
  }

  @Test
  void testLeavesExistingFilesUntouched(@TempDir Path dir) throws IOException {
    writeWordpieceDistillation(dir);
    // A vocab.txt the caller already wrote must not be overwritten.
    Files.write(dir.resolve("vocab.txt"), List.of("[PAD]", "[UNK]", "hello", "world", "cat"));
    Files.writeString(dir.resolve("tokenizer_config.json"), "{\"do_lower_case\": false}");

    final ModelAssembler.Result result = ModelAssembler.assemble(dir);

    assertFalse(result.wroteVocabulary());
    assertFalse(result.wroteTokenizerConfig());
    assertTrue(Files.readString(dir.resolve("tokenizer_config.json")).contains("false"));
  }

  @Test
  void testRejectsAMissingDistillationFile(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("tokenizer.json"), WORDPIECE_TOKENIZER_JSON);
    // no model.safetensors, no config.json
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> ModelAssembler.assemble(dir));
    assertTrue(e.getMessage().contains("model.safetensors"), e.getMessage());
  }

  @Test
  void testReportsTheMissingSentencePieceModelFile(@TempDir Path dir) throws IOException {
    // A Unigram distillation without its trained .model file: the assembler cannot fabricate it.
    Files.writeString(dir.resolve("tokenizer.json"),
        "{\"model\":{\"type\":\"Unigram\",\"vocab\":[[\"<unk>\",0.0],[\"a\",-1.0]]}}");
    Files.writeString(dir.resolve("config.json"), "{\"normalize\":true}");
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", new float[][] {{0f, 0f}, {1f, 1f}}));

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> ModelAssembler.assemble(dir));
    assertTrue(e.getMessage().contains("sentencepiece.bpe.model"), e.getMessage());
    assertTrue(e.getMessage().contains("copy it from the teacher"), e.getMessage());
  }

  @Test
  void testLoadsTheRealSentencePieceModelAfterItsFileIsPresent(@TempDir Path dir)
      throws IOException {
    // Assemble a SentencePiece directory around the bundled tiny model: once its .model file is
    // present the assembler only has to verify it loads.
    final byte[] modelBytes;
    try (InputStream in = getClass().getResourceAsStream("/opennlp/embeddings/tiny-unigram.model")) {
      modelBytes = in.readAllBytes();
    }
    Files.write(dir.resolve("sentencepiece.bpe.model"), modelBytes);
    Files.writeString(dir.resolve("config.json"), "{\"normalize\":false}");
    // A tokenizer.json whose vocab is the model's own poolable pieces, so the coverage check
    // passes; the matrix carries one row per piece.
    final opennlp.subword.sentencepiece.SentencePieceTokenizer tokenizer =
        opennlp.subword.sentencepiece.SentencePieceTokenizer.load(dir.resolve("sentencepiece.bpe.model"));
    final StringBuilder vocab = new StringBuilder("{\"model\":{\"type\":\"Unigram\",\"vocab\":[");
    int rows = 0;
    for (int id = 0; id < tokenizer.vocabularySize(); id++) {
      if (rows > 0) {
        vocab.append(',');
      }
      vocab.append('[').append(jsonString(tokenizer.idToPiece(id))).append(",-1.0]");
      rows++;
    }
    vocab.append("]}}");
    Files.writeString(dir.resolve("tokenizer.json"), vocab.toString());
    final float[][] matrix = new float[rows][2];
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", matrix));

    final ModelAssembler.Result result = ModelAssembler.assemble(dir);
    assertEquals("SentencePiece", result.family());
    assertEquals(rows, result.vocabularySize());
    assertFalse(result.wroteVocabulary());
  }

  @Test
  void testToolPrintsASummaryAndFailsLoudlyOnABadDirectory(@TempDir Path dir) throws IOException {
    writeWordpieceDistillation(dir);
    // The tool runs the assembly without throwing on a good directory.
    new AssembleModelTool().run(new String[] {"-modelDir", dir.toString()});

    // A directory that is not a model fails as a TerminateToolException, not a raw exception.
    final Path empty = Files.createDirectory(dir.resolve("empty"));
    final TerminateToolException e = assertThrows(TerminateToolException.class,
        () -> new AssembleModelTool().run(new String[] {"-modelDir", empty.toString()}));
    assertTrue(e.getMessage().contains("tokenizer.json") || e.getMessage().contains("distilled"),
        e.getMessage());
  }

  private static String jsonString(String s) {
    final StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}

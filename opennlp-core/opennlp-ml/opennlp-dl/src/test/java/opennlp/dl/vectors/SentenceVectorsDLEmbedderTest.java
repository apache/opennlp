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

package opennlp.dl.vectors;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.embeddings.TextEmbedder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@link TextEmbedder} adapter driven through a real ONNX session. The bundled
 * {@code tiny-vectors.onnx} (see {@code gen_tiny_vectors_model.py} next to it) computes
 * {@code output[b][t] = float(input_ids[b][t]) * [0.5, -1, 2]}, so every expected vector is
 * hand-computable from the vocabulary ids: {@code getVectors} returns the vector at the
 * {@code [CLS]} position, and {@code [CLS]} sits at line 7 of the test vocabulary.
 */
class SentenceVectorsDLEmbedderTest {

  // 7 * [0.5, -1, 2]
  private static final float[] CLS_VECTOR = {3.5f, -7f, 14f};

  private static File model() throws URISyntaxException {
    return new File(SentenceVectorsDLEmbedderTest.class
        .getResource("/opennlp/dl/vectors/tiny-vectors.onnx").toURI());
  }

  private static File vocab(Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    // Line number = id: [UNK]=2, [SEP]=3, hello=4, world=5, [CLS]=7.
    Files.write(file, List.of("[PAD]", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "unused2", "[CLS]"));
    return file.toFile();
  }

  @Test
  void testEmbedderContractOverARealSession(@TempDir Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(), vocab(dir))) {

      // The original entry point is untouched by the interface adoption.
      assertArrayEquals(CLS_VECTOR, vectors.getVectors("hello world"), 1e-5f);

      final TextEmbedder embedder = vectors;

      // The dimension comes from the model's declared output metadata, no inference needed.
      assertEquals(3, embedder.dimension());

      // The seam produces the same vector as the original entry point, for String and
      // non-String inputs alike.
      assertArrayEquals(CLS_VECTOR, embedder.embed("hello world"), 1e-5f);
      assertArrayEquals(CLS_VECTOR, embedder.embed(new StringBuilder("hello world")), 1e-5f);

      // The inherited default batch method returns one vector per input, in input order;
      // this model's [CLS]-position output is input-independent by construction.
      final float[][] batch = embedder.embedAll(List.of("hello world", "hello"));
      assertEquals(2, batch.length);
      assertArrayEquals(CLS_VECTOR, batch[0], 1e-5f);
      assertArrayEquals(CLS_VECTOR, batch[1], 1e-5f);

      assertThrows(IllegalArgumentException.class, () -> embedder.embed(null));
      assertThrows(IllegalArgumentException.class, () -> embedder.embedAll(null));
    }
  }
}

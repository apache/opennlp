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
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;

import static opennlp.dl.vectors.TinyVectorModels.DELTA;
import static opennlp.dl.vectors.TinyVectorModels.TINY_POOLED;
import static opennlp.dl.vectors.TinyVectorModels.TINY_VECTORS;
import static opennlp.dl.vectors.TinyVectorModels.UNIT_VECTOR;
import static opennlp.dl.vectors.TinyVectorModels.model;
import static opennlp.dl.vectors.TinyVectorModels.scale;
import static opennlp.dl.vectors.TinyVectorModels.tokens;
import static opennlp.dl.vectors.TinyVectorModels.vocab;
import static opennlp.dl.vectors.TinyVectorModels.vocabPad6;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the vector configuration independently of the ONNX session settings. */
class SentenceVectorsOptionsTest {

  /** Default vector options preserve the existing constructor's output. */
  @Test
  void testDefaults(@TempDir final Path dir) throws Exception {
    final SentenceVectorsOptions options = new SentenceVectorsOptions();
    assertEquals(Pooling.MEAN, options.pooling());
    assertTrue(options.normalize());
    assertEquals(SentenceVectorsDL.DEFAULT_MAX_LENGTH, options.maxLength());
    assertNull(options.padding());
    final File graph = model(dir, TINY_VECTORS);
    final File vocabulary = vocab(dir);
    try (SentenceVectorsDL legacy = new SentenceVectorsDL(graph, vocabulary);
         SentenceVectorsDL configured = new SentenceVectorsDL(graph, vocabulary, true, options,
             new InferenceOptions())) {
      assertArrayEquals(UNIT_VECTOR, configured.embed("hello world"), DELTA);
      assertArrayEquals(legacy.embed("hello world"), configured.embed("hello world"), DELTA);
    }
  }

  /** Pooling, normalization and truncation come from the vector options. */
  @Test
  void testVectorSettings(@TempDir final Path dir) throws Exception {
    final File graph = model(dir, TINY_VECTORS);
    final File vocabulary = vocab(dir);
    try (SentenceVectorsDL cls = new SentenceVectorsDL(graph, vocabulary, true,
        new SentenceVectorsOptions(Pooling.CLS, false, 8, null), new InferenceOptions());
         SentenceVectorsDL truncated = new SentenceVectorsDL(graph, vocabulary, true,
             new SentenceVectorsOptions(Pooling.MEAN, false, 3, null), new InferenceOptions())) {
      assertArrayEquals(scale(7), cls.embed("hello world"), DELTA);
      assertArrayEquals(scale(14.0f / 3), truncated.embed("hello world"), DELTA);
    }
  }

  /** Explicit padding overrides the default even when the model supplies pooled vectors. */
  @Test
  void testExplicitPadding(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_POOLED),
        vocabPad6(dir), true, new SentenceVectorsOptions(Pooling.MEAN, false, 6,
            PaddingStrategy.MAX_LENGTH), new InferenceOptions())) {
      assertArrayEquals(scale(14 + 3 * 6), vectors.embed("hello"), DELTA);
    }
  }

  /** Unspecified padding preserves exact-length batches on the CPU. */
  @Test
  void testAutomaticPadding(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_VECTORS), vocab(dir),
        true, new SentenceVectorsOptions(), new InferenceOptions())) {
      assertEquals(List.of(List.of(0), List.of(1)),
          vectors.batches(new Tokens[] {tokens(4), tokens(3)}));
    }
  }

  /** The session's lower-case override still takes precedence over the constructor argument. */
  @Test
  void testInferenceLowerCaseOverride(@TempDir final Path dir) throws Exception {
    final InferenceOptions inference = new InferenceOptions();
    inference.setLowerCase(true);
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_VECTORS), vocab(dir),
        false, new SentenceVectorsOptions(), inference)) {
      assertArrayEquals(vectors.embed("hello"), vectors.embed("HELLO"), DELTA);
    }
  }

  /** A missing pooling mode is rejected before a session is created. */
  @Test
  void testNullPooling() {
    assertEquals("pooling must not be null", assertThrows(IllegalArgumentException.class,
        () -> new SentenceVectorsOptions(null, true, 8, null)).getMessage());
  }

  /** The length includes both wrapper tokens, so it must be at least two. */
  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1})
  void testInvalidLength(final int length) {
    assertEquals("maxLength must be at least 2", assertThrows(IllegalArgumentException.class,
        () -> new SentenceVectorsOptions(Pooling.MEAN, true, length, null)).getMessage());
  }

  /** The minimum length retains the classification and separator tokens. */
  @Test
  void testMinimumLength(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_VECTORS), vocab(dir),
        true, new SentenceVectorsOptions(Pooling.MEAN, false, 2, null), new InferenceOptions())) {
      assertArrayEquals(scale(5), vectors.embed("hello world"), DELTA);
    }
  }

  /** A missing vector configuration is rejected with the public argument name. */
  @Test
  void testNullOptions(@TempDir final Path dir) throws Exception {
    final File graph = model(dir, TINY_VECTORS);
    final File vocabulary = vocab(dir);
    assertEquals("vectorOptions must not be null", assertThrows(IllegalArgumentException.class,
        () -> new SentenceVectorsDL(graph, vocabulary, true, null,
            new InferenceOptions())).getMessage());
  }
}

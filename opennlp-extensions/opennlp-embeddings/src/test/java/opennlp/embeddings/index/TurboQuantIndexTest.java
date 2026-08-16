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
package opennlp.embeddings.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quantized index against the exact one: self-retrieval and recall on a deterministic
 * random collection, the persisted round trip, and the malformed-directory contract.
 */
class TurboQuantIndexTest {

  private static final int DIMENSION = 64;
  private static final int COUNT = 100;
  private static final long SEED = 7;

  /** {@return the deterministic Gaussian test collection, one row per id} */
  private static float[][] collection() {
    final Random random = new Random(SEED);
    final float[][] vectors = new float[COUNT][DIMENSION];
    for (final float[] vector : vectors) {
      for (int d = 0; d < DIMENSION; d++) {
        vector[d] = (float) random.nextGaussian();
      }
    }
    return vectors;
  }

  private static TurboQuantIndex quantized(float[][] vectors, int bits) {
    final TurboQuantIndex index = new TurboQuantIndex(DIMENSION, bits, 42);
    for (int i = 0; i < vectors.length; i++) {
      index.add("v" + i, vectors[i]);
    }
    index.freeze();
    return index;
  }

  @Test
  void testSelfRetrievalSurvivesQuantization() {
    final float[][] vectors = collection();
    final TurboQuantIndex index = quantized(vectors, 4);
    for (int i = 0; i < vectors.length; i++) {
      assertEquals("v" + i, index.topK(vectors[i], 1).get(0).id(),
          "vector " + i + " must be its own nearest neighbor");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 4})
  void testRecallAgainstTheExactIndex(int bits) {
    final float[][] vectors = collection();
    final TurboQuantIndex quantized = quantized(vectors, bits);
    final FlatFloatIndex exact = new FlatFloatIndex(DIMENSION);
    for (int i = 0; i < vectors.length; i++) {
      exact.add("v" + i, vectors[i]);
    }
    exact.freeze();

    final Random random = new Random(SEED + 1);
    double overlap = 0;
    final int queries = 20;
    final int k = 10;
    for (int q = 0; q < queries; q++) {
      final float[] query = new float[DIMENSION];
      for (int d = 0; d < DIMENSION; d++) {
        query[d] = (float) random.nextGaussian();
      }
      final Set<String> truth = new HashSet<>();
      for (final VectorIndex.Hit hit : exact.topK(query, k)) {
        truth.add(hit.id());
      }
      for (final VectorIndex.Hit hit : quantized.topK(query, k)) {
        if (truth.contains(hit.id())) {
          overlap++;
        }
      }
    }
    final double recall = overlap / (queries * k);
    // 4 bits tracks the exact ranking closely; 2 bits trades more recall for half the bytes.
    assertTrue(recall >= (bits == 4 ? 0.85 : 0.6),
        "recall@" + k + " at " + bits + " bits: " + recall);
  }

  @Test
  void testWriteReadRoundTripAnswersIdentically(@TempDir Path dir) throws IOException {
    final float[][] vectors = collection();
    final TurboQuantIndex index = quantized(vectors, 4);
    index.write(dir);

    final TurboQuantIndex reloaded = TurboQuantIndex.read(dir);
    assertEquals(index.size(), reloaded.size());
    assertEquals(index.dimension(), reloaded.dimension());
    assertEquals(index.bits(), reloaded.bits());
    // The file stores the codes, scales, and seed, so a reloaded index scores identically.
    assertEquals(index.topK(vectors[3], 5), reloaded.topK(vectors[3], 5));
  }

  @Test
  void testWritingRequiresAFrozenNonEmptyIndex(@TempDir Path dir) {
    final TurboQuantIndex building = new TurboQuantIndex(DIMENSION, 4, 42);
    building.add("a", collection()[0]);
    assertThrows(IllegalStateException.class, () -> building.write(dir));

    final TurboQuantIndex empty = new TurboQuantIndex(DIMENSION, 4, 42);
    empty.freeze();
    assertThrows(IllegalStateException.class, () -> empty.write(dir));
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4})
  void testBytesPerVectorIncludesPaddedCodesScaleAndNorm(int bits) {
    final int unpaddedDimension = 65;
    final TurboQuantIndex index = new TurboQuantIndex(unpaddedDimension, bits, 42);
    final float[] vector = new float[unpaddedDimension];
    vector[0] = 1;
    index.add("one", vector);
    index.freeze();

    final int paddedDimension = 128;
    final double expected = (paddedDimension * bits + Byte.SIZE - 1) / Byte.SIZE
        + 2 * Float.BYTES;
    assertEquals(expected, index.bytesPerVector());
  }

  @Test
  void testBytesPerVectorRequiresAFrozenNonEmptyIndex() {
    final TurboQuantIndex building = new TurboQuantIndex(DIMENSION, 4, 42);
    building.add("one", collection()[0]);
    assertThrows(IllegalStateException.class, building::bytesPerVector);

    final TurboQuantIndex empty = new TurboQuantIndex(DIMENSION, 4, 42);
    empty.freeze();
    assertThrows(IllegalStateException.class, empty::bytesPerVector);
  }

  @Test
  void testReadRejectsAMissingFile(@TempDir Path dir) {
    assertThrows(IllegalArgumentException.class, () -> TurboQuantIndex.read(dir));
  }

  @Test
  void testReadRejectsADuplicateId(@TempDir Path dir) throws IOException {
    final TurboQuantIndex index = quantized(collection(), 4);
    index.write(dir);
    final List<String> ids = Files.readAllLines(dir.resolve(TurboQuantIndex.IDS_FILE));
    ids.set(1, ids.get(0));
    Files.write(dir.resolve(TurboQuantIndex.IDS_FILE), ids);

    assertThrows(InvalidFormatException.class, () -> TurboQuantIndex.read(dir));
  }

  @Test
  void testReadRejectsAnIdCountMismatch(@TempDir Path dir) throws IOException {
    final TurboQuantIndex index = quantized(collection(), 4);
    index.write(dir);
    Files.write(dir.resolve(TurboQuantIndex.IDS_FILE), List.of("one-extra-id"),
        StandardOpenOption.APPEND);

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> TurboQuantIndex.read(dir));
    assertTrue(e.getMessage().contains("do not belong"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5, 32})
  void testAnUnsupportedBitWidthIsRejected(int bits) {
    assertThrows(IllegalArgumentException.class, () -> new TurboQuantIndex(DIMENSION, bits, 42));
  }
}

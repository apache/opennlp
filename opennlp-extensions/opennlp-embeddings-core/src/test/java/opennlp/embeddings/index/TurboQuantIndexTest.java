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

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers quantized self-retrieval, recall against the exact index, persistence, and malformed
 * index directories.
 */
class TurboQuantIndexTest {

  private static final int DIMENSION = 64;
  private static final int COUNT = 100;
  private static final long SEED = 7;

  /** {@return a repeatable Gaussian test collection, one row per id} */
  private float[][] collection() {
    final Random random = new Random(SEED);
    final float[][] vectors = new float[COUNT][DIMENSION];
    for (final float[] vector : vectors) {
      for (int d = 0; d < DIMENSION; d++) {
        vector[d] = (float) random.nextGaussian();
      }
    }
    return vectors;
  }

  private TurboQuantIndex quantized(float[][] vectors, int bits) {
    final TurboQuantIndex index = new TurboQuantIndex(DIMENSION, bits, 42);
    for (int i = 0; i < vectors.length; i++) {
      index.add("v" + i, vectors[i]);
    }
    index.freeze();
    return index;
  }

  @Test
  void testSelfRetrievalAfterQuantization() {
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
    assertTrue(recall >= (bits == 4 ? 0.85 : 0.6),
        "recall@" + k + " at " + bits + " bits: " + recall);
  }

  @Test
  void testWriteReadRoundTripPreservesScores(@TempDir Path dir) throws IOException {
    final float[][] vectors = collection();
    final TurboQuantIndex index = quantized(vectors, 4);
    index.write(dir);

    final TurboQuantIndex reloaded = TurboQuantIndex.read(dir);
    assertEquals(index.size(), reloaded.size());
    assertEquals(index.dimension(), reloaded.dimension());
    assertEquals(index.bits(), reloaded.bits());
    assertEquals(index.topK(vectors[3], 5), reloaded.topK(vectors[3], 5));
    assertTrue(Files.isRegularFile(dir.resolve(TurboQuantIndex.MANIFEST_FILE)));
    assertThrows(IllegalStateException.class, () -> reloaded.add("new", vectors[0]));
  }

  @Test
  void testWritingRequiresAFrozenNonEmptyIndex(@TempDir Path dir) {
    final TurboQuantIndex building = new TurboQuantIndex(DIMENSION, 4, 42);
    building.add("a", collection()[0]);
    assertThrows(IllegalStateException.class, () -> building.write(dir));

    final TurboQuantIndex empty = new TurboQuantIndex(DIMENSION, 4, 42);
    empty.freeze();
    assertThrows(IllegalStateException.class, () -> empty.write(dir));
    assertThrows(IllegalArgumentException.class, () -> empty.write(null));
  }

  /**
   * Checks the reported vector size using file growth after adding vectors.
   *
   * @param dimension The vector dimension.
   * @param bits The quantization width.
   * @param expected The expected bytes per vector.
   * @param dir The temporary directory.
   * @throws IOException Thrown if file access fails.
   */
  @ParameterizedTest
  @CsvSource({"1,2,17", "1,3,17", "1,4,17", "3,2,17", "3,3,18", "3,4,18",
      "65,2,48", "65,3,64", "65,4,80", "300,4,272"})
  void testBytesPerVectorMatchesFileGrowth(int dimension, int bits, int expected,
                                         @TempDir Path dir) throws IOException {
    final TurboQuantIndex small = new TurboQuantIndex(dimension, bits, 42);
    final TurboQuantIndex large = new TurboQuantIndex(dimension, bits, 42);
    final float[] vector = new float[dimension];
    vector[0] = 1;
    small.add("v0", vector);
    for (int i = 0; i < 3; i++) {
      large.add("v" + i, vector);
    }
    small.freeze();
    large.freeze();
    final Path smallDirectory = dir.resolve("small");
    final Path largeDirectory = dir.resolve("large");
    small.write(smallDirectory);
    large.write(largeDirectory);

    final long smallBytes = Files.size(smallDirectory.resolve(TurboQuantIndex.VECTORS_FILE));
    final long largeBytes = Files.size(largeDirectory.resolve(TurboQuantIndex.VECTORS_FILE));
    final double bytesPerAddedVector = (double) (largeBytes - smallBytes)
        / (large.size() - small.size());

    assertEquals(expected, bytesPerAddedVector);
    assertEquals(bytesPerAddedVector, small.bytesPerVector());
    assertEquals(bytesPerAddedVector, large.bytesPerVector());
    assertEquals(bytesPerAddedVector, TurboQuantIndex.read(smallDirectory).bytesPerVector());
    assertEquals(bytesPerAddedVector, TurboQuantIndex.read(largeDirectory).bytesPerVector());
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
    assertThrows(IllegalArgumentException.class, () -> TurboQuantIndex.read(null));
  }

  /**
   * Reports an incomplete vector header as an invalid index format.
   *
   * @param length The retained header byte count.
   * @param dir The temporary directory.
   * @throws IOException Thrown if writing fails.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, 4, 7, 8, 11, 12, 15, 16, 23, 24, 27})
  void testReadReportsTruncatedHeaderAsInvalidFormat(int length, @TempDir Path dir)
      throws IOException {
    final TurboQuantIndex index = new TurboQuantIndex(3, 4, 42);
    index.add("Alice", new float[] {1f, 0f, 0f});
    index.freeze();
    index.write(dir);
    final byte[] vectors = Files.readAllBytes(dir.resolve(TurboQuantIndex.VECTORS_FILE));
    final byte[] truncated = Arrays.copyOf(vectors, length);
    IndexFiles.write(dir, TurboQuantIndex.VECTORS_FILE, TurboQuantIndex.IDS_FILE,
        List.of("Alice"), file -> Files.write(file, truncated));

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> TurboQuantIndex.read(dir));
    assertInstanceOf(EOFException.class, error.getCause());
    assertTrue(error.getMessage().contains(TurboQuantIndex.VECTORS_FILE));
    assertTrue(error.getMessage().contains("truncated"));
  }

  @Test
  void testReadRejectsADuplicateId(@TempDir Path dir) throws IOException {
    final TurboQuantIndex index = quantized(collection(), 4);
    index.write(dir);
    final List<String> ids = Files.readAllLines(dir.resolve(TurboQuantIndex.IDS_FILE));
    final byte[] vectors = Files.readAllBytes(dir.resolve(TurboQuantIndex.VECTORS_FILE));
    ids.set(1, ids.get(0));
    IndexFiles.write(dir, TurboQuantIndex.VECTORS_FILE, TurboQuantIndex.IDS_FILE, ids,
        file -> Files.write(file, vectors));

    assertThrows(InvalidFormatException.class, () -> TurboQuantIndex.read(dir));
  }

  @Test
  void testReadRejectsAnIdCountMismatch(@TempDir Path dir) throws IOException {
    final TurboQuantIndex index = quantized(collection(), 4);
    index.write(dir);
    final byte[] vectors = Files.readAllBytes(dir.resolve(TurboQuantIndex.VECTORS_FILE));
    final List<String> ids = Files.readAllLines(dir.resolve(TurboQuantIndex.IDS_FILE));
    ids.add("one-extra-id");
    IndexFiles.write(dir, TurboQuantIndex.VECTORS_FILE, TurboQuantIndex.IDS_FILE, ids,
        file -> Files.write(file, vectors));

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> TurboQuantIndex.read(dir));
    assertTrue(e.getMessage().contains("do not belong"), e.getMessage());
  }

  @Test
  void testReadRejectsIdsChangedWithoutTheirVectors(@TempDir Path dir) throws IOException {
    final TurboQuantIndex index = quantized(collection(), 4);
    index.write(dir);
    final List<String> ids = Files.readAllLines(dir.resolve(TurboQuantIndex.IDS_FILE));
    ids.set(0, "renamed-row");
    Files.write(dir.resolve(TurboQuantIndex.IDS_FILE), ids);

    assertThrows(InvalidFormatException.class, () -> TurboQuantIndex.read(dir));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5, 32})
  void testAnUnsupportedBitWidthIsRejected(int bits) {
    assertThrows(IllegalArgumentException.class, () -> new TurboQuantIndex(DIMENSION, bits, 42));
  }
}

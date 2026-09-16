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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests cosine scores, result ordering, persistence, and malformed index directories.
 */
class FlatFloatIndexTest {

  @Test
  void testScoresMatchKnownCosineSimilarities() {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("x", new float[] {1f, 0f});
    index.add("y", new float[] {0f, 1f});
    index.add("diagonal", new float[] {1f, 1f});
    index.freeze();

    final List<VectorIndex.Hit> hits = index.topK(new float[] {1f, 0f}, 3);
    assertEquals(3, hits.size());
    assertEquals("x", hits.get(0).id());
    assertEquals(1.0, hits.get(0).score(), 1e-12);
    assertEquals("diagonal", hits.get(1).id());
    assertEquals(Math.sqrt(0.5), hits.get(1).score(), 1e-12);
    assertEquals("y", hits.get(2).id());
    assertEquals(0.0, hits.get(2).score(), 1e-12);
  }

  @Test
  void testAnInverseVectorScoresMinusOne() {
    final FlatFloatIndex index = new FlatFloatIndex(3);
    index.add("inverse", new float[] {-2f, 0f, 0f});
    index.freeze();

    assertEquals(-1.0, index.topK(new float[] {5f, 0f, 0f}, 1).get(0).score(), 1e-12);
  }

  @Test
  void testFiniteInputsProduceABoundedFiniteScore() {
    final float[] vector = {2_920_951_808f, -8_143_641_600f};
    final FlatFloatIndex index = new FlatFloatIndex(vector.length);
    index.add("same", vector);
    index.freeze();

    final double score = index.topK(vector, 1).get(0).score();
    assertTrue(Double.isFinite(score), "score: " + score);
    assertTrue(score <= 1.0, "score: " + score);
    assertEquals(1.0, score, 1e-12);
  }

  @Test
  void testExtremeFiniteInputsDoNotOverflowTheDotProduct() {
    final float[] vector = {Float.MAX_VALUE, 0f};
    final FlatFloatIndex index = new FlatFloatIndex(vector.length);
    index.add("same", vector);
    index.freeze();

    assertEquals(1.0, index.topK(vector, 1).get(0).score(), 1e-12);
  }

  @Test
  void testAnOddDimensionExercisesTheUnrolledTail() {
    // Dimension 5 leaves one coordinate for the scalar tail after the 4-wide unroll.
    final FlatFloatIndex index = new FlatFloatIndex(5);
    index.add("v", new float[] {1f, 2f, 3f, 4f, 5f});
    index.freeze();

    final double norm = Math.sqrt(1 + 4 + 9 + 16 + 25);
    // Only the scalar tail contributes to this dot product.
    final float[] query = new float[] {0f, 0f, 0f, 0f, 2f};
    assertEquals(5.0 * 2 / (2 * norm), index.topK(query, 1).get(0).score(), 1e-12);
  }

  @Test
  void testWriteReadRoundTripPreservesScores(@TempDir Path dir) throws IOException {
    final FlatFloatIndex index = new FlatFloatIndex(3);
    index.add("x", new float[] {1f, 0f, 0f});
    index.add("diagonal", new float[] {1f, 1f, 0f});
    index.add("zero", new float[] {0f, 0f, 0f});
    index.freeze();
    index.write(dir);

    final FlatFloatIndex reloaded = FlatFloatIndex.read(dir);
    assertEquals(index.size(), reloaded.size());
    assertEquals(index.dimension(), reloaded.dimension());
    assertEquals(index.topK(new float[] {1f, 0.5f, 0f}, 3),
        reloaded.topK(new float[] {1f, 0.5f, 0f}, 3));
    assertEquals(List.of("x", "diagonal", "zero"),
        Files.readAllLines(dir.resolve(FlatFloatIndex.IDS_FILE)));
    assertTrue(Files.isRegularFile(dir.resolve(FlatFloatIndex.MANIFEST_FILE)));
    assertThrows(IllegalStateException.class,
        () -> reloaded.add("new", new float[] {0f, 0f, 1f}));
  }

  @Test
  void testWritingRequiresAFrozenNonEmptyIndex(@TempDir Path dir) {
    final FlatFloatIndex building = new FlatFloatIndex(2);
    building.add("a", new float[] {1f, 0f});
    assertThrows(IllegalStateException.class, () -> building.write(dir));

    final FlatFloatIndex empty = new FlatFloatIndex(2);
    empty.freeze();
    assertThrows(IllegalStateException.class, () -> empty.write(dir));
    assertThrows(IllegalArgumentException.class, () -> empty.write(null));
  }

  @Test
  void testReadRejectsAMissingFile(@TempDir Path dir) {
    assertThrows(IllegalArgumentException.class, () -> FlatFloatIndex.read(dir));
    assertThrows(IllegalArgumentException.class, () -> FlatFloatIndex.read(null));
  }

  @Test
  void testReadRejectsADuplicateId(@TempDir Path dir) throws IOException {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("a", new float[] {1f, 0f});
    index.add("b", new float[] {0f, 1f});
    index.freeze();
    index.write(dir);
    final byte[] vectors = Files.readAllBytes(dir.resolve(FlatFloatIndex.VECTORS_FILE));
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE,
        List.of("a", "a"), file -> Files.write(file, vectors));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsAnIdCountMismatch(@TempDir Path dir) throws IOException {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("a", new float[] {1f, 0f});
    index.freeze();
    index.write(dir);
    final byte[] vectors = Files.readAllBytes(dir.resolve(FlatFloatIndex.VECTORS_FILE));
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE,
        List.of("a", "one-extra-id"), file -> Files.write(file, vectors));

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
    assertTrue(e.getMessage().contains("do not belong"), e.getMessage());
  }

  @Test
  void testReadRejectsIdsChangedWithoutTheirVectors(@TempDir Path dir) throws IOException {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("alice", new float[] {1f, 0f});
    index.add("rabbit", new float[] {0f, 1f});
    index.freeze();
    index.write(dir);
    Files.write(dir.resolve(FlatFloatIndex.IDS_FILE), List.of("queen", "rabbit"));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsATruncatedVectorsFile(@TempDir Path dir) throws IOException {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("a", new float[] {1f, 0f});
    index.freeze();
    index.write(dir);
    final Path vectors = dir.resolve(FlatFloatIndex.VECTORS_FILE);
    final byte[] bytes = Files.readAllBytes(vectors);
    final byte[] truncated = java.util.Arrays.copyOf(bytes, bytes.length - 1);
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE, List.of("a"),
        file -> Files.write(file, truncated));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsATruncatedHeader(@TempDir Path dir) throws IOException {
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE, List.of("a"),
        file -> Files.write(file, new byte[] {0x4f}));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsAnUnknownFileMagic(@TempDir Path dir) throws IOException {
    final ByteBuffer file = ByteBuffer.allocate(5 * Integer.BYTES);
    file.put(new byte[] {'B', 'A', 'D', '!'});
    file.putInt(2);
    file.putInt(1);
    file.putFloat(1f);
    file.putFloat(0f);
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE, List.of("Alice"),
        path -> Files.write(path, file.array()));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsAnInvalidShape(@TempDir Path dir) throws IOException {
    final ByteBuffer header = ByteBuffer.allocate(3 * Integer.BYTES);
    header.put(new byte[] {'O', 'N', 'F', '1'});
    header.putInt(2);
    header.putInt(Integer.MAX_VALUE);
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE, List.of("Alice"),
        path -> Files.write(path, header.array()));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsTrailingBytes(@TempDir Path dir) throws IOException {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("Alice", new float[] {1f, 0f});
    index.freeze();
    index.write(dir);
    final Path vectorsFile = dir.resolve(FlatFloatIndex.VECTORS_FILE);
    final byte[] vectors = Files.readAllBytes(vectorsFile);
    final byte[] extended = java.util.Arrays.copyOf(vectors, vectors.length + 1);
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE, List.of("Alice"),
        path -> Files.write(path, extended));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsANonFiniteVector(@TempDir Path dir) throws IOException {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("a", new float[] {1f, 0f});
    index.freeze();
    index.write(dir);

    final Path vectorsFile = dir.resolve(FlatFloatIndex.VECTORS_FILE);
    final byte[] bytes = Files.readAllBytes(vectorsFile);
    ByteBuffer.wrap(bytes).putFloat(12, Float.NaN);
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE, List.of("a"),
        file -> Files.write(file, bytes));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }

  @Test
  void testReadRejectsAnEmptyPersistedIndex(@TempDir Path dir) throws IOException {
    final ByteBuffer header = ByteBuffer.allocate(3 * Integer.BYTES);
    header.put(new byte[] {'O', 'N', 'F', '1'});
    header.putInt(2);
    header.putInt(0);
    IndexFiles.write(dir, FlatFloatIndex.VECTORS_FILE, FlatFloatIndex.IDS_FILE, List.of(),
        file -> Files.write(file, header.array()));

    assertThrows(InvalidFormatException.class, () -> FlatFloatIndex.read(dir));
  }
}

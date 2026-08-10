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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the loader contract of {@link QuantizedEmbeddingMatrix#read(Path)}: malformed
 * file content fails with the checked {@link InvalidFormatException}, never with an
 * unchecked exception or an allocation failure.
 */
public class QuantizedMatrixFormatTest {

  /** The on-disk magic of a quantized matrix, as written by the writer. */
  private static final int MAGIC = 0x4F4E5131;

  @Test
  void testBadMagicIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("bad-magic.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(0xCAFEBABE);
    }
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(file));
    assertTrue(e.getMessage().contains("magic"), e.getMessage());
  }

  @Test
  void testNegativeRowCountIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("negative-rows.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(MAGIC);
      out.writeInt(-5);
    }
    assertThrows(InvalidFormatException.class, () -> QuantizedEmbeddingMatrix.read(file));
  }

  @Test
  void testImplausibleRowCountFailsBeforeAllocating(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("huge-rows.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(MAGIC);
      out.writeInt(Integer.MAX_VALUE);
      out.writeInt(8);
      out.writeInt(4);
      out.writeLong(17L);
      out.writeInt(16);
    }
    assertThrows(InvalidFormatException.class, () -> QuantizedEmbeddingMatrix.read(file));
  }

  @Test
  void testUnsupportedBitWidthIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("bad-bits.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(MAGIC);
      out.writeInt(1);
      out.writeInt(8);
      out.writeInt(7);
    }
    assertThrows(InvalidFormatException.class, () -> QuantizedEmbeddingMatrix.read(file));
  }

  @Test
  void testDeclaredPayloadBeyondFileSizeFailsBeforeAllocating(@TempDir Path dir)
      throws IOException {
    // A 1.1 MB file declaring 1,000,000 rows of 512 dimensions at 4 bits describes 256 MB of
    // packed codes plus 8 MB of scales and norms. Both dimensions individually pass a
    // "smaller than the file size" plausibility check, so the loader must hold the declared
    // total against the bytes actually present, before allocating anything row-sized.
    final Path file = dir.resolve("huge-payload.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(MAGIC);
      out.writeInt(1_000_000);
      out.writeInt(512);
      out.writeInt(4);
      out.writeLong(17L);
      out.writeInt(16);
      for (int i = 0; i < 16; i++) {
        out.writeFloat(i - 7.5f);
      }
      out.write(new byte[1_100_000 - 92]);
    }
    final InvalidFormatException e = assertTimeoutPreemptively(Duration.ofSeconds(10),
        () -> assertThrows(InvalidFormatException.class,
            () -> QuantizedEmbeddingMatrix.read(file)));
    assertTrue(e.getMessage().contains(file.toString()), e.getMessage());
  }

  @Test
  void testInvalidStoredGridIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = validFile(dir);
    final byte[] bytes = Files.readAllBytes(file);
    // The first grid level is the big-endian float at offset 28, after the six header fields.
    writeNaN(bytes, 28);
    final Path patched = dir.resolve("bad-grid.quantized");
    Files.write(patched, bytes);
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));
    assertTrue(e.getMessage().contains("grid"), e.getMessage());
  }

  @Test
  void testNonFiniteDecodedNormIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = validFile(dir);
    final byte[] bytes = Files.readAllBytes(file);
    // Row 0's decoded norm is the big-endian float at offset 96: 28 header bytes, 64 bytes of
    // grid levels, and 4 bytes for row 0's scale.
    writeNaN(bytes, 96);
    final Path patched = dir.resolve("bad-norm.quantized");
    Files.write(patched, bytes);
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));
    assertTrue(e.getMessage().contains("norm"), e.getMessage());
  }

  @Test
  void testNonFinitePoolingWeightIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("valid-weights.quantized");
    QuantizedEmbeddingMatrix.quantize(new float[] {1f, 2f, 3f, 4f}, 1, 4, 4, 17L)
        .withPoolingWeights(new float[] {2f})
        .write(file);
    final byte[] bytes = Files.readAllBytes(file);
    // Row 0's pooling weight is the big-endian float at offset 101, right after the weight
    // presence flag at offset 100.
    writeNaN(bytes, 101);
    final Path patched = dir.resolve("bad-weight.quantized");
    Files.write(patched, bytes);
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));
    assertTrue(e.getMessage().contains("pooling"), e.getMessage());
  }

  @Test
  void testPoolingWeightsDeclaredBeyondFileSizeAreRejectedAsFormatError(@TempDir Path dir)
      throws IOException {
    final Path file = validFile(dir);
    final byte[] bytes = Files.readAllBytes(file);
    // Flipping the presence flag at offset 100 declares per-row pooling weights the file does
    // not contain, so the declared total exceeds the file size.
    bytes[100] = 1;
    final Path patched = dir.resolve("flagged-weights.quantized");
    Files.write(patched, bytes);
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));
    assertTrue(e.getMessage().contains("pooling"), e.getMessage());
  }

  /**
   * {@return a valid one-row, four-dimension, 4-bit quantized file without pooling weights}
   * Its layout is fixed: 28 header bytes, 64 bytes of grid levels, one scale at offset 92, one
   * decoded norm at offset 96, the weight presence flag at offset 100, and two packed code
   * bytes, 103 bytes in total.
   *
   * @param dir The directory to write into.
   * @throws IOException Thrown if writing fails.
   */
  private static Path validFile(Path dir) throws IOException {
    final Path file = dir.resolve("valid.quantized");
    QuantizedEmbeddingMatrix.quantize(new float[] {1f, 2f, 3f, 4f}, 1, 4, 4, 17L).write(file);
    return file;
  }

  /**
   * Overwrites four bytes with the big-endian bits of {@code Float.NaN}.
   *
   * @param bytes  The file image to patch.
   * @param offset The offset of the float to replace.
   */
  private static void writeNaN(byte[] bytes, int offset) {
    final int nan = Float.floatToIntBits(Float.NaN);
    bytes[offset] = (byte) (nan >>> 24);
    bytes[offset + 1] = (byte) (nan >>> 16);
    bytes[offset + 2] = (byte) (nan >>> 8);
    bytes[offset + 3] = (byte) nan;
  }

  private static DataOutputStream out(Path file) throws IOException {
    final OutputStream raw = Files.newOutputStream(file);
    return new DataOutputStream(raw);
  }
}

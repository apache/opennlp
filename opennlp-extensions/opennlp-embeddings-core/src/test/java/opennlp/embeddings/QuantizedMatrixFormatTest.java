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
 * Verifies the loader contract of {@link QuantizedEmbeddingMatrix#read(Path)}: malformed
 * file content produces a checked {@link InvalidFormatException} before an unchecked exception
 * or an allocation failure.
 */
public class QuantizedMatrixFormatTest {

  /** The on-disk magic of a quantized matrix, as written by the writer. */
  private static final int MAGIC = 0x4F4E5132;

  @Test
  void testEarlierFormatVersionIsRejectedByMagic(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("old-version.quantized");
    try (DataOutputStream out = out(file)) {
      out.writeInt(0x4F4E5131);
    }

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(file));

    assertTrue(error.getMessage().contains("magic"), error.getMessage());
  }

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
    // packed codes plus 16 MB of scales and norms. Both dimensions individually pass a
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
    writeFloatNaN(bytes, 28);
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
    // Row 0's decoded norm is the big-endian double at offset 100: 28 header bytes, 64 bytes of
    // grid levels, and 8 bytes for row 0's scale.
    writeDoubleNaN(bytes, 100);
    final Path patched = dir.resolve("bad-norm.quantized");
    Files.write(patched, bytes);
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));
    assertTrue(e.getMessage().contains("norm"), e.getMessage());
  }

  @Test
  void testNegativeScaleIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = validFile(dir);
    final byte[] bytes = Files.readAllBytes(file);
    writeDouble(bytes, 92, -1.0);
    final Path patched = dir.resolve("negative-scale.quantized");
    Files.write(patched, bytes);

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));

    assertTrue(error.getMessage().contains("scale"), error.getMessage());
  }

  @Test
  void testScaleThatWouldOverflowDecodingIsRejectedAsFormatError(@TempDir Path dir)
      throws IOException {
    final Path file = validFile(dir);
    final byte[] bytes = Files.readAllBytes(file);
    writeDouble(bytes, 92, Double.MAX_VALUE);
    final Path patched = dir.resolve("overflowing-scale.quantized");
    Files.write(patched, bytes);

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));

    assertTrue(error.getMessage().contains("scale"), error.getMessage());
  }

  @Test
  void testDecodedNormBeyondFloatVectorRangeIsRejectedAsFormatError(@TempDir Path dir)
      throws IOException {
    final Path file = validFile(dir);
    final byte[] bytes = Files.readAllBytes(file);
    writeDouble(bytes, 100, Double.MAX_VALUE);
    final Path patched = dir.resolve("overflowing-norm.quantized");
    Files.write(patched, bytes);

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));

    assertTrue(error.getMessage().contains("norm"), error.getMessage());
  }

  @Test
  void testZeroScaleWithPositiveNormIsRejectedAsFormatError(@TempDir Path dir)
      throws IOException {
    final Path file = validFile(dir);
    final byte[] bytes = Files.readAllBytes(file);
    writeDouble(bytes, 92, 0.0);
    final Path patched = dir.resolve("inconsistent-zero-scale.quantized");
    Files.write(patched, bytes);

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));

    assertTrue(error.getMessage().contains("scale"), error.getMessage());
    assertTrue(error.getMessage().contains("norm"), error.getMessage());
  }

  @Test
  void testNonFinitePoolingWeightIsRejectedAsFormatError(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("valid-weights.quantized");
    QuantizedEmbeddingMatrix.quantize(new float[] {1f, 2f, 3f, 4f}, 1, 4, 4, 17L)
        .withPoolingWeights(new float[] {2f})
        .write(file);
    final byte[] bytes = Files.readAllBytes(file);
    // Row 0's pooling weight is the big-endian float at offset 109, right after the weight
    // presence flag at offset 108.
    writeFloatNaN(bytes, 109);
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
    // Flipping the presence flag at offset 108 declares per-row pooling weights the file does
    // not contain, so the declared total exceeds the file size.
    bytes[108] = 1;
    final Path patched = dir.resolve("flagged-weights.quantized");
    Files.write(patched, bytes);
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));
    assertTrue(e.getMessage().contains("pooling"), e.getMessage());
  }

  @Test
  void testInvalidPoolingWeightFlagIsRejectedAsFormatError(@TempDir Path dir)
      throws IOException {
    final Path file = dir.resolve("valid-weights.quantized");
    QuantizedEmbeddingMatrix.quantize(new float[] {1f, 2f, 3f, 4f}, 1, 4, 4, 17L)
        .withPoolingWeights(new float[] {2f})
        .write(file);
    final byte[] bytes = Files.readAllBytes(file);
    bytes[108] = 2;
    final Path patched = dir.resolve("bad-weights-flag.quantized");
    Files.write(patched, bytes);

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> QuantizedEmbeddingMatrix.read(patched));

    assertTrue(error.getMessage().contains("flag"), error.getMessage());
  }

  /**
   * {@return a valid one-row, four-dimension, 4-bit quantized file without pooling weights}
   * Its layout is fixed: 28 header bytes, 64 bytes of grid levels, one double scale at offset
   * 92, one double decoded norm at offset 100, the weight presence flag at offset 108, and two
   * packed code bytes, 111 bytes in total.
   *
   * @param dir The directory to write into.
   * @throws IOException Thrown if writing fails.
   */
  private Path validFile(Path dir) throws IOException {
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
  private void writeFloatNaN(byte[] bytes, int offset) {
    final int nan = Float.floatToIntBits(Float.NaN);
    bytes[offset] = (byte) (nan >>> 24);
    bytes[offset + 1] = (byte) (nan >>> 16);
    bytes[offset + 2] = (byte) (nan >>> 8);
    bytes[offset + 3] = (byte) nan;
  }

  /** Overwrites eight bytes with the big-endian bits of {@link Double#NaN}. */
  private void writeDoubleNaN(byte[] bytes, int offset) {
    writeDouble(bytes, offset, Double.NaN);
  }

  private void writeDouble(byte[] bytes, int offset, double value) {
    final long bits = Double.doubleToLongBits(value);
    for (int i = 0; i < Long.BYTES; i++) {
      bytes[offset + i] = (byte) (bits >>> (56 - 8 * i));
    }
  }

  private DataOutputStream out(Path file) throws IOException {
    final OutputStream raw = Files.newOutputStream(file);
    return new DataOutputStream(raw);
  }
}

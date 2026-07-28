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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The writer's output round-trips through the module's own reader, including a matrix larger than
 * one encoding chunk; the bytes it lays down are the safetensors layout, header padded so the
 * data starts aligned; and a shape that does not match the value count is rejected.
 */
class SafetensorsWriterTest {

  /** More floats than fit in one encoding chunk, so the streaming loop runs more than once. */
  private static final int MULTI_CHUNK_ROWS = 400;

  /** The column count of the multi-chunk fixture; rows times columns exceeds 1 MiB of floats. */
  private static final int MULTI_CHUNK_COLS = 1024;

  @Test
  void testRoundTripsThroughTheReader(@TempDir Path dir) throws IOException {
    final float[] values = {1.5f, -2.25f, 3e8f, 0, -0.5f, 42};
    final Path file = dir.resolve(ModelFileNames.SAFETENSORS);

    SafetensorsWriter.writeMatrix(file, 2, 3, values);

    final SafetensorsFile tensors = SafetensorsFile.read(file);
    assertEquals(SafetensorsWriter.EMBEDDINGS_TENSOR, tensors.singleMatrixTensorName());
    assertArrayEquals(new int[] {2, 3}, tensors.tensorInfo(SafetensorsWriter.EMBEDDINGS_TENSOR)
        .shape());
    assertArrayEquals(values, tensors.readFloats(SafetensorsWriter.EMBEDDINGS_TENSOR));
  }

  @Test
  void testRoundTripsAMatrixSpanningSeveralWriteChunks(@TempDir Path dir) throws IOException {
    final float[] values = new float[MULTI_CHUNK_ROWS * MULTI_CHUNK_COLS];
    for (int i = 0; i < values.length; i++) {
      values[i] = i * 0.5f;
    }
    final Path file = dir.resolve(ModelFileNames.SAFETENSORS);

    SafetensorsWriter.writeMatrix(file, MULTI_CHUNK_ROWS, MULTI_CHUNK_COLS, values);

    final SafetensorsFile tensors = SafetensorsFile.read(file);
    assertArrayEquals(new int[] {MULTI_CHUNK_ROWS, MULTI_CHUNK_COLS},
        tensors.tensorInfo(SafetensorsWriter.EMBEDDINGS_TENSOR).shape());
    assertArrayEquals(values, tensors.readFloats(SafetensorsWriter.EMBEDDINGS_TENSOR));
  }

  /**
   * Pins the on-disk layout: an 8-byte little-endian header length, the JSON header, then the
   * values as little-endian {@code F32}, with nothing after them. A change to any of the three
   * fails here rather than in whatever tool reads the distilled model next.
   */
  @Test
  void testWritesTheSafetensorsByteLayout(@TempDir Path dir) throws IOException {
    final float[] values = {1, -2, 0.5f, 0, 7, -0.25f};
    final Path file = dir.resolve(ModelFileNames.SAFETENSORS);

    SafetensorsWriter.writeMatrix(file, 3, 2, values);

    final byte[] bytes = Files.readAllBytes(file);
    final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    final long headerLength = buffer.getLong();
    final byte[] headerBytes = new byte[(int) headerLength];
    buffer.get(headerBytes);
    assertEquals("{\"embeddings\":{\"dtype\":\"F32\",\"shape\":[3,2],\"data_offsets\":[0,24]}}",
        new String(headerBytes, StandardCharsets.UTF_8).stripTrailing());
    assertEquals(Long.BYTES + headerLength + (long) values.length * Float.BYTES, bytes.length,
        "the file is the length prefix, the header, and the values, with nothing after");
    for (int i = 0; i < values.length; i++) {
      assertEquals(values[i], buffer.getFloat(), "value " + i + " must be little-endian F32");
    }
  }

  /**
   * The header is space-padded so the tensor data starts on an 8-byte boundary, the way the
   * reference safetensors writer emits it. The header text length varies with the digits of the
   * shape and the byte count, so every width has to be checked.
   */
  @ParameterizedTest
  @CsvSource({"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "99", "100", "1000"})
  void testPadsTheHeaderToAnEightByteBoundary(int cols, @TempDir Path dir) throws IOException {
    final Path file = dir.resolve(ModelFileNames.SAFETENSORS);

    SafetensorsWriter.writeMatrix(file, 1, cols, new float[cols]);

    final long headerLength = ByteBuffer.wrap(Files.readAllBytes(file))
        .order(ByteOrder.LITTLE_ENDIAN).getLong();
    assertEquals(0, (Long.BYTES + headerLength) % 8, "shape [1," + cols + "] leaves the data "
        + "unaligned at byte " + (Long.BYTES + headerLength));
  }

  @Test
  void testCreatesTheMissingParentDirectory(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("nested").resolve("deeper")
        .resolve(ModelFileNames.SAFETENSORS);

    SafetensorsWriter.writeMatrix(file, 1, 2, new float[] {1, 2});

    assertTrue(Files.isRegularFile(file), file + " must exist");
  }

  @Test
  void testReplacesAnExistingFile(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve(ModelFileNames.SAFETENSORS);
    SafetensorsWriter.writeMatrix(file, 2, 3, new float[6]);

    SafetensorsWriter.writeMatrix(file, 1, 2, new float[] {7, 8});

    final SafetensorsFile tensors = SafetensorsFile.read(file);
    assertArrayEquals(new int[] {1, 2},
        tensors.tensorInfo(SafetensorsWriter.EMBEDDINGS_TENSOR).shape());
    assertArrayEquals(new float[] {7, 8},
        tensors.readFloats(SafetensorsWriter.EMBEDDINGS_TENSOR));
  }

  @Test
  void testRejectsANullFile() {
    assertEquals("File must not be null", assertThrows(IllegalArgumentException.class,
        () -> SafetensorsWriter.writeMatrix(null, 1, 1, new float[1])).getMessage());
  }

  @Test
  void testRejectsNullValues(@TempDir Path dir) {
    assertEquals("Values must not be null", assertThrows(IllegalArgumentException.class,
        () -> SafetensorsWriter.writeMatrix(dir.resolve(ModelFileNames.SAFETENSORS), 1, 1, null))
        .getMessage());
  }

  @ParameterizedTest
  @CsvSource({"0, 2, 2", "2, 0, 2", "-1, 2, 2", "2, 3, 5", "2, 3, 7", "1, 1, 0"})
  void testRejectsAShapeThatDoesNotMatchTheValues(int rows, int cols, int valueCount,
                                                  @TempDir Path dir) {
    final Path file = dir.resolve(ModelFileNames.SAFETENSORS);

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SafetensorsWriter.writeMatrix(file, rows, cols, new float[valueCount]));

    assertEquals("Values has " + valueCount + " elements, not " + rows + " x " + cols,
        e.getMessage());
    assertTrue(Files.notExists(file), "a rejected write must not leave a file behind");
  }
}

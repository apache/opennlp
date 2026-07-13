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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

/**
 * Writes small well-formed safetensors fixtures for tests and benchmarks. Negative tests that
 * need deliberately malformed bytes still hand-roll them.
 */
final class SafetensorsTestFiles {

  /** Not instantiable. */
  private SafetensorsTestFiles() {
  }

  /** One F32 tensor to write: a name, a shape, and the values in row-major order. */
  record Tensor(String name, int[] shape, float[] values) {
  }

  /**
   * {@return a tensor of the given 2-D matrix, row-major}
   *
   * @param name The tensor name.
   * @param rows The matrix rows, each of the same length.
   */
  static Tensor matrix(String name, float[][] rows) {
    final int dimension = rows[0].length;
    final float[] values = new float[rows.length * dimension];
    for (int r = 0; r < rows.length; r++) {
      System.arraycopy(rows[r], 0, values, r * dimension, dimension);
    }
    return new Tensor(name, new int[] {rows.length, dimension}, values);
  }

  static Tensor vector(String name, float[] values) {
    return new Tensor(name, new int[] {values.length}, values);
  }

  /**
   * Writes a safetensors file holding the given tensors as {@code F32}, header first, data in
   * declaration order.
   */
  static void write(Path file, Tensor... tensors) throws IOException {
    write(file, "F32", tensors);
  }

  /**
   * Writes a safetensors file encoding each tensor value as {@code dtype}, one of {@code F32},
   * {@code F16} (IEEE half), or {@code BF16} (bfloat16). The {@link Tensor} values stay
   * {@code float}; they are converted to the target dtype's bytes here.
   */
  static void write(Path file, String dtype, Tensor... tensors) throws IOException {
    final int elementBytes = switch (dtype) {
      case "F32" -> Float.BYTES;
      case "F16", "BF16" -> Short.BYTES;
      default -> throw new IllegalArgumentException("unsupported test dtype: " + dtype);
    };
    final ByteArrayOutputStream data = new ByteArrayOutputStream();
    final StringJoiner header = new StringJoiner(",", "{", "}");
    int offset = 0;
    for (final Tensor tensor : tensors) {
      final ByteBuffer buffer =
          ByteBuffer.allocate(tensor.values().length * elementBytes).order(ByteOrder.LITTLE_ENDIAN);
      for (final float value : tensor.values()) {
        switch (dtype) {
          case "F32" -> buffer.putFloat(value);
          case "F16" -> buffer.putShort(Float.floatToFloat16(value));
          case "BF16" -> buffer.putShort((short) (Float.floatToIntBits(value) >>> 16));
          default -> throw new IllegalArgumentException("unsupported test dtype: " + dtype);
        }
      }
      data.writeBytes(buffer.array());
      final StringJoiner shape = new StringJoiner(",", "[", "]");
      for (final int dimension : tensor.shape()) {
        shape.add(Integer.toString(dimension));
      }
      final int end = offset + tensor.values().length * elementBytes;
      header.add("\"" + tensor.name() + "\":{\"dtype\":\"" + dtype + "\",\"shape\":" + shape
          + ",\"data_offsets\":[" + offset + "," + end + "]}");
      offset = end;
    }
    final byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
    final ByteBuffer out = ByteBuffer.allocate(8 + headerBytes.length + data.size())
        .order(ByteOrder.LITTLE_ENDIAN);
    out.putLong(headerBytes.length);
    out.put(headerBytes);
    out.put(data.toByteArray());
    Files.write(file, out.array());
  }
}
